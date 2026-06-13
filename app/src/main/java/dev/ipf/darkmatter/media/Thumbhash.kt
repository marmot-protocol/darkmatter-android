package dev.ipf.darkmatter.media

import android.graphics.Bitmap
import android.util.Base64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compact perceptual hash for thumbnail placeholders. Port of the reference
 * thumbhash encoder (https://evanw.github.io/thumbhash/) — given a small
 * image (≤100×100), produces a ~20-25 byte hash that decodes to a tiny
 * blurred preview a receiver can render before the full bytes arrive.
 *
 * Marmot's NIP-92 `imeta` accepts a base64 thumbhash field; this encoder
 * matches the canonical wire format so other clients can decode it.
 */
object Thumbhash {
    /**
     * Encode [bitmap] to a base64-no-padding thumbhash string, or null when
     * the bitmap can't be sampled (too small, recycled, or so dim the
     * algorithm degenerates). Caller is responsible for downscaling the
     * source to ≤100×100 — call [encodeFromBitmap] for the convenience
     * wrapper that does the downscale.
     */
    fun encodeBase64(bitmap: Bitmap): String? {
        val raw = encode(bitmap) ?: return null
        return Base64.encodeToString(raw, Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Downscale [source] so the longer edge is ≤ [MAX_EDGE_PX], encode to a
     * thumbhash, base64 the result. Returns null when [source] is blank,
     * recycled, or any intermediate step fails. The downscaled bitmap is
     * recycled before return; the caller's source bitmap is untouched.
     */
    fun encodeFromBitmap(source: Bitmap): String? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        val (w, h) = scaledDimensions(source.width, source.height)
        val sampled =
            if (w == source.width && h == source.height) {
                source
            } else {
                Bitmap.createScaledBitmap(source, w, h, true)
            }
        return try {
            encodeBase64(sampled)
        } finally {
            if (sampled !== source) sampled.recycle()
        }
    }

    internal const val MAX_EDGE_PX: Int = 100

    internal fun scaledDimensions(
        srcW: Int,
        srcH: Int,
    ): Pair<Int, Int> {
        if (srcW <= MAX_EDGE_PX && srcH <= MAX_EDGE_PX) return srcW to srcH
        val longerEdge = max(srcW, srcH)
        val scale = MAX_EDGE_PX.toDouble() / longerEdge.toDouble()
        val w = (srcW * scale).toInt().coerceAtLeast(1)
        val h = (srcH * scale).toInt().coerceAtLeast(1)
        return w to h
    }

    private fun encode(bitmap: Bitmap): ByteArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0 || w > MAX_EDGE_PX || h > MAX_EDGE_PX) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var avgR = 0.0
        var avgG = 0.0
        var avgB = 0.0
        var avgA = 0.0
        for (px in pixels) {
            val a = ((px shr 24) and 0xFF) / 255.0
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            avgR += a / 255.0 * r
            avgG += a / 255.0 * g
            avgB += a / 255.0 * b
            avgA += a
        }
        if (avgA > 0.0) {
            avgR /= avgA
            avgG /= avgA
            avgB /= avgA
        }

        val hasAlpha = avgA < w * h
        val lLimit = if (hasAlpha) 5 else 7
        val maxEdge = max(w, h)
        val lx = max(1, ((lLimit * w).toDouble() / maxEdge).roundToInt())
        val ly = max(1, ((lLimit * h).toDouble() / maxEdge).roundToInt())

        val lCh = DoubleArray(w * h)
        val pCh = DoubleArray(w * h)
        val qCh = DoubleArray(w * h)
        val aCh = DoubleArray(w * h)
        for (i in 0 until w * h) {
            val px = pixels[i]
            val alpha = ((px shr 24) and 0xFF) / 255.0
            val r = avgR * (1.0 - alpha) + alpha / 255.0 * ((px shr 16) and 0xFF)
            val g = avgG * (1.0 - alpha) + alpha / 255.0 * ((px shr 8) and 0xFF)
            val b = avgB * (1.0 - alpha) + alpha / 255.0 * (px and 0xFF)
            lCh[i] = (r + g + b) / 3.0
            pCh[i] = (r + g) / 2.0 - b
            qCh[i] = r - g
            aCh[i] = alpha
        }

        val (lDc, lAc, lScale) = encodeChannel(lCh, w, h, max(3, lx), max(3, ly))
        val (pDc, pAc, pScale) = encodeChannel(pCh, w, h, 3, 3)
        val (qDc, qAc, qScale) = encodeChannel(qCh, w, h, 3, 3)
        val (aDc, aAc, aScale) =
            if (hasAlpha) encodeChannel(aCh, w, h, 5, 5) else Triple(0.0, DoubleArray(0), 0.0)

        val isLandscape = w > h
        val header24 =
            (63.0 * lDc).roundToInt() or
                ((31.5 + 31.5 * pDc).roundToInt() shl 6) or
                ((31.5 + 31.5 * qDc).roundToInt() shl 12) or
                ((31.0 * lScale).roundToInt() shl 18) or
                ((if (hasAlpha) 1 else 0) shl 23)
        val lLengthBits = if (isLandscape) ly else lx
        val header16 =
            lLengthBits or
                ((63.0 * pScale).roundToInt() shl 3) or
                ((63.0 * qScale).roundToInt() shl 9) or
                ((if (isLandscape) 1 else 0) shl 15)
        val acStart = if (hasAlpha) 6 else 5
        val acCount = lAc.size + pAc.size + qAc.size + aAc.size
        val hash = ByteArray(acStart + (acCount + 1) / 2)
        hash[0] = (header24 and 0xFF).toByte()
        hash[1] = ((header24 shr 8) and 0xFF).toByte()
        hash[2] = ((header24 shr 16) and 0xFF).toByte()
        hash[3] = (header16 and 0xFF).toByte()
        hash[4] = ((header16 shr 8) and 0xFF).toByte()
        if (hasAlpha) {
            hash[5] = ((15.0 * aDc).roundToInt() or ((15.0 * aScale).roundToInt() shl 4)).toByte()
        }
        var acIndex = 0
        val acGroups = if (hasAlpha) arrayOf(lAc, pAc, qAc, aAc) else arrayOf(lAc, pAc, qAc)
        for (group in acGroups) {
            for (f in group) {
                val nibble = (15.0 * f).roundToInt() and 0x0F
                val slot = acStart + (acIndex shr 1)
                val shift = (acIndex and 1) shl 2
                hash[slot] = ((hash[slot].toInt() and 0xFF) or (nibble shl shift)).toByte()
                acIndex += 1
            }
        }
        return hash
    }

    private fun encodeChannel(
        channel: DoubleArray,
        w: Int,
        h: Int,
        nx: Int,
        ny: Int,
    ): Triple<Double, DoubleArray, Double> {
        var dc = 0.0
        val ac = ArrayList<Double>(nx * ny)
        var scale = 0.0
        val fx = DoubleArray(w)
        for (cy in 0 until ny) {
            var cx = 0
            while (cx * ny < nx * (ny - cy)) {
                for (x in 0 until w) fx[x] = cos(PI / w * cx * (x + 0.5))
                var f = 0.0
                for (y in 0 until h) {
                    val fy = cos(PI / h * cy * (y + 0.5))
                    for (x in 0 until w) f += channel[x + y * w] * fx[x] * fy
                }
                f /= (w * h).toDouble()
                if (cx > 0 || cy > 0) {
                    ac.add(f * 2.0)
                    if (kotlin.math.abs(f) > scale) scale = kotlin.math.abs(f)
                } else {
                    dc = f
                }
                cx += 1
            }
        }
        val acArr = DoubleArray(ac.size) { ac[it] }
        if (scale > 0.0) {
            for (i in acArr.indices) acArr[i] = 0.5 + 0.5 / scale * acArr[i]
        }
        return Triple(dc, acArr, scale)
    }
}

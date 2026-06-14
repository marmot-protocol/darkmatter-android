package dev.ipf.darkmatter.media

import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Workaround for an upstream gap: the runtime's HTTP client refuses to
 * follow redirects (SSRF defense — see `crates/marmot-app/src/media.rs`'s
 * `redirect::Policy::none()`), so canonical-host → CDN-host setups
 * (e.g. `blossom.primal.net` → `r2a.primal.net`) surface as
 * `BlobStore("download returned HTTP 30x")`.
 *
 * This resolver follows the chain in Kotlin, revalidating each hop with
 * a same-shape SSRF check (HTTPS only, no IP literals, no obvious
 * loopback / private hostnames, bounded redirect count), then hands the
 * resolved canonical URL back to the FFI for a single retry. The runtime
 * still does the actual fetch + decrypt — we just hand it a URL it'll
 * accept on the first try.
 *
 * TODO(darkmatter#413): delete this resolver once the runtime grows a
 * built-in per-hop-validated follow path. The wrapper is intentionally
 * narrow so removing it is a single-file delete plus one call-site swap.
 */
internal object BlossomRedirectResolver {
    private const val TAG = "BlossomRedirectResolver"
    private const val MAX_HOPS = 5
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * Follow up to [MAX_HOPS] redirects starting at [initialUrl]. Returns
     * the final non-redirect URL on success, or null if the chain fails
     * any per-hop SSRF check, exceeds the hop cap, or never settles on a
     * 2xx response. Caller should fall back to the original URL on null —
     * the runtime's original error then propagates as usual.
     */
    fun resolve(initialUrl: String): String? {
        var current = runCatching { URI(initialUrl) }.getOrNull() ?: return null
        if (!isAllowed(current)) return null
        var followed = false
        for (hop in 0 until MAX_HOPS) {
            val (status, location) = runCatching { probeOnce(current) }.getOrElse { return null }
            when (status) {
                in 200..299 -> return if (followed) current.toString() else null
                in 300..399 -> {
                    val raw = location ?: return null
                    val next = runCatching { current.resolve(raw) }.getOrNull() ?: return null
                    if (!isAllowed(next)) {
                        Log.w(TAG, "rejected redirect to $next (failed SSRF check)")
                        return null
                    }
                    current = next
                    followed = true
                }
                else -> return null
            }
        }
        Log.w(TAG, "redirect chain exceeded $MAX_HOPS hops")
        return null
    }

    /**
     * Single GET request that reads headers only — body is never consumed.
     * GET (not HEAD) is required: Blossom servers like primal.net answer
     * HEAD with 200 from the canonical host and only emit the 30x redirect
     * to the CDN host on GET. We still avoid the bandwidth cost by
     * disconnecting before touching the response stream.
     */
    private fun probeOnce(uri: URI): Pair<Int, String?> {
        val conn =
            (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                useCaches = false
            }
        try {
            conn.connect()
            val status = conn.responseCode
            val location = conn.getHeaderField("Location")
            return status to location
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * Per-hop SSRF check. Mirrors the spirit of the runtime's
     * `validate_blossom_fetch_url`: HTTPS only, public host, no IP
     * literals (the runtime separately blocks loopback/private/link-local
     * via DNS-resolution-time checks; here we keep the surface tight by
     * refusing IP literals altogether — Blossom servers worth the name
     * publish via DNS hostnames).
     */
    private fun isAllowed(uri: URI): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        if (host.isEmpty()) return false
        // IPv4-literal rejection — any all-dotted-decimal label.
        if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return false
        // IPv6 literals show up surrounded by brackets in the URI host;
        // refuse those too.
        if (host.startsWith("[")) return false
        // Reject obvious loopback / internal hostnames just in case DNS is
        // pointing at a private space anyway.
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".internal")) return false
        return true
    }
}

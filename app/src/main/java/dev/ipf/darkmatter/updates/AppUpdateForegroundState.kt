package dev.ipf.darkmatter.updates

/** Process-local lifecycle state used to keep app-update OS notifications background-only. */
internal object AppUpdateForegroundState {
    @Volatile
    var isForeground: Boolean = false

    fun shouldPostBackgroundNotification(): Boolean = !isForeground
}

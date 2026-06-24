package dev.ipf.darkmatter.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.ipf.darkmatter.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BackgroundConnectionBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        val appContext = context.applicationContext
        // Off the main thread: isEnabled() loads SharedPreferences synchronously
        // on first access, and onReceive runs on the main thread during the boot
        // broadcast window. goAsync() keeps the process alive until finish().
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val enabled = BackgroundConnectionPreferences.isEnabled(appContext)
                if (!BackgroundConnectionPolicy.shouldStartFromSystemWake(action, enabled)) return@launch
                val started = NotificationStreamForegroundService.start(appContext)
                if (BuildConfig.DEBUG) Log.i("DMForegroundSvc", "system wake action=$action started=$started")
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}

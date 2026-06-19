package dev.ipf.darkmatter.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.ipf.darkmatter.R

/**
 * Creates and maintains the per-type notification channels for #288.
 *
 * Each [NotificationChannelSpec] becomes one OS channel so the user gets native
 * per-type controls (sound, vibration, importance, badge, lockscreen visibility,
 * DND bypass) from the system notification details — no in-app duplication of
 * those toggles. Muting a type is just setting its OS channel to "None".
 *
 * The legacy single channel ([NotificationChannelSpec.LEGACY_MESSAGES_CHANNEL_ID])
 * is deleted here so it doesn't linger as a dead entry; its role is taken over
 * by [NotificationChannelSpec.DIRECT_MESSAGES], which keeps the same
 * IMPORTANCE_HIGH + vibration + private-lockscreen defaults the old channel had.
 */
object NotificationChannels {
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        NotificationChannelSpec.entries.forEach { spec ->
            manager.createNotificationChannel(buildChannel(context, spec))
        }
        // Retire the pre-#288 single channel. Safe to call repeatedly: deleting
        // a missing channel is a no-op. We do this last so a partial run never
        // leaves the user with no message channel at all.
        manager.deleteNotificationChannel(NotificationChannelSpec.LEGACY_MESSAGES_CHANNEL_ID)
    }

    private fun buildChannel(
        context: Context,
        spec: NotificationChannelSpec,
    ): NotificationChannel =
        NotificationChannel(
            spec.id,
            context.getString(spec.nameRes()),
            spec.importance.toAndroidImportance(),
        ).apply {
            description = context.getString(spec.descriptionRes())
            // Preserve the legacy message-channel behaviour for the message
            // channels (vibrate + private lockscreen); reactions/invites inherit
            // OS defaults for their importance and stay private on the lockscreen
            // so a redacted public version is always shown instead of the body.
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            when (spec) {
                NotificationChannelSpec.DIRECT_MESSAGES,
                NotificationChannelSpec.GROUP_MESSAGES,
                -> enableVibration(true)

                NotificationChannelSpec.REACTIONS,
                NotificationChannelSpec.INVITES,
                -> Unit
            }
        }

    private fun NotificationChannelSpec.nameRes(): Int =
        when (this) {
            NotificationChannelSpec.DIRECT_MESSAGES -> R.string.notification_channel_direct_messages
            NotificationChannelSpec.GROUP_MESSAGES -> R.string.notification_channel_group_messages
            NotificationChannelSpec.REACTIONS -> R.string.notification_channel_reactions
            NotificationChannelSpec.INVITES -> R.string.notification_channel_invites
        }

    private fun NotificationChannelSpec.descriptionRes(): Int =
        when (this) {
            NotificationChannelSpec.DIRECT_MESSAGES -> R.string.notification_channel_direct_messages_description
            NotificationChannelSpec.GROUP_MESSAGES -> R.string.notification_channel_group_messages_description
            NotificationChannelSpec.REACTIONS -> R.string.notification_channel_reactions_description
            NotificationChannelSpec.INVITES -> R.string.notification_channel_invites_description
        }

    private fun ChannelImportance.toAndroidImportance(): Int =
        when (this) {
            ChannelImportance.HIGH -> NotificationManager.IMPORTANCE_HIGH
            ChannelImportance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
            ChannelImportance.LOW -> NotificationManager.IMPORTANCE_LOW
        }
}

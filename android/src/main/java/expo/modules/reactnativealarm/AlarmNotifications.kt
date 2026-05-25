package expo.modules.reactnativealarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Builds the alarm notification channel and the ongoing foreground notification.
 *
 * The channel is high importance with alarm audio attributes and DND bypass so
 * the alarm surfaces aggressively. Sound itself is played by [AlarmSoundPlayer]
 * (not the channel) to support consumer-supplied raw resources and looping, so
 * the channel sound is suppressed to avoid double playback.
 */
internal object AlarmNotifications {

  /** Create the alarm channel on API 26+. No-op on older versions. */
  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(AlarmConstants.CHANNEL_ID) != null) return

    val channel = NotificationChannel(
      AlarmConstants.CHANNEL_ID,
      AlarmConstants.CHANNEL_NAME,
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "Time-critical alarm notifications"
      // Sound is handled by AlarmSoundPlayer; silence the channel to avoid overlap.
      setSound(null, alarmAudioAttributes())
      enableVibration(true)
      setBypassDnd(true)
      lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }
    manager.createNotificationChannel(channel)
  }

  /**
   * Build the ongoing foreground notification with a single Stop action.
   *
   * @param stopIntent PendingIntent that delivers [AlarmConstants.ACTION_STOP].
   */
  fun buildForegroundNotification(
    context: Context,
    record: AlarmRecord,
    stopIntent: PendingIntent,
  ): Notification =
    NotificationCompat.Builder(context, AlarmConstants.CHANNEL_ID)
      .setContentTitle(record.title)
      .setContentText(record.body)
      .setSmallIcon(appIcon(context))
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setAutoCancel(false)
      .setOnlyAlertOnce(true)
      .addAction(0, record.stopLabel, stopIntent)
      .build()

  /** Use the consumer app's launcher icon; fall back to a guaranteed-present icon. */
  private fun appIcon(context: Context): Int {
    val appIcon = context.applicationInfo.icon
    return if (appIcon != 0) appIcon else android.R.drawable.ic_lock_idle_alarm
  }

  private fun alarmAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()
}

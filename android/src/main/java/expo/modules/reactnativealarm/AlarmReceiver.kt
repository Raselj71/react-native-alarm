package expo.modules.reactnativealarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Receives the exact-alarm broadcast scheduled by [AlarmScheduler] and promotes
 * it into a foreground [AlarmService] so playback can survive the broadcast's
 * short execution window even when the app is killed.
 *
 * A `BroadcastReceiver.onReceive` runs on the main thread with a strict time
 * budget, so this class does nothing heavy: it only reparses the record and
 * hands off to the service.
 */
internal class AlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val extras = intent.extras ?: return
    val record = AlarmRecord.fromIntentExtras(extras) ?: return

    // A one-shot alarm has now fired; remove it from persistence so it is not
    // re-armed on the next reboot. (Repeat logic, if ever added, lives elsewhere.)
    AlarmStore(context).remove(record.id)

    val serviceIntent = AlarmService.startIntent(context, record)
    ContextCompat.startForegroundService(context, serviceIntent)
  }
}

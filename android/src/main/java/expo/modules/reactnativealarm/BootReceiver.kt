package expo.modules.reactnativealarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms persisted alarms after the device reboots.
 *
 * Android clears all [android.app.AlarmManager] schedules across a restart, so
 * without this the next alarm would never fire until the app is reopened. On
 * boot we re-schedule every still-future record and drop any that elapsed while
 * the device was off.
 *
 * Handles both the normal boot broadcast and the direct-boot
 * `LOCKED_BOOT_COMPLETED` (delivered before the user unlocks on devices with
 * file-based encryption); the config plugin registers both intent filters.
 */
internal class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_LOCKED_BOOT_COMPLETED,
      ACTION_QUICKBOOT_POWERON,
      -> AlarmScheduler.reschedule(context.applicationContext)
    }
  }

  private companion object {
    // Some OEMs (e.g. HTC) send this instead of the standard boot action.
    const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
  }
}

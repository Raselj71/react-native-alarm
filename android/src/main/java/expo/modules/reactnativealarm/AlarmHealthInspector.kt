package expo.modules.reactnativealarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/**
 * Reports whether the three capabilities a reliable alarm depends on are
 * currently granted. Mirrors the `AlarmHealth` shape in the TS contract.
 */
internal object AlarmHealthInspector {

  /** @return map matching `{ notificationsEnabled, canScheduleExactAlarms, ignoreBatteryOptimizations }`. */
  fun inspect(context: Context): Map<String, Any> = mapOf(
    "notificationsEnabled" to notificationsEnabled(context),
    "canScheduleExactAlarms" to canScheduleExactAlarms(context),
    "ignoreBatteryOptimizations" to ignoresBatteryOptimizations(context),
  )

  private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

  /** Always true below API 31, where exact alarms need no special permission. */
  private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    return alarmManager?.canScheduleExactAlarms() ?: false
  }

  private fun ignoresBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
  }
}

package expo.modules.reactnativealarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens the relevant system Settings screens so the user can grant the
 * capabilities an exact, always-fires alarm needs.
 *
 * Each launcher returns `true` if an Activity was started, `false` if it could
 * not be (e.g. unsupported OS version or no resolvable Activity). When an
 * [Activity] is available the intent is launched from it; otherwise it falls
 * back to an application-context launch with `FLAG_ACTIVITY_NEW_TASK`.
 */
internal object AlarmSettingsLauncher {

  /**
   * Exact-alarm permission screen (API 31+). Pre-31 there is nothing to grant,
   * so this returns `false` to signal "no action taken".
   */
  fun openExactAlarmSettings(context: Context, activity: Activity?): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
      .setData(appUri(context))
    return launch(context, activity, intent)
  }

  /**
   * Battery-optimization exemption screen. Prefers the global "ignore battery
   * optimizations" list; falls back to this app's details page on older OSes.
   */
  fun openBatteryOptimizationSettings(context: Context, activity: Activity?): Boolean {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
      appDetailsIntent(context)
    }
    return launch(context, activity, intent)
  }

  /** The app's notification settings (API 26+), else its app-details page. */
  fun openNotificationSettings(context: Context, activity: Activity?): Boolean {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
      appDetailsIntent(context)
    }
    return launch(context, activity, intent)
  }

  // --- internals -------------------------------------------------------------

  private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(appUri(context))

  private fun appUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

  private fun launch(context: Context, activity: Activity?, intent: Intent): Boolean {
    val resolvedFrom = activity ?: context.also {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Guard against devices that don't expose the target Settings Activity.
    if (intent.resolveActivity(context.packageManager) == null) return false
    return runCatching { resolvedFrom.startActivity(intent) }.isSuccess
  }
}

package expo.modules.reactnativealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules, cancels and restores exact alarms via [AlarmManager], and keeps the
 * persisted [AlarmStore] in sync with what is actually scheduled.
 *
 * Each alarm is delivered as a broadcast to [AlarmReceiver]. The PendingIntent
 * request code is derived from the alarm id so that re-scheduling the same id
 * replaces the previous alarm and cancelling can target it precisely.
 */
internal object AlarmScheduler {

  /**
   * Persist [record] and arm an exact alarm for it.
   *
   * Uses [AlarmManager.setExactAndAllowWhileIdle] when exact alarms are
   * permitted (always pre-API 31, gated by `canScheduleExactAlarms` on API 31+),
   * otherwise degrades to [AlarmManager.setAndAllowWhileIdle] which still fires
   * in Doze but without exact timing.
   */
  fun schedule(context: Context, record: AlarmRecord) {
    AlarmStore(context).save(record)

    val alarmManager = context.alarmManager() ?: return
    val pendingIntent = broadcastPendingIntent(context, record)

    if (canScheduleExact(alarmManager)) {
      alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        record.timestampMs,
        pendingIntent,
      )
    } else {
      alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        record.timestampMs,
        pendingIntent,
      )
    }
  }

  /** Cancel the alarm with [id] (if any) and forget it. */
  fun cancel(context: Context, id: String) {
    context.alarmManager()?.cancel(cancellationPendingIntent(context, id))
    AlarmStore(context).remove(id)
  }

  /** Cancel every persisted alarm and clear the store. */
  fun cancelAll(context: Context) {
    val store = AlarmStore(context)
    val alarmManager = context.alarmManager()
    store.all().forEach { record ->
      alarmManager?.cancel(cancellationPendingIntent(context, record.id))
    }
    store.clear()
  }

  /**
   * Re-arm all persisted alarms whose time is still in the future, dropping any
   * that already elapsed. Invoked by [BootReceiver] after the device restarts,
   * since the OS clears scheduled alarms across reboots.
   */
  fun reschedule(context: Context) {
    val store = AlarmStore(context)
    val now = System.currentTimeMillis()
    val (future, past) = store.all().partition { it.timestampMs > now }
    past.forEach { store.remove(it.id) }
    future.forEach { schedule(context, it) }
  }

  // --- internals -------------------------------------------------------------

  private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      alarmManager.canScheduleExactAlarms()
    } else {
      true
    }

  /** PendingIntent carrying the full record, used to arm the alarm. */
  private fun broadcastPendingIntent(context: Context, record: AlarmRecord): PendingIntent {
    val intent = record.writeToIntent(receiverIntent(context))
    return PendingIntent.getBroadcast(
      context,
      record.id.hashCode(),
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }

  /**
   * PendingIntent used only to cancel. It must match the scheduled one on
   * (action, data, component, request code); extras are not part of equality,
   * so a bare receiver intent with the same request code is sufficient.
   */
  private fun cancellationPendingIntent(context: Context, id: String): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      id.hashCode(),
      receiverIntent(context),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun receiverIntent(context: Context): Intent =
    Intent(context, AlarmReceiver::class.java)

  private fun Context.alarmManager(): AlarmManager? =
    getSystemService(Context.ALARM_SERVICE) as? AlarmManager
}

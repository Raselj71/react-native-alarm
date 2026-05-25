package expo.modules.reactnativealarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager

/**
 * Foreground service that owns an alarm's lifetime once it fires.
 *
 * Running in the foreground lets playback and the notification survive even when
 * the host app is killed. Responsibilities:
 *  - show the ongoing notification with a Stop action,
 *  - play the (consumer or default) alarm sound via [AlarmSoundPlayer],
 *  - emit `fired` / `stopped` events through [AlarmEventBridge],
 *  - stop itself when a one-shot sound completes or the user taps Stop.
 *
 * Started by [AlarmReceiver] (fire) and by [ReactNativeAlarmModule.stopSound]
 * (stop, via [stopIntent]).
 */
internal class AlarmService : Service() {

  private val player by lazy { AlarmSoundPlayer(this) }
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == AlarmConstants.ACTION_STOP) {
      handleStop(intent.getStringExtra(AlarmConstants.EXTRA_ID))
      return START_NOT_STICKY
    }

    val record = intent?.extras?.let(AlarmRecord.Companion::fromIntentExtras)
    if (record == null) {
      stopSelf()
      return START_NOT_STICKY
    }

    handleFire(record)
    return START_NOT_STICKY
  }

  /** Show the notification, acquire a brief wake lock and start playback. */
  private fun handleFire(record: AlarmRecord) {
    AlarmNotifications.ensureChannel(this)
    val notification = AlarmNotifications.buildForegroundNotification(
      context = this,
      record = record,
      stopIntent = stopPendingIntent(this, record.id),
    )
    startForeground(AlarmConstants.FOREGROUND_NOTIFICATION_ID, notification)
    acquireWakeLock()

    AlarmEventBridge.emit(AlarmEventBridge.ACTION_FIRED, record.id, record.dataJson)

    player.play(record.sound, record.loopSound) {
      // One-shot finished: tear down the service (also fires onDestroy cleanup).
      stopPlaybackAndSelf()
    }
  }

  /** Stop playback, emit `stopped`, and shut the service down. */
  private fun handleStop(id: String?) {
    if (id != null) {
      AlarmEventBridge.emit(AlarmEventBridge.ACTION_STOPPED, id, null)
    }
    stopPlaybackAndSelf()
  }

  private fun stopPlaybackAndSelf() {
    player.stop()
    releaseWakeLock()
    stopForegroundCompat()
    stopSelf()
  }

  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    wakeLock = powerManager.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK,
      AlarmConstants.WAKE_LOCK_TAG,
    ).apply {
      setReferenceCounted(false)
      // Safety cap so a stuck alarm can never hold the CPU indefinitely.
      acquire(WAKE_LOCK_TIMEOUT_MS)
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
    wakeLock = null
  }

  @Suppress("DEPRECATION")
  private fun stopForegroundCompat() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      stopForeground(true)
    }
  }

  override fun onDestroy() {
    player.stop()
    releaseWakeLock()
    super.onDestroy()
  }

  companion object {

    private const val WAKE_LOCK_TIMEOUT_MS = 60_000L

    /** Intent that starts the service to fire [record]. */
    fun startIntent(context: Context, record: AlarmRecord): Intent =
      record.writeToIntent(Intent(context, AlarmService::class.java))

    /** Intent that tells the service to stop the given alarm. */
    fun stopIntent(context: Context, id: String? = null): Intent =
      Intent(context, AlarmService::class.java).apply {
        action = AlarmConstants.ACTION_STOP
        if (id != null) putExtra(AlarmConstants.EXTRA_ID, id)
      }

    /** PendingIntent wrapping [stopIntent], used by the notification's Stop action. */
    private fun stopPendingIntent(context: Context, id: String): PendingIntent =
      PendingIntent.getService(
        context,
        id.hashCode(),
        stopIntent(context, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
  }
}

package expo.modules.reactnativealarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

/**
 * Wraps a single [MediaPlayer] dedicated to alarm playback.
 *
 * Resolution order for the sound:
 *  1. a raw resource named [AlarmRecord.sound] bundled by the consumer app
 *     (e.g. `res/raw/adhan.mp3` -> sound = "adhan"),
 *  2. the system default alarm ringtone as a fallback.
 *
 * Audio always uses [AudioAttributes.USAGE_ALARM] so it plays at alarm volume
 * and can bypass Do-Not-Disturb on supported devices. Every MediaPlayer call is
 * guarded; audio failures must never crash the foreground service.
 */
internal class AlarmSoundPlayer(private val context: Context) {

  private var mediaPlayer: MediaPlayer? = null

  /**
   * Start playing the alarm sound.
   *
   * @param loop when true, repeats until [stop]; when false, plays once and
   *   invokes [onComplete] so the caller can tear the service down.
   */
  fun play(soundName: String?, loop: Boolean, onComplete: () -> Unit) {
    stop() // never stack players

    val uri = resolveSoundUri(soundName)
    if (uri == null) {
      Log.w(TAG, "No playable alarm sound resolved; skipping playback")
      if (!loop) onComplete()
      return
    }

    runCatching {
      MediaPlayer().apply {
        setAudioAttributes(alarmAudioAttributes())
        setDataSource(context, uri)
        isLooping = loop
        setOnPreparedListener { start() }
        setOnErrorListener { _, what, extra ->
          Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
          true
        }
        if (!loop) {
          setOnCompletionListener { onComplete() }
        }
        prepareAsync()
      }
    }.onSuccess { mediaPlayer = it }
      .onFailure {
        Log.e(TAG, "Failed to start alarm playback", it)
        if (!loop) onComplete()
      }
  }

  /** Stop and release the player. Safe to call repeatedly. */
  fun stop() {
    mediaPlayer?.let { player ->
      runCatching {
        if (player.isPlaying) player.stop()
      }
      runCatching { player.release() }
    }
    mediaPlayer = null
  }

  /** Resolve a consumer raw resource by name, else the default alarm ringtone. */
  private fun resolveSoundUri(soundName: String?): Uri? {
    if (!soundName.isNullOrEmpty()) {
      val resId = context.resources.getIdentifier(soundName, "raw", context.packageName)
      if (resId != 0) {
        return Uri.parse("android.resource://${context.packageName}/$resId")
      }
      Log.w(TAG, "Raw resource '$soundName' not found; using default alarm sound")
    }
    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
  }

  private fun alarmAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()

  private companion object {
    const val TAG = "AlarmSoundPlayer"
  }
}

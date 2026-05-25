package expo.modules.reactnativealarm

/**
 * Central location for every string key, identifier and magic constant used by
 * the Android alarm layer. Keeping them here avoids duplicated string literals
 * scattered across the receiver / service / scheduler and prevents subtle
 * key-mismatch bugs when data is passed through Intents and SharedPreferences.
 */
internal object AlarmConstants {

  /** Intent action that tells [AlarmService] to stop playback and shut down. */
  const val ACTION_STOP = "expo.modules.reactnativealarm.action.STOP"

  // --- Intent extras (also used as JSON keys in persistence) -----------------
  const val EXTRA_ID = "id"
  const val EXTRA_TIMESTAMP = "timestampMs"
  const val EXTRA_TITLE = "title"
  const val EXTRA_BODY = "body"
  const val EXTRA_SOUND = "sound"
  const val EXTRA_LOOP = "loopSound"
  const val EXTRA_STOP_LABEL = "stopLabel"
  const val EXTRA_DATA = "dataJson"

  /** Key of the raw `data` object on the JS input map (before JSON-encoding). */
  const val EXTRA_DATA_INPUT = "data"

  /** Notification channel that hosts the foreground alarm notification. */
  const val CHANNEL_ID = "react-native-alarm-channel"
  const val CHANNEL_NAME = "Alarms"

  /** Fixed id for the single foreground notification the service shows. */
  const val FOREGROUND_NOTIFICATION_ID = 0x4A41 // arbitrary, stable

  /** SharedPreferences file backing [AlarmStore]. */
  const val PREFS_NAME = "react-native-alarm.store"

  /** Default Stop-action label when the consumer does not supply one. */
  const val DEFAULT_STOP_LABEL = "Stop"

  /** Tag used for the brief wake lock held while the alarm fires. */
  const val WAKE_LOCK_TAG = "react-native-alarm:fire"
}

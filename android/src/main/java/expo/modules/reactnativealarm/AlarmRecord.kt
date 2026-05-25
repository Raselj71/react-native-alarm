package expo.modules.reactnativealarm

import android.content.Intent
import android.os.Bundle
import org.json.JSONObject

/**
 * Immutable description of a single scheduled alarm.
 *
 * The same record travels through three boundaries:
 *  - JS -> native (parsed from the `scheduleAlarm` argument map),
 *  - native persistence ([AlarmStore], serialized as JSON),
 *  - process -> process via [Intent] extras (scheduler -> receiver -> service).
 *
 * @property dataJson Opaque consumer payload, kept as a JSON string so it can be
 *   round-tripped without imposing a schema. Echoed back in alarm events.
 */
internal data class AlarmRecord(
  val id: String,
  val timestampMs: Long,
  val title: String,
  val body: String,
  val sound: String?,
  val loopSound: Boolean,
  val stopLabel: String,
  val dataJson: String?,
) {

  /** Serialize for persistence in [AlarmStore]. */
  fun toJson(): JSONObject = JSONObject().apply {
    put(AlarmConstants.EXTRA_ID, id)
    put(AlarmConstants.EXTRA_TIMESTAMP, timestampMs)
    put(AlarmConstants.EXTRA_TITLE, title)
    put(AlarmConstants.EXTRA_BODY, body)
    put(AlarmConstants.EXTRA_SOUND, sound ?: JSONObject.NULL)
    put(AlarmConstants.EXTRA_LOOP, loopSound)
    put(AlarmConstants.EXTRA_STOP_LABEL, stopLabel)
    put(AlarmConstants.EXTRA_DATA, dataJson ?: JSONObject.NULL)
  }

  /** Copy this record's fields onto an [Intent] as extras. */
  fun writeToIntent(intent: Intent): Intent = intent.apply {
    putExtra(AlarmConstants.EXTRA_ID, id)
    putExtra(AlarmConstants.EXTRA_TIMESTAMP, timestampMs)
    putExtra(AlarmConstants.EXTRA_TITLE, title)
    putExtra(AlarmConstants.EXTRA_BODY, body)
    putExtra(AlarmConstants.EXTRA_SOUND, sound)
    putExtra(AlarmConstants.EXTRA_LOOP, loopSound)
    putExtra(AlarmConstants.EXTRA_STOP_LABEL, stopLabel)
    putExtra(AlarmConstants.EXTRA_DATA, dataJson)
  }

  companion object {

    /** Rebuild a record from persisted JSON. Returns null if the id is missing. */
    fun fromJson(json: JSONObject): AlarmRecord? {
      val id = json.optString(AlarmConstants.EXTRA_ID).takeIf { it.isNotEmpty() } ?: return null
      return AlarmRecord(
        id = id,
        timestampMs = json.optLong(AlarmConstants.EXTRA_TIMESTAMP),
        title = json.optString(AlarmConstants.EXTRA_TITLE),
        body = json.optString(AlarmConstants.EXTRA_BODY),
        sound = json.optStringOrNull(AlarmConstants.EXTRA_SOUND),
        loopSound = json.optBoolean(AlarmConstants.EXTRA_LOOP, false),
        stopLabel = json.optString(AlarmConstants.EXTRA_STOP_LABEL, AlarmConstants.DEFAULT_STOP_LABEL),
        dataJson = json.optStringOrNull(AlarmConstants.EXTRA_DATA),
      )
    }

    /** Rebuild a record from [Intent] extras delivered to the receiver/service. */
    fun fromIntentExtras(extras: Bundle): AlarmRecord? {
      val id = extras.getString(AlarmConstants.EXTRA_ID) ?: return null
      return AlarmRecord(
        id = id,
        timestampMs = extras.getLong(AlarmConstants.EXTRA_TIMESTAMP),
        title = extras.getString(AlarmConstants.EXTRA_TITLE) ?: "",
        body = extras.getString(AlarmConstants.EXTRA_BODY) ?: "",
        sound = extras.getString(AlarmConstants.EXTRA_SOUND),
        loopSound = extras.getBoolean(AlarmConstants.EXTRA_LOOP, false),
        stopLabel = extras.getString(AlarmConstants.EXTRA_STOP_LABEL) ?: AlarmConstants.DEFAULT_STOP_LABEL,
        dataJson = extras.getString(AlarmConstants.EXTRA_DATA),
      )
    }

    /**
     * Build a record from the loosely-typed map Expo delivers for the JS
     * `AlarmInput`. The `data` object is flattened into a JSON string so it can
     * be persisted and echoed back verbatim. Returns null if `id` is missing.
     */
    fun fromJsMap(map: Map<String, Any?>): AlarmRecord? {
      val id = (map[AlarmConstants.EXTRA_ID] as? String)?.takeIf { it.isNotEmpty() } ?: return null
      val timestamp = (map[AlarmConstants.EXTRA_TIMESTAMP] as? Number)?.toLong() ?: return null
      val data = map[AlarmConstants.EXTRA_DATA_INPUT]
      return AlarmRecord(
        id = id,
        timestampMs = timestamp,
        title = map[AlarmConstants.EXTRA_TITLE] as? String ?: "",
        body = map[AlarmConstants.EXTRA_BODY] as? String ?: "",
        sound = (map[AlarmConstants.EXTRA_SOUND] as? String)?.takeIf { it.isNotEmpty() },
        loopSound = map[AlarmConstants.EXTRA_LOOP] as? Boolean ?: false,
        stopLabel = (map[AlarmConstants.EXTRA_STOP_LABEL] as? String)?.takeIf { it.isNotEmpty() }
          ?: AlarmConstants.DEFAULT_STOP_LABEL,
        dataJson = dataToJson(data),
      )
    }

    /** Serialize a JS `data` value (map/list/scalar) to a JSON string, or null. */
    private fun dataToJson(data: Any?): String? = when (data) {
      null -> null
      is Map<*, *> -> JSONObject(data.entries.associate { (k, v) -> k.toString() to v }).toString()
      else -> runCatching { JSONObject().put("value", data).toString() }.getOrNull()
    }

    /** Treat JSON `null` (and missing keys) as a Kotlin null instead of the string "null". */
    private fun JSONObject.optStringOrNull(key: String): String? =
      if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotEmpty() }
  }
}

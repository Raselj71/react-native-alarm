package expo.modules.reactnativealarm

import android.content.Context
import org.json.JSONObject

/**
 * SharedPreferences-backed persistence for scheduled alarms.
 *
 * Records are stored as a single JSON object keyed by alarm id, which makes
 * lookups, replacement and removal O(1) on id. Persistence lets [BootReceiver]
 * restore exact alarms after a reboot (the OS clears them) and lets
 * [AlarmScheduler] cancel by id without the caller re-supplying the full record.
 *
 * The class is intentionally small and pure: it only reads/writes the prefs and
 * (de)serializes [AlarmRecord]. No scheduling logic lives here.
 */
internal class AlarmStore(context: Context) {

  private val prefs = context.applicationContext
    .getSharedPreferences(AlarmConstants.PREFS_NAME, Context.MODE_PRIVATE)

  /** Persist (or replace) a record keyed by its id. */
  fun save(record: AlarmRecord) {
    val root = readRoot()
    root.put(record.id, record.toJson())
    writeRoot(root)
  }

  /** Remove the record with [id]. No-op if absent. */
  fun remove(id: String) {
    val root = readRoot()
    if (root.has(id)) {
      root.remove(id)
      writeRoot(root)
    }
  }

  /** Drop every persisted record. */
  fun clear() {
    prefs.edit().remove(KEY_RECORDS).apply()
  }

  /** All persisted records. Malformed entries are skipped defensively. */
  fun all(): List<AlarmRecord> {
    val root = readRoot()
    val out = ArrayList<AlarmRecord>(root.length())
    val keys = root.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      val obj = root.optJSONObject(key) ?: continue
      AlarmRecord.fromJson(obj)?.let(out::add)
    }
    return out
  }

  private fun readRoot(): JSONObject =
    runCatching { JSONObject(prefs.getString(KEY_RECORDS, "{}") ?: "{}") }
      .getOrElse { JSONObject() }

  private fun writeRoot(root: JSONObject) {
    prefs.edit().putString(KEY_RECORDS, root.toString()).apply()
  }

  private companion object {
    const val KEY_RECORDS = "records"
  }
}

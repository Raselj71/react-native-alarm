package expo.modules.reactnativealarm

import org.json.JSONObject

/**
 * One-way bridge that lets background components (the [AlarmReceiver] and
 * [AlarmService]) push `onAlarmEvent` events back to JS.
 *
 * The catch: those components run via the OS even when no JS runtime is alive
 * (app killed / cold-started by the alarm). They cannot hold an Expo module
 * reference directly. So [ReactNativeAlarmModule] registers a lightweight
 * [Emitter] callback here while it is alive, and clears it when destroyed.
 *
 * When an event is dispatched:
 *  - if the module is alive, the event reaches JS;
 *  - if not, [emit] is a silent no-op — the foreground notification is still
 *    shown by the service, so the user is never left without feedback.
 *
 * All access is synchronized because events originate on the main thread while
 * the module lifecycle callbacks may run on another.
 */
internal object AlarmEventBridge {

  /** Implemented by the live module to forward a payload to JS. */
  fun interface Emitter {
    fun emit(action: String, id: String, data: JSONObject?)
  }

  @Volatile
  private var emitter: Emitter? = null

  /** Called from [ReactNativeAlarmModule.OnCreate]. */
  @Synchronized
  fun register(emitter: Emitter) {
    this.emitter = emitter
  }

  /** Called from [ReactNativeAlarmModule.OnDestroy]; clears stale references. */
  @Synchronized
  fun unregister(emitter: Emitter) {
    if (this.emitter === emitter) {
      this.emitter = null
    }
  }

  /**
   * Forward an event to JS if a module is currently listening.
   *
   * @param action one of `fired` | `stopped` | `opened` (matches the TS union).
   * @param id     the alarm id this event refers to.
   * @param dataJson optional consumer payload to echo back, as a JSON string.
   */
  fun emit(action: String, id: String, dataJson: String?) {
    val data = dataJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    emitter?.emit(action, id, data)
  }

  // Event action names mirrored from ReactNativeAlarm.types.ts.
  const val ACTION_FIRED = "fired"
  const val ACTION_STOPPED = "stopped"
  const val ACTION_OPENED = "opened"

  /** The single JS event name the module exposes. */
  const val EVENT_NAME = "onAlarmEvent"
}

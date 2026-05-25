package expo.modules.reactnativealarm

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import expo.modules.interfaces.permissions.PermissionsResponse
import expo.modules.interfaces.permissions.PermissionsStatus
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject

/**
 * Expo module surface for the Android alarm layer.
 *
 * Responsibilities are deliberately thin here: each function validates inputs,
 * obtains a [Context]/Activity, and delegates to a focused collaborator
 * ([AlarmScheduler], [AlarmService], [AlarmHealthInspector], [AlarmSettingsLauncher]).
 *
 * ### Service -> JS event bridge
 * The [AlarmReceiver] and [AlarmService] run via the OS and may execute while no
 * JS runtime exists (app killed). They cannot hold a module reference, so they
 * publish events through the process-wide [AlarmEventBridge]. While this module
 * is alive it registers an emitter that forwards those events to JS via
 * [sendEvent]; on destroy it unregisters. If no module is alive the emit is a
 * no-op and only the foreground notification is shown — which is acceptable.
 */
class ReactNativeAlarmModule : Module() {

  /** Forwards background events to JS; registered for this module's lifetime. */
  private val eventEmitter = AlarmEventBridge.Emitter { action, id, data ->
    sendEvent(AlarmEventBridge.EVENT_NAME, alarmEventBody(action, id, data))
  }

  /** Non-null app context or a thrown Expo exception (module not ready). */
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("ReactNativeAlarm")

    Events(AlarmEventBridge.EVENT_NAME)

    OnCreate {
      AlarmEventBridge.register(eventEmitter)
    }

    OnDestroy {
      AlarmEventBridge.unregister(eventEmitter)
    }

    AsyncFunction("scheduleAlarm") { input: Map<String, Any?> ->
      val record = AlarmRecord.fromJsMap(input)
        ?: throw IllegalArgumentException("scheduleAlarm requires a string 'id' and numeric 'timestampMs'")
      AlarmScheduler.schedule(context, record)
      true
    }

    AsyncFunction("cancelAlarm") { id: String ->
      AlarmScheduler.cancel(context, id)
      true
    }

    AsyncFunction("cancelAll") {
      AlarmScheduler.cancelAll(context)
      true
    }

    AsyncFunction("stopSound") {
      ContextCompat.startForegroundService(context, AlarmService.stopIntent(context))
    }

    AsyncFunction("getAlarmHealth") {
      AlarmHealthInspector.inspect(context)
    }

    AsyncFunction("requestPermissions") { promise: Promise ->
      requestNotificationPermission(promise)
    }

    AsyncFunction("openExactAlarmSettings") {
      AlarmSettingsLauncher.openExactAlarmSettings(context, appContext.currentActivity)
    }

    AsyncFunction("openBatteryOptimizationSettings") {
      AlarmSettingsLauncher.openBatteryOptimizationSettings(context, appContext.currentActivity)
    }

    AsyncFunction("openNotificationSettings") {
      AlarmSettingsLauncher.openNotificationSettings(context, appContext.currentActivity)
    }
  }

  /**
   * Request POST_NOTIFICATIONS on API 33+. On older versions the permission is
   * implicitly granted, so we resolve with the current notifications-enabled
   * state. Resolves `true` only when notifications can actually be posted.
   */
  private fun requestNotificationPermission(promise: Promise) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      promise.resolve(AlarmHealthInspector.inspect(context)["notificationsEnabled"] == true)
      return
    }

    val permissions = appContext.permissions
    if (permissions == null) {
      // No permissions manager available; report current state rather than fail.
      promise.resolve(AlarmHealthInspector.inspect(context)["notificationsEnabled"] == true)
      return
    }

    permissions.askForPermissions(
      { results: Map<String, PermissionsResponse> ->
        val granted = results[Manifest.permission.POST_NOTIFICATIONS]?.status ==
          PermissionsStatus.GRANTED
        promise.resolve(granted)
      },
      Manifest.permission.POST_NOTIFICATIONS,
    )
  }

  /** Build the `onAlarmEvent` payload, attaching `data` only when present. */
  private fun alarmEventBody(action: String, id: String, data: JSONObject?): Map<String, Any?> {
    val body = HashMap<String, Any?>(3)
    body["action"] = action
    body["id"] = id
    if (data != null) {
      body["data"] = data.toMap()
    }
    return body
  }

  /** Shallow conversion of a [JSONObject] to a map Expo can serialize to JS. */
  private fun JSONObject.toMap(): Map<String, Any?> {
    val out = HashMap<String, Any?>(length())
    keys().forEach { key ->
      out[key] = if (isNull(key)) null else get(key)
    }
    return out
  }
}

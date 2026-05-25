import ExpoModulesCore
import UserNotifications
import UIKit

// ────────────────────────────────────────────────────────────────────────────
// iOS killed-state limitation
// ────────────────────────────────────────────────────────────────────────────
// When the app is KILLED (not just backgrounded), iOS does not allow arbitrary
// code execution on a notification fire. The OS will play only a short,
// pre-bundled sound attached to the UNNotificationContent (≤30s, .wav/.caf
// recommended). There is NO way to start AVAudioPlayer, run background tasks,
// or present custom UI from a killed state without a push notification + a
// background-execution entitlement that Apple does not grant to general-purpose
// alarm apps.
//
// Therefore this library's iOS strategy is:
//   • scheduleAlarm always schedules a UNNotificationRequest.  This handles
//     the background/killed/locked-screen case with a bounded notification sound.
//   • When the app is FOREGROUND, scheduleAlarm additionally starts an
//     AVAudioPlayer (via AlarmAudioPlayer) for full/looping audio.
//   • stopSound stops the AVAudioPlayer (foreground only).
//   • Notification-action events ('stopped' / 'opened') require a
//     UNUserNotificationCenterDelegate — see delegation notes below.
// ────────────────────────────────────────────────────────────────────────────

// ────────────────────────────────────────────────────────────────────────────
// UNUserNotificationCenterDelegate conflict note
// ────────────────────────────────────────────────────────────────────────────
// UNUserNotificationCenter allows only ONE delegate.  In an Expo / React Native
// app that delegate is usually owned by expo-notifications (or the host app).
// Forcibly replacing it would silently break the app's own notification handling.
//
// This library therefore takes a non-destructive approach:
//   • On init it checks whether a delegate is already set.
//   • If NONE exists, it installs AlarmNotificationDelegate (a private inner
//     helper) as the delegate, which emits 'fired' on foreground delivery,
//     'stopped' on the Stop action, and 'opened' on tap.
//   • If a delegate IS already set, this library does NOT replace it and the
//     'stopped'/'opened' events will NOT be emitted from iOS.  scheduleAlarm
//     and audio playback continue to work fully; only the response events are
//     unavailable in that configuration.
//
// Consumer guidance: mount this module before expo-notifications initialises
// its own delegate if you need response events on iOS, OR handle alarm
// notification responses in your own app delegate and call stopSound() manually.
// ────────────────────────────────────────────────────────────────────────────

// MARK: - Constants

private enum AlarmConstants {
  /// UNNotificationCategory identifier for alarm notifications.
  static let categoryID = "RNA_ALARM"
  /// UNNotificationAction identifier for the Stop action.
  static let stopActionID = "RNA_STOP"
  /// Default label for the Stop action button.
  static let defaultStopLabel = "Stop"
  /// UserDefaults key under which scheduled alarm IDs are persisted.
  static let scheduledIdsKey = "ReactNativeAlarm.scheduledIds"
  /// Event name emitted to JS.
  static let eventName = "onAlarmEvent"
  /// UserInfo keys stored in the notification request.
  static let userInfoKeyID = "rna_id"
  static let userInfoKeyLoopSound = "rna_loopSound"
  static let userInfoKeySound = "rna_sound"
  static let userInfoKeyData = "rna_data"
}

// MARK: - Module

public class ReactNativeAlarmModule: Module {

  /// Foreground audio player – lives for the module's lifetime.
  private let audioPlayer = AlarmAudioPlayer()

  /// Whether we installed our own delegate (so we know to clean up on destroy).
  private var ownsDelegateSlot = false

  public func definition() -> ModuleDefinition {
    Name("ReactNativeAlarm")

    Events(AlarmConstants.eventName)

    // ── Lifecycle ────────────────────────────────────────────────────────────

    OnCreate {
      self.registerNotificationCategory()
      self.installDelegateIfFree()
    }

    OnDestroy {
      // Only nil-out the delegate if WE installed it; don't touch an external one.
      if self.ownsDelegateSlot {
        UNUserNotificationCenter.current().delegate = nil
        self.ownsDelegateSlot = false
      }
      self.audioPlayer.stop()
    }

    // ── scheduleAlarm ────────────────────────────────────────────────────────

    AsyncFunction("scheduleAlarm") { (input: [String: Any], promise: Promise) in
      guard
        let id = input["id"] as? String, !id.isEmpty,
        let timestampMs = (input["timestampMs"] as? NSNumber)?.doubleValue
      else {
        promise.reject(
          "E_INVALID_INPUT",
          "scheduleAlarm requires a non-empty string 'id' and numeric 'timestampMs'."
        )
        return
      }

      let title = input["title"] as? String ?? ""
      let body = input["body"] as? String ?? ""
      let sound = (input["sound"] as? String)?.nilIfEmpty
      let loopSound = input["loopSound"] as? Bool ?? false
      let stopLabel = (input["stopLabel"] as? String)?.nilIfEmpty ?? AlarmConstants.defaultStopLabel
      let data = input["data"] as? [String: Any]

      // Re-register category with the (possibly custom) stop label.
      // UNUserNotificationCenter de-dupes by category id, so calling this
      // repeatedly is safe and ensures the latest stopLabel is always used.
      self.registerNotificationCategory(stopLabel: stopLabel)

      // Build notification content.
      let content = UNMutableNotificationContent()
      content.title = title
      content.body = body
      content.categoryIdentifier = AlarmConstants.categoryID

      // Attach metadata so the delegate / consumer can identify the alarm.
      var userInfo: [String: Any] = [
        AlarmConstants.userInfoKeyID: id,
        AlarmConstants.userInfoKeyLoopSound: loopSound,
      ]
      if let s = sound { userInfo[AlarmConstants.userInfoKeySound] = s }
      if let d = data { userInfo[AlarmConstants.userInfoKeyData] = d }
      content.userInfo = userInfo

      // Sound: use named sound if provided, otherwise the default critical alert.
      // Note for consumers: pass the full filename including extension, e.g.
      // "adhan.wav" or "alarm.caf". The file must be bundled in the app target.
      // Sounds longer than ~30s will be truncated by the OS.
      if let soundName = sound {
        content.sound = UNNotificationSound(named: UNNotificationSoundName(rawValue: soundName))
      } else {
        // .defaultCritical bypasses the ringer switch; requires entitlement on
        // iOS 15+. Fall back to .default if it fails at runtime (EAS will warn).
        content.sound = .defaultCritical
      }

      // Trigger: fire at the requested time (minimum 1-second delay required by
      // the OS — earlier timestamps are clamped to 1s).
      let nowSeconds = Date().timeIntervalSince1970
      let fireSeconds = timestampMs / 1000.0
      let delay = max(1.0, fireSeconds - nowSeconds)
      let trigger = UNTimeIntervalNotificationTrigger(timeInterval: delay, repeats: false)

      let request = UNNotificationRequest(
        identifier: id,
        content: content,
        trigger: trigger
      )

      UNUserNotificationCenter.current().add(request) { [weak self] error in
        if let error = error {
          promise.reject("E_SCHEDULE_FAILED", error.localizedDescription)
          return
        }
        self?.persistScheduledId(id)

        // Foreground path: if the app is active, also start the audio player so
        // the consumer gets full / looping playback without waiting for the
        // notification sound (which doesn't play in the foreground by default).
        DispatchQueue.main.async {
          let appState = UIApplication.shared.applicationState
          if appState == .active, let soundName = sound {
            self?.audioPlayer.play(soundName: soundName, loop: loopSound)
          }
        }

        promise.resolve(true)
      }
    }

    // ── cancelAlarm ──────────────────────────────────────────────────────────

    AsyncFunction("cancelAlarm") { (id: String) -> Bool in
      UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
      self.removePersistedId(id)
      return true
    }

    // ── cancelAll ────────────────────────────────────────────────────────────

    AsyncFunction("cancelAll") { () -> Bool in
      UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
      self.clearPersistedIds()
      return true
    }

    // ── stopSound ────────────────────────────────────────────────────────────
    // Stops foreground AVAudioPlayer. Has no effect in background/killed states
    // (the notification sound is managed by the OS and stops automatically).

    AsyncFunction("stopSound") { () -> Void in
      self.audioPlayer.stop()
    }

    // ── getAlarmHealth ───────────────────────────────────────────────────────

    AsyncFunction("getAlarmHealth") { (promise: Promise) in
      UNUserNotificationCenter.current().getNotificationSettings { settings in
        let enabled = settings.authorizationStatus == .authorized ||
                      settings.authorizationStatus == .provisional
        promise.resolve([
          "notificationsEnabled": enabled,
          // iOS has no "exact alarm" permission — exact delivery via
          // UNTimeIntervalNotificationTrigger is available by default.
          "canScheduleExactAlarms": true,
          // iOS has no battery optimisation whitelist concept.
          "ignoreBatteryOptimizations": true,
        ] as [String: Any])
      }
    }

    // ── requestPermissions ───────────────────────────────────────────────────

    AsyncFunction("requestPermissions") { (promise: Promise) in
      UNUserNotificationCenter.current().requestAuthorization(
        options: [.alert, .sound, .badge]
      ) { granted, error in
        if let error = error {
          print("[ReactNativeAlarm] requestPermissions error: \(error)")
        }
        promise.resolve(granted)
      }
    }

    // ── Settings openers ────────────────────────────────────────────────────
    // All three variants open the app's Settings page — iOS does not have
    // separate screens for exact alarms or battery optimisation.

    AsyncFunction("openExactAlarmSettings") { () -> Bool in
      return self.openAppSettings()
    }

    AsyncFunction("openBatteryOptimizationSettings") { () -> Bool in
      return self.openAppSettings()
    }

    AsyncFunction("openNotificationSettings") { () -> Bool in
      return self.openAppSettings()
    }
  }

  // MARK: - Notification category registration

  /// Register (or re-register) the alarm UNNotificationCategory.
  ///
  /// This must be called before any alarm is scheduled so iOS knows how to
  /// render the Stop action button. Calling it multiple times is safe.
  ///
  /// - Parameter stopLabel: Label for the Stop action; defaults to "Stop".
  private func registerNotificationCategory(stopLabel: String = AlarmConstants.defaultStopLabel) {
    let stopAction = UNNotificationAction(
      identifier: AlarmConstants.stopActionID,
      title: stopLabel,
      options: [.destructive, .authenticationRequired]
    )
    let alarmCategory = UNNotificationCategory(
      identifier: AlarmConstants.categoryID,
      actions: [stopAction],
      intentIdentifiers: [],
      options: [.customDismissAction]
    )
    UNUserNotificationCenter.current().setNotificationCategories([alarmCategory])
  }

  // MARK: - Delegate installation

  /// Install our response delegate only when no delegate exists.
  private func installDelegateIfFree() {
    let center = UNUserNotificationCenter.current()
    guard center.delegate == nil else {
      // Another component (expo-notifications, app delegate) already owns the
      // delegate slot. We respect that and skip installation. As a consequence,
      // 'stopped' and 'opened' events will NOT be emitted on iOS in this config.
      // See the delegate conflict note at the top of this file.
      print("[ReactNativeAlarm] UNUserNotificationCenter delegate already set; " +
            "skipping ReactNativeAlarm delegate installation. " +
            "'stopped'/'opened' events will not be emitted.")
      return
    }
    let delegate = AlarmNotificationDelegate { [weak self] eventPayload in
      self?.sendEvent(AlarmConstants.eventName, eventPayload)
    }
    center.delegate = delegate
    // Retain the delegate via associated storage — UNUserNotificationCenter only
    // holds a weak reference, so we must keep it alive ourselves.
    objc_setAssociatedObject(
      self,
      &AssociatedKeys.delegateKey,
      delegate,
      .OBJC_ASSOCIATION_RETAIN_NONATOMIC
    )
    ownsDelegateSlot = true
  }

  // MARK: - UserDefaults persistence

  /// Persist a scheduled alarm identifier so cancelAll can enumerate them.
  private func persistScheduledId(_ id: String) {
    var ids = UserDefaults.standard.stringArray(forKey: AlarmConstants.scheduledIdsKey) ?? []
    if !ids.contains(id) { ids.append(id) }
    UserDefaults.standard.set(ids, forKey: AlarmConstants.scheduledIdsKey)
  }

  private func removePersistedId(_ id: String) {
    var ids = UserDefaults.standard.stringArray(forKey: AlarmConstants.scheduledIdsKey) ?? []
    ids.removeAll { $0 == id }
    UserDefaults.standard.set(ids, forKey: AlarmConstants.scheduledIdsKey)
  }

  private func clearPersistedIds() {
    UserDefaults.standard.removeObject(forKey: AlarmConstants.scheduledIdsKey)
  }

  // MARK: - Settings

  /// Open the app's own Settings URL (the only deep-link iOS exposes to apps).
  @discardableResult
  private func openAppSettings() -> Bool {
    guard let url = URL(string: UIApplication.openSettingsURLString) else { return false }
    DispatchQueue.main.async {
      UIApplication.shared.open(url)
    }
    return true
  }
}

// MARK: - Associated object key storage

private enum AssociatedKeys {
  static var delegateKey = "ReactNativeAlarm.delegateKey"
}

// MARK: - AlarmNotificationDelegate

/// A UNUserNotificationCenterDelegate that emits alarm events to JS.
///
/// Installed only when no other delegate owns the slot — see the conflict note
/// at the top of ReactNativeAlarmModule.swift.
private final class AlarmNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {

  /// Called by the module to forward events to JS.
  private let emit: ([String: Any]) -> Void

  init(emit: @escaping ([String: Any]) -> Void) {
    self.emit = emit
  }

  // ── Foreground notification display ─────────────────────────────────────
  // When the app is FOREGROUND and a notification arrives, iOS calls this method
  // to ask how the notification should be presented.  We show it (so the user
  // can tap the Stop action) and emit a 'fired' event.

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    let info = notification.request.content.userInfo
    guard let id = info[AlarmConstants.userInfoKeyID] as? String else {
      completionHandler([.banner, .sound, .badge])
      return
    }

    let dataPayload = info[AlarmConstants.userInfoKeyData] as? [String: Any]
    emit(alarmEventBody(action: "fired", id: id, data: dataPayload))

    // Show the banner + play sound so the user can see and dismiss it.
    completionHandler([.banner, .sound, .badge])
  }

  // ── Notification response (tap / action) ────────────────────────────────

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    let info = response.notification.request.content.userInfo
    guard let id = info[AlarmConstants.userInfoKeyID] as? String else {
      completionHandler()
      return
    }

    let dataPayload = info[AlarmConstants.userInfoKeyData] as? [String: Any]

    let action: String
    switch response.actionIdentifier {
    case AlarmConstants.stopActionID:
      // User tapped the Stop action button.
      action = "stopped"
    case UNNotificationDefaultActionIdentifier:
      // User tapped the notification body — opened the app.
      action = "opened"
    case UNNotificationDismissActionIdentifier:
      // User dismissed the notification; no JS event needed.
      completionHandler()
      return
    default:
      // Unknown action — treat as opened.
      action = "opened"
    }

    emit(alarmEventBody(action: action, id: id, data: dataPayload))
    completionHandler()
  }

  // MARK: - Private helpers

  private func alarmEventBody(
    action: String,
    id: String,
    data: [String: Any]?
  ) -> [String: Any] {
    var body: [String: Any] = ["action": action, "id": id]
    if let d = data { body["data"] = d }
    return body
  }
}

// MARK: - String helper

private extension String {
  /// Returns nil when the string is empty; self otherwise.
  var nilIfEmpty: String? { isEmpty ? nil : self }
}

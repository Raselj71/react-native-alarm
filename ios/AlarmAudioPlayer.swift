import AVFoundation
import Foundation

/// Manages foreground AVAudioPlayer playback for the alarm module.
///
/// This class is **only useful when the app is in the foreground** (or has been
/// granted a background-audio entitlement). For the background/killed-state
/// use-case the notification system handles sound via UNNotificationContent.sound.
///
/// Threading: all methods must be called on the main thread.
final class AlarmAudioPlayer: NSObject, AVAudioPlayerDelegate {

  private var player: AVAudioPlayer?

  /// Whether a sound is currently playing.
  var isPlaying: Bool { player?.isPlaying ?? false }

  /// Callback invoked when a non-looping sound finishes naturally.
  var onPlaybackFinished: (() -> Void)?

  // MARK: - Playback

  /// Attempt to play a bundled sound file.
  ///
  /// - Parameters:
  ///   - soundName: Filename as supplied by the consumer (e.g. `"adhan.wav"` or
  ///     `"bell.caf"`). **Must include the file extension** so we can split it
  ///     into `name` + `ext` for `Bundle.main.url(forResource:withExtension:)`.
  ///   - loop: When `true`, playback repeats indefinitely until `stop()` is
  ///     called. When `false`, the sound plays once.
  /// - Returns: `true` if playback started; `false` if the file was not found or
  ///   the audio session could not be configured.
  @discardableResult
  func play(soundName: String, loop: Bool) -> Bool {
    stop()

    // Split "adhan.wav" → name="adhan", ext="wav"
    let url: URL?
    let dotIndex = soundName.lastIndex(of: ".")
    if let idx = dotIndex {
      let name = String(soundName[soundName.startIndex..<idx])
      let ext = String(soundName[soundName.index(after: idx)...])
      url = Bundle.main.url(forResource: name, withExtension: ext)
    } else {
      // No extension supplied — try as-is; may still work for some resources.
      url = Bundle.main.url(forResource: soundName, withExtension: nil)
    }

    guard let resolvedURL = url else {
      print("[ReactNativeAlarm] AlarmAudioPlayer: sound file '\(soundName)' not found in main bundle.")
      return false
    }

    // Configure AVAudioSession for playback (will mix or duck other audio
    // according to system policy; use .playback so silence-switch is honoured
    // for alarms but sound still plays with a locked screen).
    let session = AVAudioSession.sharedInstance()
    do {
      try session.setCategory(.playback, mode: .default)
      try session.setActive(true)
    } catch {
      print("[ReactNativeAlarm] AlarmAudioPlayer: AVAudioSession setup failed: \(error)")
      return false
    }

    guard let newPlayer = try? AVAudioPlayer(contentsOf: resolvedURL) else {
      print("[ReactNativeAlarm] AlarmAudioPlayer: could not create AVAudioPlayer for '\(soundName)'.")
      return false
    }

    newPlayer.delegate = self
    newPlayer.numberOfLoops = loop ? -1 : 0
    newPlayer.prepareToPlay()
    guard newPlayer.play() else {
      print("[ReactNativeAlarm] AlarmAudioPlayer: AVAudioPlayer.play() returned false for '\(soundName)'.")
      return false
    }

    player = newPlayer
    return true
  }

  /// Stop playback immediately and deactivate the audio session.
  func stop() {
    guard let p = player else { return }
    p.stop()
    player = nil
    deactivateSession()
  }

  // MARK: - AVAudioPlayerDelegate

  func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
    self.player = nil
    deactivateSession()
    onPlaybackFinished?()
  }

  func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
    print("[ReactNativeAlarm] AlarmAudioPlayer: decode error: \(error?.localizedDescription ?? "unknown")")
    self.player = nil
    deactivateSession()
  }

  // MARK: - Private helpers

  private func deactivateSession() {
    // Best-effort — ignore errors (another component may still own the session).
    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
  }
}

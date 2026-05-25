# react-native-alarm

A generic exact-alarm scheduler for React Native / Expo with any-state sound playback and a **Stop** notification action. Works across app states (foreground, background, killed). Useful for adhan reminders, medicine alerts, meeting alarms, or any use case that needs a precise, audible alarm.

- **Android**: fires a foreground service at the exact scheduled time, plays a looping (or one-shot) custom sound, and presents a persistent notification with a *Stop* action — even when the app is killed.
- **iOS**: schedules a `UNUserNotification` with a custom sound (up to ~30 s) and a *Stop* action category. Full background audio is **not** possible on iOS when the app is killed (OS limitation; see [iOS caveats](#ios-caveats)).

---

## Installation

```sh
npm install @raselj5060/react-native-alarm
```

This package requires [expo-modules-core](https://docs.expo.dev/modules/overview/) and must be used inside an **Expo** managed or bare workflow project.

---

## Setup

### 1. Add the config plugin

In `app.json` (or `app.config.js`):

```json
{
  "expo": {
    "plugins": [
      "@raselj5060/react-native-alarm"
    ]
  }
}
```

The plugin automatically injects the required Android permissions, the foreground service declaration, and the broadcast receivers into your `AndroidManifest.xml`.

### 2. Prebuild and build natively

> **Expo Go is not supported.** You need a custom native build.

```sh
npx expo prebuild          # generate android/ and ios/ folders
npx expo run:android       # local Android build
npx expo run:ios           # local iOS build
# — or use EAS Build:
eas build --platform android
eas build --platform ios
```

---

## Bundling a sound file

Sounds are **your responsibility** — the library does not bundle any audio.

Pass the sound name via the `sound` field of `AlarmInput` (no file extension).

### Android

Place an MP3 (or any media file Android supports) in your app's `raw` resources folder:

```
android/app/src/main/res/raw/<name>.mp3
```

Example: `android/app/src/main/res/raw/adhan.mp3` → `sound: 'adhan'`

### iOS

Add a WAV or CAF file to your Xcode app target (check *Add to target* when adding the file):

```
<name>.wav   (or <name>.caf)
```

Then pass `sound: 'adhan'` (without extension). iOS notification sounds must be ≤30 s.

---

## Usage

```ts
import {
  scheduleAlarm,
  cancelAlarm,
  cancelAll,
  stopSound,
  getAlarmHealth,
  requestPermissions,
  openExactAlarmSettings,
  openBatteryOptimizationSettings,
  openNotificationSettings,
  addAlarmEventListener,
  isAvailable,
} from '@raselj5060/react-native-alarm';

// Check availability (false on web or when native module is absent)
console.log(isAvailable());

// Request permissions before scheduling
const granted = await requestPermissions();

// Schedule an alarm 10 seconds from now
await scheduleAlarm({
  id: 'morning-alarm',
  timestampMs: Date.now() + 10_000,
  title: 'Wake up',
  body: 'Good morning!',
  sound: 'adhan',      // name of your bundled audio file (no extension)
  loopSound: true,     // keep playing until the user taps Stop
  stopLabel: 'Stop',   // label on the notification action button
  data: { prayer: 'fajr' },
});

// Listen for events
const unsubscribe = addAlarmEventListener((event) => {
  console.log(event.id, event.action, event.data);
  // event.action: 'fired' | 'stopped' | 'opened'
});

// Stop a currently playing alarm sound immediately
await stopSound();

// Cancel a specific alarm before it fires
await cancelAlarm('morning-alarm');

// Cancel all scheduled alarms
await cancelAll();

// Clean up the listener
unsubscribe();
```

---

## API Reference

### `isAvailable(): boolean`

Returns `true` if the native alarm module is loaded (i.e. on Android or iOS in a native build). Returns `false` on web or in Expo Go.

---

### `scheduleAlarm(input: AlarmInput): Promise<boolean>`

Schedule an alarm to fire at a precise time. Returns `true` on success.

#### `AlarmInput`

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `string` | Yes | Consumer-defined identifier. Used to cancel this alarm. Must be unique per alarm. |
| `timestampMs` | `number` | Yes | Unix epoch milliseconds at which the alarm should fire. |
| `title` | `string` | Yes | Notification title shown when the alarm fires. |
| `body` | `string` | Yes | Notification body text. |
| `sound` | `string` | No | Name of the bundled audio file to play, without extension. Omit to use the system default alarm sound. |
| `loopSound` | `boolean` | No | If `true`, the sound loops continuously until `stopSound()` is called or the user taps the Stop action. Default: `false` (one-shot). |
| `stopLabel` | `string` | No | Text on the notification *Stop* action button. Default: `"Stop"`. |
| `data` | `Record<string, unknown>` | No | Arbitrary key-value data echoed back in `AlarmEvent`. |

---

### `cancelAlarm(id: string): Promise<boolean>`

Cancel a previously scheduled alarm by its `id`. Returns `true` on success, `false` if the native module is unavailable.

---

### `cancelAll(): Promise<boolean>`

Cancel all scheduled alarms. Returns `true` on success.

---

### `stopSound(): Promise<void>`

Stop any currently playing alarm sound immediately. No-op when no sound is playing or native module is absent.

---

### `getAlarmHealth(): Promise<AlarmHealth>`

Query the current permission and hardware status. Returns safe defaults (`false` for all fields) when the native module is absent.

#### `AlarmHealth`

| Field | Type | Description |
|---|---|---|
| `notificationsEnabled` | `boolean` | Whether the app has permission to post notifications. |
| `canScheduleExactAlarms` | `boolean` | Whether the app can schedule exact alarms (Android `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`). Always `true` on iOS. |
| `ignoreBatteryOptimizations` | `boolean` | Whether the app is exempt from battery optimizations (Android). Always `true` on iOS. |

---

### `requestPermissions(): Promise<boolean>`

Request notification (and, on Android 13+, `POST_NOTIFICATIONS`) permissions. Returns `true` if permissions are granted. On iOS this triggers the system permission dialog on first call.

---

### `openExactAlarmSettings(): Promise<boolean>`

Open the system screen where the user can grant the *Schedule exact alarms* permission (Android 12+). No-op on iOS.

---

### `openBatteryOptimizationSettings(): Promise<boolean>`

Open the system screen where the user can exempt the app from battery optimization (Android). No-op on iOS.

---

### `openNotificationSettings(): Promise<boolean>`

Open the system notification settings for this app.

---

### `addAlarmEventListener(listener: (event: AlarmEvent) => void): () => void`

Subscribe to alarm lifecycle events. Returns an unsubscribe function; call it to remove the listener (e.g. in a `useEffect` cleanup). Safe no-op when the native module is absent.

#### `AlarmEvent`

| Field | Type | Description |
|---|---|---|
| `id` | `string` | The `id` that was passed to `scheduleAlarm`. |
| `action` | `'fired' \| 'stopped' \| 'opened'` | `fired`: the alarm time was reached and the notification was shown. `stopped`: the user tapped the Stop action. `opened`: the user tapped the notification body to open the app. |
| `data` | `Record<string, unknown>` | The `data` object from `AlarmInput`, if any. |

---

## Permissions

The config plugin adds the following to `AndroidManifest.xml` automatically:

| Permission | Purpose |
|---|---|
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after device reboot. |
| `SCHEDULE_EXACT_ALARM` | Schedule alarms at exact times (Android 12 and below, and some OEMs). |
| `USE_EXACT_ALARM` | Schedule exact alarms without requiring user approval (Android 13+ clock/calendar apps). |
| `FOREGROUND_SERVICE` | Run the alarm foreground service. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required for media-playback foreground services (Android 14+). |
| `POST_NOTIFICATIONS` | Post notifications (Android 13+, requires runtime grant). |
| `WAKE_LOCK` | Keep the CPU awake while the alarm fires. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Show the battery-optimization exemption dialog. |

### Runtime permission flow

Call these in order before your first `scheduleAlarm`:

1. `await requestPermissions()` — requests `POST_NOTIFICATIONS` (Android 13+) and the iOS notification permission.
2. Check `(await getAlarmHealth()).canScheduleExactAlarms` — if `false` on Android, call `openExactAlarmSettings()` to send the user to the system settings page.
3. Optionally check `ignoreBatteryOptimizations` and call `openBatteryOptimizationSettings()` for reliability on battery-aggressive OEMs (Xiaomi, OnePlus, etc.).

---

## Platform behavior

### Android

- Alarms are scheduled via `AlarmManager.setExactAndAllowWhileIdle`.
- When the alarm fires, a foreground service starts and plays audio through `MediaPlayer`.
- `loopSound: true` keeps the service alive and looping until `stopSound()` or the Stop notification action.
- Works in all app states: foreground, background, **and killed**.
- A `BroadcastReceiver` on `BOOT_COMPLETED` allows you to reschedule alarms after device restart (your app must re-schedule; the library fires a `fired`-style event at boot only if the alarm time has passed).

### iOS

- Alarms are scheduled via `UNUserNotificationCenter`.
- The notification sound is played by the OS at delivery time.
- **Looping audio is not possible when the app is killed** — the OS plays the notification sound once (maximum ~30 s). `loopSound: true` is best-effort: when the app is foregrounded at alarm time, `AVAudioPlayer` loops; when killed, only the system notification sound plays.
- Sounds must be ≤30 s, in a format Core Audio supports (WAV, CAF, AIFF).

#### iOS caveats

- **UNUserNotificationCenter delegate**: the module sets itself as the `UNUserNotificationCenter` delegate. If another library (or your own code) also sets the delegate, the module will **not** replace it — `fired`, `stopped`, and `opened` events are therefore best-effort on iOS and may not fire if another delegate is active first.
- **Notification categories**: the module calls `setNotificationCategories` to register a *Stop* action category. This **replaces** any previously registered categories. If your app uses other notification categories (e.g. from `expo-notifications`), integrate carefully or register all categories through a single call.

---

## License

MIT — see [LICENSE](./LICENSE).

/**
 * Expo config plugin for @raselj5060/react-native-alarm
 *
 * Injects Android manifest entries required by the alarm module:
 *   - uses-permission declarations
 *   - AlarmReceiver  (exported=false)
 *   - BootReceiver   (exported=true, handles BOOT_COMPLETED / LOCKED_BOOT_COMPLETED / QUICKBOOT_POWERON)
 *   - AlarmService   (foreground service, mediaPlayback type)
 *
 * iOS requires no manifest injection (UNUserNotificationCenter is used at runtime).
 *
 * The plugin is idempotent: re-running prebuild never duplicates entries.
 */

import {
  AndroidConfig,
  ConfigPlugin,
  withAndroidManifest,
} from '@expo/config-plugins';

// Convenience alias so helper signatures stay short.
type ManifestApp = AndroidConfig.Manifest.ManifestApplication;
type ManifestReceiver = NonNullable<ManifestApp['receiver']>[number];
type ManifestService = NonNullable<ManifestApp['service']>[number];
type IntentFilter = AndroidConfig.Manifest.ManifestIntentFilter;

// ─── Constants ────────────────────────────────────────────────────────────────

/** Kotlin package name shared by all native components. */
const PKG = 'expo.modules.reactnativealarm';

/** All Android permissions the module requires. */
const PERMISSIONS = [
  'android.permission.SCHEDULE_EXACT_ALARM',
  'android.permission.USE_EXACT_ALARM',
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
  'android.permission.WAKE_LOCK',
  'android.permission.RECEIVE_BOOT_COMPLETED',
] as const;

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Adds a `<receiver>` entry to `application` only when one with the same
 * `android:name` is not already present (idempotent).
 */
function ensureReceiver(
  application: ManifestApp,
  name: string,
  exported: 'true' | 'false',
  intentFilters?: IntentFilter[]
): void {
  application.receiver ??= [];

  if (application.receiver.some((r) => r.$['android:name'] === name)) return;

  const entry: ManifestReceiver = {
    $: { 'android:name': name, 'android:exported': exported },
  };
  if (intentFilters) {
    entry['intent-filter'] = intentFilters;
  }

  application.receiver.push(entry);
}

/**
 * Adds a `<service>` entry to `application` only when one with the same
 * `android:name` is not already present (idempotent).
 */
function ensureService(
  application: ManifestApp,
  name: string,
  exported: 'true' | 'false',
  foregroundServiceType: string
): void {
  application.service ??= [];

  if (application.service.some((s) => s.$['android:name'] === name)) return;

  const entry: ManifestService = {
    $: {
      'android:name': name,
      'android:exported': exported,
      'android:foregroundServiceType': foregroundServiceType,
    },
  };

  application.service.push(entry);
}

// ─── Plugin ───────────────────────────────────────────────────────────────────

/**
 * Expo config plugin for @raselj5060/react-native-alarm.
 *
 * Add to your app.json / app.config.js:
 *   { "plugins": ["@raselj5060/react-native-alarm"] }
 */
const withReactNativeAlarm: ConfigPlugin = (config) => {
  // ── Step 1: permissions ──────────────────────────────────────────────────
  // AndroidConfig.Permissions.withPermissions is already idempotent.
  config = AndroidConfig.Permissions.withPermissions(config, [...PERMISSIONS]);

  // ── Step 2: manifest components ─────────────────────────────────────────
  config = withAndroidManifest(config, (manifestConfig) => {
    const androidManifest = manifestConfig.modResults;
    const [application] = androidManifest.manifest.application ?? [];

    if (!application) {
      // Defensive guard — should never be absent in a valid Expo project.
      console.warn(
        '[react-native-alarm] withAndroidManifest: <application> element not found; ' +
          'skipping receiver/service injection.'
      );
      return manifestConfig;
    }

    // AlarmReceiver — receives PendingIntent callbacks when an alarm fires.
    ensureReceiver(application, `${PKG}.AlarmReceiver`, 'false');

    // BootReceiver — re-schedules persisted alarms after a device reboot.
    ensureReceiver(application, `${PKG}.BootReceiver`, 'true', [
      {
        action: [
          { $: { 'android:name': 'android.intent.action.BOOT_COMPLETED' } },
          { $: { 'android:name': 'android.intent.action.LOCKED_BOOT_COMPLETED' } },
          { $: { 'android:name': 'android.intent.action.QUICKBOOT_POWERON' } },
        ],
      },
    ]);

    // AlarmService — foreground service that plays audio while an alarm rings.
    ensureService(application, `${PKG}.AlarmService`, 'false', 'mediaPlayback');

    return manifestConfig;
  });

  return config;
};

export default withReactNativeAlarm;

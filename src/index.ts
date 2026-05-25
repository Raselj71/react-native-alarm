import { NativeAlarm } from './ReactNativeAlarmModule';
import type { AlarmEvent, AlarmHealth, AlarmInput } from './ReactNativeAlarm.types';

export type { AlarmEvent, AlarmHealth, AlarmInput };

const SAFE_HEALTH: AlarmHealth = {
  notificationsEnabled: false,
  canScheduleExactAlarms: false,
  ignoreBatteryOptimizations: false,
};

/** Returns true if the native alarm module is available on this platform. */
export function isAvailable(): boolean {
  return NativeAlarm != null;
}

/** Schedule an alarm. Returns true on success, false if the native module is unavailable. */
export async function scheduleAlarm(input: AlarmInput): Promise<boolean> {
  if (NativeAlarm) {
    return NativeAlarm.scheduleAlarm(input);
  }
  return false;
}

/** Cancel a scheduled alarm by id. Returns false if the native module is unavailable. */
export async function cancelAlarm(id: string): Promise<boolean> {
  if (NativeAlarm) {
    return NativeAlarm.cancelAlarm(id);
  }
  return false;
}

/** Cancel all scheduled alarms. Returns false if the native module is unavailable. */
export async function cancelAll(): Promise<boolean> {
  if (NativeAlarm) {
    return NativeAlarm.cancelAll();
  }
  return false;
}

/** Stop a currently playing alarm sound. No-op if the native module is unavailable. */
export async function stopSound(): Promise<void> {
  if (NativeAlarm) {
    await NativeAlarm.stopSound();
  }
}

/** Query permission and hardware status. Returns safe defaults when native module is unavailable. */
export async function getAlarmHealth(): Promise<AlarmHealth> {
  if (NativeAlarm) {
    return NativeAlarm.getAlarmHealth();
  }
  return SAFE_HEALTH;
}

/** Request notification / alarm permissions. Returns false if the native module is unavailable. */
export async function requestPermissions(): Promise<boolean> {
  if (NativeAlarm) {
    return NativeAlarm.requestPermissions();
  }
  return false;
}

/** Open the system exact-alarm settings screen. Returns false if unavailable. */
export async function openExactAlarmSettings(): Promise<boolean> {
  if (NativeAlarm) {
    return NativeAlarm.openExactAlarmSettings();
  }
  return false;
}

/** Open the battery optimization settings screen. Returns false if unavailable. */
export async function openBatteryOptimizationSettings(): Promise<boolean> {
  if (NativeAlarm) {
    return NativeAlarm.openBatteryOptimizationSettings();
  }
  return false;
}

/** Open the notification settings screen. Returns false if unavailable. */
export async function openNotificationSettings(): Promise<boolean> {
  if (NativeAlarm) {
    return NativeAlarm.openNotificationSettings();
  }
  return false;
}

/**
 * Subscribe to alarm lifecycle events (fired / stopped / opened).
 * Returns an unsubscribe function. Safe no-op when the native module is absent.
 */
export function addAlarmEventListener(listener: (e: AlarmEvent) => void): () => void {
  if (!NativeAlarm) {
    return () => {};
  }
  // NativeAlarm extends NativeModule which is already an EventEmitter.
  // Cast to access the typed addListener; the native module itself is the emitter.
  const emitter = NativeAlarm as unknown as {
    addListener: (event: string, listener: (e: AlarmEvent) => void) => { remove: () => void };
  };
  const sub = emitter.addListener('onAlarmEvent', listener);
  return () => sub.remove();
}

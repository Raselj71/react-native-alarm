export type AlarmInput = {
  /** Consumer-defined id, used to cancel this alarm. */
  id: string;
  /** Epoch milliseconds at which the alarm should fire. */
  timestampMs: number;
  title: string;
  body: string;
  /** Android: raw resource name (no extension). iOS: bundled sound filename. Omit for system default alarm sound. */
  sound?: string;
  /** Keep the sound playing until the user stops it (e.g. full adhan). Default false (one-shot). */
  loopSound?: boolean;
  /** Text of the Stop action button. Default "Stop". */
  stopLabel?: string;
  /** Arbitrary data echoed back in alarm events. */
  data?: Record<string, unknown>;
};

export type AlarmHealth = {
  notificationsEnabled: boolean;
  canScheduleExactAlarms: boolean;
  ignoreBatteryOptimizations: boolean;
};

export type AlarmEvent = {
  id: string;
  action: 'fired' | 'stopped' | 'opened';
  data?: Record<string, unknown>;
};

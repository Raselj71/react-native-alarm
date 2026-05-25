// Web platform: no native alarm module available.
// Exporting undefined preserves the same shape as the native file so that
// src/index.ts can safely guard with `if (NativeAlarm)`.
export const NativeAlarm = undefined;

import { requireOptionalNativeModule } from 'expo-modules-core';
import type { AlarmHealth, AlarmInput } from './ReactNativeAlarm.types';

export type NativeAlarmModule = {
  scheduleAlarm: (input: AlarmInput) => Promise<boolean>;
  cancelAlarm: (id: string) => Promise<boolean>;
  cancelAll: () => Promise<boolean>;
  stopSound: () => Promise<void>;
  getAlarmHealth: () => Promise<AlarmHealth>;
  requestPermissions: () => Promise<boolean>;
  openExactAlarmSettings: () => Promise<boolean>;
  openBatteryOptimizationSettings: () => Promise<boolean>;
  openNotificationSettings: () => Promise<boolean>;
  addListener: (event: string) => void;
  removeListeners: (count: number) => void;
};

export const NativeAlarm = requireOptionalNativeModule<NativeAlarmModule>('ReactNativeAlarm');

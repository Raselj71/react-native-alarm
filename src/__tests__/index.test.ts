jest.mock('expo-modules-core', () => ({
  requireOptionalNativeModule: jest.fn(() => undefined),
  EventEmitter: class {},
}));

import * as Alarm from '../index';

describe('react-native-alarm JS API (native module absent)', () => {
  it('scheduleAlarm resolves false when native module missing', async () => {
    await expect(Alarm.scheduleAlarm({ id: 'a', timestampMs: Date.now() + 1000, title: 't', body: 'b' })).resolves.toBe(false);
  });
  it('cancelAlarm / cancelAll / stopSound do not throw', async () => {
    await expect(Alarm.cancelAlarm('a')).resolves.toBe(false);
    await expect(Alarm.cancelAll()).resolves.toBe(false);
    await expect(Alarm.stopSound()).resolves.toBeUndefined();
  });
  it('getAlarmHealth returns a safe default when missing', async () => {
    await expect(Alarm.getAlarmHealth()).resolves.toEqual({
      notificationsEnabled: false,
      canScheduleExactAlarms: false,
      ignoreBatteryOptimizations: false,
    });
  });
});

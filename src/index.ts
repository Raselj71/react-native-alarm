// Reexport the native module. On web, it will be resolved to ReactNativeAlarmModule.web.ts
// and on native platforms to ReactNativeAlarmModule.ts
export { default } from './ReactNativeAlarmModule';
export * from './ReactNativeAlarm.types';

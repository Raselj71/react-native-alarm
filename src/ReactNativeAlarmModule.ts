import { NativeModule, requireNativeModule } from 'expo';

declare class ReactNativeAlarmModule extends NativeModule<{}> {}

export default requireNativeModule<ReactNativeAlarmModule>('ReactNativeAlarm');

import { registerWebModule, NativeModule } from 'expo';

// ReactNativeAlarmModule is not available on the web platform.
class ReactNativeAlarmModule extends NativeModule<{}> {}

export default registerWebModule(ReactNativeAlarmModule, 'ReactNativeAlarmModule');

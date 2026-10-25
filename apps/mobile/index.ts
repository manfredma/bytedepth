import { registerRootComponent } from "expo";

import App from "./App";

// registerRootComponent 会调用 AppRegistry.registerComponent('main', () => App)。
// 无论通过 Expo Go 还是原生构建加载应用，
// 都确保运行环境正确初始化。
registerRootComponent(App);

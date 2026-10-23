# 移动端开发

> 导航：[知识库](../README.md) · [阅读客户端设计](../architecture/mobile-reader.md) · [API 契约](../architecture/mobile-api.md) · [发布边界](release-platform-only.md)。

客户端位于 `apps/mobile/`，独立维护 package、lockfile 和 `node_modules`。Node 版本以该目录的 `.nvmrc` 为准，依赖按 lockfile 安装；Android 与服务端统一使用 Java 25；已在本机用现有 JDK25 完成 release、AndroidTest 与 upgradeFixture 构建，Linux Host 的正式构建仍须独立验证。原生 SDK、NDK 与 Gradle 固定输入由 `apps/mobile/android-toolchain.json` 声明，生成工程及 Host preflight 必须核对一致，当前版本见 [App README](../../apps/mobile/README.md)。不修改全局 Java 选择来适配单个工具。

## 本地检查与构建

完整入口为 `bash scripts/run-local-quality.sh`，包含移动配置夹具和 `bash scripts/run-mobile-quality.sh`。移动入口先执行独立的 `npm ci --ignore-scripts --no-audit --no-fund`，再进行类型、lint、全量测试及覆盖率、固定阅读资源一致性与 Expo 公开配置检查。依赖 Expo plugin 的根配置夹具在移动入口完成安装与检查后运行，干净 worktree 也遵守这一顺序。`verify-mobile-coverage.mjs` 从源码清单检查每个业务文件的实际计数，缺失报告不能算通过。离线单测不调用 staging、部署、集成测试或 E2E。

在固定移动工具环境中，从 `apps/mobile/` 运行：

```sh
bash ../../scripts/with-mobile-toolchain-lock.sh npm ci --ignore-scripts --no-audit --no-fund
npm run prebuild:android
npm run android:build
```

`android/` 与 `ios/` 是忽略的生成目录。原生配置通过 `app.config.ts` 和 config plugin 修改，配置或原生依赖变化后重新生成；不要只改生成文件。显示版本与 Expo 版本读取同一个 package version，Android versionCode 在 app config 声明。`withPinnedAndroidBuild.cjs` 核对生成 wrapper 的固定 URL 并写入官方 SHA-256；release 覆盖模板的 debug 签名配置，以未签名包交给 Host 受管签名。参考 [Gradle wrapper 校验](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification) 和 [Expo config plugin](https://docs.expo.dev/config-plugins/plugins/)。

依赖安装会删除并重建 `node_modules`，必须与 Gradle、原生生成及 JS 测试串行。移动质量入口与原生命令通过同一个 worktree 锁互斥；命令未结束前不删除锁或启动第二次构建。包装进程收到 HUP、INT 或 TERM 时保留锁，因为原生子进程可能仍在使用依赖；中断后先核对全部相关进程终态，再清理遗留锁。正常命令失败且已退出时可释放锁。Gradle 首次 wrapper 下载也只能由一个命令持有，下载缓慢但持续增长时等待原任务，不启动竞争下载。长构建、下载和 Metro 由协调 Agent 持有；短任务子 Agent 结束时可能终止其子进程。

开发构建需要同一 Node 环境启动 Metro。它只能证明工程与原生运行时可用；正式签名、分发、升级和 staging 业务验证均不能用本地 debug APK 替代。

## 本机数据与阅读边界

历史、精确位置、公共正文及偏好只保留本机；私有批注、CSRF 和互动草稿不落盘。Session 只写 SecureStore。`withLocalDataPrivacy.cjs` 同时关闭 Android 备份并排除 cloud-backup 与 device-transfer 的应用数据域；不能仅依赖 `allowBackup=false`，部分设备的数据迁移仍需明确 extraction rules。配置夹具和 plugin 测试检查这两层，构建后还要核对合并 Manifest 和实际 XML。依据见 [Android 备份规则](https://developer.android.com/identity/data/autobackup)。

缓存与正文预算由 `apps/mobile/src/config.ts` 声明，单图字节和尺寸限制由独立 AssetDownloader 实现。正文与资源先验证、写不可变文件，再由一次 SQLite 事务发布索引。事务成功后的快照必须反映已提交的新版本；之后历史写入失败不能恢复已回收的旧副本或伪报离线完整。系统删除文件、解码失败或未映射图片必须降级，不能保持“离线可读”。相关原子提交、故障恢复与坏图片测试保留在 storage/reader 测试中。

撤回只接受结构化的 state UNAVAILABLE 或内容接口的 `POST_UNAVAILABLE`；通用 404、认证错误和网络失败保留旧完整副本。迟到撤回响应不得删除后来重新打开的版本。阅读器只消费无凭据 data URI 和固定本地资源；深色主题、目录定位、图片后到及恢复锚点的行为由 WebView 组件与 DOM runtime 测试共同约束。

## 界面生命周期与限流

私有查询订阅账号私有状态清理事件；即使账号未变，后台、离线或退出触发清理后也要立即移除数据、错误与草稿，并使迟到响应失效。私有数据不能仅靠请求完成时比较用户 ID。

native-stack 会保留被遮挡页面。一次阅读会话以页面 focus 为边界：失焦时释放仓储订阅、互动和异步操作；返回时重新打开并检查版本与可见性。目录与主题变化不应重新加载正文，内部链接迟到结果不能在失焦后导航。

`Retry-After` 是原始绝对截止时间；断网、重连或私有状态清理不能提前解除限流。截止后由读者明确重试，不在后台自动提交登录、注册或互动。取消批注编辑时恢复已保存文本和可见性。useQuery、ReaderScreen、AccountScreen 与 interaction panels 的回归测试覆盖这些边界。

## 已知原生工具链警告

当前 Expo / React Native 生成配置使用 `android.builtInKotlin=false`、`android.newDsl=false` 以及 legacy variant API。AGP 9.2.1 提示这些接口或开关将在 AGP 10 移除；这是当前依赖兼容债务，不能把带这些输出的构建称为零警告。升级 Expo / React Native / AGP 时重新生成工程并核对上游兼容配置，完成构建和原生回归后再删除旧设置；不要只编辑忽略目录或屏蔽全部警告。wrapper、插件版本和生成配置检查用于固定可重现输入，不能代替升级验证。

## 真实验证与发布

测试夹具和 DOM 模拟不能替代 Android WebView。模拟器连接 staging 验证选区、续读、旋转、键盘、安全区、代码、表格、Mermaid、图片/SVG、进程重启及断网；同时核对网络中没有阅读位置上报或凭据外泄。可执行用例、受管夹具与同签名升级流程见 [Android staging instrumentation](mobile-instrumentation.md)。具体矩阵以[客户端设计](../architecture/mobile-reader.md#验收矩阵)为准。

服务端候选、客户端完整 SHA、APK 摘要、证书、版本、不可变下载地址和 mobile evidence 必须对应。正式构建与受管签名由 canonical release-platform 和 Host Agent 执行；不存在平台回执时保持未就绪。手机验收只在 staging 自测及签名 APK 证据齐全后发起。

## 首版 Mac 签名试用包（2026-10-23 授权）

项目所有者已明确同意首版先用现有 Mac 签名试用包。此例外只用于 Android 首版手机验收：本机使用持久测试证书签署 release、versionCode=1 升级夹具及 AndroidTest APK，连接当前已验证 staging，逐项执行 [九项真实 Android 用例](mobile-instrumentation.md#执行与判定)，再交付实际 APK、完整源码 SHA、APK SHA-256、公开证书 SHA-256、版本、后端 Deployment/SHA/digest 与原始测试记录。私钥和口令保留在本机用户私有目录，权限分别为 0700/0600，不进仓库或日志，不复用 Android debug key。

这不是 canonical 平台制品，不生成或伪装 Host evidence、平台 manifest 或发布回执；手机验收前仍须核对实际签名、安装、同签名覆盖升级、离线 bundle 和九项断言。服务端发布仍由 release-platform 编排，staging 仍是唯一集成与验收环境。Linux 自动构建、KVM 模拟器及正式平台 APK 分发延后，不是本次手机验收的前置条件；后续正式发布恢复既有平台流程，保持证书身份或明确迁移方案，避免试用数据被无意清除。

首版已知取舍：release 暂不启用 R8 裁剪/资源收缩，以保持独立 AndroidTest 与低版本升级夹具的共享运行库可用；这会增加包体，正式优化兼容性在 APK 主线完成后处理。阅读器布局重排产生的滚动事件不改写精确字符锚点，只有用户移动或目录跳转更新位置；该行为由阅读壳回归和真实横竖屏用例共同保护。文章列表标题最多两行、摘要最多三行，防止长搜索摘要撑满屏幕。SVG 图片丢弃 role/aria 元信息后渲染，缓存仍保留原始摘要绑定字节，危险属性规则不放宽。

## 与手机网站保持一致的视觉

原生导航与公共组件映射网站 `static/css/theme.css` 的 default / midnight 语义变量，配色对应关系由 `runtime/__tests__/ui.test.tsx` 校验；不要沿用 React Navigation 默认蓝色或另设深色主题。页面遵守[前端设计语言](../architecture/frontend-design-language.md)，首页沿用网站编辑式标题，品牌资源复用网站图标。空栏目不展示标题，重复品牌不占正文空间。功能测试通过不代表视觉验收通过；交付前在真实 Android 运行时逐页检查浅色、深色、长文本、键盘与安全区，并保存截图供手机验收对照。

原生图标沿用已确认原型的 Lucide 线性图标，仅增加 `lucide-react-native` 与 Expo 固定矩阵中的 `react-native-svg`，不引入整套 UI 框架。Jest 使用 Lucide 官方 CommonJS 入口（该包的 react-native export 为 `.mjs`），实际 Metro 构建继续使用其原生入口；不得以全量图标 mock 绕过真实组件测试。

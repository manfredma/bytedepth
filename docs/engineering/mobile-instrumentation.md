# Android staging instrumentation

> 状态：测试源与受管构建输入已实现；本地 JS 配置测试不构成 Android 编译、安装或 case PASS。真实结果必须由 release-platform 当前 task/nonce 的 Host evidence 证明。
> 导航：[移动端开发](mobile-development.md) · [移动 API](../architecture/mobile-api.md) · [客户端架构](../architecture/mobile-reader.md)。

`apps/mobile/plugins/withStagingInstrumentation.cjs` 在 Expo 生成工程中配置 AndroidX 标准 `androidx.test.runner.AndroidJUnitRunner`，仅把 `testing/android/MobileReaderStagingTest.kt` 复制到 `app/src/androidTest`，仅使用 `androidTestImplementation` 测试依赖。正式 APK 不包含测试凭据、fixture JSON、测试 HTTP 入口或专用 JS bridge。Android 生成目录仍不入库。

构建输入冻结在客户端源码 SHA：正式 `versionCode=2`；`upgradeFixture` 从 release 初始化，保持相同 applicationId/versionName，唯一版本覆盖为 `versionCode=1`，不签名、不 debuggable。React Native 对该非 debug variant 打包 JS；Host 仍须核对 APK 确实含离线 bundle，不能用 Metro 运行结果替代。

| Gradle task                       | unsigned output                                                                 |
| --------------------------------- | ------------------------------------------------------------------------------- |
| `:app:assembleRelease`            | `android/app/build/outputs/apk/release/app-release-unsigned.apk`                |
| `:app:assembleReleaseAndroidTest` | `android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk` |
| `:app:assembleUpgradeFixture`     | `android/app/build/outputs/apk/upgradeFixture/app-upgradeFixture-unsigned.apk`  |

Host 使用同一 trusted preview key 签署正式 APK、fixture1 与临时测试 APK。测试 APK 使用 `adb install -t` 安装，instrumentation target 为 Host 已安装的 canonical signed bytes；禁止运行会替换 target 的 `connectedAndroidTest`。

## 受管 fixture 协议

Host 从固定 root-only `staging-fixtures.json` 取得既有 staging admin 的 `username/password`，通过独立 builder 的 0600 临时文件传给以下固定命令。不得把凭据放入参数、报告、manifest 或日志。根工程依赖严格按现有 lockfile 安装，fixture 进程使用固定 `/opt/shared-e2e/chrome-linux64/chrome`，不下载浏览器。它针对当前真实 staging，不使用隔离的后端集成测试 slot。

```text
node scripts/mobile-staging-fixtures.mjs prepare --input <managed-input.json> --output <managed-output.json>
node scripts/mobile-staging-fixtures.mjs cleanup --input <managed-output.json>
```

输入为 `schemaVersion=1`、固定 `origin=https://staging-bytedepth.bytedepth.cn`、task UUID `runId`、完整 `backendCommitSha`、`backendArtifactSha256` 及 `username/password`；可选 `secondUsername/secondPassword` 必须同时存在且账号不同。没有第二账号时，通过现有 `/register` 和 admin 审核流程创建唯一 owned user；在注册意图、精确 ID 和批准前分别持久化归属。cleanup 只删除该 owned ID，使用现有用户管理表单对应路由，不删除传入的既有第二账号。

输出保留五项绑定字段以及 `schemaVersion`，增加：

- `complete`：所有 owned 文章发布、移动 API ID 与搜索索引确认、两个账号私有批注准备完成后才为 true。
- `account`、`secondAccount`：仅供真实 UI 登录的短期私密测试输入。
- `articles.primary/secondary/uncached/broken`：各含实际 `id/title/slug/editPath/postPath`；primary 含长文、代码、表格、Mermaid、PNG、被动 SVG 及站内/站外链接；broken 引用该 run 唯一不存在的必需图片。
- `exactQuote/privateNote/secondPrivateNote`：真实 owned 选区和两个不同的私有批注标记。
- `webCookie`：独立网站 session，用于比较真实 `/reading-history` readCount/totalActiveSeconds；不与移动会话混用。
- `ownedUser`：仅在 helper 创建账号时存在，包含精确 ID 与唯一用户名；部分失败时 ID 可以为 null，由精确用户名重新定位归属。

helper 复用 `annotation-fixture.js`、`admin-auth.js` 与 `owned-form.js`。文章在发布前保存实际 ID 与编辑路径，cleanup 逐项验证 title/runId/ID/path 并核对实际编辑页标题；一个失败不会跳过其他 owned 项。每项删除前将 `cleanupStarted` 持久化，删除成功后将 `retired` 持久化；输出通过 0600 临时文件、fsync 与原子 rename 更新。重试先校验归属，再跳过已退休项；只有已持久化删除意图的精确 owned 编辑路径返回 404 或 owned 用户查无此人时才按已完成处理。用户缺失必须先核对 HTTP 200、响应和最终 URL 均为精确 username 查询的固定 staging `/admin/users`，并验证现有「用户管理」标题及已登录退出表单；503/500、登录跳转和无认证标记页面都保留未完成归属。正常删除从精确 owned 删除表单读取现有 CSRF token。准备失败后的自动 cleanup 与 Host finally cleanup 共用这一协议。cleanup 失败使验证失败；保留私密输入供后续受管恢复，不得吞错后发 PASS。成功 cleanup 删除输出文件；Host 最后删除设备与主机临时凭据。

## 执行与判定

Host 把完整输出 push 到唯一 `/data/local/tmp/bytedepth-mobile-fixtures.json` 并设置 shell-owned 0600。每次执行固定 runner：

```text
adb shell am instrument -w -r \
  -e mobileCase <case> \
  -e mobileFixturePath /data/local/tmp/bytedepth-mobile-fixtures.json \
  -e backendCommitSha <40-hex> \
  -e backendArtifactSha256 <64-hex> \
  -e apiSchemaVersion 1 \
  cn.bytedepth.reader.preview.test/androidx.test.runner.AndroidJUnitRunner
```

只有真实断言完成后测试才输出 `MOBILE_CASE <case> PASS`。Host 还必须同时核对 JUnit `OK`、`INSTRUMENTATION_CODE: -1`、受管 backend 绑定、实际已安装 APK digest/version/certificate；marker 单独不构成证据。

| 固定 public case       | 真实断言                                                                                                                                                                                   |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `guest-reading`        | 游客 UI、实际 WebView 文本、SQLite 历史、全部缓存摘要、最近入口                                                                                                                            |
| `public-content`       | WebView 代码/表格/Mermaid SVG、PNG/SVG 解码、目录跳转                                                                                                                                      |
| `offline-recovery`     | 实际 `svc wifi/data` 切网并核对 ConnectivityManager；完整副本及图片离线可读；缺失必需图片不得发布完整副本；离线无副本提示与联网恢复                                                        |
| `resume-rotation`      | 先执行内部 `resume-seed`，Host force-stop 进程，再读取真实历史/位置哨兵；实际字符的 viewport fraction、旋转与再次打开                                                                      |
| `session-isolation`    | A 私有标记、退出游客无私有标记、B 仅见 B、A 再登录恢复 A；公开副本不变；文件/SQLite 无私有标记                                                                                             |
| `anonymous-rating`     | 真实 UI 改分与服务端 visitorScore/ratingCount，同一安装只保留一票                                                                                                                          |
| `bridge-navigation`    | 伪造 bridge 身份不能改位置；站内链接进原生 reader；不安全 scheme 与 WebView 远程跳转被拒绝                                                                                                 |
| `no-history-upload`    | 登录移动阅读、滚动并超过网站 tracker flush 周期；独立网站 session 的真实历史计数不变，本地位置存在                                                                                         |
| `upgrade-preservation` | fixture1 内部 `upgrade-seed` 后 Host force-stop；`adb install -r` canonical2；同一真实历史/位置/缓存摘要以及离线渲染保留；注入未来 SQLite schema 后拒绝打开且不清数据，finally 恢复 schema |

`resume-seed`、`upgrade-seed` 是内部准备阶段，不增加 public evidence case。升级阶段禁止 uninstall/pm clear；正式应用始终保持 canonical bytes。网络切换在 finally 恢复。UI 控件、账号、fixture、受管浏览器、模拟器或任何断言缺失均失败，禁止用 DOM/Jest/API HTTP200 代替 Android PASS。

## 本地防复发检查

```bash
node --test scripts/test-fixtures/mobile/instrumentation.test.mjs
cd apps/mobile
npm test -- --runInBand src/__tests__/stagingInstrumentation.test.ts --coverage --collectCoverageFrom=plugins/withStagingInstrumentation.cjs
npm run typecheck
npm run lint
```

续读断言构造 `AnnotationDocument` 时必须保留数值 `revision`；Kotlin 使用 `JSONObject.getLong`，不得把正文修订转换成字符串，否则实际 mapping 会返回 `INVALID_MAPPING`。

Node 使用实际 annotation runtime 检查这一映射，同时检查 fixture 绑定、owned 生命周期、部分失败与可重试 cleanup 边界；Jest 检查插件配置、幂等性及 AndroidTest 源目录。上述检查不会启动浏览器、模拟器或原生构建，因此不能勾选真实 staging 验证。

新增 Kotlin instrumentation 文件需通过 Node fixture 中的尾随空白检查，并在暂存后执行 `git diff --cached --check`；未跟踪文件不会被普通 `git diff --check` 覆盖。

## 首版 Mac 执行

按[首版授权](mobile-development.md#首版-mac-签名试用包2026-10-23-授权)，相同 fixture 助手在 macOS 使用已安装的 `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`，Linux 仍使用原受管 Chromium；不下载浏览器。模拟器使用现有 API36 arm64 AVD 与 macOS Hypervisor.Framework，无需 KVM。所有九项断言、versionCode=1→2 的 `install -r`、凭据保护、owned 数据清理和真实 staging 绑定要求保留。原始 instrumentation 日志是本机测试证据，不能上传伪装成平台 Host evidence。

## 设备回归注意事项

- 首版 release 与升级夹具关闭 R8 裁剪和资源收缩，仍为非 debug、内置 JS bundle 的 release 包。独立 AndroidTest APK 会调用目标 APK 中的共享运行库；裁剪可能移除仅被测试使用的 API，导致 AndroidJUnitRunner 在断言前因 Trace/LazyKt 缺失崩溃。插件回归约束这一配置；后续启用优化须重新证明两个目标 variant 与完整设备矩阵兼容，不能仅凭编译通过恢复。
- UI 原生空闲不等于 React 页面、WebView 或异步安装身份已就绪。用例等待实际控件/阅读壳/持久化结果；搜索结果使用文章按钮的无障碍名称，避免误点含相同标题的输入框。旋转后重新查找失效节点，仅对 StaleObjectException 最多三次，不重试业务断言。
- 滚动准备必须等待本次新位置写入并稳定，不能把旧的非零进度当作本次结果。重启和旋转同时核对持久坐标与实际字符的 viewport fraction。数据库未来版本用例 finally 直接恢复并核验 schema，不通过额外 UI 启动掩盖原始失败。
- 长时间排查后，独立网站 session 可能失效。设备用例前确认 `/reading-history` 的真实 authenticated 标记，必要时使用同一既有账号重新登录并私密更新 fixture 的 webCookie；不跳过认证断言或用移动 session 代替。凭据文件始终 0600，最终仍清理 owned 文章、账号与设备临时文件。

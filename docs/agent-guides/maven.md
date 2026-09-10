# Maven

系统默认 Java 8，所有 `mvn` 命令必须加 `JAVA_HOME` 前缀。

## 告警零容忍

Maven 构建、测试、PMD/JaCoCo 等质量门禁的输出出现任何 `WARNING`，均不得忽略或以 `BUILD SUCCESS` 视为验收通过。必须先定位并修复告警；无法在当前范围内修复时，应停止发布或部署并报告原因。该规则同样适用于 Maven Release Plugin 与生产部署前验证。

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean test -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean package -DskipTests -Dsort.skip=true
```

多模块项目测试前先刷新本地缓存：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test -Dsort.skip=true
```

所有生产 Java 类必须达到行、分支、方法 100% 覆盖率。脚本会先执行完整测试、合并跨模块执行数据，再逐模块执行全量校验：

```bash
bash scripts/verify-changed-coverage.sh
```

必须在每次生产 Java 改动完成后、更新发布记录和运行 Release Plugin 前独立执行该脚本；不得把首次执行留到发布流程。脚本默认依据最近正式 Tag 自动识别变更类，并将工作区中尚未提交的生产 Java 改动一并纳入校验。需要清理历史覆盖债务时可显式执行 `COVERAGE_INCLUDES='**' bash scripts/verify-changed-coverage.sh`。

聚合 XML 和 HTML 报告位于 `bytedepth-start/target/site/jacoco-aggregate/`；门禁要求每个生产类的行、分支、方法均为零遗漏。

## 测试环境边界

本机只运行断网、无外部进程仍可执行的单元测试和静态检查；内存数据库、内存实现与进程内 mock/fake（包括进程内 Redis 实现）都属于单元测试。连接任何独立进程（包括 Redis、MySQL、Flyway、Docker/Testcontainers、Nginx）的测试属于集成测试，必须在 staging（124）执行；E2E 也必须在 staging 执行。将 `E2E_BASE_URL` 指向 staging 但仍在本机启动浏览器，不属于 staging E2E 验收。本机临时启动 Redis 或其他服务只能用于开发期诊断，不能替代 staging 集成验收。

离线单元测试只使用以下 Java 25 命令；它们不激活 `staging-integration`，默认 Surefire 也会排除 `**/*IT.java`：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test -Dsort.skip=true
```

`*IT` 只能在 staging 主机由 `run-staging-integration-tests.sh` 运行；它在 Compose 网络中执行 `mvn -Pstaging-integration verify`。E2E 只能在同一 staging 主机由 `run-staging-e2e-tests.sh` 运行；该 wrapper 固定 `E2E_BASE_URL=https://staging.bytedepth.cn` 和 checkout 中的 `.e2e/chrome-linux64/chrome`，不能用本机浏览器替代。两个命令及其 evidence 传递流程见 [部署手册](../../deploy/README.md#集成测试)。

### staging 验证的执行纪律

部署、集成测试和 E2E 是有成本的串行门禁，不能把它们当成开发期试错工具。提交候选分支前，先在本机完成离线单测和 runner 的 fake/fixture 测试；首次 staging 验证前必须一次性核对 runner 的前提：部署 SHA 一致、Compose 服务健康、可用磁盘空间、固定的 Playwright 浏览器可执行，以及 E2E 所需的真实公开数据。E2E 不得依赖可能被同步清除的固定文章、用户或其他种子数据，应在运行时发现当前可用的只读数据，或由 runner 显式创建并清理测试数据。

staging 上一次完整 `*IT` 目前通常需要约 3–4 分钟（Maven `verify` 与一次性测试容器），完整 E2E 通常约 1 分钟。超过这个范围时，先读取该次 run 的持久化日志和进程状态，取得第一个明确错误后再修复；不得依据路径、镜像、文章或系统包的猜测连续改动并重复部署。每次修复都先用对应的离线 runner 测试复现，再进行一次 staging 重试。

不得新增 Maven 模块；如确有必要，必须先获得项目所有者的明确同意。

运行 jar：

```bash
$(/usr/libexec/java_home -v 25)/bin/java --enable-native-access=ALL-UNNAMED -jar target/xxx.jar
```

改完 Java 源码后需重新构建并重启应用才能生效——旧进程不会热加载改动。本机开发时：`mvn package -DskipTests` → 停旧进程 → 重新启动 → 验证。不要改完代码不重启就让用户验证，导致用户看到的是旧版本。最终验证应在 staging 上进行（见 [AGENTS.md](../../AGENTS.md)），本机重启仅用于开发期快速确认。

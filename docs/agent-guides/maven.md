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

不得新增 Maven 模块；如确有必要，必须先获得项目所有者的明确同意。

运行 jar：

```bash
$(/usr/libexec/java_home -v 25)/bin/java --enable-native-access=ALL-UNNAMED -jar target/xxx.jar
```

改完 Java 源码后需重新构建并重启应用才能生效——旧进程不会热加载改动。本机开发时：`mvn package -DskipTests` → 停旧进程 → 重新启动 → 验证。不要改完代码不重启就让用户验证，导致用户看到的是旧版本。最终验证应在 staging 上进行（见 [AGENTS.md](../../AGENTS.md)），本机重启仅用于开发期快速确认。

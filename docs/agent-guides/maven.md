# Maven

系统默认 Java 8，所有 `mvn` 命令必须加 `JAVA_HOME` 前缀。

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package -DskipTests -Dsort.skip=true
```

多模块项目测试前先刷新本地缓存：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true
```

对本次改动涉及的生产 Java 类，必须运行覆盖率门禁。脚本会先执行完整测试、合并跨模块执行数据，再对每个改动类逐一校验：

```bash
bash scripts/verify-changed-coverage.sh HEAD
```

提交已存在时，把 `HEAD` 替换为本次改动前的基准提交，例如 `HEAD~1` 或 `origin/main`。聚合 XML 和 HTML 报告位于 `bytedepth-coverage/target/site/jacoco-aggregate/`；门禁要求每个改动类的行、分支、方法均为零遗漏。

运行 jar：

```bash
$(/usr/libexec/java_home -v 21)/bin/java -jar target/xxx.jar
```

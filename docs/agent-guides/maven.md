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

所有生产 Java 类必须达到行、分支、方法 100% 覆盖率。脚本会先执行完整测试、合并跨模块执行数据，再逐模块执行全量校验：

```bash
bash scripts/verify-changed-coverage.sh
```

聚合 XML 和 HTML 报告位于 `bytedepth-start/target/site/jacoco-aggregate/`；门禁要求每个生产类的行、分支、方法均为零遗漏。

不得新增 Maven 模块；如确有必要，必须先获得项目所有者的明确同意。

运行 jar：

```bash
$(/usr/libexec/java_home -v 21)/bin/java -jar target/xxx.jar
```

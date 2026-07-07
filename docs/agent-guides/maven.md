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

运行 jar：

```bash
$(/usr/libexec/java_home -v 21)/bin/java -jar target/xxx.jar
```

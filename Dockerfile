# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# 配置阿里云 Maven 镜像加速（内嵌，不依赖外部文件）
# mirrorOf=* 覆盖所有仓库（含 Spring repo.spring.io），避免从境外仓库下载慢
RUN mkdir -p /root/.m2 && cat > /root/.m2/settings.xml <<'SETTINGS'
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <name>Aliyun Maven Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
SETTINGS

# 先复制 pom 文件，利用 Docker layer 缓存加速依赖下载
COPY pom.xml .
COPY bytedepth-domain/pom.xml bytedepth-domain/
COPY bytedepth-app/pom.xml bytedepth-app/
COPY bytedepth-infrastructure/pom.xml bytedepth-infrastructure/
COPY bytedepth-adapter/pom.xml bytedepth-adapter/
COPY bytedepth-start/pom.xml bytedepth-start/
# 限制 Maven heap，避免 2C2G 服务器构建期间内存耗尽导致 SSH 失联
ENV MAVEN_OPTS='-Xmx512m --enable-native-access=ALL-UNNAMED'
RUN mvn dependency:go-offline -Dsort.skip=true -q

# 复制源码并打包
COPY . .
RUN mvn clean package -Dmaven.test.skip=true -Dsort.skip=true

# ---- Stage 2: Run ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/bytedepth-start/target/bytedepth-start.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]

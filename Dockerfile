# ---- Stage 1: Build ----
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build

# 先复制 pom 文件，利用 Docker layer 缓存加速依赖下载
COPY pom.xml .
COPY bytedepth-domain/pom.xml bytedepth-domain/
COPY bytedepth-app/pom.xml bytedepth-app/
COPY bytedepth-infrastructure/pom.xml bytedepth-infrastructure/
COPY bytedepth-adapter/pom.xml bytedepth-adapter/
COPY bytedepth-start/pom.xml bytedepth-start/
# Maven reads this project-level Java 25 compatibility configuration before
# dependency prewarming as well as before the final package build.
COPY .mvn/jvm.config .mvn/jvm.config
COPY .mvn/settings.xml .mvn/settings.xml
# 限制 Maven heap，避免 2C2G 服务器构建期间内存耗尽导致 SSH 失联
ENV MAVEN_OPTS='-Xmx512m'
RUN --mount=type=bind,from=maven-cache,target=/root/.m2/repository,readonly \
    mvn -o dependency:go-offline -Dsort.skip=true -q

# 复制源码并打包
COPY . .
# .mvn/maven.config explicitly selects this workspace file, taking precedence
# over /root/.m2/settings.xml.  Replace it only inside the build layer so the
# image build uses the Tencent mirror without changing the source checkout.
RUN --mount=type=bind,from=maven-cache,target=/root/.m2/repository,readonly \
    mvn -o clean package -Dmaven.test.skip=true -Dsort.skip=true

# ---- Stage 2: Run ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/bytedepth-start/target/bytedepth-start.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]

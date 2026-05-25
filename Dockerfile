# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先复制 pom 文件，利用 Docker layer 缓存加速依赖下载
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY bytedepth-domain/pom.xml bytedepth-domain/
COPY bytedepth-app/pom.xml bytedepth-app/
COPY bytedepth-infrastructure/pom.xml bytedepth-infrastructure/
COPY bytedepth-adapter/pom.xml bytedepth-adapter/
COPY bytedepth-start/pom.xml bytedepth-start/
RUN mvn dependency:go-offline -Dsort.skip=true -q
# 复制源码并打包
COPY . .
RUN mvn clean package -DskipTests -Dsort.skip=true

# ---- Stage 2: Run ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/bytedepth-start/target/bytedepth-start-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# bytedepth

Spring Boot 多模块博客（DDD 分层）+ Obsidian 笔记同步。笔记库 `~/w/w/`，远程 `175.24.197.202`，知识库见 `.omc/wiki/`。

## Maven（必须）

系统默认 Java 8，所有 mvn 命令必须加 `JAVA_HOME` 前缀：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package -DskipTests -Dsort.skip=true
# 运行 jar
$(/usr/libexec/java_home -v 21)/bin/java -jar target/xxx.jar
```

## 笔记同步

```bash
# --remote 必须在子命令前，否则 exit 2
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote sync
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote update-links
```

## 部署

```bash
# docker restart 不切换镜像，必须用 up --build
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "cd /opt/bytedepth && git pull && sudo docker compose up --build -d app"
```

## 代码质量

- 改完先跑 `mvn test` 全绿再汇报，**不能只编译通过**
- 多模块项目测试前先 `mvn clean install -DskipTests -Dsort.skip=true` 刷本地缓存，再跑 `mvn test`
- 改 Controller 构造器注入 → 同步更新 `@WebMvcTest` 里的 `@MockBean`
- 接口改名 → 检查**所有调用方**一起改
- 部署后等 10-15 秒再验证（容器启动时间）
- Obsidian 锚点：空格→`%20`，其余原样（`anchor = heading_text.replace(' ', '%20')`）

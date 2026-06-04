# bytedepth — AI Coding 上下文

## 项目速览

个人技术博客（Spring Boot）+ Obsidian 笔记同步工作流。
- **博客源码**：当前目录（Spring Boot 多模块，DDD 分层）
- **笔记库**：`~/w/w/`（Obsidian Vault）
- **远程服务器**：`http://175.24.197.202`（腾讯云，Docker Compose 部署）
- **同步脚本**：`~/.claude/skills/obsidian-to-bytedepth/import_via_api.py`

详细知识库见项目 wiki（`.claude/wiki/`），包含：架构、同步工作流、笔记规范、常见坑。

---

## 强制约束（每次必须遵守）

### Maven 命令

```bash
# 必须加 -Dsort.skip=true，必须加 clean
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean compile -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package -DskipTests -Dsort.skip=true
```

### Java 版本

系统默认 Java 8，**编译和运行都必须指定 Java 21**：
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn ...
$(/usr/libexec/java_home -v 21)/bin/java -jar ...
```

### 笔记同步脚本

```bash
# --remote 必须在子命令之前（放后面会 exit 2）
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote sync       # ✅
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote update-links
```

### 部署

```bash
# docker restart 不切换镜像，必须用 up --build
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@175.24.197.202 \
  "cd /opt/bytedepth && git pull && sudo docker compose up --build -d app"
```

---

## 代码质量要求

- 改完代码 → 先跑 `mvn test` 全绿 → 才能说完成，**不能只编译通过就汇报**
- 改了 Controller 构造器注入 → 同步更新 `@WebMvcTest` 里的 `@MockBean`
- 接口改名（如 `findAll` → `findPage`）→ 检查**所有调用方**一起改
- 部署后等 10-15 秒再验证（Docker 容器启动时间）

---

## 笔记锚点格式

Obsidian 锚点规则：**空格 → `%20`，其余字符原样保留**（不小写，不用连字符）

```python
anchor = heading_text.replace(' ', '%20')  # 就这一行
```

---

## 服务器信息

服务器 IP、SSH 密钥、Admin 密码、MySQL 密码等敏感信息保存在：
`~/.claude/projects/-Users-maxingfang-IdeaProjects-github-bytedepth/memory/reference_bytedepth_server.md`

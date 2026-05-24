# bytedepth 个人网站设计文档

**日期：** 2026-05-24  
**状态：** 已确认，待实现  

---

## 一、项目目标

构建个人技术网站 **bytedepth**，兼具博客与项目展示功能，同时作为实践 **COLA 框架 + DDD（领域驱动设计）** 的工程载体。

---

## 二、技术选型

| 层次 | 技术 |
|------|------|
| Web 框架 | Spring Boot 3 |
| 前端渲染 | Thymeleaf 3（服务端渲染） |
| 后端架构 | COLA 4.x + DDD |
| 持久化 | MyBatis-Plus + MySQL 8 |
| 缓存 | Redis 7（访客统计 + Session） |
| 认证 | Spring Security |
| 富文本编辑器 | Editor.md |
| 部署 | Docker Compose + Nginx（腾讯云轻量 2核4GB） |

---

## 三、Maven 多模块结构

```
bytedepth/                               ← 根 pom（parent）
├── bytedepth-domain/                    ← 纯领域模型，零框架依赖
├── bytedepth-app/                       ← 应用服务层
├── bytedepth-infrastructure/            ← 持久化 / 外部服务实现
├── bytedepth-adapter/                   ← Web 入口 / Thymeleaf 模板
└── bytedepth-start/                     ← Spring Boot 启动入口
```

**模块依赖方向（编译期强制）：**

```
adapter → app → domain ← infrastructure
start 聚合 adapter + infrastructure（运行时组装）
```

**关键约束：** `bytedepth-domain` 的 `pom.xml` 禁止引入 Spring、MyBatis 等框架依赖。

---

## 四、领域模型（domain 模块）

### 4.1 聚合根清单

| 聚合根 | 职责 |
|--------|------|
| `Post` | 博文：标题、Markdown 正文、状态（DRAFT/PUBLISHED/DELETED）、发布时间 |
| `Tag` | 标签：名称、slug |
| `Category` | 分类：名称、父分类（树形结构） |
| `Comment` | 评论：内容、作者信息、审核状态（PENDING/APPROVED/REJECTED） |
| `Project` | 项目：名称、描述、技术标签、链接、排序 |
| `Stats` | 访客统计：页面路径、PV 计数 |
| `AdminUser` | 管理员：用户名、密码哈希、角色 |

### 4.2 核心领域规则（示例）

```java
// Post.java — 领域逻辑内聚于聚合根
public class Post {
    public void publish() {
        if (this.status != PostStatus.DRAFT) {
            throw new DomainException("只有草稿可以发布");
        }
        this.status = PostStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        registerEvent(new PostPublishedEvent(this.id));
    }
}
```

### 4.3 Repository 接口（domain 层声明，infrastructure 层实现）

每个聚合根对应一个 Repository 接口，例如：

```java
public interface PostRepository {
    void save(Post post);
    Optional<Post> findById(Long id);
    List<Post> findPublished(int page, int size);
}
```

---

## 五、应用层（app 模块）

### 5.1 COLA 命令/查询分离

```
app/
├── post/
│   ├── command/  CreatePostCmdExe / UpdatePostCmdExe / PublishPostCmdExe / DeletePostCmdExe
│   └── query/    GetPostQryExe / ListPostsQryExe
├── comment/
│   ├── command/  SubmitCommentCmdExe / ReviewCommentCmdExe
│   └── query/    ListCommentsQryExe
├── project/
│   ├── command/  CreateProjectCmdExe / UpdateProjectCmdExe
│   └── query/    ListProjectsQryExe
└── stats/
    └── query/    GetStatsQryExe
```

### 5.2 CQRS 原则

- **Command 侧：** 通过聚合根触发领域逻辑，经 Repository 持久化
- **Query 侧：** 直接查询 DTO，跳过聚合根，infrastructure 层提供专用 QueryMapper（性能优先）

---

## 六、基础设施层（infrastructure 模块）

```
infrastructure/
├── post/
│   ├── PostRepositoryImpl.java     ← 实现 domain 的 PostRepository
│   ├── PostDO.java                 ← 数据库映射对象（与 Post 实体分离）
│   ├── PostMapper.java             ← MyBatis-Plus Mapper
│   └── PostQueryMapper.java        ← 查询专用 Mapper
├── comment/ ...
├── stats/
│   ├── StatsRepositoryImpl.java
│   └── RedisStatsCounter.java      ← Redis 实时计数，定时刷写 MySQL
└── cache/
    └── RedisCacheService.java
```

**DO ↔ 实体转换：** 由 `RepositoryImpl` 负责，domain 层对 `*DO` 完全无感知。

---

## 七、适配器层（adapter 模块）

### 7.1 Controller 分区

```
adapter/web/
├── public/    HomeController / PostController / ProjectController
└── admin/     AdminPostController / AdminCommentController / AdminStatsController
```

### 7.2 Thymeleaf 模板结构

```
resources/templates/
├── layout/
│   ├── base.html           ← 前台公共布局
│   └── admin-base.html     ← 后台布局
├── public/
│   ├── index.html          ← 首页：最新文章 + 项目
│   ├── posts/
│   │   ├── list.html       ← 文章列表（分页 + 分类过滤）
│   │   └── detail.html     ← 文章详情 + 评论区
│   └── projects/
│       └── list.html
└── admin/
    ├── dashboard.html
    ├── posts/
│   │   ├── list.html
│   │   └── edit.html       ← 富文本编辑器（Editor.md）
    └── comments/
        └── list.html       ← 评论审核
```

---

## 八、主要功能清单

| 功能 | 说明 |
|------|------|
| 博文管理 | 创建/编辑/发布/删除，支持 Markdown |
| 分类 & 标签 | 文章多标签、树形分类 |
| 评论系统 | 访客提交 → 管理员审核 → 展示 |
| 项目展示 | 项目卡片，含技术标签和链接 |
| 管理后台 | Spring Security 保护，富文本编辑 |
| 访客统计 | Redis 实时 PV 计数，定时持久化 |

---

## 九、部署方案

```
腾讯云轻量服务器（2核4GB）
├── Nginx（宿主机）      → 反向代理 80/443 → 应用 8080，SSL（Let's Encrypt）
└── Docker Compose
    ├── mysql:8.0        → 数据挂载：/data/mysql
    ├── redis:7          → 数据挂载：/data/redis（appendonly）
    └── bytedepth-app    → Spring Boot jar 镜像
```

**发布流程：**

```
本地修改 → mvn clean package → git push
→ 服务器 git pull → docker compose up -d --build
```

---

## 十、数据库表（核心）

| 表名 | 说明 |
|------|------|
| `post` | 博文主表 |
| `tag` | 标签 |
| `category` | 分类（支持父子关系） |
| `post_tag` | 博文-标签关联 |
| `comment` | 评论 |
| `project` | 项目展示 |
| `page_stats` | 页面访问统计 |
| `admin_user` | 管理员账号 |

---

## 十一、后续扩展方向（非本期范围）

- RSS 订阅
- 全文搜索（Elasticsearch）
- 图片 CDN
- 多管理员角色

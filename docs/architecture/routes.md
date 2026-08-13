# 路由一览

bytedepth 是 Thymeleaf SSR 应用，所有 Controller 返回视图名或重定向。

## 前台页面

| 路径 | 方法 | Controller | 说明 |
|------|------|-----------|------|
| `/` | GET | HomeController | 首页 |
| `/posts` | GET | PostController | 文章列表（支持 tag/category 筛选） |
| `/posts/{identifier}` | GET | PostController | 文章详情（slug 或数字 ID） |
| `/posts/new` | GET | PostController | 新建文章表单 |
| `/posts` | POST | PostController | 创建文章 |
| `/posts/{slug}/publish` | POST | PostController | 发布文章 |
| `/posts/{slug}/rating` | POST | PostRatingController | 文章评分 |
| `/posts/{slug}/reading-progress` | POST | PostReadingController | 阅读进度上报 |
| `/posts/{slug}/comments` | POST | CommentController | 提交评论 |
| `/columns` | GET | ColumnController | 专栏列表 |
| `/columns/{slug}` | GET | ColumnController | 专栏详情 |
| `/search` | GET | SearchController | 搜索 |
| `/u/{username}` | GET | UserProfileController | 用户主页 |
| `/projects` | GET | ProjectController | 项目列表 |
| `/releases` | GET | ReleaseController | 版本发布记录 |
| `/about` | GET | AboutController | 关于页面 |
| `/register` | GET | RegisterController | 注册表单 |
| `/register` | POST | RegisterController | 提交注册 |
| `/login` | GET | LoginController | 登录页面 |
| `/sitemap.xml` | GET | SitemapController | 站点地图 |

## 后台管理

| 路径 | 方法 | Controller | 说明 |
|------|------|-----------|------|
| `/admin` | GET | AdminDashboardController | 仪表盘 |
| `/admin/posts` | GET | AdminPostController | 文章管理列表 |
| `/admin/posts/new` | GET | AdminPostController | 新建文章 |
| `/admin/posts/{id}/edit` | GET | AdminPostController | 编辑文章 |
| `/admin/posts` | POST | AdminPostController | 创建文章 |
| `/admin/posts/{id}` | POST | AdminPostController | 更新文章 |
| `/admin/posts/{id}/publish` | POST | AdminPostController | 发布文章 |
| `/admin/posts/{id}/delete` | POST | AdminPostController | 删除文章 |
| `/admin/posts/{id}/tags` | POST | AdminPostController | 设置文章标签 |
| `/admin/posts/{id}/slug` | POST | AdminPostController | 更新 slug |
| `/admin/posts/{id}/series/assign` | POST | AdminPostController | 绑定专栏 |
| `/admin/posts/{id}/series/remove` | POST | AdminPostController | 移出专栏 |
| `/admin/categories` | GET/POST | AdminCategoryController | 分类管理 |
| `/admin/tags` | GET | AdminTagListController | 标签管理 |
| `/admin/tags/delete/{id}` | POST | AdminTagListController | 删除标签 |
| `/admin/series` | GET | AdminSeriesListController | 专栏列表 |
| `/admin/series` | POST | AdminSeriesListController | 创建专栏 |
| `/admin/series/{id}/delete` | POST | AdminSeriesListController | 删除专栏 |
| `/admin/series/{slug}` | GET | AdminSeriesDetailController | 专栏详情编辑 |
| `/admin/series/{slug}/posts` | POST | AdminSeriesDetailController | 添加文章到专栏 |
| `/admin/series/{slug}/posts/{postId}/remove` | POST | AdminSeriesDetailController | 移出文章 |
| `/admin/series/{slug}/posts/{postId}/up` | POST | AdminSeriesDetailController | 上移文章 |
| `/admin/series/{slug}/posts/{postId}/down` | POST | AdminSeriesDetailController | 下移文章 |
| `/admin/users` | GET | AdminUserController | 用户管理 |
| `/admin/comments` | GET | AdminCommentController | 评论管理 |
| `/admin/analytics` | GET | AdminAnalyticsController | 统计分析 |
| `/admin/ops` | GET | AdminOpsController | 系统运维 |
| `/admin/images/upload` | POST | ImageController | 图片上传 |
| `/admin/view-logs` | GET | AdminViewLogController | 查看日志 |
| `/admin/projects` | GET | AdminProjectController | 项目管理 |
| `/admin/projects` | POST | AdminProjectController | 新建项目 |
| `/admin/search` | GET | AdminSearchController | 搜索管理 |

## 权限

后台管理页面统一要求 `admin:dashboard:view` 或对应资源权限：
- `blog:post:create` — 创建文章
- `blog:post:manage` — 管理所有文章（含他人）
- `blog:series:manage` — 管理专栏
- `ops:monitor:view` — 查看运维监控
- `ops:deploy:execute` — 执行部署
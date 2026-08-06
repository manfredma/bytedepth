# bytedepth-adapter

Web 适配层。Controller、页面渲染、安全配置与输入适配。

**依赖方向：** app（不直接使用持久化或 Redis API）

**责任：**
- 前台页面 Controller（文章列表/详情、搜索、专栏、评论、评分等）
- 后台管理 Controller（文章管理、专栏管理、分类/标签管理、用户管理、运维等）
- 安全配置（Spring Security、CSRF、权限校验）
- 限流过滤
- 工具类（Markdown 渲染、SEO、IP 解析等）
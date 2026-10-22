# 移动 API 契约

> 状态：已确认并实现的 v1 契约；真实环境验证以对应候选的 staging evidence 为准。
> 导航：[项目知识库](../README.md) · [移动阅读客户端](mobile-reader.md) · [ADR-0025](decisions/0025-react-native-mobile-reader.md) · [批注锚定](annotation-anchoring.md)。

接口统一位于 `/api/mobile/v1`，由现有 adapter 暴露 JSON，复用 app/domain 用例。正文接口不调用网页详情 Controller，不产生网页 PV、阅读事件或历史记录。客户端没有服务端阅读历史、续读或离线队列接口。

## 数据与错误

所有数据库 ID 使用十进制字符串，不能经 JavaScript Number 转换。时间使用 ISO-8601 UTC 字符串；现有不含时区的业务时间按 Asia/Shanghai 转换。正文、状态与批注 envelope 的 `schemaVersion` 为 1。API 的 DTO 是白名单，不能直接序列化领域聚合、principal、密码哈希或匿名所有权摘要。

分页格式为 `{items,page,pageSize,total,totalPages,hasNext}`，page 从 1 开始，pageSize 默认 10、范围 1–50。搜索与专栏沿用既有固定页长 10，其他页长返回 400。调用现有 int-offset 后端前，`(page-1)*pageSize` 必须以 long 计算且不超过 `Integer.MAX_VALUE`，超出返回 400；内存分页也先用 long 检查范围，再缩窄为索引。分类与标签不能同时筛选。搜索 query 去首尾空白，长度 1–200。

失败响应为 `{code,message}`。400 表示无效输入，401 表示需登录，403 区分 `CSRF_INVALID` 与 `FORBIDDEN`，429 使用 `RATE_LIMITED` 并携带 `Retry-After`；未知异常为 500 `INTERNAL_ERROR`，不向客户端返回内部异常。过滤器与 MVC 均须使用这一格式，移动端不收到 HTML 登录跳转。账号、CSRF、个人互动响应禁止缓存，客户端仅允许公共正文包进入离线仓储。

## 会话与账号

移动端仅使用 `X-App-Session`，不会回退读取网站 Cookie；Web 不读取移动会话头。所有写操作，包括匿名评分与注册，均携带有效 CSRF。Session 空闲超时沿用服务端配置，首版过期后重新登录，无 JWT、移动 remember-me 或长期刷新令牌。

| 方法与路径            | 请求/成功响应                                                          |
| --------------------- | ---------------------------------------------------------------------- |
| `GET /csrf`           | `{headerName,token}`；必要时建立匿名 Session，标识由响应头返回         |
| `POST /login`         | 表单 username/password 与 CSRF；204，Session 轮换后客户端重新获取 CSRF |
| `GET /me`             | `{id,username,permissions}`，permissions 为权限 code 数组              |
| `POST /logout`        | 校验 CSRF、销毁会话，204；客户端即使收不到响应也清本机会话和私有内存   |
| `POST /registrations` | JSON username/password；202 `{status:"PENDING"}`，不自动登录           |

登录使用标准 Spring Security 表单过滤器处理，失败统一 401 `AUTHENTICATION_FAILED`。注册用户名 trim 后 2–50 字符；密码沿用应用用例的 8–64 字符且含字母和数字；重复用户名为 409 `USERNAME_TAKEN`。`PENDING`/`BANNED` 账号不能登录。

会话过期后的写请求可能先因 CSRF 返回 403。客户端重新取得 CSRF 并核验 `/me`，不无限重试，也不自动重放写入。限流按 HTTP 方法与路径匹配，覆盖 PUT/PATCH/DELETE，不能只复用旧 POST 路由判断。

## 发现、专栏与公共正文

| 方法与路径                          | 行为                                                           |
| ----------------------------------- | -------------------------------------------------------------- |
| `GET /posts`、`GET /discovery`      | 当前公开文章摘要与分类/标签入口                                |
| `GET /search?query=...`             | 复核发布状态与当前 slug 的结构化结果，不把索引 HTML 交原生渲染 |
| `GET /columns`、`GET /columns/{id}` | 已发布文章组成的专栏与服务端定义的顺序                         |
| `GET /posts/resolve?slug=...`       | 当前公开文章 `{postId,slug}`；供正文站内链接进入原生路由       |
| `GET /posts/{id}/state`             | 可访问性与当前表示身份，不产生阅读上报                         |
| `GET /posts/{id}/content`           | 重新核对公开状态后的公共正文包                                 |

正文身份为 `{postId,contentVersion,projectionVersion,digest,representationHash,shellVersion}`。`contentVersion` 是正文修订号；`digest` 是 ArticleDocument 文本摘要，不能代替包含元信息、HTML、文档投影、manifest、阅读壳版本的表示摘要。服务端未知的可变站外图片字节不纳入已知摘要证明。

state 返回 200，公开时为 `{schemaVersion:1,postId,availability:"AVAILABLE",identity}`；不可访问时为 `{schemaVersion:1,postId,availability:"UNAVAILABLE"}`，不带标题、正文或版本。后者是明确撤回依据；网络错误、通用网关 404、认证失败不是撤回证据。content 对不可访问文章返回 404 `POST_UNAVAILABLE`。

条件请求必须先核对发布状态，再判断 ETag。304 仅表示此刻仍公开且表示未变化。读取文章与 revision、计算批注 placement、判断移动写入资格时使用同一锁内目标，避免检查后被撤回或版本混合。

公共正文包包含 schemaVersion、正文身份、slug、title、publishedAt、category、series、净化 html、ArticleDocument 与 resources。manifest 每项为 `{key,url,role:"image",required:true,mediaType,sha256}`，所有正文图片均必需，包括懒加载图；类型/摘要未知时显式 null。服务端不递归抓站外页面，不夹带账号、评分、评论、批注、Session 或 CSRF。

正文中的 HTTP/HTTPS 图片地址可能含浏览器容忍而严格 URI 解析器拒绝的字符。manifest 保留这些必需资源及来源，不能因坏图片地址使整篇正文接口失败，也不能删掉资源后误报完整。客户端把来源作为数据解析，由独立下载器检查实际 HTTPS 地址和字节；失败显示占位并保持未完整保存。服务端仅报告图片地址，不主动抓取。

## 在线互动

| 方法与路径                                      | 请求/成功响应                                                    |
| ----------------------------------------------- | ---------------------------------------------------------------- |
| `GET /posts/{id}/ratings`                       | `{averageScore,ratingCount,visitorScore}`                        |
| `PUT /posts/{id}/rating`                        | `{score}`，1–5，204                                              |
| `GET /posts/{id}/comments`                      | 已批准评论分页，不泄露待审记录                                   |
| `POST /posts/{id}/comments`                     | `{content}`，trim 后 1–2000 字符，204后重新读取                  |
| `GET /posts/{id}/annotations`                   | 公开及当前账号有权读取的批注 envelope                            |
| `POST /posts/{id}/annotations`                  | 现有版本化 anchor、文字、颜色与可见性；201                       |
| `PATCH /posts/{id}/annotations/{annotationId}`  | 仅修改文字与可见性；200                                          |
| `DELETE /posts/{id}/annotations/{annotationId}` | 本人批注删除；204                                                |
| `GET /me/annotations`                           | 当前账号本人的批注分页；只返回可访问文章，不持久化客户端私有副本 |

评分身份为 `X-App-Visitor: app:<UUID>`，按安装而非账号管理，与 Web 访客身份分别计票；同身份同文章可改分。首次评分仍建立匿名 Session 和 CSRF。清数据/重装重建安装身份；这不保证真人去重。评论要求登录且具有 `blog:comment:create`；批注写入要求登录并验证所有权。每次移动写入都在应用边界重新确认文章已发布，不改变 Web 草稿主人批注规则。

批注 envelope 携带 `{schemaVersion,postId,contentVersion,projectionVersion,digest,annotations}`。三项版本/摘要必须与当前正文一致才绘制覆盖层，旧正文只显示列表与原始引用。新建严格沿用 ADR-0024 的 anchor、片段范围、Unicode 码点与请求 UUID：相同请求同载荷可重放，异载荷 409 `REQUEST_REUSED`、旧版 409 `STALE_REVISION`、已删除请求 410 `DELETED_REQUEST`、非法 anchor 422 `INVALID_ANCHOR`。exact 最多 500 码点，批注文字最多 2000 UTF-16 单元；不把 PATCH 描述为已有正文版本冲突保护。

首次访问可能初始化正文修订并迁移旧批注。必须在同一文章锁内先完成迁移，再读取供 placement 使用的不可变批注行；列表分页和 PATCH 返回值均遵守此顺序，避免将迁移前快照写成错误的 placement 缓存。

互动响应不进入公共正文缓存；私有内容只在线保存在当前账号/页面 generation 的内存。退出、换号、后台、离线、失效或页面销毁清除私有内容与草稿，迟到结果不能恢复它们。

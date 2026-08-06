# bytedepth-domain

领域模型与 Repository 抽象层。不依赖框架或持久化 API。

**依赖方向：** 无内部依赖（最底层模块）

**责任：**
- 领域实体（Post、Comment、User、Category、Tag、Series 等）
- 值对象与枚举
- Repository 接口定义
- 领域事件
- 通用工具（SlugUtils、DomainException）
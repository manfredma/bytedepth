# bytedepth-app

应用用例层。实现查询与命令用例，定义应用端口。

**依赖方向：** domain

**责任：**
- 查询用例（`*QryExe`）
- 命令用例（`*CmdExe`）
- DTO 与用例入参
- 应用端口接口（供 infrastructure 实现）
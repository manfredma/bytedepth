# 代码质量

Maven、Java 25、完整测试与变更覆盖率的唯一说明见 [maven.md](maven.md)。

- 改 Controller 构造器注入时，同步更新 `@WebMvcTest` 里的 `@MockBean`。
- 接口改名时，检查所有调用方一起改。

# 代码质量

- 改完先跑 `mvn test` 全绿再汇报，不能只编译通过。
- 多模块项目测试前先 `mvn clean install -DskipTests -Dsort.skip=true` 刷本地缓存，再跑 `mvn test`。
- 改 Controller 构造器注入时，同步更新 `@WebMvcTest` 里的 `@MockBean`。
- 接口改名时，检查所有调用方一起改。
- 部署后等 10-15 秒再验证容器启动状态。

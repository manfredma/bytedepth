# 架构决策记录（ADR）

本目录记录 bytedepth 的架构决策，随 Git 历史和对应 PR 一起评审。

## 编号与状态

- 编号递增：`ADR-0001`、`ADR-0002`……；文件名为 `NNNN-kebab-title.md`。
- 从 [0000-template.md](0000-template.md) 创建新记录。
- 状态为 **Proposed**、**Accepted**、**Deprecated** 或 **Superseded**。替代既有决策时保留旧 ADR，并互相链接以维持可追溯性。

## 流程

1. 写设计 spec 前，先判断决策是否影响模块边界、外部接口或长期约束，或是否存在不易回退的方案取舍。
2. 项目所有者确认是否需要 ADR。
3. 需要时，先写 ADR，再写 spec；ADR 随同实现 PR 评审，不得事后补录。

小改动可只在提交信息和 PR 描述中说明。

## 索引

| 编号 | 标题 | 状态 | 日期 |
|------|------|------|------|
| [ADR-0001](0001-published-post-driven-rss.md) | 由已发布文章状态动态生成 RSS | Proposed | 2026-09-08 |
| [ADR-0002](0002-staging-integration-test-boundary.md) | 将跨进程测试固定为 staging 集成测试 | Accepted | 2026-09-10 |

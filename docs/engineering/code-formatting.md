# 代码格式化规范

## 目标

格式化是 AI Agent、开发者和本地质量检查之间的确定性收尾步骤。格式化器只调整代码外观，不承担语义级 lint；GitHub Actions 不执行项目质量门禁，也不替代本地格式化流程。

## 工具与规则

| 文件                                                   | 工具                          | 规则                                                            |
| ------------------------------------------------------ | ----------------------------- | --------------------------------------------------------------- |
| Java                                                   | Spotless + google-java-format | 根 `pom.xml` 统一配置；不单独设置 Java 行宽                     |
| JavaScript/TypeScript/HTML/CSS/SCSS/JSON/YAML/Markdown | Prettier                      | `.prettierrc.json`，目标行宽 120；Markdown `proseWrap=preserve` |
| Shell                                                  | shfmt 3.11.0                  | 2 空格缩进、case 缩进、语法感知换行                             |
| 通用文本                                               | `.editorconfig`               | UTF-8、LF、文件末尾换行、行尾空格和基础缩进                     |

Markdown 的 Mermaid、Obsidian 特殊语法必须先通过 fixture 验证；锁文件、生成物、依赖目录、压缩文件和二进制文件不纳入格式化。

带 Thymeleaf 等服务端模板语法的 HTML（`**/src/main/resources/templates/**/*.html`）显式保护，不交给纯 HTML formatter 解析；这类模板必须由对应模板工具或人工维护。

## 命令

统一入口不依赖 Git，按路径工作：

```bash
bash scripts/format-code.sh path/to/file path/to/directory
bash scripts/format-check.sh path/to/file path/to/directory
bash scripts/format-code.sh --all
bash scripts/format-check.sh --all
```

`--changed` 是可选的 Git 读取适配器；格式化脚本永远不会执行 `git add`、`git commit` 或其他 Git 写操作。无参数调用直接失败并显示帮助。

Prettier 依赖项目本地 `node_modules`，先执行：

```bash
npm ci --ignore-scripts --no-audit --no-fund
```

shfmt 使用固定版本并校验 SHA-256，二进制缓存位于用户或 release-platform 构建缓存目录，不提交到仓库。缺少 Node/Maven 依赖时 formatter 直接失败，不隐式安装依赖。

Java 也可以直接通过 Maven Wrapper 调用 Spotless：

```bash
source scripts/lib/java-25.sh
JAVA_HOME="$(resolve_java_25)" ./mvnw \
  com.diffplug.spotless:spotless-maven-plugin:3.10.2:check
```

## AI Agent 收尾流程

```text
修改代码
→ format-code（默认按明确路径）
→ format-check
→ run-local-quality.sh（最先再次执行全仓库 format-check）
→ 测试
→ 检查 git diff
→ 提交 PR
```

首次全仓库格式化、formatter 升级和业务修改必须分开提交。格式化工具的 fixture 测试必须保持幂等：连续执行两次格式化不得产生第二次变化。

格式化必须在提交 PR 前由 AI Agent 或开发者本地完成；仓库不再维护 GitHub Actions quality workflow。release-platform 的 QUALITY/BUILD 由 Host Agent 按候选 SHA 执行，不承担本地 formatter 的替代职责。

## 复用

当前实现先服务 Maven 多模块项目。稳定后再抽取为独立版本化模板仓库；其他项目保留自己的工具版本、忽略规则和锁文件，不使用 Git submodule。Gradle 适配器不在当前范围内。

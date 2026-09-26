# ADR-0004: 共享 staging Chromium 运行时

- **状态**: Accepted
- **日期**: 2026-09-13

bytedepth、Career、Daylilt 与 Toolbox 的 staging E2E 统一引用 root 管理的 `/opt/shared-e2e/chrome-linux64/chrome`，并复用 root 的 Playwright ffmpeg 运行时（`/root/.cache/ms-playwright/ffmpeg-*/ffmpeg-linux`）。浏览器实体和录制工具迁出 `/opt/bytedepth/.e2e`，公共路径不得反向软链到任何项目目录。

各项目只记录共享浏览器/ffmpeg 运行时版本与本项目 lockfile 哈希；项目 bootstrap、部署和 runner 禁止自行下载、删除或升级浏览器。root 维护的 staging runtime bootstrap 负责补齐 Playwright ffmpeg。运行时升级后，四个项目都必须重新 bootstrap 并执行 staging E2E。

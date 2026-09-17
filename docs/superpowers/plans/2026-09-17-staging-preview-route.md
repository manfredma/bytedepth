# Staging Preview Route Implementation Plan (Superseded)

> **Status:** Superseded before production release.

The temporary query/Cookie-based staging preview route from this plan was intentionally replaced after owner review. It must not be used for current deployment or acceptance.

The current design is documented in [`2026-09-17-staging-domain-search-isolation.md`](2026-09-17-staging-domain-search-isolation.md):

- staging uses `https://staging-bytedepth.bytedepth.cn/` directly;
- `BYTEDEPTH_ENVIRONMENT=staging` hides RSS navigation and auto-discovery;
- staging `/feed.xml` and `/sitemap.xml` return `404`;
- production RSS, sitemap, and canonical URLs remain enabled;
- the staging hostname is not an authentication boundary.

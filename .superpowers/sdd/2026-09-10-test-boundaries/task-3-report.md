# Task 3 report — staging integration runner

## Delivered

- Added `deploy/run-staging-integration-tests.sh`.
  - Refuses every deployment mode except `staging`.
  - Extracts only a nonblank `REDIS_PASSWORD` from the deployed checkout's `.env`.
  - Copies `/opt/bytedepth` into a `mktemp -d` workspace before Maven can write.
  - Runs `maven:3.9-eclipse-temurin-25` with `--rm`, `--network bytedepth_default`, Redis DNS `redis:6379`, and all `bytedepth.it.redis.*` properties.
  - Captures Maven output through `tee` and fails on case-insensitive `WARNING`.
- Added mock-only regression coverage in `scripts/test-run-staging-integration-tests.sh` for non-staging refusal, isolated source mounting, required Maven properties, blank credentials, Docker/Maven failure, warning rejection, and forbidden host/port patterns.
- Documented the staging-only invocation and safety boundary in `deploy/README.md`.

## TDD evidence

The initial shell test was run before the runner existed and failed with the expected missing-runner message. A later regression check with the runner's Docker-failure message intentionally removed caused the mock test to fail; restoring that message made the full mock suite pass.

## Verification

Executed locally without Docker, Maven integration tests, or staging access:

```bash
bash scripts/test-run-staging-integration-tests.sh
bash -n deploy/run-staging-integration-tests.sh
bash -n scripts/test-run-staging-integration-tests.sh
git diff --check
```

The mock suite passed and the syntax/diff checks returned zero status with no warnings. The real runner remains intentionally unexecuted locally; it must run only after a candidate is deployed to staging.

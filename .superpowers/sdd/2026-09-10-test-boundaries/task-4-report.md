# Task 4 report — commit-bound staging release evidence

## Delivered

- `prepare-release.sh` now requires an explicit local `BYTEDEPTH_STAGING_EVIDENCE_DIR` before it can invoke Maven Release Plugin. It rejects absent, symbolic-link, malformed, mismatched, non-40-character-SHA, wrong-command, or non-passing `staging-integration` and `staging-e2e` records.
- The integration runner now records its deployed checkout's full SHA only after Maven succeeds and output has no `WARNING`.
- Added the staging-host E2E runner. It fixes the staging URL and `/usr/bin/chromium`, rejects non-staging execution, failed Playwright, unavailable Chromium, and `WARNING` output, then records the full checked-out SHA only after success.
- Both records are root-owned under `/var/lib/bytedepth-staging/test-history/`, mode `0600`, and contain only commit, command, UTC timestamp, and passed result.
- Updated the deployment, Maven, release, and repository operating rules with exact offline/staging commands and the mandatory evidence-copy flow before Tag creation.

## Test evidence

1. The extended release-script test first failed against the previous script: it allowed release preparation with no staging evidence.
2. The new E2E wrapper test first failed because `deploy/run-staging-e2e-tests.sh` did not exist.
3. The final mock suites cover missing integration/E2E records, malformed green-only records, each mismatched SHA branch, the matching happy path, warning rejection, failed-command rejection, fixed staging URL, Chromium injection, and post-success-only evidence creation.

| Command | Result |
| --- | --- |
| `bash scripts/test-prepare-release.sh` | PASS |
| `bash scripts/test-run-staging-integration-tests.sh` | PASS |
| `bash scripts/test-run-staging-e2e-tests.sh` | PASS |
| `npm test` | PASS — 76 tests; 100% statements, branches, functions, lines |
| `npm run lint` | PASS |
| `git diff --check` | PASS |

The worktree initially lacked `node_modules`, so the first `npm test` and `npm run lint` exited 127 with `vitest`/`eslint` not found. `npm ci` restored the lockfile-defined local dependencies; no manifest or lockfile changed.

## Deferred staging validation

No Failsafe integration or Playwright command was run locally. The two runners must execute on staging after the current `main` SHA is deployed; only then may their copied records be supplied to release preparation.

## Review round 1

- Each runner now removes its previous evidence before validating and starting a new run. The mock suites prove a success followed by a failed or WARNING rerun leaves no passed record.
- Both runners snapshot the complete checkout SHA before copying source or starting E2E, require the latest deployment-history SHA to match it, then recheck both before writing evidence.
- Redis credentials now use a private `0600` Docker env file and the Failsafe profile maps the container environment to the test system property. They no longer appear in Maven or Docker arguments, and the runner replaces any exact password occurrence with `[REDACTED]` before `tee`; the mock regression test verifies both safeguards.
- `run-staging-e2e-tests.sh` is tracked executable and its regression test checks the Git mode.
- Evidence retrieval documentation now uses controlled `sudo cat` through SSH because the evidence directory deliberately remains root-owned `0700` with `0600` records.
- Release parsing requires exactly four newline-terminated lines and normalizes the UTC ISO timestamp through the platform date parser; impossible timestamps and an incomplete fifth line are covered by negative tests.

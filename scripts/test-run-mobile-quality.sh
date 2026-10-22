#!/usr/bin/env bash
set -Eeuo pipefail
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
readonly FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT
mkdir -p "$FIXTURE/scripts/lib" "$FIXTURE/scripts" "$FIXTURE/apps/mobile/.expo" "$FIXTURE/bin"
cp "$SOURCE_ROOT/scripts/lib/mobile-worktree-lock.sh" "$FIXTURE/scripts/lib/"
cp "$SOURCE_ROOT/scripts/run-mobile-quality.sh" "$FIXTURE/scripts/"
printf '22.22.1\n' > "$FIXTURE/apps/mobile/.nvmrc"
cat > "$FIXTURE/bin/node" << 'NODE'
#!/usr/bin/env bash
if [[ "${1:-}" == --version ]]; then printf 'v22.22.1\n';exit 0;fi
printf 'node:%s\n' "$*" >> "$TRACE"
[[ "$*" != *"${FAIL_STAGE:-__never__}"* ]]
NODE
cat > "$FIXTURE/bin/npm" << 'NPM'
#!/usr/bin/env bash
printf 'npm:%s\n' "$*" >> "$TRACE"
[[ "$*" != *"${FAIL_STAGE:-__never__}"* ]] || exit 17
if [[ "$1" == ci && "${MISSING_DEPS:-0}" == 0 ]];then mkdir -p node_modules/.bin;touch node_modules/.bin/{jest,tsc};chmod +x node_modules/.bin/{jest,tsc};fi
NPM
chmod +x "$FIXTURE/bin/"*
run_case() { PATH="$FIXTURE/bin:$PATH" TRACE="$FIXTURE/trace" bash "$FIXTURE/scripts/run-mobile-quality.sh" > "$FIXTURE/result" 2>&1; }
: > "$FIXTURE/trace"
run_case
for stage in 'ci ' typecheck lint 'test ' verify-mobile-coverage sync-mobile-reader-assets 'exec ' verify-mobile-config; do
  if FAIL_STAGE="$stage" run_case; then
    printf 'Mobile gate accepted failed stage: %s\n' "$stage" >&2
    exit 1
  fi
done
rm -rf "$FIXTURE/apps/mobile/node_modules"
if MISSING_DEPS=1 run_case; then
  printf 'Mobile gate accepted absent dependencies.\n' >&2
  exit 1
fi
ln -s "$FIXTURE/bin" "$FIXTURE/apps/mobile/node_modules"
if run_case; then
  printf 'Mobile gate accepted shared dependencies.\n' >&2
  exit 1
fi
rm "$FIXTURE/apps/mobile/node_modules"
# Actual coverage validator must reject stale/unreported source and uncovered branches.
mkdir -p "$FIXTURE/coverage-case/plugins" "$FIXTURE/coverage-case/src" "$FIXTURE/coverage-case/coverage"
printf 'export const example=1;\n' > "$FIXTURE/coverage-case/src/example.ts"
printf '' > "$FIXTURE/coverage-case/App.tsx"
printf '{}\n' > "$FIXTURE/coverage-case/coverage/coverage-final.json"
if node "$SOURCE_ROOT/scripts/verify-mobile-coverage.mjs" "$FIXTURE/coverage-case" > "$FIXTURE/coverage-result" 2>&1; then
  printf 'Coverage accepted missing source.\n' >&2
  exit 1
fi
if rg -n '(^|[[:space:]])(ssh|scp|sftp|curl|wget|sudo)[[:space:]]|deploy/|-Pstaging-integration|test:e2e|release-platform-agent' "$SOURCE_ROOT/scripts/run-mobile-quality.sh"; then
  printf 'Mobile local gate crosses staging boundary.\n' >&2
  exit 1
fi
node - "$FIXTURE/coverage-case" << 'NODE'
const fs=require('node:fs'),path=require('node:path');const root=process.argv[2];const values={};for(const file of ['src/example.ts','App.tsx'])values[path.join(root,file)]={s:{0:1},f:{0:1},b:{0:[1,1]}};fs.writeFileSync(path.join(root,'coverage/coverage-final.json'),JSON.stringify(values));
NODE
node "$SOURCE_ROOT/scripts/verify-mobile-coverage.mjs" "$FIXTURE/coverage-case" > "$FIXTURE/coverage-result"
node - "$FIXTURE/coverage-case" << 'NODE'
const fs=require('node:fs'),path=require('node:path');const root=process.argv[2],file=path.join(root,'coverage/coverage-final.json');const values=JSON.parse(fs.readFileSync(file));values[path.join(root,'src/example.ts')].b[0][1]=0;fs.writeFileSync(file,JSON.stringify(values));
NODE
if node "$SOURCE_ROOT/scripts/verify-mobile-coverage.mjs" "$FIXTURE/coverage-case" > "$FIXTURE/coverage-result" 2>&1; then
  printf 'Coverage accepted an uncovered branch.\n' >&2
  exit 1
fi
# Native builds and dependency installation share one worktree input lock.
[[ -f "$SOURCE_ROOT/scripts/lib/mobile-worktree-lock.sh" ]]
[[ -f "$SOURCE_ROOT/scripts/with-mobile-toolchain-lock.sh" ]]
mkdir -p "$FIXTURE/scripts/lib"
cp "$SOURCE_ROOT/scripts/lib/mobile-worktree-lock.sh" "$FIXTURE/scripts/lib/"
cp "$SOURCE_ROOT/scripts/with-mobile-toolchain-lock.sh" "$FIXTURE/scripts/"
mkdir "$FIXTURE/apps/mobile/.expo/toolchain.lock"
if bash "$FIXTURE/scripts/with-mobile-toolchain-lock.sh" touch "$FIXTURE/should-not-run" > "$FIXTURE/lock-result" 2>&1; then
  printf 'Native toolchain accepted overlapping mutation.\n' >&2
  exit 1
fi
[[ ! -e "$FIXTURE/should-not-run" ]]
rmdir "$FIXTURE/apps/mobile/.expo/toolchain.lock"
bash "$FIXTURE/scripts/with-mobile-toolchain-lock.sh" touch "$FIXTURE/lock-was-held"
[[ -f "$FIXTURE/lock-was-held" && ! -e "$FIXTURE/apps/mobile/.expo/toolchain.lock" ]]

# A signal can leave child native processes alive: retain the lock for inspection.
python3 - "$FIXTURE" << 'PYTHON'
import os, pathlib, signal, subprocess, sys, time
root = pathlib.Path(sys.argv[1])
child_pid = root / "child.pid"
wrapper = root / "scripts/with-mobile-toolchain-lock.sh"
lock = root / "apps/mobile/.expo/toolchain.lock"
program = "import os,pathlib,time; pathlib.Path(__import__('sys').argv[1]).write_text(str(os.getpid())); time.sleep(2)"
process = subprocess.Popen(["bash", str(wrapper), sys.executable, "-c", program, str(child_pid)], start_new_session=True)
try:
    deadline = time.monotonic() + 5
    while not child_pid.exists():
        assert time.monotonic() < deadline, "Child failed to start"
        time.sleep(.01)
    process.send_signal(signal.SIGTERM)
    time.sleep(.1)
    os.kill(int(child_pid.read_text()), 0)
    assert lock.is_dir(), "Interrupted wrapper released a live child's input lock"
    blocked = subprocess.run(["bash", str(wrapper), "touch", str(root / "interrupted-overlap")], capture_output=True)
    assert blocked.returncode != 0 and not (root / "interrupted-overlap").exists()
    process.wait(timeout=5)
    assert lock.is_dir(), "Interrupted wrapper removed the lock before explicit inspection"
finally:
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()
PYTHON
rmdir "$FIXTURE/apps/mobile/.expo/toolchain.lock"

printf 'Mobile runner failure, coverage and toolchain-lock boundaries passed.\n'

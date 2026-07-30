#!/usr/bin/env bash
set -euo pipefail

base_ref="${1:-HEAD}"
if ! git rev-parse --verify --quiet "${base_ref}^{commit}" >/dev/null; then
  echo "Base ref not found: ${base_ref}" >&2
  exit 2
fi

changed_files="$({
  git diff --name-only --diff-filter=ACMR "${base_ref}...HEAD"
  git diff --name-only --diff-filter=ACMR
  git diff --cached --name-only --diff-filter=ACMR
  git ls-files --others --exclude-standard
} | sort -u | grep '/src/main/java/.*\.java$' || true)"

if [[ -z "${changed_files}" ]]; then
  echo "No changed production Java classes to verify."
  exit 0
fi

java_home="$(/usr/libexec/java_home -v 21)"
JAVA_HOME="${java_home}" mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME="${java_home}" mvn verify -Pchanged-coverage -Dcoverage.includes='**' -Dsort.skip=true

while IFS= read -r source_file; do
  module="${source_file%%/*}"
  class_file="${source_file#*/src/main/java/}"
  class_file="${class_file%.java}.class"
  echo "Checking 100% coverage: ${class_file}"
  JAVA_HOME="${java_home}" mvn -pl "${module}" -Pchanged-coverage \
    -Dcoverage.includes="${class_file}" -Dsort.skip=true \
    jacoco:check@check-changed-classes
done <<< "${changed_files}"

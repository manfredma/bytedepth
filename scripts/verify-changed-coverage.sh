#!/usr/bin/env bash
set -euo pipefail

java_home="$(/usr/libexec/java_home -v 21)"
JAVA_HOME="${java_home}" mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME="${java_home}" mvn verify -Pchanged-coverage -Dcoverage.includes='**' \
  -Dcoverage.check.skip=true -Dsort.skip=true

for module in bytedepth-domain bytedepth-app bytedepth-infrastructure bytedepth-adapter bytedepth-start; do
  echo "Checking 100% coverage: ${module}"
  JAVA_HOME="${java_home}" mvn -pl "${module}" -Pchanged-coverage \
    -Dcoverage.includes='**' -Dsort.skip=true \
    -Dcoverage.check.skip=false \
    jacoco:check@check-changed-classes
done

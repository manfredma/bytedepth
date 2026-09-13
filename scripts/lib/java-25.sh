#!/usr/bin/env bash

# Resolves the repository's required Java 25 on macOS and Linux CI runners.
resolve_java_25() {
    if [[ -x /usr/libexec/java_home ]]; then
        /usr/libexec/java_home -v 25
    elif [[ -n "${JAVA_HOME_25_X64:-}" && -x "$JAVA_HOME_25_X64/bin/java" ]]; then
        printf '%s\n' "$JAVA_HOME_25_X64"
    elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] \
        && "$JAVA_HOME/bin/java" -version 2>&1 | rg -q 'version "25[."]'; then
        printf '%s\n' "$JAVA_HOME"
    else
        printf 'Java 25 is required. Set JAVA_HOME_25_X64 or JAVA_HOME.\n' >&2
        return 1
    fi
}

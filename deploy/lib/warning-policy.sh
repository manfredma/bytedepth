#!/usr/bin/env bash

# Returns success only when every warning in a captured log is explicitly
# allowlisted. Keep the allowlist narrow: a technical-debt entry may permit a
# known warning, but it must not weaken the gate for unrelated warnings.
warning_policy_check_file() {
    local log_file="${1:?warning log path is required}"

    awk '
        function lower(value) { return tolower(value) }
        {
            line = lower($0)
            has_warning = line ~ /(^|[^[:alpha:]])warn(ing)?([^[:alpha:]]|$)/
            is_allowlisted_javassist = line ~ /^\[warning\].*org\.javassist:javassist:jar:3\.21\.0-ga([[:space:]]|$)/
            if (has_warning && !is_allowlisted_javassist) {
                print NR ":" $0
                blocked = 1
            }
        }
        END { exit blocked }
    ' "$log_file"
}

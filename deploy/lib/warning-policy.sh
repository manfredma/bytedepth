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
            # TD-0006: completed Tomcat default-provider seeding, 101..999ms only.
            # Exact logger/message/algorithm; errors, other providers and >=1s block.
            is_allowlisted_random = line ~ / warn [0-9]+ --- .*o\.a\.c\.util\.sessionidgeneratorbase[[:space:]]*: creation of securerandom instance for session id generation using \[sha1prng\] took \[[1-9][0-9][0-9]\] milliseconds\.$/ && line !~ /took \[100\]/
            if (has_warning && !is_allowlisted_javassist && !is_allowlisted_random) {
                print NR ":" $0
                blocked = 1
            }
        }
        END { exit blocked }
    ' "$log_file"
}

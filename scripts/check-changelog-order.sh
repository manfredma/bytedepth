#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly CHANGELOG_FILE="${1:-$SOURCE_ROOT/docs/releases/CHANGELOG.md}"

if [[ ! -f "$CHANGELOG_FILE" || -L "$CHANGELOG_FILE" ]]; then
    printf 'Changelog order check refused: missing or symlinked file %s.\n' "$CHANGELOG_FILE" >&2
    exit 1
fi

awk '
    BEGIN {
        section_count = 0
        release_count = 0
        saw_unreleased = 0
        previous_major = 0
        previous_minor = 0
        previous_patch = 0
        failed = 0
        template_started = 0
    }

    /^## / {
        if (template_started) {
            next
        }

        section_count++

        if (section_count == 1 && $0 != "## Unreleased") {
            print "Changelog order check failed: ## Unreleased must be the first level-two section."
            failed = 1
        }

        if ($0 == "## Unreleased") {
            if (section_count != 1 || saw_unreleased) {
                print "Changelog order check failed: ## Unreleased must appear exactly once at the top."
                failed = 1
            }
            saw_unreleased = 1
            next
        }

        if ($0 == "## 记录模板") {
            if (release_count == 0) {
                print "Changelog order check failed: ## 记录模板 must follow versioned release sections."
                failed = 1
            }
            template_started = 1
            next
        }

        if ($0 !~ /^## \[v[0-9]+\.[0-9]+\.[0-9]+\] - [0-9]{4}-[0-9]{2}-[0-9]{2}[[:space:]]*$/) {
            print "Changelog order check failed: invalid level-two section: " $0
            failed = 1
            next
        }

        version = $0
        sub(/^## \[v/, "", version)
        sub(/\].*$/, "", version)
        split(version, components, ".")
        major = components[1] + 0
        minor = components[2] + 0
        patch = components[3] + 0

        if (release_count > 0 &&
            (major > previous_major ||
             (major == previous_major && minor > previous_minor) ||
             (major == previous_major && minor == previous_minor && patch >= previous_patch))) {
            print "Changelog order check failed: release sections must be strict SemVer descending: " $0
            failed = 1
        }

        previous_major = major
        previous_minor = minor
        previous_patch = patch
        release_count++
        next
    }

    END {
        if (!saw_unreleased) {
            print "Changelog order check failed: missing ## Unreleased section."
            failed = 1
        }
        exit failed
    }
' "$CHANGELOG_FILE"

printf 'Changelog order check passed.\n'

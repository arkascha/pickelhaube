#!/usr/bin/env bash
# Prints a version's release notes in the Play Console's format (<en-US>…</en-US><de-DE>…</de-DE>),
# from fastlane/metadata/android/<locale>/changelogs/<version code>.txt – the one place they are kept,
# which F-Droid reads as well. Without an argument: the version code in app/build.gradle.kts.
#
#   scripts/play-release-notes.sh        # the current version
#   scripts/play-release-notes.sh 19     # a given version code
set -euo pipefail
cd "$(dirname "$0")/.."

code=${1:-$(sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)}
status=0
for locale in en-US de-DE; do
    file=fastlane/metadata/android/$locale/changelogs/$code.txt
    if [[ ! -f $file ]]; then
        echo "Missing: $file" >&2
        status=1
        continue
    fi
    chars=$(tr -d '\n' < "$file" | wc -m)
    (( chars <= 500 )) || { echo "Too long for Google Play and F-Droid: $file ($chars characters, max. 500)" >&2; status=1; }
    printf '<%s>\n%s\n</%s>\n' "$locale" "$(cat "$file")" "$locale"
done
exit "$status"

#!/bin/sh
# Stop hook for AutoItem.
# Before Claude finishes a turn, make sure the plugin still builds and its tests pass.
#  - Skips when nothing build-relevant changed since the last green build, so Q&A turns stay fast.
#  - On failure, blocks the stop once (exit 2) and hands Claude the error output.
#  - Never blocks twice in a row (stop_hook_active guard), so it can't loop forever.

input=$(cat)

if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
[ -f gradlew ] || exit 0

stamp=".claude/.last-green-build"
fingerprint=$(
  find src build.gradle.kts settings.gradle.kts gradle.properties gradle/wrapper/gradle-wrapper.properties \
    -type f -print0 2>/dev/null |
    xargs -0 shasum 2>/dev/null |
    LC_ALL=C sort |
    shasum |
    cut -d' ' -f1
)

if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$fingerprint" ]; then
  exit 0
fi

log=$(mktemp "${TMPDIR:-/tmp}/autoitem-build.XXXXXX") || exit 0

if sh ./gradlew build --console=plain -q >"$log" 2>&1; then
  printf '%s' "$fingerprint" >"$stamp"
  rm -f "$log"
  exit 0
fi

{
  echo "./gradlew build failed. Fix the compile errors or failing tests before finishing."
  echo "Last 60 lines of output:"
  tail -n 60 "$log"
} >&2
rm -f "$log"
exit 2

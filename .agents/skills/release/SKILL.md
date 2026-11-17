---
name: release
description: Prepare a AutoItem release — version bump, changelog, clean build, smoke tests on every supported version, and a Modrinth/Hangar changelog draft.
disable-model-invocation: true
arguments: [version]
---

# Release AutoItem $version

1. `git status` must show a clean working tree. If it doesn't, stop and tell Pun what's uncommitted.
2. Version: use `$version` if it was given. Otherwise propose a semver bump based on `CHANGELOG.md` → `Unreleased` (a fix is a patch, a new option or feature is a minor), and confirm it with Pun.
3. Set `version=` in `gradle.properties`. In `CHANGELOG.md`, turn `Unreleased` into `## [x.y.z] - YYYY-MM-DD` and add a fresh, empty `Unreleased` section above it.
4. `./gradlew clean build`
5. Run `/test-server` for all four servers. Any failure stops the release.
6. If anything under `src/main` that records checks, plans or performs refills changed since the last tag (`git describe --tags --abbrev=0`, then `git diff <tag> --stat -- src/main`), run `/exploit-audit` and stop on any confirmed Critical or High finding.
7. Commit `Release x.y.z` and tag `vx.y.z`. **Don't push**; ask Pun first.
8. Output:
   - the jar path: `build/libs/AutoItem-x.y.z.jar`
   - the supported versions line: `Paper 1.21.1 – 26.x (and forks, incl. Folia)`
   - a changelog in Markdown, ready to paste into Modrinth and Hangar

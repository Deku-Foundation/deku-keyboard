# Deku Keyboard development and release flow

## Branch responsibilities

- `main`: stable, reviewed code only. Do not commit experiments or unverified fixes directly here.
- `dev`: all normal development, bug fixes, refactoring and experiments. Keep the branch buildable and open PRs for logically separate fixes when practical.
- `pre-release`: staging branch for a candidate that has completed code review and CI on `dev`. Only promote a tested commit here; do not use it for day-to-day development.

## Promotion flow

1. Work and test on `dev`.
2. Require the Android CI build and resource/string checks to pass, review the diff, and run targeted regression tests.
3. Merge the selected tested commit from `dev` into `pre-release`.
4. Build a candidate from `pre-release`, publish it explicitly as a GitHub **Pre-release** tied to a version tag (for example, `v0.1.0-rc.1`), and install/test it on real devices.
5. Record device/Android version, input method and language, reproduction steps, and results. Any failed test returns to `dev`; repeat the promotion after a fix.
6. Once the release gate is satisfied and the maintainer approves, merge the exact tested release candidate into `main`, create a stable version tag (for example, `v0.1.0`), and publish the stable release.

GitHub Releases are tag-based, not branch-based: the release tag must point to the exact commit tested on `pre-release`. Never create a stable release by tagging untested `dev` code.

## Release gate

- CI is green for the exact candidate SHA.
- Regression tests cover each claimed fix.
- Manual verification covers IME lifecycle, text entry/deletion, clipboard, language/RTL behavior, and affected device/editor combinations.
- Privacy/security and license/provenance reviews are current.
- Inherited app-store assets are reviewed/replaced before any app-store publication.
- Release notes state known limitations; do not claim all upstream bugs are fixed unless each has evidence.

## Upstream bug reports

Use `docs/quality/FLORISBOARD_OPEN_BUGS_AUDIT.md` as a triage inventory. It is not a promise to port every report: confirm each issue against current Deku code and treat old-version/device-specific reports and feature requests separately.

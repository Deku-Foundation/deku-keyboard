# Initial security review

Date: 2026-10-25

## Scope

This is a limited import-stage review of repository metadata, the source archive extraction process, GitHub Actions setup, and a small number of project identity files. It is **not** a full code audit, penetration test, dependency audit, or release approval.

## Actions taken

- The source ZIP was integrity-checked before extraction. Archive paths were checked for traversal, symbolic links were rejected, and the expected Android project markers were required.
- The source archive was removed from the repository after extraction so the repository contains source files rather than a ZIP-only snapshot.
- A non-functional instruction-like comment found in the app Gradle build script was removed. It was unrelated to building or testing the project.
- The Android CI workflow uses read-only repository permissions, does not persist checkout credentials, validates the Gradle wrapper, builds a debug APK, and uploads it as a workflow artifact.
- The localization validation uses a read-only pull-request workflow rather than the upstream `pull_request_target` pattern.
- The upstream Crowdin integration is not enabled because its project settings and secrets do not belong to Deku Foundation.
- Upstream licensing and attribution are retained in `LICENSE` and `UPSTREAM.md`.

## Follow-up required

Before any stable release, review application identifier and signing strategy, Android permissions and exported components, extension trust boundaries, clipboard/data handling, outbound network requests, dependency vulnerabilities, build reproducibility, third-party licenses, and remaining upstream branding. Finalize the privacy policy after verifying actual data flows.

Do not interpret this preliminary note as evidence that every source file or dependency has been audited.

# Contributing to Deku Keyboard

Thank you for helping improve Deku Keyboard, an open-source Android keyboard maintained by Deku Foundation.

This repository began as an import of the FlorisBoard open-source codebase. Its Apache-2.0 license and applicable source notices are retained; see [UPSTREAM.md](UPSTREAM.md). Deku Keyboard is an independent derivative, not the official FlorisBoard project.

## Current status

This is a development baseline, not a stable release. The app/package rebrand has been applied. The package rebrand is complete and post-migration CI is being checked. The upstream add-ons endpoint is disabled until Deku has its own reviewed service; inherited store listings/screenshots still need replacement or explicit licensing review. Do not upload Fastlane metadata or publish builds as stable releases yet; see [fastlane/README.md](fastlane/README.md), [PRIVACY.md](PRIVACY.md), and [SECURITY_REVIEW.md](SECURITY_REVIEW.md).

## Issues and proposals

Before filing an issue, search the [existing issues](https://github.com/Deku-Foundation/deku-keyboard/issues) to avoid duplicates. Use the repository's issue templates for bugs, crashes, and feature requests. Include the app version, Android version, device details, and precise reproduction steps when relevant. Remove personal information and secrets from logs before sharing them.

For feature proposals, describe the user problem and expected behavior first. Larger changes should start with a discussion in an issue so the implementation can be reviewed before substantial work begins.

## Development environment

The imported project currently uses the following toolchain:

- JDK 17
- Android SDK and NDK compatible with the versions declared by the Gradle project
- CMake and Ninja
- Git
- Rust toolchain for the native components
- Python 3 for some optional repository utilities

Open the repository in Android Studio, or configure the Android SDK/NDK locally, then run:

```bash
./gradlew clean assembleDebug
```

The CI workflow builds a debug APK and publishes it as an artifact. Pull requests should include test evidence for the changes they affect. Run relevant unit tests where available; do not claim tests passed unless you ran them or have a successful CI result.

## Code and review expectations

- Keep changes focused and explain the reason for each change.
- Preserve upstream copyright notices and third-party license requirements.
- Do not commit credentials, private keys, user data, or real crash logs containing personal information.
- Do not add new network services, analytics, telemetry, or external integrations without reviewing privacy implications and documenting the behavior.
- Avoid further blind renames of internal packages, resource identifiers, or extension formats. The initial namespace migration is complete; any future changes must be targeted, tested, and considered against extension compatibility.
- Use [the AI policy](AI_POLICY.md) for research and AI-assisted changes.
- Follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Translations

The imported localization workflow is not yet configured for a Deku-owned translation service. The current validation workflow rejects direct edits to translated `values-*/strings.xml` files. Until a supported process is documented, change base-language strings only and discuss translation-process changes separately.

## Security reports

Do not post credentials, personal data, or unpatched vulnerability details in a public issue. Follow the reporting guidance in [SECURITY_REVIEW.md](SECURITY_REVIEW.md) and use a verified private contact path for sensitive reports.

---

This contribution guide is maintained by Deku Foundation and replaces the upstream project-specific contribution instructions for this repository.

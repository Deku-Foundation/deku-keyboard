# Deku Keyboard

**Deku Keyboard** is an open-source Android keyboard project maintained by [Deku Foundation](https://github.com/Deku-Foundation). This README is maintained by Deku Foundation and replaces the upstream project overview for this independent repository.

This independent project began from the FlorisBoard open-source codebase. It is now being adapted and maintained independently by Deku Foundation; see [UPSTREAM.md](UPSTREAM.md) for attribution and license context.

[![Deku Keyboard CI](https://github.com/Deku-Foundation/deku-keyboard/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/Deku-Foundation/deku-keyboard/actions/workflows/android.yml)

## Project status

This is the source-import baseline, not a published stable release. The first goal is to establish a reproducible build and CI. Signing and release distribution will be reviewed before a public Deku release. The Android application ID and primary adaptive icon have been changed; internal namespace/class names and inherited store-listing assets remain subject to targeted compatibility and licensing review. Do not treat unofficial builds as production releases.

## Baseline capabilities

The imported codebase includes the upstream keyboard features and architecture, including theme customization, clipboard history, emoji input, and extension support. Feature availability and quality will be revalidated during development.

## Build locally

Requirements and toolchain details are defined by the Gradle wrapper and the CI workflow. From the repository root, run:

```bash
./gradlew clean assembleDebug
```

The debug APK is generated at app/build/outputs/apk/debug/app-debug.apk when the build succeeds.

## Privacy and security

The privacy notice is currently a [pre-release draft](PRIVACY.md), not a final policy. The [initial security review](SECURITY_REVIEW.md) documents completed import-stage checks and the remaining audit work. Neither document claims that the full application has been audited.

## Attribution and licensing

This project is derived from FlorisBoard and retains its upstream license, copyright statements, and applicable third-party notices. See [LICENSE](LICENSE) and [UPSTREAM.md](UPSTREAM.md). Deku Keyboard is an independent project and is not the official FlorisBoard project. The app label, application ID, adaptive icon, palette, repository links, and Gradle project name have been updated; internal namespace/class names, some upstream integrations, and inherited store-listing assets remain subject to targeted compatibility and licensing review.

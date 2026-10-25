# Deku Keyboard

**Deku Keyboard** is an open-source Android keyboard project maintained by [Deku Foundation](https://github.com/Deku-Foundation).

This repository begins from the [FlorisBoard](https://github.com/florisboard/florisboard) open-source codebase and will be developed independently. The current baseline retains upstream implementation and third-party components; the project will be rebranded and modernized in reviewed stages rather than by indiscriminate search-and-replace.

[![Deku Keyboard CI](https://github.com/Deku-Foundation/deku-keyboard/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/Deku-Foundation/deku-keyboard/actions/workflows/android.yml)

## Project status

This is the source-import baseline, not a published stable release. The first goal is to establish a reproducible build and CI. Application identifiers, signing, release distribution, remaining in-app branding, and visual assets will be reviewed before a public Deku release. Do not treat unofficial builds as production releases.

## Baseline capabilities

The imported codebase includes the upstream keyboard features and architecture, including theme customization, clipboard history, emoji input, and extension support. Feature availability and quality will be revalidated during development.

## Build locally

Requirements and toolchain details are defined by the Gradle wrapper and the CI workflow. From the repository root, run:

```bash
./gradlew clean assembleDebug
```

The debug APK is generated at app/build/outputs/apk/debug/app-debug.apk when the build succeeds.

## Attribution and licensing

This project is derived from FlorisBoard and retains its upstream license, copyright statements, and applicable third-party notices. See [LICENSE](LICENSE) and [UPSTREAM.md](UPSTREAM.md). Deku Keyboard is an independent project and is not the official FlorisBoard project.

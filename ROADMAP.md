# Deku Keyboard roadmap

This roadmap describes the work planned for the independent Deku Keyboard repository. It replaces FlorisBoard-specific release milestones; upstream features and implementation details remain credited in [UPSTREAM.md](UPSTREAM.md). Items are priorities, not delivery-date promises.

## Phase 0 — source baseline and build hygiene

- [x] Extract the source archive into the repository root and remove the ZIP-only snapshot.
- [x] Retain upstream licensing and add an attribution note.
- [x] Add GitHub Actions for wrapper validation, Android debug builds, artifacts, and base-string validation.
- [x] Set an initial Deku app label, Android application ID, project name, primary adaptive icon, palette, repository links, and beta label.
- [x] Record the pre-release privacy and initial import-review status.
- [ ] Confirm a green CI run for the latest package/icon changes and review test results.
- [ ] Identify and remove obsolete upstream-only workflows, secrets assumptions, and project integrations.

## Phase 1 — independent project foundations

- [ ] Complete a file-by-file inventory of upstream branding, resource identifiers, package names, licenses, and third-party assets.
- [ ] Replace remaining internal FlorisBoard package names and resource keys in small, build-tested migrations rather than global search-and-replace.
- [ ] Review the Android manifest, exported components, permissions, application ID, signing, backup rules, and deep links.
- [ ] Replace inherited store icons, feature graphics, screenshots, translated listings, and historical changelogs before any store release.
- [ ] Define a Deku-owned translation workflow before enabling any Crowdin or other translation credentials.
- [ ] Establish dependency update and vulnerability-review practices.

## Phase 2 — privacy, security, and reliability

- [ ] Review clipboard handling, extension import/trust boundaries, logs, crash reporting, network requests, and third-party SDKs.
- [ ] Finalize the privacy notice only after actual app data flows are verified.
- [ ] Add tests for key input, language layouts, settings persistence, and regression-prone flows.
- [ ] Validate accessibility, keyboard usability, screen sizes, and Android versions on physical/emulated devices.
- [ ] Produce repeatable debug builds and document provenance of generated artifacts.

## Phase 3 — Deku-specific product direction

- [ ] Gather user feedback before committing to major feature work.
- [ ] Evaluate Arabic and right-to-left typing, punctuation/digit behavior, and multilingual switching as first-class test scenarios.
- [ ] Review the keyboard layout, theme customization, clipboard, emoji, extension, and suggestion features for usability and privacy.
- [ ] Decide which upstream features to keep, replace, or remove based on maintenance cost and user needs.
- [ ] Define release criteria, support policy, versioning, and a signing/release process.

## Release gate

No stable release should be published until the build is reproducible, inherited store assets are replaced or properly relicensed, privacy behavior is documented accurately, relevant security checks are completed, and the maintainer team has reviewed the release candidate.

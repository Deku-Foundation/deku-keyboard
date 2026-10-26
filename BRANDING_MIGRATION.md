# Deku identity migration report

This migration updates Android and library package namespaces, source paths, class and file names, JNI symbols, Android component references, resource names, built-in extension identifiers, labels, project links, issue templates, and development documentation.

## Preserved attribution and dependencies

- LICENSE, UPSTREAM.md, source copyright/SPDX notices, and third-party license data are preserved.
- The live add-ons-store host remains beta.addons.florisboard.org until Deku operates a replacement. It is not renamed to a fabricated domain.
- Store-listing images and historic metadata still require review and replacement; see fastlane/README.md before publication.
- The original Crowdin configuration is archived under docs/automation as disabled reference material. No upstream Crowdin credentials or upload workflow are enabled.

## Remaining reference scan

| File | Pattern | Count |
|---|---|---:|
| app/build.gradle.kts | beta.addons.florisboard.org | 1 |

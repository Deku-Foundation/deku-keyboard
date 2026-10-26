# Deku identity migration report

This migration updates Android and library package namespaces, source paths, class and file names, JNI symbols, Android component references, resource names, built-in extension identifiers, labels, project links, issue templates, and development documentation.

## Preserved attribution and dependencies

- LICENSE, UPSTREAM.md, source copyright/SPDX notices, and third-party license data are preserved.
- The upstream add-ons-store endpoint is disabled in the application. `DEKU_ADDONS_STORE_URL` is empty, and the UI no longer sends users or extension-update metadata to FlorisBoard's service. A Deku-owned update service must be implemented and security-reviewed before enabling online add-on updates.
- Store-listing images and historic metadata still require review and replacement; see fastlane/README.md before publication.
- The original Crowdin configuration is archived under docs/automation as disabled reference material. No upstream Crowdin credentials or upload workflow are enabled.

## Remaining reference scan

| File | Pattern | Count |
|---|---|---:|
No legacy package paths remain. The upstream add-ons hostname is no longer compiled into the application; attribution and third-party copyright notices are retained where required.

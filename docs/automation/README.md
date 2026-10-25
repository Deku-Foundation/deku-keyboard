# GitHub Actions integration notes

- android.yml runs the Android debug build and publishes the APK as a workflow artifact.
- validate-strings-no-translations.yml carries the upstream validation for localized strings.xml changes.
- crowdin-upload.yml.disabled is retained as a reference only. It is intentionally not an active workflow because the upstream Crowdin project and its secrets do not belong to this repository. Enable a translation integration only after configuring Deku-owned credentials and project settings.

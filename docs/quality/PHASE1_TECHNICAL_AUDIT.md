# Deku Keyboard — Phase 1 Technical Audit

Audit baseline: dev commit 4cf7486e4c8722a61d4e3c5b3b84194a4e01602b (2026-10-28). Follow-up implementation commits are applied after this snapshot; line numbers below refer to that baseline unless otherwise noted.

## 1. Architecture map

| Area | Implementation | Assessment |
|---|---|---|
| Android IME lifecycle | app/src/main/kotlin/org/deku/foundation/dekukeyboard/DekuImeService.kt | Owns IME callbacks, editor lifecycle, extract/fullscreen handling, and window-inset reporting. |
| IME window and sizing | ime/window/ImeRootView.kt, ImeWindow.kt, ImeWindowController.kt, ime/keyboard/DekuImeSizing.kt | Root Compose view intentionally fills the IME window to measure root insets. The actual keyboard window is positioned and sized separately using the window spec and row count. Do not fix a visual symptom by shrinking the root view before reproducing it; that can break inset/touch reporting. |
| Keyboard model and touch | ime/keyboard3/ImeController.kt, KeyboardLayoutBuilder.kt, interaction/PointerTracker.kt, ui/ImeKeyboardBox.kt, ui/ImeKeyButton.kt, ui/LongPressBox.kt | K3 builds runtime keyboard models; one pointer tracker maps touch coordinates to keys and popup alternatives. Main key pressed state existed, but the UI had no animated transform and used hard-coded red feedback. |
| Language/subtype settings | ime/core/SubtypeManager.kt, app/settings/localization/LocalizationScreen.kt, SelectLocaleScreen.kt, SelectCharacterLayoutScreen.kt, bundled localization extension JSON | Locale/subtype and character layout are separate concepts. Presets select locale defaults; layout assets and popup maps are separate catalogues. JSON validity alone does not prove that changes save and restore on-device. |
| NLP and suggestions | ime/nlp/NlpManager.kt, nlp/latin/LatinLanguageProvider.kt, nlp/arabic/ArabicLanguageProvider.kt, emoji and clipboard providers | Provider-based and local. Arabic suggestions currently use a bundled frequency list rather than a compact trained transformer. I found no wired translation/writing-assistant model runtime or weights in the inspected architecture. |
| Keyboard themes | ime/theme/DekuImeTheme.kt, DekuAssetResolver.kt, Snygg stylesheets under app/src/main/assets/extensions/org.deku.foundation.dekukeyboard.themes/ | The keyboard theme is separate from the Settings Activity Material theme. Key shape, background, elevation, and pressed state are stylesheet-driven; ad-hoc renderer colors bypass that system. |
| Clipboard/editor | ime/clipboard/, ime/keyboard3/ImeEditor.kt, live InputConnection access | Recent commits changed copy/cut to read live selection. Sensitive-field and incognito behavior still requires manual/instrumented verification. |
| Emoji/media | ime/media/emoji/, MediaInputLayout.kt | Emoji palette/history and suggestions exist. Feature-gate/compatibility TODOs remain; they must be traced before treating them as missing core functionality. |
| Settings/preferences | app/settings/**, app/AppPrefs.kt, JetPref | Domain screens are split out. Search, onboarding, previews, and cleanup still need a full UX audit. |
| Native/build modules | lib:android, lib:compose, lib:kotlin, lib:native (Rust/CMake), lib:snygg | Changes to Rust linking, ABI configuration, and Gradle conventions require focused build validation. |

## 2. Verified TODO/FIXME/stub findings

This is the verified high-value list from files inspected on dev, not a claim that every comment in all 1,283 repository paths has been exhaustively enumerated. GitHub code search without a pinned branch can return default-branch results; triage must use the actual dev file content.

| File / baseline location | Finding | Priority | Resolution plan |
|---|---|---:|---|
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/extension/ExtensionController.kt | delete(), importExtension(), and export() are now wired to the UI. Deletion is limited to indexed internal extensions; .flex import checks manifest/type, duplicate IDs, path traversal, duplicate ZIP entries, entry count, and extracted size; export requires a SAF content:// URI. | Implemented on dev; runtime round-trip not yet verified | Validate CI and test import/export/delete, malformed ZIPs, duplicate IDs, and cleanup of failed partial imports on-device. Treat this as security-sensitive code until those tests pass. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/extension/Loader.kt | Older default-branch search surfaced a placeholder comment, but the inspected dev file does not contain that stub. Current dev resolves locale/layout/popup-map assets, falls back to QWERTY, and caches eight compiled models. | Verified substantially implemented | Preserve these semantics with tests for language switching, stale compile cancellation, fallback behavior, and popup-map loading; do not reimplement from stale default-branch search results. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/window/ImeWindowMode.kt:56 | Fixed.THUMBS is marked as a placeholder. | P2 | Determine whether UI/config exposes it. Implement a real layout or remove unreachable enum/UI with migration-safe config handling. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ui/ImeKeyboardBox.kt:108,129,303 | Locale fallback and effect-based row-count update are marked incomplete; flick hints remain unimplemented. | P1/P2 | Use resolved active subtype for the spacebar; replace row-count side effect only after state ownership is clear; add hints only with tracker support and performance tests. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/nlp/NlpManager.kt:374,442,471 | NLP cleanup comments, clipboard-text assembly hack, and phone-number preprocessing TODOs remain. | P1 | Profile first; keep clipboard data out of private fields; use bounded Unicode-aware extraction and unit tests before regex changes. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ImeController.kt and ime/keyboard3/ImeEditor.kt | Stateful model compilation and expensive editing operation are noted as TODOs. | P1 | Measure key-to-commit latency, cancel stale compilation, cache only by complete model identity, and avoid unbounded surrounding-text reads. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/DekuApplication.kt | Application bootstrap is marked as messy. | P2 | Map service lifetimes and singleton initialization before splitting; do not rewrite without tests. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/media/MediaInputLayout.kt and app/AppPrefs.kt | Emoji-search feature gate has a TODO to remove after implementation. | P2 | Verify feature and preference migration, then remove the gate or unfinished entry. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/theme/DekuAssetResolver.kt | A rewrite is requested by a TODO. | P2 | Add URI/asset-resolution tests before refactoring. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/DekuSpellCheckerService.kt | Sentence suggestions delegate to platform behavior despite a custom-solution TODO. | P2 | Verify platform behavior and implement a local provider-backed path only if measurable benefit exists. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/extension/ExtensionController.kt | Broad extension errors are not yet reported with fine-grained violations. | P2 | Add typed validation errors and stable messages after extension lifecycle semantics are tested. |
| LANGUAGEPACKS-CHINESE.md | Contains a literal TODO download URL placeholder. | P2 | Replace with a real release/repository URL or remove the misleading instruction. Never publish a fake URL. |
| lib/native/src/main/rust/CMakeLists.txt | Native linker permits multiple symbol definitions and has a TODO. | P1 | Audit exported symbols/link graph; remove broad linker allowance only after fixing duplicates per ABI. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/io/FileStorage.kt | Historical search showed path-traversal TODOs; current dev implementation rejects absolute paths, dot segments, NUL, and resolved paths outside the storage base. | Verified fixed | Preserve regression tests for traversal variants; do not regress to string-prefix-only checks. |
| app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/nlp/LanguagePackExtension.kt | The legacy language-pack implementation is commented out as a block and is not an active runtime feature. | Cleanup | Remove the dead commented-out code after checking history/attribution, or restore it only with a supported design and tests. |

## 3. Risks by priority

### P0 — blocker/security
- **Incognito and sensitive fields:** Password variations now have an explicit InputAttributes.isPasswordField classification; initial surrounding text is not requested for password fields; spell checks, suggestions, direct suggestions, composing analysis, and auto-commit are blocked, and current candidates are cleared on entry. Unit coverage was added for text/visible/web/numeric passwords. This still needs CI and real-app testing across all providers and Android editor implementations.
- **Offline guarantee:** The inspected app/src/main/AndroidManifest.xml has no android.permission.INTERNET. Keep CI enforcing its absence. Dependencies can still contain network code, so no outbound feature should be introduced.
- **Extension/archive trust boundary:** delete/import/export have implementations on dev, but must not be considered production-safe until the latest build passes and round-trip/security testing confirms ZIP limits, path guards, cleanup, and protection of bundled assets.

### P1 — major UX/reliability
- **Hard-coded red feedback:** ImeKeyboardBox.kt used Color.Red for the peek/swipe line and pending multi-tap highlight. Fixed on dev: the line resolves the active Snygg glide-trail foreground, pending multi-tap uses Snygg focus styling, and the drag-marker token follows each bundled theme's primary color. Debug touch boundaries remain red only in the explicit developer overlay.
- **Missing press motion/feedback:** Main keys and action keys now use a short spring scale at graphicsLayer; successful key releases dispatch configured KeyRelease feedback, and extended long presses dispatch existing LongPress feedback on the main thread. Device-side latency/jank and haptic timing remain unverified.
- **Keyboard fills screen / white view:** ImeRootView is intentionally full-root for geometry/inset measurement, while ImeWindow wraps its measured content and is anchored to the bottom. The default landscape mode changed from DYNAMICALLY_SHOW to NEVER_SHOW, and DekuImeService reevaluates fullscreen mode when the preference changes. This targets Android's default landscape extract-mode behavior without breaking root coordinate/inset measurement. Verify stored user preferences and reproduce across portrait, landscape, extract mode, split screen, gesture/3-button navigation; inspect onComputeInsets, ImeWindowSpec, row count, and Snygg background together.
- **Language/layout confusion:** These are separate data models and screens. CI proves asset reference consistency, not successful runtime save/restore. Test add/edit/remove, app restart, rapid switching, and mixed Arabic/English typing.
- **Arabic RTL:** Preserve physical row/control-key ordering; directionality belongs in text rendering and locale-specific layout data. Regress Arabic and mixed-script typing. Verify Arabic popup alternatives against the actual bundled mapping files.
- **Lag/jank:** Measure pointer/input latency before adding model inference or more animations. Compile keyboard models off the main thread, cancel stale work, and avoid recomputing layout on every touch.
- **Build governance:** Current CI checks identity, XML, strings, layout references, unit tests, and build. Green CI does not prove IME behavior on a device.

### P2 — polish/feature completeness
- Improve consistency across day/night/AMOLED/borderless themes; eliminate hard-coded non-debug colors.
- Finish or hide unfinished UI such as flick hints and the thumbs placeholder.
- Add reduced-motion, TalkBack semantics, contrast and touch-target checks.
- Keep prediction/translation/writing/voice modular and offline. Do not claim transformer quality or sub-30ms latency until licensed model assets, device benchmarks, memory measurements, cancellation, and sensitive-input exclusion exist.

## 4. Follow-up implementation status (2026-10-28)

- **Applied on dev:** K3 and action-key spring press animations; Snygg-based multi-tap highlight and theme-bound glide trail/drag marker; configured release and long-press feedback dispatch; retry-safe NLP provider lifecycle; explicit password classification and NLP suppression; landscape fullscreen default changed to NEVER_SHOW with live reevaluation; extension deletion/import/export implementations with local-only document picker, SAF export, and bounded ZIP handling; unsupported language-pack extension shortcut removed in favor of the bundled locale/layout picker.
- **Added tests:** password input variation classification and extension-path/archive-name guards. These are committed but pending validation by current-head Android CI.
- **CI at audit update:** Android run for SHA `1acd01ee13d1c7ef6b6cd05ea1b929a0e582ff45` was in progress; see [run 40758612378](https://github.com/Deku-Foundation/deku-keyboard/actions/runs/40758612378). Resource validation for the same SHA passed at [run 40758618044](https://github.com/Deku-Foundation/deku-keyboard/actions/runs/40758618044). Earlier successful builds do not verify later changes.
- **Still not implemented:** Neural translation, rewrite/writing assistant, or speech model. No compatible local model weights or runtime were found in the inspected code. No cloud fallback should be added.

## 5. Fix order
1. Maintain package identity, no-network policy, storage path safety, and CI.
2. Fix key feedback/popup styling and cover touch-state regressions.
3. Validate language/subtype/layout flows on-device, especially Arabic popups and stable physical key positions.
4. Resolve live stubs and the K3 loader TODO only after tracing their callers/contracts.
5. Profile IME layout/input and suggestions, then optimize measured bottlenecks.
6. Redesign themes/settings and add accessibility/reduced-motion behavior.
7. Evaluate local prediction, translation, writing assistance, and voice only with licensed weights and measured APK/RAM/latency budgets; no cloud fallback.

## 6. References consulted
- Android Developers, [Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method): IME lifecycle, UI, screen sizes, and input types.
- Android Developers, [Compose animation performance](https://developer.android.com/develop/ui/compose/animation/quick-guide): prefer draw-phase/lambda modifiers and graphicsLayer when layout need not change.
- Android Developers, [Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices): remember expensive calculations, reduce recompositions, defer state reads, and avoid backwards writes.
- Android Accessibility, [Touch target size](https://support.google.com/accessibility/android/answer/7101858): use at least 48dp interactive targets where practical.
- HeliBoard, [layout format documentation](https://github.com/HeliBorg/HeliBoard/blob/main/layouts.md): separates locale-specific popups from layout data and documents simple/JSON layouts and popup constraints.
- HeliBoard, [project README](https://github.com/heliborg/heliboard): offline operation, dictionaries, customizable layouts/themes, and dynamic colors.
- HeliBoard, [preferences guide](https://github.com/HeliBorg/HeliBoard/wiki/3.-Preferences): popup source/order, preview, haptics/sounds, and extra-key options.

This is a source-backed engineering audit, not a claim of passing device validation. Device-specific issues remain unverified until tested on representative Android hardware and editor apps.

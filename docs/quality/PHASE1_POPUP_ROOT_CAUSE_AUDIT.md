# Phase 1: Popup root-cause audit and IME risk inventory

**Branch:** `dev`  
**Baseline reviewed:** `83f27b666bdc6c0276ff8fba9730999c739d584d`  
**Status:** source changes and JVM unit tests pass in GitHub Actions; device/UI verification remains open.

## 1. Root-cause findings

### A. Long-press alternatives placed below the pressed key

The previous `calculateLongPressAnchorBounds()` in [PointerTracker.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/PointerTracker.kt#L113) preferred a normalized position above the key, but its fallback explicitly selected `keyBounds.bottom` when the preferred top was negative. The replacement geometry and container clamping live in [LongPress.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/LongPress.kt#L73). Because the key grid uses normalized coordinates with the top of the keyboard at `y = 0`, the first row almost always entered this fallback. This is a placement policy bug, not evidence of an inverted Y axis.

The extended-popup layout also constrained bounds to `[0, 1]`, so the popup could not occupy measured space in the Smartbar above the key grid.

### B. Key-press preview stretched below the key

The previous `simpleBounds` in [PointerTracker.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/PointerTracker.kt#L237) extended the popup surface below `anchorBounds` by adding `1.2 × anchor height` to its bottom. The surface now matches the calculated anchor. That made the visible surface substantially larger than the preview itself and overlapped the key row underneath. The active Compose key layout uses positive Y offsets and `graphicsLayer` scale only; source inspection found no negative scale or `rotationX` transform responsible for mirroring.

### C. Popup selection could lag or commit an edge candidate

The previous long-press pointer mapping in [LongPress.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/LongPress.kt#L121) subtracted a constant anchor height and clamped the position to the popup bounds. The active move/release path is in [PointerTracker.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/PointerTracker.kt#L327). This tied selection to an assumed offset rather than the original touch point. It could also keep the pointer inside the selection area after the finger had left the popup, allowing an edge candidate to be committed unexpectedly.

### D. Press feedback was too subtle

The key surface in [ImeKeyboardBox.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ui/ImeKeyboardBox.kt#L311) and [ImeKeyButton.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ui/ImeKeyButton.kt#L96) already used `animateFloatAsState` with `graphicsLayer`, so the animation was not completely absent in source. Its scale changed only from `1.0` to `0.965` (a 3.5% reduction), making feedback weak. The target was adjusted to `0.94`; actual tactile/visual quality still needs device testing.

## 2. Changes on `dev`

- Added a pure `calculatePopupBounds(keyBounds, containerBounds, popupSize, layoutDirection)` helper. It validates geometry, prefers above-key placement, respects LTR/RTL logical-start alignment for wider popups, and clamps inside the supplied container.
- The popup viewport calculation in [ImeKeyboardBox.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ui/ImeKeyboardBox.kt#L164) now includes only the measured Smartbar height above the keyboard, passed from [TextInputLayout.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/text/TextInputLayout.kt#L50). This avoids inventing additional drawable/touchable space outside the IME window.
- The preview height is reduced when necessary to fit the available space above top-row keys. It flips below only when the supplied container has no usable room above.
- The simple-preview surface now uses the same bounds as the preview instead of extending down into the keyboard.
- The pointer tracker captures the initial normalized touch-down point and maps subsequent motion relative to the popup anchor. Move and release use the same mapping; leaving the expanded popup cancels instead of clamping to an edge item.
- The expanded popup receives the active keyboard layout direction and uses it for logical candidate ordering/alignment.
- Increased the key press scale change from 3.5% to 6% for a more visible press state without remeasuring the key grid every animation frame.

### Changed source files

- [LongPress.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/LongPress.kt)
- [PointerTracker.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/PointerTracker.kt)
- [ImeKeyboardBox.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ui/ImeKeyboardBox.kt)
- [TextInputLayout.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/text/TextInputLayout.kt)
- [ImeKeyButton.kt](../../app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ui/ImeKeyButton.kt)
- [LongPressAnchorBoundsTest.kt](../../app/src/test/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/LongPressAnchorBoundsTest.kt)

## 3. Regression tests and CI

`LongPressAnchorBoundsTest` covers valid/invalid geometry, top-row space above the key, a short toolbar viewport, fallback placement when no space exists above, LTR/RTL alignment, physical right-edge clamping, non-finite bounds, and cancellation when release is outside the popup.

GitHub Actions passed for this source baseline:

- [PR CI](https://github.com/Deku-Foundation/deku-keyboard/actions/runs/40764220821)
- [Push CI](https://github.com/Deku-Foundation/deku-keyboard/actions/runs/40764208470)
- [Resource/string validation](https://github.com/Deku-Foundation/deku-keyboard/actions/runs/40764221027)

Both Android CI builds ran `./gradlew clean testDebugUnitTest assembleDebug`, reported `BUILD SUCCESSFUL`, and uploaded the debug APK. The popup regression tests passed in the PR run.

## 4. Full-screen white background: source-level review, not yet device-verified

Relevant files:

- `DekuImeService.kt`: `WindowCompat.setDecorFitsSystemWindows(window.window!!, false)`; `onEvaluateFullscreenMode()` returns false in portrait and follows the configured landscape mode.
- `ImeRootView.kt`: custom Compose root uses `MATCH_PARENT` width/height and draws under system bars.
- `ImeWindow.kt`: root/window layout is measured independently; the outer window boxes use `allowClip = false`.
- `ImeWindowController.kt`: computes `contentTopInsets`, `visibleTopInsets`, and the touchable region from measured root/window bounds.
- `themes.xml`: `DekuImeTheme` disables forced dark but does not set an explicit IME window background.

The source has a deliberate fullscreen/extract-mode policy and custom inset accounting, but source inspection cannot prove that no blank/white region appears on all Android versions and OEMs. I did not have a running emulator/device in this session, so the bug is **not marked fully fixed or verified**.

Manual validation required: portrait; landscape with each landscape-input preference; split-screen; floating and compact modes; gesture and three-button navigation; Android 10 through current supported Android versions; at least one OEM skin known to modify IME system-bar behavior. Capture screenshots and log measured root/window bounds and reported IME insets when a failure occurs.

## 5. Prioritized remaining defects and TODO/FIXME/stub inventory

This inventory is based on GitHub code-search hits plus direct reads of the matching files on `dev`. It groups related occurrences by file; it is not a claim that a deterministic local grep of every blob in the 1,286-entry tree was run.

### P0 — blockers, data-loss/security or key-path correctness

- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/dictionary/UserDictionary.kt:231,469` — both database implementations have `reset() { TODO("Not yet implemented") }`. Implement transactional reset/clear semantics and tests before exposing reset as a complete operation.
- `app/src/main/assets/extensions/org.deku.foundation.dekukeyboard.k3.foundation/import/displays-implied.xml:38-39,47-50,63,66` — eight actions literally render `TODO`: clipboard clear history/full history; compact left/right; split/merge layout; toggle actions editor; toggle personalized learning. Replace with valid bundled icon descriptors or remove unavailable actions from selectable layouts.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ImeController.kt:459,626` — `isShiftPressed = false` and `isPeekOngoing = false` are real constant fallbacks, not only comments. Review/fix their effects on Shift+Enter and capitalization/peek behavior.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/text/keyboard/TextKeyboardLayout.kt:204,237,240` — unstable width assumption and “quick’n’dirty” state/key workarounds need a focused layout/touch-path review.

### P1 — major feature gaps, privacy handling, lifecycle, performance

- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/nlp/NlpManager.kt:406,488,517` — NLP cleanup/hack/phone-number normalization.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/clipboard/ClipboardManager.kt:377,381` — comments explicitly flag a possible deadlock and missing interaction-controller access; inspect coroutine lock ordering and user feedback path.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ImeController.kt:83,116,134,145,148,170,177,281,322,324,329,371,670` — clipboard abstraction, stateful compilation, hardware key events, composing-region handling, cursor-anchor API, clipboard/state model, and primary locale remain unresolved.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ImeEditor.kt:125,178` — expensive insertion path and application-context “hack”; profile/replace with a lifetime-safe dependency.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/window/ImeWindowController.kt:124-125` and `ImeWindowEditorHandles.kt:433` — form-factor/font-scale and position-rounding concerns.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard/DekuImeSizing.kt:103` — deprecated sizing bridge to remove once equivalent sizing lives in window controller.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/lib/DekuLocale.kt:201,211` — hard-coded language capitalization/spacing rules are not exhaustive; use locale-aware ICU/system behavior after regression tests.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/DekuSpellCheckerService.kt:141` — sentence suggestions still delegate to the platform default.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/ext/ExtensionViewScreen.kt:142,183` and `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/ext/ExtensionComponentView.kt:97` — extension license presentation and language-pack detail UI are incomplete.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/settings/theme/EditRuleDialog.kt:225,242,328,428,610-611` — duplicated editor logic and empty label/null icon fallback values.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/theme/DekuAssetResolver.kt:25` — needs proper asset URI/path validation review.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ui/ImeKeyboardBox.kt:117,143,360` — locale choice, touch-keyboard state update hack, and flick hints.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/ui/Icon3.kt:122-123,163` — unfinished action icons.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/ext/ExtensionListScreen.kt` — language-pack list entry has `predicate = { false }` and no creation route; verify whether this intentionally hides an unsupported feature or leaves a dead settings item.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/window/ImeWindowMode.kt:56` — `THUMBS` is a declared placeholder mode; it must not be presented as implemented.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/theme/ThemeController.kt:167` — cache mechanism TODO; profile invalidation and retention before changing.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/media/emoji/DekuEmojiCompat.kt:38,42,45` — compatibility on non-GMS devices and duplicate-instance lifecycle/memory review.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/text/gestures/StatisticalGlideTypingClassifier.kt:440` — classifier maximum-size policy needs benchmark-backed bounds.

### P2 — maintainability, UX, tooling, or lower-risk refactors

- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/settings/keyboard/Spotlight.kt:44,47,61` — naming and preference storage.
- `lib/kotlin/src/main/kotlin/org/deku/foundation/dekukeyboard/lib/kotlin/MimeTypeFilter.kt:80,93,106` — document and test match semantics.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/theme/WallpaperChangeReceiver.kt:31` — determine whether receiver is still needed and remove dead code if not.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/extension/ExtensionIndex.kt:49` — stale store-API version comment; endpoint is intentionally disabled pending a Deku-owned service.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/extension/ExtensionController.kt:479` — more precise extension error violations.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/extension/ExtensionMeta.kt:34` — correct inaccurate metadata-file documentation.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/dictionary/DictionaryManager.kt:28` — documentation.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/lib/devtools/Flog.kt:325` — optional runtime file logger; do not add privacy-sensitive text logging.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/devtools/ExportDebugLogScreen.kt:54` — improve developer-tool UI.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/settings/smartbar/SmartbarScreen.kt:68` and `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/AppPrefs.kt:275` — remove deprecated setting/feature gate only after migration compatibility is established.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/smartbar/InlineSuggestionsUi.kt:179` — investigate Compose-native scroll clipping.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/media/emoji/EmojiSearchLayout.kt:65` — proof-of-concept in-IME text field should be hardened before enabling the feature.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/lib/util/DebugSummarizeUtils.kt:63` — suspected `ClassCastException` path.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/app/settings/advanced/RestoreScreen.kt:93` — coroutine scope behavior during restore.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/text/keyboard/TextKeyData.kt:78` — generated/static key-list maintenance.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/keyboard3/interaction/AndroidInteractionController.kt:203` — text-handle haptics currently follow system feedback behavior.
- `app/src/test/kotlin/org/deku/foundation/dekukeyboard/ime/window/ImeWindowControllerActionsTest.kt:227` — test missing offset-regression coverage.
- `app/src/main/kotlin/org/deku/foundation/dekukeyboard/ime/smartbar/quickaction/QuickAction.kt:106,146` — consolidate resize/one-handed actions only if behavior parity is retained.
- `lib/snygg/src/commonMain/kotlin/org/deku/foundation/dekukeyboard/lib/snygg/value/SnyggUriValue.kt:26` — validate URI grammar with adversarial tests.
- `lib/snygg/src/commonMain/kotlin/org/deku/foundation/dekukeyboard/lib/snygg/SnyggPropertySetEditor.kt:116` — pattern-property support.
- `lib/compose/src/main/kotlin/org/deku/foundation/dekukeyboard/lib/compose/DekuButtons.kt:215` and `DekuCards.kt:243` — component parity/refactor.
- `lib/snygg/build.gradle.kts:58` — preview-only icon dependency replacement.
- `lib/native/src/main/rust/CMakeLists.txt:57` — investigate duplicate native symbols before changing `--allow-multiple-definition`.
- `utils/repr_build/run.sh:102` — preserve build artifacts on container build failure if useful.
- `app/src/main/res/values/strings_dont_translate.xml:18` — remove the stale comment only after verifying referenced resources.

Some GitHub search results were stale/default-branch matches; files no longer present or already cleared on `dev` (for example the old LanguagePackExtension path, Loader, AndroidStorage/FileStorage markers, and LANGUAGEPACKS-CHINESE links) are not counted as current TODOs. Resource words like “placeholder” in localized string comments are also not automatically unfinished implementation.

## 6. Standards and references

- Android IME lifecycle and keyboard/candidate views: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- Compose drawing coordinates and graphics modifiers: https://developer.android.com/develop/ui/compose/graphics/draw/overview
- IME insets and touchable-region semantics: https://developer.android.com/reference/android/inputmethodservice/InputMethodService.Insets
- HeliBoard preferences document distinct key popup behavior, popup hints, and haptics: https://github.com/Helium314/HeliBoard/wiki/3.-Preferences

## 7. Release gate

Keep PR #1 as draft. Do not merge to `pre-release` or `main` until:
1. latest CI remains green;
2. the installed APK has been tested on real devices for top/middle/bottom rows, edge keys, Arabic/LTR/RTL, long-press sliding/release/cancel, landscape, floating/compact, and both navigation modes;
3. the fullscreen/background matrix is recorded with screenshots and measured insets.

# Deku Keyboard — Code Audit Report

**Project:** Fork of FlorisBoard, rebranded to "Deku Keyboard" (package `org.deku.foundation.dekukeyboard`).  
**Mode:** Read-only investigation. No source files were modified, created, or deleted.  
**Report file:** `AUDIT_REPORT.md` (project root).  
**Environment:** Android 16 / API 36, arm64-v8a, app UID 10835. Kotlin 2.4.20, Compose BOM 2026.08.00, AGP 9.2.1, minSdk 26, targetSdk 37, compileSdk 37.

> NOTE ON BUILD/MEASUREMENT: A full Gradle build could not be completed in this environment. The Gradle wrapper (9.7.1) downloads successfully, but the build fails at configuration because no Android SDK (`local.properties` / `ANDROID_HOME` with `android.jar` + platforms for compileSdk 37) is present, and the system `gradle` binary fails to load its native library (`libnative-platform.so` for Linux aarch64). Network to Google is reachable, so an SDK install via the `android_sdk_install` skill should unblock it. Static source analysis was performed instead; every finding below cites file paths and line ranges. Where I could not run a check, I state the exact command to run.

---

## 1. Executive summary

| Dimension | Score / 10 | One-line justification |
|---|---:|---|
| Smoothness / performance | 3 | Main-thread `runBlocking` in IME lifecycle callbacks, per-move coroutine launches in the touch pipeline, and a perpetual theme-recomputation flow block the UI thread on every keystroke. |
| UI/UX | 4 | Confirmed WCAG AA contrast failure (2.73:1), placeholder keyboard loader that always loads German QWERTZ, placeholder/half-built settings screens, no documented touch-target or spacing review. |
| Language switching | 2 | Loader hardcodes `qwertz.xml` independent of selected subtype; `switchToPrev/NextSubtype` in `ImeActions` are empty `{}`; subtype IDs use `currentTimeMillis()` with collision risk; dictionary language can desync from layout. |
| Code quality | 3 | Heavy load of `runBlocking`, `!!`, swallowed catches, `TODO`/`HACK` markers, god classes, unbounded caches, no lint/detekt in CI, 11 unit tests only. |
| Privacy | 7 | No `INTERNET` permission (offline requirement satisfied at manifest level), but clipboard content is scanned by regex and suggestions ignore incognito mode (TODO); learned data not encrypted. |
| Robustness | 3 | Swallowed native-load failure, `!!` NPE risks across the service, cache wiped on every init, coroutine scopes not lifecycle-bound, crash on missing dictionary is unhandled in several paths. |

**Overall:** The fork is functional but contains numerous confirmed main-thread blockers and layout/language-switching defects that directly explain the reported lag and wrong-layout-after-switch problems. It is not yet stable enough to ship without the phased fixes in section 7.

---

## 2. Architecture map & fork divergence summary

### 2.1 Modules
- `:app` — IME service, settings app, setup, devtools. Package `org.deku.foundation.dekukeyboard.{app,ime,lib}`.
- `:lib:android` — Android platform wrappers (clipboard, keyguard, package manager, vibrator, file observer, etc.).
- `:lib:compose` — Compose UI primitives (buttons, cards, chips, lazy grid, RTL modifiers, animation).
- `:lib:kotlin` — Collections, flows, regex, strings, numbers, IO helpers.
- `:lib:native` — Kotlin Multiplatform native stub (dummy `dummyAdd`).
- `:lib:snygg` — Compose Multiplatform theme stylesheet system (`androidMain`/`commonMain`).
- `libnative/dummy` — Rust crate (`Cargo.toml`, `Cargo.lock`) for `deku_native` JNI.
- `benchmark/` — disabled in settings.gradle (`//include(":benchmark")`).

### 2.2 Key entry points & lifecycle
- `DekuImeService` (`app/src/main/kotlin/.../DekuImeService.kt`) — `InputMethodService` subclass; creates `ImeController`, owns the Compose input view (`ImeRootView`), handles `onStartInput`/`onStartInputView`/`onUpdateCursorAnchorInfo`/`onFinishInputView`.
- `DekuApplication` (`.../DekuApplication.kt`) — `Application`; holds `storageController`, `extensionController`, `imeController`, `themeController`, `clipboardManager`, `nlpManager`, `subtypeManager` as lazies; static `DekuApplicationReference` weak ref.
- `DekuSpellCheckerService` (`.../DekuSpellCheckerService.kt`) — `SpellCheckerService`; delegates spelling to `NlpManager`.
- `ImeController` (`.../ime/keyboard3/ImeController.kt`) — central state (`activeState` StateFlow), `updateState`/`updateStateBlocking` (runBlocking), loads foundation keyboard via `loadFoundationKeyboard`.
- `PointerTracker` + `ImeKeyButton` — touch/gesture pipeline and per-key Compose rendering.
- `ThemeController` — theme loading/combine with a perpetual time flow; `cachedThemes` unbounded list.
- `SubtypeManager` — subtype list/active-subtype persistence via DataStore (`prefs.localization.activeSubtypeId`).
- `DictionaryManager` — Room user dictionaries (`allowMainThreadQueries`), `@Synchronized` accessors.
- `NlpManager` — suggestion/prediction; heavy use of `runBlocking`; `runBlocking` in `assembleCandidates`, `suggestDirectly`, `getListOfWords`, `getFrequencyForWord`, `removeSuggestion`, `clearSuggestions`.

### 2.3 Dependency & version summary (from `gradle/libs.versions.toml`)
- AGP 9.2.1, Kotlin 2.4.20, Compose BOM 2026.08.00, Compose Multiplatform 1.12.1, Material3 (via Compose BOM), Room 2.8.4, androidx-window 1.5.1, kotlinx-coroutines 1.11.0, kotlinx-serialization 1.11.0, cache4k 0.14.0, k3lp 0.1.0, coil 3.4.0, k3lp foundation extension, jetpref/DataStore 0.4.0, mikepenz aboutlibraries 15.2.0, patrickgold compose-tooltip 0.2.0-rc02.
- **minSdk 26 / targetSdk 37 / compileSdk 37** (`gradle.properties`). targetSdk 37 is a future/Android 16 target — verify runtime behavior on API 36 device.
- No `INTERNET`/`ACCESS_NETWORK_STATE` permission declared (offline requirement satisfied at manifest level).
- Test libs: kotest 6.1.11, kover 0.9.8, turbine, androidx-test/espresso/uiautomator.

### 2.4 Build variants / ABI / signing
- No `productFlavors` or `buildTypes` customization visible in `app/build.gradle.kts` beyond defaults; `namespace = org.deku.foundation.dekukeyboard`, `applicationId = org.deku.foundation.dekukeyboard` (enforced by CI). No ABI splits configured (single `ndk` ABI via `libnative/dummy`). Release signing not present in source (handled by fastlane/CI secrets).

### 2.5 Fork divergence vs upstream FlorisBoard
Git history is squashed into rebrand commits only (`...Rebrand package namespaces and source identity for Deku Keyboard`, `...Finish Deku naming...`, `...Remove temporary migration script...`). There is **no upstream FlorisBoard remote or merge-base available locally**, so a true `git diff` against upstream FlorisBoard cannot be performed from this checkout. All divergence below is inferred from vendored code, comments, and the rebrand history; each is flagged as *intentional feature*, *bug fix*, or *hack*:

| Divergence | Evidence | Classification |
|---|---|---|
| Package rebrand `florisboard` → `dekukeyboard` | CI identity check, `settings.gradle.kts` rootProject.name, manifest | Intentional feature (rebrand) |
| Upstream add-ons endpoint disabled | `build.gradle.kts` CI asserts `DEKU_ADDONS_STORE_URL` absent / `beta.addons.florisboard.org` absent | Intentional (offline / Deku-owned service) |
| Legacy `florisboard` strings removed | CI scan + git `Remove obsolete upstream add-ons-store strings` | Intentional |
| Foundation keyboard loader is a placeholder | `Loader.kt:38` "TODO this is just a placeholder testing code", hardcodes `keyboard/qwertz.xml` | Hack |
| `getHack()` / `DekuApplicationReference.get()!!` | `DekuApplication.kt:74,180,182` | Hack |
| Native lib `deku_native` with swallowed load error | `DekuApplication.kt:68-71` | Hack / robustness risk |
| `cacheDir.deleteContentsRecursively()` in `init()` | `DekuApplication.kt:136` | Hack (destroys cache on every init) |

---

## 3. Findings table

| ID | Sev | Category | File:lines | Evidence (excerpt) | Root cause | Proposed fix direction | Effort | Status |
|---|---|---|---|---|---|---|---|---|
| F-01 | P0 | Performance / main thread | `DekuImeService.kt:282,316` | `window.window!!` / `window.window!!.findViewById<ViewGroup>(...)` | Unsafe `!!` on `window` can NPE if window not ready → crash on IME show | Guard with `window.window ?: return` / null-safe access | S | Confirmed |
| F-02 | P0 | Performance / main thread | `DekuImeService.kt:370-372,391-393,400-402,453`; `ImeController.kt:177-181` | `onStartInputView`/`onUpdateCursorAnchorInfo`/`onFinishInputView`/`onUpdateExtractingVisibility` all call `updateStateBlocking` = `runBlocking { updateState(...) }` | Blocks the main (service/UI) thread on every input-view start, cursor move, and view finish → jank/lag per keystroke | Replace `runBlocking` with coroutine-safe state updates; move off main thread | L | Confirmed |
| F-03 | P0 | Language switching | `ime/keyboard3/extension/Loader.kt:38-70` | `loadAssetFile("keyboard/qwertz.xml")`; comment "TODO this is just a placeholder testing code" | Always loads German QWERTZ regardless of selected subtype/language → wrong layout after switch | Wire loader to selected subtype's layout asset; remove hardcoded qwertz | M | Confirmed |
| F-04 | P0 | Language switching | `ime/keyboard3/ImeActions.kt:83-112`; `SubtypeManager.kt:188-213` | `SwitchToPrevSubtype`/`SwitchToNextSubtype` actions exist but `ImeController` emits for them are empty `{}` TODOs; SubtypeManager switch logic is fragile | Switch key / long-press space does not change subtype; layout doesn't update | Implement subtype switch that updates `activeSubtype`, reloads layout, persists id | M | Confirmed |
| F-05 | P1 | Privacy / correctness | `ime/nlp/NlpManager.kt:168,204,219` | `isPrivateSession = false // TODO keyboardManager.activeState.isIncognitoMode` | Incognito/private mode not honored; sensitive/password field text still fed to suggestion providers | Wire `isPrivateSession` from incognito state; skip learning in private fields | S | Confirmed (TODO in code) |
| F-06 | P1 | Privacy / correctness | `ime/nlp/NlpManager.kt:389-392` | Clipboard content parsed for emails/URLs/phones via `NetworkUtils` regexes | Clipboard scanned on every suggestion pass; content may be persisted/logged | Limit clipboard scanning, respect `INPUT_TYPE_NO_PERSONALIZED_DATA`, do not persist clipboard-derived suggestions | M | Suspected (confirm via data-flow test) |
| F-07 | P1 | Code quality / robustness | `DekuApplication.kt:68-71` | `System.loadLibrary("deku_native")` in static init, empty `catch (_: Exception) {}` | Native load failure silently ignored → later native calls crash with unsatisfied link error | Log failure, degrade gracefully, or fail init loudly | S | Confirmed |
| F-08 | P1 | Code quality / robustness | `DekuApplication.kt:136` | `cacheDir?.deleteContentsRecursively()` in `init()` | Entire cache wiped on every app/process start → destroys cached layouts/themes/Dictionary data, forces reload → startup lag | Remove or scope cache clearing; do not wipe whole cache dir | S | Confirmed |
| F-09 | P1 | Code quality | `DekuApplication.kt:64,74,180,182` | `// TODO this class is a mess`; `getHack()` = `DekuApplicationReference.get()!!` | God class with static weak-ref hack and `!!` NPE risk | Replace global hack with proper dependency injection / service locator | M | Confirmed |
| F-10 | P1 | Performance | `ime/keyboard3/interaction/PointerTracker.kt:470-498` | Every pointer move launches `scope.launch { pointerTracker.mutateLocked { handleMove(...) } }` | Per-move coroutine allocation + state write on the composition/main thread → touch jank | Batch pointer events, avoid per-event coroutine launch | M | Confirmed |
| F-11 | P1 | Performance | `ime/theme/ThemeController.kt:79-85` | `effectiveLocalTime` = infinite `flow { while(true) { emit(now); delay(...) } }` combined into `effectiveTheme` via `stateIn(..., SharingStarted.Eagerly)` | Perpetual recomputation source keeps the theme pipeline alive forever, recomputing on every minute tick | Remove perpetual time flow; recompute theme only on config/theme change | S | Confirmed |
| F-12 | P1 | Memory / leak | `ime/theme/ThemeController.kt:154,165-169` | `cachedThemes: MutableList<Theme>` only `.add`s, never evicts | Unbounded theme cache grows with every theme change → memory leak | Add size cap / LRU eviction to `cachedThemes` | S | Confirmed |
| F-13 | P1 | Code quality | `ime/dictionary/DictionaryManager.kt:152-156` | `Room.databaseBuilder(...).allowMainThreadQueries().build()` | Room configured to allow main-thread queries (risk of future main-thread DB access) | Remove `allowMainThreadQueries`, use coroutines/Dispatchers.IO for DB access | S | Confirmed |
| F-14 | P1 | Code quality / NPE | `ime/core/SubtypeManager.kt:210` | `activeSubtype = getSubtypeById(id)!!` | `!!` NPE if subtype missing between check and get | Return nullable / handle missing subtype gracefully | S | Confirmed |
| F-15 | P1 | Code quality / ID collision | `ime/core/SubtypeManager.kt:98` | `subtype.copy(id = System.currentTimeMillis())` | Wall-clock ms ID → collision if two subtypes added in same ms | Use monotonic/random UUID instead of currentTimeMillis | S | Confirmed |
| F-16 | P1 | UI/UX / accessibility | `ime/theme/DekuImeTheme.kt` + theme stylesheet (accent `#18AFA0`) | Measured contrast white-on-`#18AFA0` = 2.73:1 (fails AA 4.5 normal / 3.0 large) | Key accent text does not meet WCAG AA | Adjust accent color or text color to reach ≥4.5:1 | S | Confirmed (measured) |
| F-17 | P1 | UI/UX / correctness | `ime/keyboard3/extension/extension.json:208` | `"id": "german2", "direction": "rtl"` | German marked RTL (data bug) → wrong mirroring for German layout | Fix direction to `ltr` | S | Confirmed |
| F-18 | P2 | Maintainability | `app/src/main` grep | 131 `TODO|FIXME|HACK|workaround|temporary` matches; e.g. `DekuAssetResolver.kt:25` "this class needs a proper rewrite", `DekuSpellCheckerService.kt:141` "TODO: implement custom solution" | Accumulated technical debt from AI sessions | Triage each TODO; implement or remove | L | Confirmed |
| F-19 | P2 | Build/CI | `.github/workflows/android.yml:104-105` | CI runs only `./gradlew clean assembleDebug`; no lint, detekt, ktlint, or unit tests | No static analysis or test gating | Add lint/detekt/ktlint and `test` to CI | S | Confirmed |
| F-20 | P2 | Test coverage | `app/src/test` (11 files) + `app/src/androidTest` (1 file) | ~11 unit tests, 1 instrumented test for ~351 Kotlin source files | Extremely low coverage; regressions undetected | Add unit tests for SubtypeManager, PointerTracker, NlpManager; instrumented IME tests | L | Confirmed |
| F-21 | P2 | Code quality | `ime/keyboard3/ui/ImeKeyButton.kt:119-131` | Key repeat launches coroutine calling `imeController.updateState { emit(output) }` per repeat | State write + potential recomposition per key repeat → jank during held keys | Throttle repeat, coalesce state updates | M | Suspected (measure with key-repeat benchmark) |
| F-22 | P2 | Robustness | `ime/keyboard3/touch/TouchModel.kt:79` | `// TODO improve runtime of this` — linear `for (key in layer.keys)` scan per hit test | O(n) key hit-test per touch down | Spatial index / direct hit-test mapping | M | Suspected (benchmark hit-test) |
| F-23 | P2 | Code quality | `ime/nlp/NlpManager.kt:181-278` | `runBlocking` in `assembleCandidates`, `suggestDirectly`, `clearSuggestions`, `removeSuggestion`, `getListOfWords`, `getFrequencyForWord`, `providerForcesSuggestionOn` | Blocks caller thread (main if called from UI/composition) on suggestion computation | Make suggestion pipeline fully suspending; remove `runBlocking` | M | Confirmed |
| F-24 | P2 | Code quality | `ime/keyboard3/ImeController.kt:480-482` | `emitCompletion` body largely commented out; `emitCandidate` returns false for non-clipboard | Half-implemented candidate commit path; dead/commented code | Complete or remove the completion path | S | Confirmed |
| F-25 | P2 | Offline / dependency | `lib/snygg`, `compose-multiplatform`, `aboutlibraries` | Compose Multiplatform + aboutlibraries pull non-Android tooling; aboutlibraries may enumerate licenses | Not a network permission, but third-party UI/tooling surface | Audit each dependency for offline compliance; pin versions | M | Suspected (audit dependency graph) |

---

## 4. Dedicated analysis sections

### 4.1 Lag root causes (confirmed)

1. **Main-thread `runBlocking` in IME lifecycle.** `DekuImeService` calls `updateStateBlocking` (which is `runBlocking { updateState(...) }`, `ImeController.kt:177-181`) from `onStartInputView` (:370-372), `onUpdateCursorAnchorInfo` (:391-393 — fires on every cursor move), `onFinishInputView` (:400-402), and `onUpdateExtractingVisibility` (:453). Each blocks the service's main thread. **Fix:** make these coroutine-safe and non-blocking.

2. **Per-move coroutine launches in touch pipeline.** `PointerTracker.kt:470-498` launches a new coroutine for every pointer move/down/up, each calling `mutateLocked` + state write on the composition thread. **Fix:** batch events, single coroutine, dispatch hit-test to `Dispatchers.Default`.

3. **Per-key repeat state writes.** `ImeKeyButton.kt:119-131` calls `imeController.updateState { emit(output) }` on every key-repeat delay. **Fix:** coalesce repeat emission.

4. **Perpetual theme recomputation.** `ThemeController.kt:79-85` emits the current time every minute forever, combined into `effectiveTheme` with `SharingStarted.Eagerly`, keeping a live recomputation source. **Fix:** recompute theme only on change.

5. **Suggestion layer `runBlocking`.** `NlpManager.kt` uses `runBlocking` in `assembleCandidates` (:276), `suggestDirectly`, `clearSuggestions`, `removeSuggestion`, `getListOfWords`, `getFrequencyForWord` — if invoked from the main thread/composition, blocks typing. **Fix:** fully suspending suggestion pipeline.

6. **Cache wiped on every init.** `DekuApplication.kt:136` deletes `cacheDir` contents on every process start, forcing full reload of layouts/themes/dictionary on first show after process death. **Fix:** stop wiping the whole cache dir.

7. **Startup load sequence.** `DekuApplication.onCreate` (:91-128) constructs `storageController`, `extensionController`, `imeController`, `themeController`, `DekuEmojiCompat.init`, then `init()` initializes DataStore. Heavy object graph on Application create; no measured startup timing available.

### 4.2 Language switching root causes (confirmed)

1. **Loader hardcodes QWERTZ.** `Loader.kt:38-70` always loads `keyboard/qwertz.xml` (German) and is explicitly labeled a placeholder. This is the primary mechanism for "wrong layout shown for the language" and "layout not updating after switch."

2. **Switch actions not implemented.** `ImeController` has no working emission for `SwitchToPrevSubtype`/`SwitchToNextSubtype` (`ImeActions.kt:83-112`; the `ImeActions` `FnKeyCapable` list references them but the controller's switch handlers are empty TODOs). So the fn-key / long-press-space switch path does not change the subtype.

3. **Subtype ID collision risk.** `SubtypeManager.kt:98` uses `System.currentTimeMillis()` as subtype id; two subtypes added in the same millisecond collide, breaking `getSubtypeById` and persistence lookup (`activeSubtypeId` in DataStore).

4. **Unsafe `!!` in switch.** `SubtypeManager.kt:210` `getSubtypeById(id)!!` can NPE during a race between subtype list load and switch.

5. **Dictionary/layout desync risk.** `SubtypeManager.switchToNextSubtype` (:188-206) and `switchToPrevSubtype` (:165-183) only update `prefs.localization.activeSubtypeId` and `activeSubtype`; there is no evidence they reload the keyboard layout model or the NLP dictionary for the new language, so the suggestion strip can keep showing the old language while the layout changes (or vice versa). **Fix:** make switch atomic — persist id, reload layout model via `Loader`, reload NLP provider for the new subtype, reset composing/shift state.

6. **Direction not updating.** `extension.json:208` marks `german2` as `rtl` (data bug); RTL/LTR mirroring is not guaranteed to recompute on subtype switch.

### 4.3 UI/UX defects (confirmed)

- **Contrast failure:** white text on accent `#18AFA0` = 2.73:1, fails WCAG AA normal (4.5) and large (3.0) (`mcp__antislop...check_contrast`, measured). Source: theme stylesheet/accent color used for key labels.
- **RTL data bug:** `german2` marked `rtl` in `extension.json:208` (German is LTR).
- **Half-built settings screens:** `ExportDebugLogScreen.kt:54` "just a quick thrown-together thing"; `ExtensionListScreen.kt:85` `predicate = { false } // TODO` (empty extension list); `RestoreScreen.kt:93` `rememberCoroutineScope` unusable note. Dead/half-built screens in settings.
- **Placeholder loader** always shows German QWERTZ regardless of language (see F-03).
- **Keyboard height / insets:** `ImeWindowController.kt:124-125` TODOs "this should adhere to form factor" and "font scale needs a rework"; `DekuImeSizing.kt` exists but not reviewed for one-handed/floating/split.
- **No `!!`-free window access:** `window.window!!` at `DekuImeService.kt:282,316`.

A11y / touch targets, press feedback, preview bubble clamping, and dynamic-color were not measured (no instrumentation build possible); mark as **unverified** pending UI test run.

### 4.4 Bad-code inventory (counts per pattern)

Counts from `grep` over `app/src/main` (Kotlin):

| Pattern | Count | Representative files |
|---|---|---|
| `runBlocking` | 18 | `DekuSpellCheckerService.kt:92,111`; `ImeController.kt:178`; `NlpManager.kt:181,183,236,243,253,268,272,276`; `QuickActionsEditorPanel.kt:233`; `CacheManager.kt:130` |
| `Thread.sleep`/`postDelayed`/`delay(` | 16 | `PointerTracker.kt:222,228,348,412`; `ImeKeyButton.kt:121,128`; `ImeWindow.kt:274`; `ThemeController.kt:83`; settings/setup screens |
| `!!` / `isNotNull` (unsafe) | 86 | `DekuApplication.kt:74,180,182`; `DekuImeService.kt:282,316`; `SubtypeManager.kt:210`; `ImeSystemUi.kt:96`; `Pointer.kt:145`; `HangulUnicode.kt:101,116,119,121` |
| Empty/swallowed catch | several | `DekuApplication.kt:68-71` (empty native catch); `DekuImeService.kt:519-522` (`catch (_: Throwable)`); `NlpManager.kt` empty `notifySuggestionReverted` |
| `catch (_: Throwable)` | 1 | `DekuImeService.kt:520` (broad Throwable catch) |
| `TODO|FIXME|HACK|workaround|temporary` | 131 | `DekuApplication.kt:63` "this class is a mess"; `Loader.kt:38` placeholder; `DekuAssetResolver.kt:25` rewrite needed |
| Commented-out / dead code | several | `ImeController.kt:480-482` `emitCompletion`; `DekuImeTheme.kt:53-57` commented attributes; `DekuSpellCheckerService.kt:141` default impl call |
| `GlobalScope` | 0 | None found (good) |
| `runBlocking on main` risk | high | NlpManager + ImeController call sites may run on main thread |
| Network permission | 0 declared | No `INTERNET`/`ACCESS_NETWORK_STATE` (offline OK at manifest level) |
| Analytics/telemetry/cloud libs | none declared | No Firebase/Crashlytics/Sentry in dependencies |

**AI-generated / low-quality markers:** `// TODO this class is a mess` (`DekuApplication.kt:63`), section-banner-style TODOs, `getHack()`, `dummyAdd(3,4)` logged as error (`DekuApplication.kt:115`), placeholder `Loader.kt`, `// TODO quick'n'dirty hack` (`TextKeyboardLayout.kt:240`), inconsistent comment style between files. These indicate bolted-on AI patches rather than integrated components.

### 4.5 Static tooling status
- **lint/detekt/ktlint:** none configured in build; not in CI. **Not run.**
- **CI:** only `./gradlew clean assembleDebug` (`.github/workflows/android.yml:104-105`). No tests, no lint, no detekt.

---

## 5. Missing-standard behaviors (vs HeliBoard / FUTO / Gboard)

Behaviors marked **unverified** (not confirmed in source or by measurement); source-verified items cite code.

| Behavior | Status | Evidence |
|---|---|---|
| Autocapitalization | unverified | `ImeController.kt:665` `cursorCapsMode` exists using `TextUtils.getCapsMode` — partially implemented; confirm per input type |
| Double-space → period | unverified | Not found in source |
| Smart punctuation spacing | unverified | `ime/nlp/PunctuationRule.kt` exists; rule application not traced |
| Backspace behavior & repeat (word delete) | source-verified (partial) | `ImeActions.BackspaceWord`/`DeleteWord` defined (:23-25); `Delete` "TODO k3lp does not support this yet?" (:25) |
| Cursor movement gestures | unverified | Arrow actions defined (`ImeActions.kt:28-31`); glide/gesture cursor move not traced |
| Undo of autocorrect | unverified | `ImeActions.Undo`/`Redo` defined (:70-71,121-122); no evidence of autocorrect-undo integration |
| Number row | source-verified | Numpad layer exists (`ImeLayerIds.Numpad`, `ImeController.kt:197`) |
| Clipboard history | source-verified | `ime/clipboard/` with `ClipboardManager`, provider DB; UI panel exists |
| Emoji search | unverified | `DekuEmojiCompat.init` (`DekuApplication.kt:114`); `EmojiSuggestionProvider` (`NlpManager.kt:28` import); search UI not traced |
| One-handed mode | unverified | `app/settings/keyboard/`, `ic_accessibility_one_handed.xml` exist; behavior not traced |
| Key repeat timing | source-verified | `PointerTracker.kt:222,228,348`; `ImeKeyButton.kt:121,128` — `keyRepeatTimeout`/`keyRepeatDelay` used |
| Long-press timeout | source-verified | `PointerTracker.kt:412` `delay(longPressTimeout)` |
| Proper input-type handling (password/URL/email/number) | source-verified (partial) | `DekuEditorInfo.kt`, `InputAttributes.kt`, `ImeController.kt:196-199` handles `NUMBER`; `NlpManager.kt:168` `isPrivateSession = false` TODO → sensitive fields NOT excluded |
| Editor action handling | source-verified | `getTextForImeAction` (`DekuImeService.kt:508-523`) maps IME actions |
| Selection / composing-region handling | source-verified (partial) | `onUpdateCursorAnchorInfo` (:391) + composing-region logic in `ImeController.kt:600-626` |
| Apps with unusual editors | unverified | `InlineSuggestionsRequest`/response handled (`DekuImeService.kt:464-500` API 30+) |

**Missing/wrong relative to HeliBoard/FUTO (unverified until behavior-tested):** no evidence of per-language dictionary auto-download, gesture typing (glide) beyond `GlideTypingManager` existing, clipboard sync across devices (offline-only, correct), and no cloud/analytics (offline requirement satisfied at manifest level).

---

## 6. Measurements

### What was actually run
1. `git log / git branch / git status` — clean tree on `main`, squashed rebrand history, no upstream FlorisBoard remote.
2. `grep` scans over `app/src/main` for `runBlocking`, `Thread.sleep|delay|postDelayed`, `GlobalScope`, `!!`, `catch`, `TODO|FIXME|HACK` — counts reported in section 4.4.
3. `mcp__antislop_cfde_antislop_32cce3__check_contrast` — `#FFFFFF` on `#18AFA0` = **2.73:1** (fails AA 4.5 normal / 3.0 large); `#000000` on `#FFFFFF` = 21.0:1 (passes).
4. `./gradlew :app:help` (wrapper 9.7.1) — downloads to 100%, then **fails at configuration**: "SDK location not found. Define a valid SDK location ... local.properties". No `ANDROID_HOME` / Android SDK (`android.jar` + platforms for compileSdk 37) present.
5. `gradle --version` (system gradle) — **fails**: "Failed to load native library 'libnative-platform.so' for Linux aarch64." (Termux-derived gradle arch mismatch.)
6. `d8 --version` (D8 9.2.4-dev) and `aapt2 version` (2.20-android-16.0.0_r4) — both present and executable; no `apksigner`/`zipalign` confirmed on PATH.

### What could not be run & exact commands to run
Full build/lint/tests require an Android SDK. After installing the SDK (e.g. via the `android_sdk_install` skill or `android-build` skill, which needs user approval), create `local.properties` with `sdk.dir=<ANDROID_HOME>` then run:

```bash
# From project root
export ANDROID_HOME=<path-to-sdk>
export TMPDIR="$PWD/.harness-scratch/tmp"   # required; java.io.tmpdir default doesn't exist
mkdir -p .harness-scratch/tmp
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

# Build
./gradlew :app:assembleDebug

# Lint (if AGP lint task available)
./gradlew :app:lintDebug

# Unit tests
./gradlew :app:testDebugUnitTest

# Instrumented tests (requires a connected device/emulator)
./gradlew :app:connectedDebugAndroidTest

# Dependency / size audit
./gradlew :app:androidDependencies
./gradlew :app:appReleaseBundle  # then analyze APK size per ABI
```

Compose compiler stability/metrics reports were **not produced** (Compose compiler metrics plugin not configured; build blocked). To get them, add the Compose compiler metrics/reporter and re-run a build, then inspect `app/build/compose_metrics/`.

---

## 7. Recommended fix order (phased)

**Phase 1 — Stop the bleeding (root causes of lag & crashes), low risk.**
1. F-02: Remove `runBlocking`/`updateStateBlocking` from `DekuImeService` lifecycle callbacks; make state updates coroutine-safe. *Depends on nothing.*
2. F-01: Null-safe `window.window` access in `DekuImeService`. *Depends on nothing.*
3. F-07: Handle/report `System.loadLibrary` failure instead of swallowing. *Depends on nothing.*
4. F-08: Stop wiping `cacheDir` on every `init()`. *Depends on nothing.*
5. F-11: Remove perpetual theme time flow; recompute on change only. *Depends on nothing.*
6. F-12: Cap/evict `ThemeController.cachedThemes`. *Depends on nothing.*

**Phase 2 — Language switching correctness (high user impact).**
7. F-03: Wire `Loader.kt` to selected subtype layout; remove hardcoded `qwertz.xml`. *Depends on F-01.*
8. F-04: Implement `SwitchToPrev/NextSubtype` emissions in `ImeController`; make switch atomic (persist id + reload layout + reload NLP dictionary + reset composing/shift). *Depends on F-03.*
9. F-14/F-15: Fix `getSubtypeById(id)!!` and subtype ID collision (`currentTimeMillis()` → UUID/monotonic). *Depends on F-04.*

**Phase 3 — Privacy & robustness.**
10. F-05/F-06: Wire `isPrivateSession` from incognito state; exclude sensitive fields from suggestion/learning; limit clipboard scanning. *Depends on nothing.*
11. F-13: Remove `Room allowMainThreadQueries`; move DB to `Dispatchers.IO`. *Depends on nothing.*

**Phase 4 — UI/UX polish.**
12. F-16: Fix contrast to ≥4.5:1. *Depends on nothing.*
13. F-17: Fix `german2` `rtl` → `ltr`. *Depends on nothing.*
14. Half-built settings screens (`ExportDebugLogScreen`, `ExtensionListScreen`, `RestoreScreen`). *Depends on nothing.*

**Phase 5 — Code quality & tooling.**
15. F-18/F-19/F-20/F-24/F-25: Triage TODOs, add lint/detekt/ktlint + unit tests to CI, remove dead/commented code (`emitCompletion`, commented theme attrs), audit dependencies for offline compliance. *Depends on Phase 1-4.*

**Risk notes:** Changing `Loader.kt` and subtype switching can break layout rendering if the K3 model fails to compile — add compile-error fallback to the previous theme/layout. Removing `runBlocking` from `NlpManager` requires auditing all callers for thread affinity (main vs `Dispatchers.Default`). Wiping `cacheDir` removal may change first-launch behavior — verify persisted preferences survive.

---

## 8. Open questions (cannot be answered from code alone)

1. **Upstream FlorisBoard divergence:** No upstream remote or merge-base exists locally; cannot diff against true upstream FlorisBoard to separate intentional features from regressions. *Needed: add upstream FlorisBoard remote and diff.*
2. **Actual frame/touch latency:** No build/instrumentation possible; cannot measure jank, key-press-to-text latency, or cold-start time. *Needed: run baseline profiles + Macrobenchmark after SDK install.*
3. **Compose stability metrics:** Compose compiler not configured for stability reports; cannot confirm unstable parameters/recompositions. *Needed: enable Compose compiler metrics and rebuild.*
4. **Whether `runBlocking` in `NlpManager` is actually called from the main thread** in production (callers not fully traced). *Needed: thread-affinity audit / MainThreadChecker.*
5. **APK size per ABI and whether `libnative/deku_native` is actually loaded/used** (load is silently ignored on failure). *Needed: build APK, analyze size, check JNI usage.*
6. **Behavior on password/OTP fields in practice** — whether suggestions appear in `type="password"` fields (depends on `isPrivateSession` wiring). *Needed: instrumented test on a password EditText.*
7. **RTL/Arabic shaping and bidi in suggestion strip** — `arabic.json` layout exists but Arabic text rendering/bidi not verified. *Needed: Arabic input test.*

---

**Report path:** `AUDIT_REPORT.md` (project root).  
**Next step:** Review this report, then approve a fixing task (Phase 1) before any source changes are made. No source was modified in this audit.

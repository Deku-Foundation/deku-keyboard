# Iteration 3 engineering report

## Scope and verification policy

This report records source changes and automated evidence for the Deku Keyboard development branch. Android IME behavior is device- and host-app-dependent; static inspection and JVM tests do not prove the behavior on every keyboard host, ROM, orientation, or navigation mode.

## Changes in this iteration

- Long-press previews and the expanded candidate panel are rendered by the keyboard-root overlay, matching the normalized coordinate space used by popup geometry. The available viewport includes the measured Smartbar region so the top row can use that space before the popup flips below.
- Key press scale state is read from the lambda-based `graphicsLayer` block, deferring the animation state read to the draw phase instead of recomposing the key tree every animation frame.
- Suggestion assembly and provider removal no longer synchronously block through `runBlocking`; stale candidate assembly is discarded by a revision counter. Dictionary-reading APIs are suspending.
- Suggestion generation is blocked when the editor is incognito, sets `IME_FLAG_NO_PERSONALIZED_LEARNING`, or uses password, email, URI, phone, numeric, or no-suggestions input types. Incognito sessions do not publish keyboard-generated or clipboard candidates.
- The local Arabic provider now normalizes hamza/alef variants, alef maqsura, taa marbuta and Arabic diacritics for matching while retaining dictionary spelling for output. A heuristic Arabizi-to-Arabic candidate index is generated locally from the bundled Arabic word list; it is not a morphology-aware transliteration model.
- Clipboard-media editor access uses an injected application context rather than a static `getHack()` lookup. Unused JNI smoke-test logging has been removed.
- CI checks source and merged debug manifests for the absence of the `INTERNET` permission and checks active dependencies for common cloud/analytics/Google ML SDKs.

## Automated quality gate

Run from the repository root:

```bash
./gradlew clean testDebugUnitTest assembleDebug
./gradlew lintDebug
find app/build/outputs/apk -type f -name '*.apk' -exec ls -lh {} \;
find app/build/outputs/apk -type f -name '*.apk' -exec sha256sum {} \;
```

The first command is the CI build/test command. Lint is listed separately because the existing CI workflow does not yet treat every legacy lint baseline item as a failure. Record the actual output of each command; do not infer APK size, warning totals, or build duration from repository metadata.

## Device validation required

Install the current CI artifact on a real device and switch to Deku Keyboard through Android's Input Method settings. Before testing, obtain the exact component with `adb shell ime list -a` rather than assuming the package suffix.

Test:
- Long press every row, especially top-row and physical screen edges, then slide through candidates and release inside and outside the popup.
- Repeat with Arabic and English layout selection, mixed RTL/LTR text, Arabic hamza alternatives, diacritics, and punctuation.
- Check press feedback and responsiveness under sustained fast tapping; check language switching after repeated switches.
- Portrait, landscape, split-screen, floating/compact modes, dark/light themes, gesture navigation, and three-button navigation.
- Password, visible-password, email, URL, numeric, phone, and OTP fields; verify suggestions disappear and incognito never shows clipboard/suggestion candidates.
- Large font scale, TalkBack, process death/restart, and low-memory pressure.

## Performance measurement plan

No key-to-render latency, cold-start timing, heap use, or on-device APK runtime measurements are claimed by this report. Measure with a physical mid-range device:
1. Record a baseline and post-change `./gradlew clean testDebugUnitTest assembleDebug` wall time and APK sizes for the exact same variant and ABI configuration.
2. Use Android Studio System Trace/Perfetto while issuing rapid taps and long presses. Inspect main-thread input dispatch, Compose recomposition, RenderThread, and frame deadlines.
3. If adding the Macrobenchmark module, use `StartupTimingMetric` for keyboard/settings startup and `FrameTimingMetric` during a scripted typing/press-animation journey. Compare P50/P90/P95/P99 and frame overrun; do not infer the requested sub-30 ms suggestion latency from frame duration.
4. Measure model memory before implementing neural inference. The current Arabic/English prefix providers are dictionary-based, not neural models, and no encrypted personalization store or translation/writing mini-model is delivered by these changes.

## Known limitations

- The geometry tests cover deterministic normalized rectangles, but the system IME window and physical device clipping still require manual validation on Android.
- Arabizi suggestions are heuristic candidates generated from the existing offline dictionary; Arabic clitic morphology, spelling correction, learned ranking, and a calibrated confidence threshold remain unimplemented.
- No local transformer, ONNX/TFLite model, translation model, writing assistant, or speech model is included.
- Glide typing is currently disabled in the implementation: its classifier is replaced by `Unit` and suggestion update returns immediately. This report does not claim glide typing works.
- Legacy TODOs and upstream-maintenance notes remain elsewhere in the inherited codebase; they must be triaged by owner and behavior rather than mass-deleted without understanding the affected feature.

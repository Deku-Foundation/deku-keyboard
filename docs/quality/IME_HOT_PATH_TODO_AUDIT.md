# IME hot-path TODO audit

**Scope:** this pass reviewed high-frequency keyboard interaction, touch-model caching, text deletion/surrounding-text access, and the input-method service lifecycle. It is a scoped audit, not a claim that every inherited TODO in every module has been eliminated.

## Resolved in the current dev branch

- Long-press popup anchors are derived from the actual key bounds and clamped to the normalized keyboard viewport. When there is no room above the top-row key, the anchor moves below it instead of being clipped or covering the pressed key. Geometry tests cover narrow keys, top-edge placement, landscape-style geometry, and invalid bounds.
- The flick/peek movement threshold is a persisted user preference in dp instead of a hard-coded value. The tracker is recreated when that preference changes.
- The touch-model cache is LRU-bounded to eight entries, matching the existing compiled K3 model cache. This prevents stale language/layout models from accumulating without a bound.
- Forward delete reads the live text-after-cursor window and uses InputConnection.deleteSurroundingTextInCodePoints; its optimistic local snapshot removes a whole Unicode code point instead of one UTF-16 code unit. Unit tests cover supplementary-plane characters.
- On API 31+, ImeEditor.getSurroundingText attempts a consistent InputConnection.getSurroundingText snapshot in one IPC call. Invalid/unsupported snapshots fall back to the legacy calls; older Android versions retain that fallback. Selection splitting is unit-tested, including UTF-16 selection offsets.
- A missing enabled voice-input IME now displays a localized message instead of failing silently.
- Removed an empty collector from DekuImeService that performed no state update. Window configuration is already combined into ImeWindowController.activeWindowSpec.
- The popup-mapping integration check now accepts a non-empty scoped uri mapping when a valid locale-specific map intentionally has no general all group. The Arabic XML test checks row semantics rather than insignificant whitespace.

## Intentionally not replaced by guesses

- **Touch hit testing:** each layer has a small ordered set of keys, and order resolves overlapping hitboxes. A spatial index is deferred until a reproducible benchmark shows this scan is a material source of latency; an index must preserve hitbox priority and be rebuilt on model changes.
- **K3 text replacement cost:** ImeEditor.replaceText uses composing-region/commit/selection operations because K3LP does not yet expose dedicated insertion/deletion primitives. Replacing it locally risks composition and selection corruption. The follow-up requires K3LP API support plus host-editor integration tests.
- **Physical Shift + Enter and key state:** emitEnter does not currently have a verified hardware-key dispatcher state to query. Guessing from software shift/caps state would change behavior on multiline fields; implementation needs hardware-key integration tests.
- **Cursor movement:** raw DPAD key events remain the compatibility path. Replacing them with cursor-anchor APIs requires tests across native text fields, WebView-based editors and editors with custom cursor behavior.
- **Window typography across form factors:** the remaining form-factor/font-scale design needs measurements and manual checks for phones, tablets, foldables, floating and one-handed windows. No speculative formula was substituted.
- **K3 compilation and clipboard architecture TODOs:** these cross module/runtime boundaries. They need separate design and compatibility work rather than deleting comments or changing behavior in this bug-fix pass.

## Validation gates

1. ./gradlew clean testDebugUnitTest assembleDebug must pass on the exact PR head.
2. Manual IME testing must verify language add/switch, Arabic popup selection, forward-delete on emoji, physical keyboard shortcuts, and long-press popup position in portrait and landscape.
3. Profile touch hit testing and model compilation before any further algorithmic optimization.

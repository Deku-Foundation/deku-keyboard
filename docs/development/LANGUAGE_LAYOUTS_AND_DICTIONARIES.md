# Language, layout, and dictionary implementation

**Status:** implementation notes for the `dev` branch, 2026-10-26.

## Keep these concepts separate

A language subtype selects a locale and an optional character-layout ID. A character layout decides which key labels and output characters are available; it does not imply that a spelling dictionary or next-word model exists for that language.

The bundled manifest is:

`app/src/main/assets/ime/keyboard/org.deku.foundation.dekukeyboard.localization/extension.json`

Its `subtypePresets` map BCP-47 language tags to preferred layout IDs. The locale picker merges those presets with system locales. The subtype editor now exposes a separate searchable character-layout picker backed by the character-layout extension manifest, so users can select layouts such as Colemak, Dvorak, Workman, and alternative layouts without treating each layout as a separate language. The keyboard loader validates explicit selections against all IDs declared in the layout manifest, loads the selected JSON asset on `Dispatchers.IO`, builds K3 XML, and compiles the resulting model. Missing or invalid layout data falls back to QWERTY and logs a warning. CI also verifies that every declared character layout has a matching JSON asset.

The current manifest includes an Arabic preset and the bundled `arabic.json` character layout. This is layout support, not Arabic spell-check or Arabic word prediction.

## Right-to-left keyboard geometry

The user's required behavior is a stable physical keyboard geometry when switching languages. RTL writing direction must not, by itself, mirror the entire key grid or swap the positions of Shift, Backspace, Space, Enter, and other control keys.

The K3 layout builder therefore treats each layout JSON row as an ordered set of physical left-to-right positions and retains the base keyboard's bottom control row. Arabic labels and outputs remain Arabic; the Arabic symbols-toggle label remains `؟١٢` / `٣٢١؟` according to the existing keyboard convention. Text direction and keyboard geometry are separate concerns and must be tested independently on an actual device.

## Dictionary and suggestion coverage

The current bundled word-frequency asset at `app/src/main/assets/ime/dict/data.json` is an English wordlist. It must not be presented as an Arabic, Persian, French, or German dictionary.

The Latin provider builds an immutable two-character prefix index once and returns bounded local prefix completions for English compositions. It does not auto-commit or send text to a network service, and it does not treat the English wordlist as a dictionary for other locales.

Arabic now has a separate `ArabicLanguageProvider`, selected for Arabic subtypes even when their saved provider ID predates this implementation. It uses the pinned 10,000-entry MSA/newswire word-frequency list under CC BY 4.0, performs Harakat/tatweel normalization, creates a local prefix index, and offers non-autocommitting completions. Unknown Arabic forms are treated as unspecified rather than misspellings because this list is not a complete morphological dictionary. The shipped data attribution is at `ime/dict/ar/ATTRIBUTION.txt` and in [ARABIC_DICTIONARY_ATTRIBUTION.md](ARABIC_DICTIONARY_ATTRIBUTION.md).

This Arabic provider is an MSA baseline, not an Algerian Darija dictionary, not a full spell-checker, and not a language model. Additional languages need separately reviewed data/models, attribution, memory/startup measurements, and regression tests. Keep layout availability independent so users can type in a language even when no matching offline dictionary is installed.

## HeliBoard patterns worth adapting

HeliBoard's public documentation separates language selection, main keyboard layouts, functional/symbol layouts, and dictionaries. Its custom layout formats support both a simple text-row format and a richer JSON format, with validation to avoid pathological row sizes and malformed layouts. Its language documentation also notes that suggestions depend on a matching dictionary, not just a selected language.

Deku should adopt those product and validation principles, but not copy HeliBoard source or assets into this Apache-2.0 codebase without a deliberate license review: HeliBoard is GPL-3.0. Prefer independently designed code and data with clearly compatible, recorded licenses.

References:
- HeliBoard repository/features/license: https://github.com/HeliBorg/HeliBoard
- Layout format, constraints, and layout validation notes: https://github.com/HeliBorg/HeliBoard/blob/main/layouts.md
- Language setup and dictionary dependency for suggestions: https://github.com/HeliBorg/HeliBoard/wiki/1.-Languages
- Custom layout workflow: https://github.com/HeliBorg/HeliBoard/wiki/2.-Layouts
- Android main-thread/ANR guidance: https://developer.android.com/topic/performance/issues/anr
- Compose performance guidance: https://developer.android.com/develop/ui/compose/performance

## Release test matrix

Before changing this implementation from `dev` to `pre-release`, verify each item on an installed build as well as in CI:

1. Add English, Arabic, and one additional locale from Settings > Languages & Layouts; save, reopen settings, and confirm all rows persist.
2. Activate Arabic from the configured language list and switch English/Arabic with the keyboard language key; confirm the keyboard does not flip/mirror as a whole and that keys emit the expected Arabic characters.
3. Check Shift, Backspace, Space, Enter, symbol-toggle, popup characters, and numeric/symbol layers in both locales.
4. Confirm a locale with no bundled layout safely uses QWERTY and leaves the stored locale intact.
5. Confirm English prefix completions work offline; confirm Arabic shows no English suggestions until a properly licensed Arabic dictionary is integrated.
6. Repeat language changes inside a text field, after closing/reopening the keyboard, and after force-stopping/restarting the app. Capture logcat if the active subtype and visible layout disagree.
7. Use Macrobenchmark / Android Studio profiling for startup, rapid key taps, swiping, emoji/clipboard opening, and repeated language switching before claiming lag or animation regressions are fixed.

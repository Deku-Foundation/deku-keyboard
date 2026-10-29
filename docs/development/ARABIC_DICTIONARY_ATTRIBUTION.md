# Arabic MSA frequency data: provenance and scope

The bundled Arabic frequency asset is copied from the upstream language pack at a pinned revision:
- Data: [10,000-entry frequency list](https://github.com/Luraty/language-packs/blob/7459e864b2bad4448b149f2a8b92b101156ca858/packs/ar/frequency.txt)
- Source project: [Luraty/language-packs — Arabic pack](https://github.com/Luraty/language-packs/tree/main/packs/ar)
- License declared by upstream: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- Underlying corpora: Leipzig Corpora Collection's `ara_news_2020_1M` and `ara_news_2022_1M`, Universität Leipzig; D. Goldhahn, T. Eckart & U. Quasthoff, “Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages” (LREC 2012).
- Lemma source noted upstream: Wikidata Lexemes, Arabic (Q13955), CC0 1.0.

The upstream project's [attribution record](https://github.com/Luraty/language-packs/blob/7459e864b2bad4448b149f2a8b92b101156ca858/packs/ar/ATTRIBUTION.md) and [machine-readable source record](https://github.com/Luraty/language-packs/blob/7459e864b2bad4448b149f2a8b92b101156ca858/languages/ar/sources.json) declare the frequency list CC BY 4.0 and describe licence verification for the named corpora. A copy of this attribution is also shipped inside the APK at `ime/dict/ar/ATTRIBUTION.txt`.

## Scope and limitations

The frequency list has 10,000 MSA/newswire lemmas. It is appropriate as a small offline prefix-completion baseline, not as a complete Arabic dictionary or morphological analyser. It does not model Algerian Darija; a dialect-aware product needs a separately licensed, representative dialect corpus and evaluation.

Deku does not infer that every absent form is misspelled. The initial spell-check provider reports known list entries as valid and unknown forms as unspecified, avoiding false spelling errors for inflections and words outside this limited list. Suggestions are generated locally, are not auto-committed, and are not sent to a network service.

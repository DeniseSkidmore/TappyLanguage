# Dataset research

First pass, 2026-11-12, from web search. Not legal advice; verify each license on the source's own page before shipping data.

## Summary

| Need | Best candidate | License | Fit |
|---|---|---|---|
| Greek word audio (native speaker) | Lingua Libre / Wikimedia Commons `El-<word>.ogg` files | CC BY-SA 4.0 (some Commons files vary per file) | Single words, native speakers. Greek coverage unknown; must be measured. |
| English word audio | Lingua Libre / Commons `En-<word>.ogg` files; gaps recorded by our native English speaker | CC BY-SA 4.0 (varies per file on Commons); own recordings CC BY-SA or CC0 (TBD) | Large English coverage expected; not measured. |
| Greek sentence audio | Mozilla Common Voice Scripted Speech 25.0, Greek | CC0 1.0 | ~17,300 validated clips, ~20 validated hours, 452 speakers. Sentences, not words: would need word-level alignment to cut single words. Possible source for phrasebook items. |
| Greek → English translation | Wiktionary via kaikki.org (wiktextract JSONL) | CC BY-SA (Wiktionary dual CC BY-SA / GFDL) | Rich: glosses, part of speech, inflections, often links to the audio file names above. Exact Modern Greek download URL to confirm. |
| Greek word frequency | hermitdave/FrequencyWords (OpenSubtitles 2018), `el` lists | Content CC BY-SA 4.0; code MIT | 50k list; subtitle-based, so it favours conversational words (good for this app). |

## License implications

- **CC BY-SA (most sources above):** attribution required (an in-app credits screen listing sources and authors). Data we ship that is derived from it (our processed word list, trimmed audio) must also be released CC BY-SA. This applies to the data files, not the app's code; the app code can stay under any license as long as the data is a separate, replaceable file.
- **CC0 (Common Voice):** no obligations. Note: one Mozilla Data Collective listing (Cypriot Greek spontaneous speech) adds a no-rehosting term on top of CC0; check the terms of whichever release we use.
- **Per-file licenses on Commons:** each audio file has its own license page; an import script must record license and author per file.
- **Net effect:** plan for language data files to be published CC BY-SA with an attribution manifest, separate from the app code. This fits the existing plan of language data as add-on files.

## Greek audio coverage check (2026-11-15)

Commons API survey: files prefixed `El-`, files prefixed `LL-Q9129` (Lingua Libre, Modern Greek), and `Category:Greek pronunciation`. 861 files; 754 distinct Greek texts, of which 650 are single words, many of them names. Matched against the FrequencyWords `el_50k` list (NFC, case-folded):

| Top N frequent words | With a Commons recording |
|---|---|
| 100 | 18 (18%) |
| 500 | 51 (10%) |
| 1,000 | 71 (7%) |
| 2,000 | 103 (5%) |

**Result: word recordings alone cannot carry the app.** About 100 useful words is enough for a prototype, not a product. The realistic main source is Common Voice Greek (CC0, ~17,300 human-read sentences) with words cut out by forced alignment (e.g. Montreal Forced Aligner, which has a Greek model). Cutting human recordings is allowed; it is not AI speech. 

Common Voice Greek sentence text (`server/data/el/sentence-collector.txt`, 6,461 sentences, 7,207 distinct words; not every sentence is necessarily recorded):

| Top N frequent words | In ≥1 sentence | In ≥3 sentences |
|---|---|---|
| 100 | 100% | 99% |
| 500 | 91% | 81% |
| 1,000 | 79% | 60% |
| 2,000 | 64% | 41% |

So sentences cover the core vocabulary well; the open question is whether alignment cuts clean enough single-word clips. Downloading Common Voice audio needs a Mozilla Data Collective account and accepting its terms (a [You] step).

## Gaps / next checks

1. ~~Count Greek single-word recordings~~: done, see above. Next: measure top-2,000 coverage in the Common Voice Greek sentence text (CC0 text in the Common Voice GitHub repo), then try aligning a sample.
2. Confirm the kaikki.org Modern Greek extract URL and its license statement.
3. Confirm the FrequencyWords Greek list's license in the repo itself, and whether OpenSubtitles terms add anything.
4. Phrasebooks (directions, airport, restaurant, etc.): no open source found yet. Claude drafts them from items that have recordings (Common Voice sentences, word recordings); user approves the English.
5. Exclude any release with a no-rehosting term (e.g. the Cypriot Greek spontaneous-speech listing).

## Sources

- [Lingua Libre (Wikipedia)](https://en.wikipedia.org/wiki/Lingua_Libre), [Lingua Libre stats](https://lingualibre.org/wiki/LinguaLibre:Stats)
- [Common Voice Scripted Speech 25.0 - Greek](https://prod.datacollective.mozillafoundation.org/datasets/cmn2cx91x01dno10754vxfu3b)
- [Common Voice Spontaneous Speech 1.0 - Cypriot Greek](https://datacollective.mozillafoundation.org/datasets/cmflnuzz3xq2ok28b4o1ojacv)
- [wiktextract](https://github.com/tatuylonen/wiktextract), [kaikki.org dictionary](https://kaikki.org/dictionary/)
- [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords)
- [Commons: Pronunciation files requests](https://commons.wikimedia.org/wiki/Commons:Pronunciation_files_requests)

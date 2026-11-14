# Data model (draft)

Derived from [requirements.md](requirements.md) as of 2026-11-12. Conceptual, not an implementation: names and types will change. `TBD` fields are still under discussion.

Three groups:
- **Language pack**: read-only data shipped in the language file.
- **User state**: running values saved on the device.
- **Level session**: in memory only; folded into user state on the between-levels screen, then discarded.

```mermaid
classDiagram
    direction LR

    %% ---------- Language pack (read-only, shipped) ----------
    class LanguagePack {
        learningLanguage : String
        knownLanguage : String
        version : String
    }
    class Word {
        id : String
        learningText : String
        knownText : String
        length : Int
        frequencyRank : Int
        kind : ItemKind
    }
    class Sentence {
        id : String
        words : List~Word~
    }
    class Phrasebook {
        name : String
        englishApproved : Boolean
    }
    class AudioClip {
        language : String
        sourceFile : String
        startMs : Int?
        endMs : Int?
    }
    class SourceRecord {
        path : String
        sourceUrl : String
        author : String
        license : String
        retrieved : Date
    }
    class ItemKind {
        <<enumeration>>
        LETTER
        WORD
    }

    LanguagePack "1" *-- "many" Word
    LanguagePack "1" *-- "many" Sentence
    LanguagePack "1" *-- "many" Phrasebook
    Sentence "1" o-- "many" Word : ordered
    Phrasebook "1" o-- "many" Word
    Phrasebook "1" o-- "many" Sentence
    Word "1" --> "1..2" AudioClip : learning + known audio
    Sentence "1" --> "1..2" AudioClip
    AudioClip "many" --> "1" SourceRecord : attribution (INDEX.csv)

    %% ---------- User state (saved on device) ----------
    class WordSkill {
        wordId : String
        combination : Combination
        repetitions : Int
        successAvg : Double
        latencyAvgMs : Double
        stability : Double
        lastSuccess : Timestamp
        lastPlayed : Timestamp
        predictedRecall() Double
        state() WordState
        unlocked : Boolean
    }
    class ModeProgress {
        contentMode : ContentMode
        level : Int
        levelSuccessAvg : Double
    }
    class Settings {
        gameplayInterface : String
        contentMode : ContentMode
        difficulty : Int
        selectedPhrasebook : String?
        recencyWeight : Double  TBD
    }
    class Combination {
        <<enumeration>>
        LEARNING_LEARNING
        KNOWN_LEARNING
        LEARNING_KNOWN
    }
    class WordState {
        <<enumeration>>
        UNPROVEN
        LEARNING
        FLUENT
    }
    class ContentMode {
        <<enumeration>>
        WORD
        SENTENCE
        PHRASEBOOK
    }

    Word "1" --> "0..3" WordSkill : one per combination
    WordSkill ..> Combination
    WordSkill ..> WordState : derived from R
    ModeProgress ..> ContentMode
    Settings ..> ContentMode

    %% ---------- Level session (in memory only) ----------
    class Level {
        number : Int
        plannedPlays : Int
        targets : List~WordSkill~
        distractors : List~Word~
    }
    class Play {
        wordId : String
        combination : Combination
        success : Boolean
        latencyMs : Int
        wrongTapWordId : String?
    }

    Level "1" *-- "many" Play
    Play ..> WordSkill : folded in after level
    Level ..> ModeProgress : updates level, levelSuccessAvg
```

Notes:
- **WordSkill** is the core of the spaced repetition: one row per word and combination, with running values only (no long-term log). `predictedRecall()` and `state()` are computed, not stored.
- **stability** is a single value for now. If we adopt FSRS-7's two-curve idea, it becomes a fast (within-session) and a slow (across-days) stability.
- **Letters** are modelled as `Word` with `kind = LETTER`, so they share WordSkill and selection.
- **Sentence score** (hits / (words + wrong taps)) is computed from the level's plays; whether sentences get their own skill rows is TBD.
- **ModeProgress** is per content mode, matching "level number saved per game mode". If gameplay interfaces multiply, the key becomes (interface, content mode).

# TappyLanguage
An android game for learning to read another language.

## Contributing

### Working with Claude Code
This repo is set up for [Claude Code](https://claude.com/claude-code). Read [CLAUDE.md](CLAUDE.md) first: it sets the ground rules and describes two gitignored working files, `docs/conversation.md` (threaded discussion) and `docs/open-items.md` (running list of open items). Revise those rules to suit how you work.

### Android Studio
- Install [Android Studio](https://developer.android.com/studio). The Gradle project is in `app/`.
- Command-line Gradle needs `JAVA_HOME` pointing at a JDK; Android Studio's bundled one works (e.g. `C:\Program Files\Android\Android Studio\jbr`).
- Optional plugin: [Mermaid](https://plugins.jetbrains.com/plugin/20146-mermaid) (Settings → Plugins → Marketplace), to render the Mermaid diagrams in `docs/requirements/`.

### Python environment
All project Python (Claude hooks, data scripts, forced alignment) runs in one conda env, `tappy`, defined in [environment.yml](environment.yml) with every package pinned (win-64).
1. Install [Miniconda](https://docs.conda.io/en/latest/miniconda.html).
2. From the repo root: `conda env create -f environment.yml` (later changes: `conda env update -f environment.yml --prune`).
3. Point Claude at the env's interpreter in `.claude/settings.local.json` (not committed, per machine):
   ```json
   { "env": { "TAPPY_PYTHON": "C:/Users/<you>/miniconda3/envs/tappy/python.exe" } }
   ```
4. Set a user environment variable `TAPPY_CONTACT` to your contact address (email or URL). The download scripts put it in their User-Agent, as Wikimedia's policy asks; they refuse to download without it. Never commit it.
5. Restart Claude Code so it sees the new variables.

### Audio data
Third-party audio is gitignored; only each language's `data/language/<language>/INDEX.csv` is committed. Before building the app, download the files:
- In Claude Code: run `/fetch-audio` (see [.claude/skills/fetch-audio/SKILL.md](.claude/skills/fetch-audio/SKILL.md)).
- Or directly: `"$TAPPY_PYTHON" -I -X utf8 .claude/skills/fetch-audio/fetch_audio.py data/language/greek`

Files are fetched one at a time, 120 s apart, to be polite to the providers, so a fresh setup takes a few hours. Already-downloaded files are skipped, so it is safe to re-run. Data rules are in [data/CLAUDE.md](data/CLAUDE.md).

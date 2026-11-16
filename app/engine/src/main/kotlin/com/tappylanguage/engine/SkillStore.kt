package com.tappylanguage.engine

/** In-memory set of skills for one pack. Creates a skill on first use. */
class SkillStore(private val pack: LanguagePack) {
    private val skills = mutableMapOf<SkillKey, Skill>()

    fun all(): Collection<Skill> = skills.values

    fun get(key: SkillKey): Skill? = skills[key]

    fun getOrCreate(key: SkillKey): Skill = skills.getOrPut(key) {
        val item = requireNotNull(pack.item(key.itemKey)) { "No item for ${key.itemKey}" }
        Skill(key, difficulty = Memory.initialDifficulty(item, pack.vocabularySize))
    }

    fun put(skill: Skill) {
        skills[skill.key] = skill
    }

    /** Fold a level's plays in play order (selection.md §3). */
    fun applyAll(plays: List<Play>) {
        plays.forEach { put(Memory.apply(getOrCreate(it.skill), it)) }
    }
}

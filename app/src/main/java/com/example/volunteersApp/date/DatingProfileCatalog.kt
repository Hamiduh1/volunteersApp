package com.example.volunteersApp.date

import java.util.Locale

/**
 * Shared with iOS `DatingProfileCatalog.swift` (VolunteersAppiOS docs/DATING_LOOP_CATALOG.md).
 * Strings are stored as-is in dating_profiles and compared as plain text across platforms,
 * so they must stay byte-identical to iOS (including the U+2026 ellipsis).
 */
enum class DatingRelationshipGoal(val value: String, val title: String, val emoji: String) {
    LONG_TERM("LONG_TERM", "Long-term partner", "💘"),
    LONG_TERM_OPEN_TO_SHORT("LONG_TERM_OPEN_TO_SHORT", "Long-term, open to short", "😍"),
    SHORT_TERM_OPEN_TO_LONG("SHORT_TERM_OPEN_TO_LONG", "Short-term, open to long", "🥂"),
    SHORT_TERM("SHORT_TERM", "Short-term fun", "🎉"),
    NEW_FRIENDS("NEW_FRIENDS", "New friends", "👋"),
    FIGURING_OUT("FIGURING_OUT", "Still figuring it out", "🤔");

    companion object {
        fun fromRaw(raw: String?): DatingRelationshipGoal? {
            val normalized = raw?.trim()?.uppercase(Locale.US).orEmpty()
            return values().firstOrNull { it.value == normalized }
        }
    }
}

object DatingProfileCatalog {
    const val MAX_INTERESTS = 5
    const val MAX_PROMPTS = 3
    const val MAX_PROMPT_ANSWER_LENGTH = 150
    const val MAX_SHORT_FIELD_LENGTH = 60
    const val MINIMUM_AGE = 18
    const val MAXIMUM_AGE = 99
    const val SUPER_LIKES_PER_DAY = 1
    const val SUPER_LIKE_CONTEXT = "Dating Loop Super Like"

    val INTERESTS: List<String> = listOf(
        "Volunteering",
        "Community service",
        "Mentoring",
        "Environment",
        "Education",
        "Faith",
        "Church",
        "Health & wellness",
        "Fitness",
        "Running",
        "Yoga",
        "Hiking",
        "Football",
        "Basketball",
        "Dancing",
        "Music",
        "Afrobeats",
        "Karaoke",
        "Movies",
        "Comedy",
        "Reading",
        "Writing",
        "Podcasts",
        "Photography",
        "Art",
        "Fashion",
        "Cooking",
        "Foodie",
        "Coffee",
        "Travel",
        "Road trips",
        "Beach",
        "Nature",
        "Gardening",
        "Pets",
        "Gaming",
        "Board games",
        "Tech",
        "Entrepreneurship",
        "Languages"
    )

    val PROMPT_QUESTIONS: List<String> = listOf(
        "A cause I care about…",
        "My favorite way to volunteer…",
        "My ideal Sunday…",
        "I'm happiest when…",
        "The way to win me over…",
        "Green flags I look for…",
        "My simple pleasures…",
        "Two truths and a lie…",
        "I'm looking for someone who…",
        "Together, we could…"
    )
}

val DatingProfile.goal: DatingRelationshipGoal?
    get() = DatingRelationshipGoal.fromRaw(relationshipGoal)

val DatingProfile.answeredPrompts: List<DatingPrompt>
    get() = prompts.filter { it.question.isNotBlank() && it.answer.isNotBlank() }

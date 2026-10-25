package com.example.volunteersApp.date

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DATING_MAX_INTERESTS = DatingProfileCatalog.MAX_INTERESTS
const val DATING_MAX_PROMPTS = DatingProfileCatalog.MAX_PROMPTS
const val DATING_PROMPT_ANSWER_MAX = DatingProfileCatalog.MAX_PROMPT_ANSWER_LENGTH
const val DATING_WORK_FIELD_MAX = DatingProfileCatalog.MAX_SHORT_FIELD_LENGTH
const val DATING_SUPER_LIKES_PER_DAY = DatingProfileCatalog.SUPER_LIKES_PER_DAY
const val DATING_FILTER_MIN_AGE = DatingProfileCatalog.MINIMUM_AGE
const val DATING_FILTER_MAX_AGE = DatingProfileCatalog.MAXIMUM_AGE

const val DATING_LOOP_LIKE_CONTEXT = "Dating Loop Profile"
const val DATING_LOOP_SUPER_LIKE_CONTEXT = DatingProfileCatalog.SUPER_LIKE_CONTEXT

typealias RelationshipGoal = DatingRelationshipGoal

val DatingRelationshipGoal.label: String get() = "$emoji $title"

val DATING_INTEREST_CATALOG: List<String> get() = DatingProfileCatalog.INTERESTS

val DATING_PROMPT_QUESTIONS: List<String> get() = DatingProfileCatalog.PROMPT_QUESTIONS

fun isDatingLoopInvitationContext(context: String?): Boolean =
    context?.contains("dating loop", ignoreCase = true) == true

fun isSuperLikeInvitationContext(context: String?): Boolean =
    context?.contains("super like", ignoreCase = true) == true

/** Birthdays are stored as UTC midnight (date picker), so read them in UTC and compare month, then day. */
fun ageFromBirthDate(birthDate: Date, now: Date = Date()): Int {
    val birth = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { time = birthDate }
    val today = Calendar.getInstance().apply { time = now }
    var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    val birthMonth = birth.get(Calendar.MONTH)
    val todayMonth = today.get(Calendar.MONTH)
    if (todayMonth < birthMonth ||
        (todayMonth == birthMonth && today.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))
    ) age--
    return age
}

fun isSameLocalDay(a: Date, b: Date = Date()): Boolean {
    val first = Calendar.getInstance().apply { time = a }
    val second = Calendar.getInstance().apply { time = b }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

/** "Name, 27" when the shared age is a valid adult age, otherwise just the name. */
fun DatingProfile.displayNameWithAge(): String {
    val base = name.ifBlank { "Community member" }
    val shownAge = age?.takeIf { it >= 18 }
    return if (shownAge != null) "$base, $shownAge" else base
}

/** Interests shared with the viewer first, then the rest in their original order. */
fun DatingProfile.interestsSharedFirst(viewer: DatingProfile?): List<String> {
    val mine = viewer?.interests.orEmpty().map { it.lowercase(Locale.ROOT) }.toSet()
    val (shared, rest) = interests.partition { it.lowercase(Locale.ROOT) in mine }
    return shared + rest
}

fun DatingProfile.visiblePrompts(): List<DatingPrompt> =
    prompts.filter { it.question.isNotBlank() && it.answer.isNotBlank() }

/**
 * iOS and Android both write dating profiles, and iOS-only fields (prompts, interests, age) arrive with
 * shapes the strict mapper may not accept. Parse field by field so one odd value can't hide a profile.
 */
fun DocumentSnapshot.toDatingProfileOrNull(): DatingProfile? {
    if (!exists()) return null
    val values = data ?: return null

    fun text(key: String): String = (values[key] as? String)?.trim().orEmpty()
    fun date(key: String): Date? = when (val value = values[key]) {
        is Timestamp -> value.toDate()
        is Date -> value
        else -> null
    }

    val prompts = (values["prompts"] as? List<*>).orEmpty().mapNotNull { entry ->
        val map = entry as? Map<*, *> ?: return@mapNotNull null
        val question = (map["question"] as? String)?.trim().orEmpty()
        val answer = (map["answer"] as? String)?.trim().orEmpty()
        if (answer.isBlank()) null else DatingPrompt(question = question, answer = answer)
    }

    return DatingProfile(
        uid = text("uid").ifBlank { id },
        name = text("name"),
        bio = text("bio"),
        gender = text("gender"),
        lookingFor = text("lookingFor"),
        phone = "",
        country = text("country"),
        imageUrls = normalizeDatingImageUrls(this),
        createdAt = date("createdAt"),
        age = (values["age"] as? Number)?.toInt()?.takeIf { it in 18..120 },
        relationshipGoal = text("relationshipGoal"),
        interests = (values["interests"] as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
            .distinct(),
        prompts = prompts,
        jobTitle = text("jobTitle"),
        school = text("school"),
        updatedAt = date("updatedAt"),
    )
}

data class DatingIncomingLike(
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String,
    val isSuperLike: Boolean,
    val timestamp: Date?,
)

data class DatingReceivedInvite(
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String,
    val status: String,
    val isSuperLike: Boolean,
    val timestamp: Date?,
    val matchedChatId: String?,
)

data class DatingProfileExtras(
    val birthDate: Date? = null,
    val relationshipGoal: RelationshipGoal? = null,
    val interests: List<String> = emptyList(),
    val prompts: List<DatingPrompt> = emptyList(),
    val jobTitle: String = "",
    val school: String = "",
    val keptImageUrls: List<String>? = null,
)

data class DatingSentLike(
    val recipientId: String,
    val status: String,
    val isSuperLike: Boolean,
    val timestamp: Date?,
    val matchedChatId: String?,
)

data class DatingMatch(
    val uid: String,
    val name: String,
    val photoUrl: String,
    val chatId: String?,
    val matchedAt: Date?,
)

enum class DatingLoopTab(val label: String) {
    Discover("Discover"),
    LikesYou("Likes You"),
    Matches("Matches"),
}

enum class DatingLikeOutcome { Sent, Matched, Failed }

/** users/{owner}/dating_comments/{senderId}; rules require exactly these keys. */
const val DATING_COMMENTS_SUBCOLLECTION = "dating_comments"
const val DATING_COMMENT_MAX_LENGTH = 300
const val DATING_COMMENT_SENDER_NAME_MAX = 80
const val DATING_COMMENT_PHOTO_URL_MAX = 2048
const val DATING_COMMENTS_LIMIT = 200L

val DATING_COMMENT_SUGGESTIONS = listOf(
    "Love your photos",
    "Your bio made me smile",
    "We have a lot in common",
    "What's your idea of a perfect date?",
)

/** A private note sent with a like. Only the profile owner sees it; replies happen on Blind Date. */
data class DatingComment(
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String,
    val text: String,
    val createdAt: Date?,
    val updatedAt: Date?,
)

fun DocumentSnapshot.toDatingCommentOrNull(): DatingComment? {
    if (!exists()) return null
    val text = getString("text")?.trim().orEmpty()
    if (text.isEmpty()) return null
    return DatingComment(
        senderId = getString("senderId")?.takeIf { it.isNotBlank() } ?: id,
        senderName = getString("senderName").orEmpty(),
        senderPhotoUrl = getString("senderPhotoUrl").orEmpty(),
        text = text,
        createdAt = getTimestamp("createdAt")?.toDate(),
        updatedAt = getTimestamp("updatedAt")?.toDate(),
    )
}

package com.example.volunteersApp.date

import java.util.Locale

enum class DatingBrowseLayout(val label: String) {
    Swipe("Swipe"),
    People("People"),
    Photos("Photos"),
    Discover("Quick view"),
}

data class DatingLoopFilters(
    val searchQuery: String = "",
    val genderFilter: Gender? = null,
    val countryFilter: String? = null,
    val hasPhotosOnly: Boolean = false,
    val minAge: Int = DATING_FILTER_MIN_AGE,
    val maxAge: Int = DATING_FILTER_MAX_AGE,
) {
    val hasAgeRange: Boolean get() = minAge > DATING_FILTER_MIN_AGE || maxAge < DATING_FILTER_MAX_AGE

    val activeCount: Int
        get() = listOf(
            searchQuery.isNotBlank(),
            genderFilter != null,
            !countryFilter.isNullOrBlank(),
            hasPhotosOnly,
            hasAgeRange,
        ).count { it }
}

data class DatingCompletenessStep(val label: String, val done: Boolean)

/** iOS 8-step meter: the 4 Blind Date basics plus birthday, goal, an interest and an answered prompt. */
fun datingProfileCompletenessSteps(profile: DatingProfile?, hasBirthDate: Boolean = profile?.age != null): List<DatingCompletenessStep> =
    listOf(
        DatingCompletenessStep("Display name", profile?.name?.isNotBlank() == true),
        DatingCompletenessStep("Bio ($DATING_BIO_MIN_LENGTH+ characters)", (profile?.bio?.trim()?.length ?: 0) >= DATING_BIO_MIN_LENGTH),
        DatingCompletenessStep("Country", profile?.country?.isNotBlank() == true),
        DatingCompletenessStep("At least 2 photos", (profile?.resolvedImageUrls()?.size ?: 0) >= 2),
        DatingCompletenessStep("Birthday", hasBirthDate),
        DatingCompletenessStep("Relationship goal", DatingRelationshipGoal.fromRaw(profile?.relationshipGoal) != null),
        DatingCompletenessStep("At least 1 interest", profile?.interests?.isNotEmpty() == true),
        DatingCompletenessStep("At least 1 answered prompt", profile?.visiblePrompts()?.isNotEmpty() == true),
    )

fun datingProfileRichCompletenessPercent(profile: DatingProfile?, hasBirthDate: Boolean = profile?.age != null): Int {
    if (profile == null) return 0
    val steps = datingProfileCompletenessSteps(profile, hasBirthDate)
    return steps.count { it.done } * 100 / steps.size
}

const val DATING_LOOP_MAX_PHOTOS = 6
const val BLIND_DATE_MAX_MEDIA = 12
const val DATING_BIO_MIN_LENGTH = 20

fun datingProfileCompletenessPercent(profile: DatingProfile?): Int {
    if (profile == null) return 0
    var score = 0
    if (profile.name.trim().isNotEmpty()) score += 25
    if (profile.bio.trim().length >= DATING_BIO_MIN_LENGTH) score += 25
    if (profile.country.trim().isNotEmpty()) score += 25
    if (profile.resolvedImageUrls().size >= 2) score += 25
    return score
}

fun isDatingLoopProfileComplete(profile: DatingProfile?): Boolean =
    datingProfileCompletenessPercent(profile) >= 100

fun preferenceAcceptsGender(lookingFor: LookingFor?, memberGender: Gender?): Boolean {
    val preference = lookingFor ?: LookingFor.EVERYONE
    val gender = memberGender ?: Gender.OTHER
    return when (preference) {
        LookingFor.EVERYONE -> true
        LookingFor.MEN -> gender == Gender.MALE
        LookingFor.WOMEN -> gender == Gender.FEMALE
    }
}

fun isMutuallyCompatibleDating(viewer: DatingProfile, member: DatingProfile): Boolean {
    if (viewer.uid.isBlank() || member.uid.isBlank() || viewer.uid == member.uid) return false
    val viewerGender = Gender.fromRaw(viewer.gender) ?: Gender.OTHER
    val memberGender = Gender.fromRaw(member.gender) ?: Gender.OTHER
    val viewerPreference = LookingFor.fromRaw(viewer.lookingFor) ?: LookingFor.EVERYONE
    val memberPreference = LookingFor.fromRaw(member.lookingFor) ?: LookingFor.EVERYONE
    return preferenceAcceptsGender(viewerPreference, memberGender) &&
        preferenceAcceptsGender(memberPreference, viewerGender)
}

fun applyDatingLoopFilters(
    profiles: List<DatingProfile>,
    viewer: DatingProfile?,
    filters: DatingLoopFilters,
    blockedIds: Set<String>,
    currentUserId: String?,
): List<DatingProfile> {
    return profiles
        .asSequence()
        .filter { profile ->
            val uid = profile.uid
            uid.isNotBlank() && uid != currentUserId && uid !in blockedIds
        }
        .filter { profile ->
            viewer == null || isMutuallyCompatibleDating(viewer, profile)
        }
        .filter { profile ->
            val query = filters.searchQuery.trim()
            query.isBlank() ||
                profile.name.contains(query, ignoreCase = true) ||
                profile.bio.contains(query, ignoreCase = true) ||
                profile.country.contains(query, ignoreCase = true)
        }
        .filter { profile ->
            val gender = filters.genderFilter
            gender == null || profile.gender.equals(gender.name, ignoreCase = true)
        }
        .filter { profile ->
            val country = filters.countryFilter?.trim().orEmpty()
            country.isBlank() || profile.country.equals(country, ignoreCase = true)
        }
        .filter { profile ->
            !filters.hasPhotosOnly || profile.resolvedImageUrls().isNotEmpty()
        }
        .filter { profile ->
            // Profiles without a shared age only drop out once the user narrows the range.
            val age = profile.age
            !filters.hasAgeRange || (age != null && age in filters.minAge..filters.maxAge)
        }
        .toList()
}

fun normalizeBlindDateBackendStatus(raw: String?): BlindDateUserStatus {
    return when (raw?.trim()?.lowercase(Locale.US)) {
        "matched" -> BlindDateUserStatus.Matched
        "active" -> BlindDateUserStatus.Active
        "expired" -> BlindDateUserStatus.Expired
        "awaiting_payment", "awaiting payment" -> BlindDateUserStatus.AwaitingPayment
        else -> BlindDateUserStatus.NotJoined
    }
}

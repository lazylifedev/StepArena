package com.lazyapps.steparena.feature.game

enum class RealUserChallengeRecoveryError { TRANSIENT, PERMANENT }

data class ParticipantIdsValidation(val opponentUid: String?)

fun validateRealUserParticipantIds(ids: List<*>, currentUid: String): ParticipantIdsValidation? {
    val participantIds = ids.filterIsInstance<String>()
    if (ids.size != 2 || participantIds.size != 2 || participantIds.toSet().size != 2) return null
    if (currentUid !in participantIds) return null
    return ParticipantIdsValidation(participantIds.single { it != currentUid })
}

fun realUserShouldShowNextChallenge(status: RealUserChallengeStatus): Boolean =
    status == RealUserChallengeStatus.FINALIZED

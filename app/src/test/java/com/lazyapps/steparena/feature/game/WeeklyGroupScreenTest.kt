package com.lazyapps.steparena.feature.game

import com.lazyapps.steparena.core.database.entity.WeeklyLeagueParticipantEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyGroupScreenTest {
    @Test fun visibleLeagueParticipantsReturnsEveryParticipantInRankOrder() {
        val participants = listOf(
            participant("p4", 4, false),
            participant("p1", 1, false),
            participant("p10", 10, true),
            participant("p2", 2, false),
        )

        assertEquals(
            listOf("p1", "p2", "p4", "p10"),
            visibleLeagueParticipants(participants).map { it.participantId },
        )
    }

    private fun participant(id: String, rank: Int, local: Boolean) =
        WeeklyLeagueParticipantEntity(
            leagueId = "week",
            participantId = id,
            displayName = id,
            avatarKey = "",
            points = rank,
            eligibleSteps = rank.toLong(),
            rank = rank,
            isLocalPlayer = local,
            generatedLocally = false,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )
}

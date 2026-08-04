package com.lazyapps.steparena.feature.game

import com.lazyapps.steparena.core.database.entity.WeeklyLeagueParticipantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyGroupScreenTest {
    @Test fun visibleLeagueParticipantsReturnsAllTenInRankOrderForEveryLocalPosition() {
        listOf(1, 5, 10).forEach { localRank ->
            val participants = (10 downTo 1).map { rank ->
                participant("p$rank", rank, rank == localRank)
            }
            val visible = visibleLeagueParticipants(participants)

            assertEquals((1..10).toList(), visible.map { it.rank })
            assertEquals(10, visible.map { it.participantId }.distinct().size)
            assertTrue((4..8).all { rank -> visible.any { it.rank == rank } })
            assertEquals(localRank, visible.single { it.isLocalPlayer }.rank)
        }
    }

    @Test fun duplicateParticipantIdIsRenderedOnlyOnce() {
        val visible = visibleLeagueParticipants(
            listOf(participant("same", 2, false), participant("same", 8, true)),
        )

        assertEquals(1, visible.size)
        assertEquals(2, visible.single().rank)
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

package com.lazyapps.steparena.game

import android.content.Context
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PlayerIdentity(val displayName: String?)

enum class DisplayNameError { TOO_LONG, CONTROL_CHARACTER }

data class DisplayNameValidation(
    val normalized: String?,
    val error: DisplayNameError? = null,
) {
    val isValid: Boolean get() = error == null
}

object PlayerDisplayNamePolicy {
    const val MAX_CODE_POINTS = 20

    fun validate(input: String): DisplayNameValidation {
        if (input.any(Char::isISOControl)) {
            return DisplayNameValidation(null, DisplayNameError.CONTROL_CHARACTER)
        }
        val normalized = input.trim().takeIf { it.isNotEmpty() }
        if (normalized == null) return DisplayNameValidation(null)
        if (normalized.codePointCount(0, normalized.length) > MAX_CODE_POINTS) {
            return DisplayNameValidation(null, DisplayNameError.TOO_LONG)
        }
        return DisplayNameValidation(normalized)
    }
}

fun publicDisplayName(displayName: String?, fallback: String): String =
    displayName?.takeIf { it.isNotBlank() } ?: fallback

class PlayerIdentityRepository(
    private val context: Context,
    private val database: StepArenaDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    val identity: Flow<PlayerIdentity> = database.gamePlayerProfile().observe().map {
        PlayerIdentity(it?.displayName)
    }

    suspend fun current(): PlayerIdentity = PlayerIdentity(database.gamePlayerProfile().get()?.displayName)

    suspend fun saveDisplayName(input: String): PlayerIdentity {
        val validation = PlayerDisplayNamePolicy.validate(input)
        require(validation.isValid)
        val now = clock.millis()
        val existing = database.gamePlayerProfile().get()
        database.gamePlayerProfile().upsert(
            (existing ?: GamePlayerProfileEntity(
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )).copy(displayName = validation.normalized, updatedAtEpochMillis = now),
        )
        database.weeklyLeagueParticipants().updateLocalDisplayName(
            publicDisplayName(validation.normalized, context.getString(R.string.game_you)),
            now,
        )
        return PlayerIdentity(validation.normalized)
    }
}

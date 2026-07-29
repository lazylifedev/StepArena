package com.lazyapps.steparena.core.database.converter

import androidx.room.TypeConverter
import com.lazyapps.steparena.game.*
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.recovery.RecoverySource
import com.lazyapps.steparena.recovery.TrackingGapReason
import com.lazyapps.steparena.recovery.TrackingGapStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionType

class ActivityConverters {
    @TypeConverter fun rankTier(value: RankTier?): String? = value?.name
    @TypeConverter fun rankTier(value: String?): RankTier? = value?.let(RankTier::valueOf)
    @TypeConverter fun matchType(value: MatchType): String = value.name
    @TypeConverter fun matchType(value: String): MatchType = MatchType.valueOf(value)
    @TypeConverter fun matchStatus(value: MatchStatus): String = value.name
    @TypeConverter fun matchStatus(value: String): MatchStatus = MatchStatus.valueOf(value)
    @TypeConverter fun matchOutcome(value: MatchOutcome?): String? = value?.name
    @TypeConverter fun matchOutcome(value: String?): MatchOutcome? = value?.let(MatchOutcome::valueOf)
    @TypeConverter fun personality(value: OpponentPersonality): String = value.name
    @TypeConverter fun personality(value: String): OpponentPersonality = OpponentPersonality.valueOf(value)
    @TypeConverter fun competitiveQuality(value: CompetitiveStepQuality): String = value.name
    @TypeConverter fun competitiveQuality(value: String): CompetitiveStepQuality = CompetitiveStepQuality.valueOf(value)
    @TypeConverter fun leagueStatus(value: LeagueStatus): String = value.name
    @TypeConverter fun leagueStatus(value: String): LeagueStatus = LeagueStatus.valueOf(value)
    @TypeConverter fun seasonStatus(value: SeasonStatus): String = value.name
    @TypeConverter fun seasonStatus(value: String): SeasonStatus = SeasonStatus.valueOf(value)
    @TypeConverter fun quality(value: DataQuality): String = value.name
    @TypeConverter fun quality(value: String): DataQuality = enumValueOf(value)
    @TypeConverter fun gapReason(value: TrackingGapReason): String = value.name
    @TypeConverter fun gapReason(value: String): TrackingGapReason = enumValueOf(value)
    @TypeConverter fun gapStatus(value: TrackingGapStatus): String = value.name
    @TypeConverter fun gapStatus(value: String): TrackingGapStatus = enumValueOf(value)
    @TypeConverter fun recoverySource(value: RecoverySource?): String? = value?.name
    @TypeConverter fun recoverySource(value: String?): RecoverySource? =
        value?.let { enumValueOf<RecoverySource>(it) }
    @TypeConverter fun sessionType(value: WalkingSessionType): String = value.name
    @TypeConverter fun sessionType(value: String): WalkingSessionType = enumValueOf(value)
    @TypeConverter fun sessionStatus(value: WalkingSessionStatus): String = value.name
    @TypeConverter fun sessionStatus(value: String): WalkingSessionStatus = enumValueOf(value)
}

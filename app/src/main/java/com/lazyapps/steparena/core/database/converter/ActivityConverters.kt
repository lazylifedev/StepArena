package com.lazyapps.steparena.core.database.converter

import androidx.room.TypeConverter
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.recovery.RecoverySource
import com.lazyapps.steparena.recovery.TrackingGapReason
import com.lazyapps.steparena.recovery.TrackingGapStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionType

class ActivityConverters {
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

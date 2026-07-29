package com.lazyapps.steparena.core.database.converter

import androidx.room.TypeConverter
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionType

class ActivityConverters {
    @TypeConverter fun quality(value: DataQuality): String = value.name
    @TypeConverter fun quality(value: String): DataQuality = enumValueOf(value)
    @TypeConverter fun sessionType(value: WalkingSessionType): String = value.name
    @TypeConverter fun sessionType(value: String): WalkingSessionType = enumValueOf(value)
    @TypeConverter fun sessionStatus(value: WalkingSessionStatus): String = value.name
    @TypeConverter fun sessionStatus(value: String): WalkingSessionStatus = enumValueOf(value)
}

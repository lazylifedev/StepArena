package com.lazyapps.steparena.core.database.model

enum class DataQuality { MEASURED, ESTIMATED, RECOVERED, MIXED, UNKNOWN }
enum class WalkingSessionType { AUTO_DETECTED, MANUAL_WALK, RANKED_MATCH, RECOVERED }
enum class WalkingSessionStatus { ACTIVE, PAUSED, COMPLETED, DISCARDED, RECOVERED }

fun mergeQuality(values: Iterable<DataQuality>): DataQuality {
    val qualities = values.toSet()
    if (qualities.isEmpty() || qualities == setOf(DataQuality.UNKNOWN)) return DataQuality.UNKNOWN
    if (qualities.size == 1) return qualities.first()
    if (DataQuality.RECOVERED in qualities && qualities.all {
            it == DataQuality.RECOVERED || it == DataQuality.UNKNOWN
        }
    ) return DataQuality.RECOVERED
    return DataQuality.MIXED
}

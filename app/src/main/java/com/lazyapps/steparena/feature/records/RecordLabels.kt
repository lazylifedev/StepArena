package com.lazyapps.steparena.feature.records

import androidx.annotation.StringRes
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionType

@StringRes fun DataQuality.labelRes(): Int = when (this) {
    DataQuality.MEASURED -> R.string.quality_measured
    DataQuality.ESTIMATED -> R.string.quality_estimated
    DataQuality.RECOVERED -> R.string.quality_recovered
    DataQuality.MIXED -> R.string.quality_mixed
    DataQuality.UNKNOWN -> R.string.quality_unknown
}

@StringRes fun WalkingSessionType.labelRes(): Int = when (this) {
    WalkingSessionType.AUTO_DETECTED -> R.string.session_type_auto
    WalkingSessionType.MANUAL_WALK -> R.string.session_type_manual
    WalkingSessionType.RANKED_MATCH -> R.string.session_type_ranked
    WalkingSessionType.RECOVERED -> R.string.session_type_recovered
}

@StringRes fun WalkingSessionStatus.labelRes(): Int = when (this) {
    WalkingSessionStatus.ACTIVE -> R.string.session_status_active
    WalkingSessionStatus.PAUSED -> R.string.session_status_paused
    WalkingSessionStatus.COMPLETED -> R.string.session_status_completed
    WalkingSessionStatus.DISCARDED -> R.string.session_status_discarded
    WalkingSessionStatus.RECOVERED -> R.string.session_status_recovered
}

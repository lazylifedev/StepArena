package com.lazyapps.steparena.game

/**
 * Debug-only scenario vocabulary. It lives in the debug source set, so none of these controls
 * can be referenced by or packaged into the release variant.
 */
enum class DebugGameScenario {
    SET_STEPS, SET_COMPETITIVE_BREAKDOWN, SET_NPC_TARGET, WIN, LOSS, DRAW, NO_CONTEST,
    ADD_RATING, REMOVE_RATING, PROMOTE, DEMOTE, THREE_WIN_STREAK, FIVE_WIN_STREAK,
    END_BEGINNER_PERIOD, LEAGUE_FIRST, LEAGUE_TENTH, END_SEASON, UNLOCK_ALL_ACHIEVEMENTS,
    CHANGE_DATE, CHANGE_TIME_ZONE, DOUBLE_FINALIZE, ABNORMAL_STEPS, HEALTH_CONNECT_ONLY,
    UNKNOWN_STEPS, RERUN_WORK_MANAGER,
}

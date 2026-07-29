package com.lazyapps.steparena.game

import com.lazyapps.steparena.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GameNotificationPresentationTest {
    @Test fun `unknown achievement id uses a general title instead of the internal id`() {
        val internalId = "future_internal_achievement_key"

        val title = GameNotificationPresentation.achievementTitle(internalId) {
            if (it == R.string.achievement_unknown_title) "新しい達成記録" else "unexpected"
        }

        assertEquals("新しい達成記録", title)
        assertFalse(title.contains(internalId))
    }

    @Test fun `known achievement id uses its official title`() {
        val title = GameNotificationPresentation.achievementTitle("first_1000_steps") {
            if (it == R.string.achievement_first_1000_title) "はじめの1,000歩" else "unexpected"
        }

        assertEquals("はじめの1,000歩", title)
    }

    @Test fun `notification presentation does not use enum names`() {
        MatchOutcome.entries.forEach { outcome ->
            val presentation = GameNotificationPresentation.matchOutcomeName(outcome) { "公開用結果文言" }
            assertFalse(presentation.contains(outcome.name))
        }
    }
}

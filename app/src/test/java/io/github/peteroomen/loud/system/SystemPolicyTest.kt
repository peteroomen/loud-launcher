package io.github.peteroomen.loud.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SystemPolicyTest {
    @Test fun summariesDoNotDoubleCountChildrenAndServicesDoNotCreateBadges() {
        val values = listOf(
            NotificationCandidate("chat", "group", true, true, true), NotificationCandidate("chat", "group", false, true, true),
            NotificationCandidate("chat", "group", false, true, true), NotificationCandidate("music", null, false, false, true),
            NotificationCandidate("hidden", null, false, true, false), NotificationCandidate("mail", null, false, true, true),
        )
        assertEquals(mapOf("chat" to 2, "mail" to 1), notificationCounts(values))
        assertEquals(3, NotificationState(true, notificationCounts(values)).total)
    }
    @Test fun calendarKeepsAllDayBoundaryDatesAcrossDeviceOffsets() {
        val month = java.time.YearMonth.of(2026, 10)
        for (zone in listOf(java.time.ZoneOffset.ofHours(13), java.time.ZoneOffset.ofHours(-12))) {
            val window = calendarWindow(month, zone)
            val first = CalendarEvent(1, "First", Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"), true, "")
            val last = first.copy(start = Instant.parse("2026-10-31T00:00:00Z"), end = Instant.parse("2026-11-01T00:00:00Z"))
            assertTrue(window.includes(first)); assertTrue(window.includes(last))
            assertTrue(window.queryStart <= first.start); assertTrue(window.queryEnd >= last.end)
            assertFalse(window.includes(first.copy(start = Instant.parse("2026-11-01T00:00:00Z"), end = Instant.parse("2026-11-02T00:00:00Z"))))
            assertTrue(window.includes(first.copy(allDay = false, start = window.localStart.minusSeconds(1), end = window.localStart.plusSeconds(1))))
        }
    }
    @Test fun automaticWeatherRefreshHonorsThirtyMinuteLimitAndManualRefreshCanOverride() {
        val then = Instant.parse("2026-10-12T00:00:00Z")
        assertFalse(weatherRefreshDue(then, then.plusSeconds(1799), false)); assertTrue(weatherRefreshDue(then, then.plusSeconds(1800), false))
        assertTrue(weatherRefreshDue(then, then.plusSeconds(1), true)); assertTrue(weatherRefreshDue(null, then, false)); assertTrue(weatherRefreshDue(then, then.minusSeconds(1), false))
    }
}

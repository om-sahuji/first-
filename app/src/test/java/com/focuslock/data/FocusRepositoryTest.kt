package com.focuslock.data

import com.focuslock.core.Clock
import com.focuslock.data.model.AppSnapshot
import com.focuslock.data.model.EscapeResult
import com.focuslock.data.model.Schedule
import com.focuslock.data.model.SessionSource
import com.focuslock.data.model.StartSessionResult
import com.focuslock.data.store.SnapshotStore
import com.focuslock.domain.EscapePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Session persistence and Cat's LIFE semantics, exercised against an in-memory store.
 *
 * These cover the behaviour that must survive process death: the end time is absolute and
 * stored, an escape cannot be spent twice, and an expired session never keeps blocking.
 */
class FocusRepositoryTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private class FakeClock(var now: Long, private val zone: ZoneId) : Clock {
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = zone
    }

    private class FakeStore(initial: AppSnapshot = AppSnapshot()) : SnapshotStore {
        private val state = MutableStateFlow(initial)
        override val data: Flow<AppSnapshot> = state
        override suspend fun update(transform: (AppSnapshot) -> AppSnapshot): AppSnapshot {
            val next = transform(state.value)
            state.value = next
            return next
        }

        override fun readBlocking(): AppSnapshot = state.value
    }

    private val startMillis = 1_700_000_000_000L
    private val clock = FakeClock(startMillis, zone)
    private val store = FakeStore()

    private fun repository() = FocusRepository(store, clock, scope)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `starting a session persists an absolute end time`() = runBlocking {
        val repo = repository()
        val result = repo.startSession(
            durationMinutes = 25,
            allowedPackages = listOf("com.example.reader"),
            allowedDomains = listOf("RollCall.iitd.ac.in"),
            websiteFilterEnabled = true,
        )
        assertTrue(result is StartSessionResult.Started)
        val session = repo.activeSession(startMillis)
        assertNotNull(session)
        assertEquals(startMillis + 25 * 60_000L, session!!.endAtMillis)
        assertEquals(listOf("com.example.reader"), session.allowedPackages)
        // Domains are stored normalized.
        assertEquals(listOf("rollcall.iitd.ac.in"), session.allowedDomains)
        assertTrue(session.websiteFilterEnabled)
    }

    @Test
    fun `session state survives process recreation`() = runBlocking {
        repository().startSession(45, listOf("com.example.reader"), emptyList(), false)
        // A new repository instance reads the same store: process death cannot reset the timer.
        val revived = repository()
        val session = revived.activeSession(startMillis + 5 * 60_000L)
        assertNotNull(session)
        assertEquals(startMillis + 45 * 60_000L, session!!.endAtMillis)
        assertEquals(40 * 60_000L, session.remainingMillis(startMillis + 5 * 60_000L))
    }

    @Test
    fun `a running session cannot be replaced or extended`() = runBlocking {
        val repo = repository()
        repo.startSession(25, emptyList(), emptyList(), false)
        val second = repo.startSession(60, emptyList(), emptyList(), false)
        assertEquals(StartSessionResult.AlreadyActive, second)
        assertEquals(startMillis + 25 * 60_000L, repo.activeSession(startMillis)!!.endAtMillis)
    }

    @Test
    fun `expired sessions stop blocking and can be replaced`() = runBlocking {
        val repo = repository()
        repo.startSession(5, emptyList(), emptyList(), false)
        clock.now = startMillis + 6 * 60_000L
        assertNull(repo.activeSession(clock.now))

        repo.reconcile(clock.now)
        assertNull(store.readBlocking().session)

        val restarted = repo.startSession(15, emptyList(), emptyList(), false)
        assertTrue(restarted is StartSessionResult.Started)
        assertEquals(clock.now + 15 * 60_000L, repo.currentSession!!.endAtMillis)
    }

    @Test
    fun `reconcile keeps a running session`() = runBlocking {
        val repo = repository()
        repo.startSession(30, emptyList(), emptyList(), false)
        clock.now = startMillis + 10 * 60_000L
        repo.reconcile(clock.now)
        assertNotNull(store.readBlocking().session)
    }

    @Test
    fun `cats life ends the session and is limited to one per local day`() = runBlocking {
        val repo = repository()
        repo.startSession(90, emptyList(), emptyList(), false)

        val used = repo.useEscape(clock.now)
        assertTrue(used is EscapeResult.Used)
        assertNull(store.readBlocking().session)
        assertEquals(
            EscapePolicy.localEpochDay(clock.now, zone),
            store.readBlocking().settings.escapeUsedOnEpochDay,
        )

        // A new session on the same day cannot be escaped again.
        repo.startSession(60, emptyList(), emptyList(), false)
        clock.now += 60_000L
        assertEquals(EscapeResult.AlreadyUsedToday, repo.useEscape(clock.now))
        assertNotNull(store.readBlocking().session)

        // The next local day refreshes the escape for a new session.
        val nextDay = EscapePolicy.localEpochDay(clock.now, zone) + 1
        clock.now = nextDay * 86_400_000L + 4 * 3_600_000L
        assertTrue(EscapePolicy.isAvailable(store.readBlocking().settings.escapeUsedOnEpochDay, clock.now, zone))
        repo.reconcile(clock.now)
        repo.startSession(30, emptyList(), emptyList(), false)
        assertTrue(repo.useEscape(clock.now) is EscapeResult.Used)
    }

    @Test
    fun `escape without a session does not consume the daily use`() = runBlocking {
        val repo = repository()
        assertEquals(EscapeResult.NoActiveSession, repo.useEscape(clock.now))
        assertNull(store.readBlocking().settings.escapeUsedOnEpochDay)
        assertTrue(repo.escapeAvailableToday())
    }

    @Test
    fun `scheduled session keeps the schedule link`() = runBlocking {
        val repo = repository()
        repo.startSession(
            durationMinutes = 30,
            allowedPackages = emptyList(),
            allowedDomains = emptyList(),
            websiteFilterEnabled = false,
            source = SessionSource.SCHEDULE,
            scheduleId = "schedule-1",
        )
        val session = repo.currentSession!!
        assertEquals(SessionSource.SCHEDULE, session.source)
        assertEquals("schedule-1", session.scheduleId)
    }

    @Test
    fun `ending a session clears only the session`() = runBlocking {
        val repo = repository()
        repo.rememberHomeDefaults(30, listOf("com.example.reader"), listOf("iitd.ac.in"), true)
        repo.startSession(30, listOf("com.example.reader"), listOf("iitd.ac.in"), true)
        repo.endSession()
        val snapshot = store.readBlocking()
        assertNull(snapshot.session)
        assertEquals(listOf("com.example.reader"), snapshot.settings.defaultAllowedPackages)
        assertEquals(listOf("iitd.ac.in"), snapshot.settings.defaultAllowedDomains)
        assertTrue(snapshot.settings.defaultWebsiteFilterEnabled)
    }

    @Test
    fun `schedules are added updated and removed`() = runBlocking {
        val repo = repository()
        val schedule = Schedule(id = "a", days = setOf(1), startMinuteOfDay = 540, durationMinutes = 25)
        repo.upsertSchedule(schedule)
        repo.upsertSchedule(schedule.copy(id = "b", startMinuteOfDay = 600))
        assertEquals(2, store.readBlocking().schedules.size)

        repo.upsertSchedule(schedule.copy(durationMinutes = 45))
        val updated = store.readBlocking().schedules.first { it.id == "a" }
        assertEquals(45, updated.durationMinutes)

        repo.setScheduleEnabled("a", false)
        assertFalse(store.readBlocking().schedules.first { it.id == "a" }.enabled)

        repo.markScheduleStarted("a", 20_000L)
        assertEquals(20_000L, store.readBlocking().schedules.first { it.id == "a" }.lastStartedEpochDay)

        repo.deleteSchedule("a")
        assertEquals(1, store.readBlocking().schedules.size)
    }

    @Test
    fun `website filter is only enabled when domains exist`() = runBlocking {
        val repo = repository()
        repo.startSession(20, emptyList(), emptyList(), websiteFilterEnabled = true)
        assertFalse(repo.currentSession!!.websiteFilterEnabled)
    }

    private fun FocusRepository.escapeAvailableToday(): Boolean = EscapePolicy.isAvailable(
        lastUsedEpochDay = store.readBlocking().settings.escapeUsedOnEpochDay,
        nowMillis = clock.now,
        zone = zone,
    )
}

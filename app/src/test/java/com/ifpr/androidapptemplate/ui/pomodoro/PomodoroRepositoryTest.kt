package com.ifpr.androidapptemplate.ui.pomodoro

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class PomodoroRepositoryTest {

    @Test
    fun `fetchPeriod returns defaults when user not logged in`() = runBlocking {
        val auth = Mockito.mock(FirebaseAuth::class.java)
        Mockito.`when`(auth.currentUser).thenReturn(null)
        val db = Mockito.mock(FirebaseDatabase::class.java)
        val repo = PomodoroRepository(auth, db)

        val p = repo.fetchPeriod("daily")
        assertEquals(0L, p.cycles_target)
        assertEquals(0L, p.created_at)
        assertEquals(emptyMap<String, String>(), p.tasks)
    }

    @Test
    fun `goalsFlow emits default when user not logged in`() = runBlocking {
        val auth = Mockito.mock(FirebaseAuth::class.java)
        Mockito.`when`(auth.currentUser).thenReturn(null)
        val db = Mockito.mock(FirebaseDatabase::class.java)
        val repo = PomodoroRepository(auth, db)

        val g = repo.goalsFlow().first()
        assertEquals(0L, g.daily_cycles_target)
    }
}

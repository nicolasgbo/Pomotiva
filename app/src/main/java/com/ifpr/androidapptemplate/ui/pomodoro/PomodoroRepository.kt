package com.ifpr.androidapptemplate.ui.pomodoro

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ktx.getValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PomodoroRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private fun uid(): String? = auth.currentUser?.uid

    private fun dateKey(timeMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    private fun userRoot() = db.reference.child("users").child(uid() ?: "")

    fun presetsFlow(): Flow<Presets> = callbackFlow {
        val u = uid() ?: run { trySend(Presets()); close(); return@callbackFlow }
        val ref = db.reference.child("users").child(u).child("pomodoro").child("presets")
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val p = snapshot.getValue<Presets>() ?: Presets()
                trySend(p)
            }
            override fun onCancelled(error: DatabaseError) {
                // Keep last value; optionally could close
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun savePresets(p: Presets) {
        val u = uid() ?: return
        db.reference.child("users").child(u).child("pomodoro").child("presets").setValue(p)
    }

    fun goalsFlow(): Flow<Goals> = callbackFlow {
        val u = uid() ?: run { trySend(Goals()); close(); return@callbackFlow }
        val ref = db.reference.child("users").child(u).child("pomodoro").child("goals")
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val g = snapshot.getValue<Goals>() ?: Goals()
                trySend(g)
            }
            override fun onCancelled(error: DatabaseError) { }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun saveGoals(g: Goals) {
        val u = uid() ?: return
        db.reference.child("users").child(u).child("pomodoro").child("goals").setValue(g)
    }

    suspend fun logSession(type: String, durationMs: Long, startAt: Long, endAt: Long) {
        val u = uid() ?: return
        val ref = db.reference.child("users").child(u).child("pomodoro").child("sessions").push()
        val log = SessionLog(startAt = startAt, endAt = endAt, type = type, durationMs = durationMs)
        ref.setValue(log)
    }

    suspend fun incrementDailyStats(onWork: Boolean, durationMs: Long, timeMillis: Long = System.currentTimeMillis()) {
        val u = uid() ?: return
        val dayKey = dateKey(timeMillis)
        val dayRef = db.reference.child("users").child(u).child("pomodoro").child("stats").child("daily").child(dayKey)
        dayRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                var stats = currentData.getValue(DailyStats::class.java)
                if (stats == null) stats = DailyStats()
                val newCycles = if (onWork) stats.cycles + 1 else stats.cycles
                val newFocus = if (onWork) stats.focus_ms + durationMs else stats.focus_ms
                val newBreak = if (!onWork) stats.break_ms + durationMs else stats.break_ms
                currentData.value = mapOf(
                    "cycles" to newCycles,
                    "focus_ms" to newFocus,
                    "break_ms" to newBreak,
                    // touch a server timestamp for last update (optional)
                    "_updatedAt" to ServerValue.TIMESTAMP
                )
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                // no-op
            }
        })
    }

    fun dailyStatsTodayFlow(): Flow<DailyStats> = callbackFlow {
        val u = uid() ?: run { trySend(DailyStats()); close(); return@callbackFlow }
        val key = dateKey()
        val ref = db.reference.child("users").child(u).child("pomodoro").child("stats").child("daily").child(key)
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val s = snapshot.getValue<DailyStats>() ?: DailyStats()
                trySend(s)
            }
            override fun onCancelled(error: DatabaseError) { }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ====== Metas genéricas por período ======
    data class PeriodData(
        val cycles_target: Long = 0L,
        val created_at: Long = 0L,
        val tasks: Map<String, String> = emptyMap()
    )

    private fun goalsRefFor(u: String) = db.reference.child("users").child(u).child("pomodoro").child("goals")

    private fun keyFor(prefix: String, suffix: String) = "${prefix}_${suffix}"

    suspend fun fetchPeriod(prefix: String): PeriodData {
        val u = uid() ?: return PeriodData()
        val ref = goalsRefFor(u)
        val snap = ref.get().await()
        val cycles = snap.child(keyFor(prefix, "cycles_target")).getValue(Long::class.java) ?: 0L
        val created = snap.child(keyFor(prefix, "created_at")).getValue(Long::class.java) ?: 0L
        val tasksSnap = snap.child(keyFor(prefix, "tasks"))
        val tasks = mutableMapOf<String, String>()
        for (child in tasksSnap.children) {
            val k = child.key ?: continue
            val v = child.getValue(String::class.java) ?: continue
            tasks[k] = v
        }
        return PeriodData(cycles_target = cycles, created_at = created, tasks = tasks)
    }

    suspend fun addTask(prefix: String, text: String): String? {
        val u = uid() ?: return null
        val ref = goalsRefFor(u).child(keyFor(prefix, "tasks")).push()
        ref.setValue(text).await()
        return ref.key
    }

    suspend fun updateTask(prefix: String, key: String, newText: String) {
        val u = uid() ?: return
        goalsRefFor(u).child(keyFor(prefix, "tasks")).child(key).setValue(newText).await()
    }

    suspend fun removeTask(prefix: String, key: String) {
        val u = uid() ?: return
        goalsRefFor(u).child(keyFor(prefix, "tasks")).child(key).removeValue().await()
    }

    suspend fun setCycles(prefix: String, cycles: Long) {
        val u = uid() ?: return
        goalsRefFor(u).child(keyFor(prefix, "cycles_target")).setValue(cycles).await()
    }

    suspend fun ensureCreatedAt(prefix: String) {
        val u = uid() ?: return
        val node = goalsRefFor(u).child(keyFor(prefix, "created_at"))
        val cur = node.get().await()
        val exists = cur.exists() && (cur.getValue(Long::class.java) ?: 0L) > 0L
        if (!exists) {
            node.setValue(ServerValue.TIMESTAMP).await()
        }
    }

    suspend fun deletePeriod(prefix: String) {
        val u = uid() ?: return
        val ref = goalsRefFor(u)
        val updates = hashMapOf<String, Any?>(
            keyFor(prefix, "cycles_target") to null,
            keyFor(prefix, "created_at") to null,
            keyFor(prefix, "tasks") to null
        )
        ref.updateChildren(updates).await()
    }
}

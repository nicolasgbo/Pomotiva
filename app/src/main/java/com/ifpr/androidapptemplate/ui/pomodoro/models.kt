package com.ifpr.androidapptemplate.ui.pomodoro

data class Presets(
    val workMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15
)

data class DailyStats(
    val cycles: Long = 0,
    val focus_ms: Long = 0,
    val break_ms: Long = 0
)

data class Goals(
    val daily_cycles_target: Long = 0,
    val daily_focus_ms_target: Long = 25L * 60 * 1000
)

data class SessionLog(
    val startAt: Long = 0,
    val endAt: Long = 0,
    val type: String = "WORK",
    val durationMs: Long = 0
)

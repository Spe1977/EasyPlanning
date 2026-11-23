package com.example.data.model

enum class AppLanguage(val code: String) {
    ITALIAN("it"),
    ENGLISH("en");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ITALIAN
    }
}

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class AuthProvider(val key: String) {
    NONE("none"),
    OFFLINE_LOCAL("offline_local"),
    GOOGLE("google"),
    HYBRID("hybrid");

    companion object {
        fun fromKey(key: String?): AuthProvider =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

enum class ScheduleViewMode {
    DAILY_LIST,
    MONTHLY_CALENDAR
}

enum class StatsPeriod {
    WEEK,
    MONTH,
    YEAR
}

enum class RecurrenceType(val key: String) {
    NONE("NONE"),
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    ROTATING_CYCLE("ROTATING_CYCLE");

    companion object {
        fun fromKey(key: String?): RecurrenceType =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

/**
 * Overtime categories with both Italian and English names & abbreviations.
 */
enum class OvertimeType(
    val key: String,
    val abbrIt: String,
    val abbrEn: String,
    val labelIt: String,
    val labelEn: String,
    val colorHex: Long
) {
    WEEKDAY(
        key = "WEEKDAY",
        abbrIt = "FER",
        abbrEn = "WD",
        labelIt = "Feriale",
        labelEn = "Weekday",
        colorHex = 0xFF2962FF
    ),
    HOLIDAY(
        key = "HOLIDAY",
        abbrIt = "FEST",
        abbrEn = "HOL",
        labelIt = "Festivo",
        labelEn = "Holiday",
        colorHex = 0xFFFF5733
    ),
    NIGHT(
        key = "NIGHT",
        abbrIt = "NOTT",
        abbrEn = "NGT",
        labelIt = "Notturno",
        labelEn = "Night",
        colorHex = 0xFF7C3AED
    ),
    NIGHT_HOLIDAY(
        key = "NIGHT_HOLIDAY",
        abbrIt = "N-FEST",
        abbrEn = "H-NGT",
        labelIt = "Notturno Festivo",
        labelEn = "Holiday Night",
        colorHex = 0xFFE11D74
    ),
    SCHEDULED(
        key = "SCHEDULED",
        abbrIt = "PROG",
        abbrEn = "SCH",
        labelIt = "Programmato",
        labelEn = "Scheduled",
        colorHex = 0xFF059669
    );

    fun abbr(lang: AppLanguage): String = if (lang == AppLanguage.ITALIAN) abbrIt else abbrEn
    fun label(lang: AppLanguage): String = if (lang == AppLanguage.ITALIAN) labelIt else labelEn

    companion object {
        fun fromKey(key: String?): OvertimeType =
            entries.firstOrNull { it.key == key } ?: WEEKDAY
    }
}

enum class AllowancePreset(
    val key: String,
    val labelIt: String,
    val labelEn: String
) {
    MEAL_VOUCHER("MEAL_VOUCHER", "Buono Pasto", "Meal Voucher"),
    ON_CALL("ON_CALL", "Reperibilità", "On-Call"),
    TRAVEL("TRAVEL", "Trasferta", "Travel Allowance"),
    NIGHT_BONUS("NIGHT_BONUS", "Indennità Notturna", "Night Allowance"),
    HOLIDAY_BONUS("HOLIDAY_BONUS", "Indennità Festiva", "Holiday Bonus"),
    SHIFT_BONUS("SHIFT_BONUS", "Indennità Turno", "Shift Allowance"),
    CUSTOM("CUSTOM", "Personalizzata", "Custom");

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.ITALIAN) labelIt else labelEn

    companion object {
        fun fromKey(key: String?): AllowancePreset =
            entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

enum class ReminderOption(val minutesBefore: Int) {
    NONE(-1),
    AT_START(0),
    MIN_15(15),
    MIN_30(30),
    HOUR_1(60),
    HOUR_2(120),
    DAY_1(1440);

    companion object {
        fun fromMinutes(minutes: Int): ReminderOption =
            entries.firstOrNull { it.minutesBefore == minutes } ?: MIN_30
    }
}

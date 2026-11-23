package com.example.sync

import com.example.data.local.EasyPlanningDao
import com.example.data.model.BackupPayload
import com.example.security.CryptoManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles atomic JSON serialization + AES-256-GCM authenticated encryption
 * for both local SAF backups (.epbak) and Google Drive appDataFolder sync.
 */
class BackupRepository(private val dao: EasyPlanningDao) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(BackupPayload::class.java)

    suspend fun createEncryptedBackupBytes(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            schemaVersion = 1,
            exportedAtMillis = System.currentTimeMillis(),
            shiftSeries = dao.getAllSeriesOnce(),
            shiftOverrides = dao.getAllOverridesOnce(),
            overtimeEntries = dao.getAllOvertimeOnce(),
            allowanceEntries = dao.getAllAllowancesOnce()
        )
        val json = payloadAdapter.toJson(payload)
        CryptoManager.encryptBackup(json.toByteArray(Charsets.UTF_8), password)
    }

    suspend fun restoreFromEncryptedBytes(encryptedBytes: ByteArray, password: CharArray) =
        withContext(Dispatchers.IO) {
            val plaintext = CryptoManager.decryptBackup(encryptedBytes, password)
            val json = String(plaintext, Charsets.UTF_8)
            val payload = payloadAdapter.fromJson(json)
                ?: throw IllegalArgumentException("Malformed backup payload")
            require(payload.schemaVersion == 1) { "Unsupported backup schema version" }

            val validatedSeries = payload.shiftSeries.map { s ->
                require(s.startMinuteOfDay in 0..1439 && s.endMinuteOfDay in 0..1439) {
                    "Invalid shift time bounds in backup"
                }
                s.copy(
                    title = s.title.trim().ifBlank { "Turno" },
                    recurrenceInterval = s.recurrenceInterval.coerceAtLeast(1),
                    cycleWorkDays = s.cycleWorkDays.coerceAtLeast(1),
                    cycleOffDays = s.cycleOffDays.coerceAtLeast(0),
                    maxOccurrences = s.maxOccurrences?.takeIf { it > 0 }
                )
            }
            val validatedOverrides = payload.shiftOverrides.map { ov ->
                ov.startMinuteOfDay?.let { require(it in 0..1439) { "Invalid override start time" } }
                ov.endMinuteOfDay?.let { require(it in 0..1439) { "Invalid override end time" } }
                ov
            }
            val validatedOvertime = payload.overtimeEntries.filter { it.halfHours in 1..48 }
            val validatedAllowances = payload.allowanceEntries
                .filter { it.label.isNotBlank() && it.quantity in 1..999 }
                .map { it.copy(label = it.label.trim()) }

            dao.replaceAllData(
                series = validatedSeries,
                overrides = validatedOverrides,
                overtime = validatedOvertime,
                allowances = validatedAllowances
            )
        }
}

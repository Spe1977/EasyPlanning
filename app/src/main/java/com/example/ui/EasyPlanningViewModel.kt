package com.example.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AllowanceEntryEntity
import com.example.data.local.OvertimeEntryEntity
import com.example.data.local.ShiftOverrideEntity
import com.example.data.local.ShiftSeriesEntity
import com.example.data.model.AllowanceDraft
import com.example.data.model.AppLanguage
import com.example.data.model.DaySummary
import com.example.data.model.OvertimeDraft
import com.example.data.model.OvertimeType
import com.example.data.model.ScheduleViewMode
import com.example.data.model.ShiftExtrasCodec
import com.example.data.model.ShiftInstance
import com.example.data.model.StatsPeriod
import com.example.data.model.StatsSummary
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.data.repository.EasyPlanningRepository
import com.example.domain.RecurrenceEngine
import com.example.domain.StatsCalculator
import com.example.i18n.stringsFor
import com.example.security.GoogleAuthManager
import com.example.security.GoogleSignInOutcome
import com.example.sync.DriveAuthOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth

sealed class DriveActionRequest {
    data object Connect : DriveActionRequest()
    data class Upload(val password: String) : DriveActionRequest()
    data class Restore(val password: String) : DriveActionRequest()
}

sealed class UiEvent {
    data class ShowMessage(val message: String) : UiEvent()
    data class LaunchDriveResolution(val pendingIntent: PendingIntent) : UiEvent()
    data class SharePdfFile(val file: File) : UiEvent()
}

class EasyPlanningViewModel(application: Application) : AndroidViewModel(application) {

    val repository = EasyPlanningRepository(application)

    val preferences: StateFlow<UserPreferences> = repository.userPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    /** Continuous Lista timeline bounds: default -24 months to +36 months, auto-expanding via Cerca Data */
    private val _timelineBounds = MutableStateFlow(
        LocalDate.now().minusMonths(24) to LocalDate.now().plusMonths(36)
    )

    private val _scrollSignal = MutableStateFlow(LocalDate.now() to 0L)
    val scrollSignal: StateFlow<Pair<LocalDate, Long>> = _scrollSignal.asStateFlow()

    private val _scheduleViewMode = MutableStateFlow(ScheduleViewMode.DAILY_LIST)
    val scheduleViewMode: StateFlow<ScheduleViewMode> = _scheduleViewMode.asStateFlow()

    private val _statsPeriod = MutableStateFlow(StatsPeriod.MONTH)
    val statsPeriod: StateFlow<StatsPeriod> = _statsPeriod.asStateFlow()

    private val _statsAnchorDate = MutableStateFlow(LocalDate.now())
    val statsAnchorDate: StateFlow<LocalDate> = _statsAnchorDate.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var pendingDriveAction: DriveActionRequest? = null
    // Held in volatile memory for the current session only (never persisted in plaintext) to power auto-sync
    private var sessionBackupPassword: CharArray? = null

    private data class RawDataBundle(
        val series: List<ShiftSeriesEntity>,
        val overrides: List<ShiftOverrideEntity>,
        val overtime: List<OvertimeEntryEntity>,
        val allowances: List<AllowanceEntryEntity>
    )

    private val rawDataFlow = combine(
        repository.allSeries,
        repository.allOverrides,
        repository.allOvertime,
        repository.allAllowances
    ) { series, overrides, overtime, allowances ->
        RawDataBundle(series, overrides, overtime, allowances)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RawDataBundle(emptyList(), emptyList(), emptyList(), emptyList())
    )

    /**
     * Expanded shift map for the visible month (plus 7-day buffer on each side for seamless week-strip).
     */
    val monthDaySummaries: StateFlow<Map<LocalDate, DaySummary>> = combine(
        rawDataFlow,
        _visibleMonth,
        _selectedDate
    ) { data, month, selected ->
        val start = minOf(month.atDay(1).minusDays(7), selected.minusDays(7))
        val end = maxOf(month.atEndOfMonth().plusDays(7), selected.plusDays(7))
        val expandedShifts = RecurrenceEngine.expandRange(data.series, data.overrides, start, end)
        val shiftsByDate = expandedShifts.groupBy { it.date }
        val overtimeByDate = RecurrenceEngine.effectiveOvertime(expandedShifts, data.overtime, start, end)
            .groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }
        val allowancesByDate = RecurrenceEngine.effectiveAllowances(expandedShifts, data.allowances, start, end)
            .groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }

        val allDates = (shiftsByDate.keys + overtimeByDate.keys + allowancesByDate.keys)
        allDates.associateWith { date ->
            DaySummary(
                date = date,
                shifts = shiftsByDate[date].orEmpty(),
                overtimeEntries = overtimeByDate[date].orEmpty(),
                allowanceEntries = allowancesByDate[date].orEmpty()
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /**
     * Continuous chronological list of active days (days with shifts, overtime, or allowances)
     * across the -24/+36 month window, computed off the main thread for silky scrolling.
     */
    val activeTimelineDays: StateFlow<List<DaySummary>> = combine(
        rawDataFlow,
        _timelineBounds
    ) { data, (start, end) ->
        val expandedShifts = RecurrenceEngine.expandRange(data.series, data.overrides, start, end)
        val shiftsByDate = expandedShifts.groupBy { it.date }
        val overtimeByDate = RecurrenceEngine.effectiveOvertime(expandedShifts, data.overtime, start, end)
            .groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }
        val allowancesByDate = RecurrenceEngine.effectiveAllowances(expandedShifts, data.allowances, start, end)
            .groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }

        val activeDates = (shiftsByDate.keys + overtimeByDate.keys + allowancesByDate.keys).sorted()
        activeDates.map { date ->
            DaySummary(
                date = date,
                shifts = shiftsByDate[date].orEmpty(),
                overtimeEntries = overtimeByDate[date].orEmpty(),
                allowanceEntries = allowancesByDate[date].orEmpty()
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val selectedDaySummary: StateFlow<DaySummary> = combine(
        monthDaySummaries,
        activeTimelineDays,
        _selectedDate
    ) { summaries, timeline, date ->
        summaries[date]
            ?: timeline.firstOrNull { it.date == date }
            ?: DaySummary(date, emptyList(), emptyList(), emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DaySummary(LocalDate.now(), emptyList(), emptyList(), emptyList())
    )

    val statsSummary: StateFlow<StatsSummary> = combine(
        rawDataFlow,
        _statsAnchorDate,
        _statsPeriod
    ) { data, anchor, period ->
        StatsCalculator.calculate(
            seriesList = data.series,
            overrides = data.overrides,
            overtimeEntries = data.overtime,
            allowanceEntries = data.allowances,
            anchorDate = anchor,
            period = period
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StatsSummary(
                period = StatsPeriod.MONTH,
                startDate = YearMonth.now().atDay(1),
                endDate = YearMonth.now().atEndOfMonth(),
                workedDays = 0,
                totalRegularMinutes = 0,
                totalOvertimeHalfHours = 0,
                overtimeByTypeHalfHours = OvertimeType.entries.associateWith { 0 },
                shiftDistribution = emptyList(),
                allowanceAggregates = emptyList(),
                totalAllowancesCount = 0
            )
        )

    // Schedule controls
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
    }

    fun jumpToDate(date: LocalDate) {
        val (curStart, curEnd) = _timelineBounds.value
        val newStart = if (date.isBefore(curStart)) date.minusMonths(6) else curStart
        val newEnd = if (date.isAfter(curEnd)) date.plusMonths(6) else curEnd
        if (newStart != curStart || newEnd != curEnd) {
            _timelineBounds.value = newStart to newEnd
        }
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
        _scrollSignal.value = date to (_scrollSignal.value.second + 1L)
    }

    fun jumpToToday() {
        jumpToDate(LocalDate.now())
    }

    fun shiftMonth(delta: Long) {
        val next = _visibleMonth.value.plusMonths(delta)
        _visibleMonth.value = next
        if (YearMonth.from(_selectedDate.value) != next) {
            _selectedDate.value = next.atDay(1)
        }
    }

    fun setScheduleViewMode(mode: ScheduleViewMode) {
        _scheduleViewMode.value = mode
    }

    // Shift CRUD with Inline Overtime & Allowances
    data class InitialShiftExtras(
        val overtime: List<OvertimeDraft>,
        val allowances: List<AllowanceDraft>,
        val applyToSeries: Boolean
    )

    fun resolveInitialExtrasForEdit(target: ShiftInstance?, isSingleOccurrenceEdit: Boolean): InitialShiftExtras {
        if (target == null) {
            return InitialShiftExtras(emptyList(), emptyList(), applyToSeries = true)
        }
        val raw = rawDataFlow.value
        val series = target.sourceSeries

        if (isSingleOccurrenceEdit) {
            val epochDay = target.date.toEpochDay()
            val directOt = raw.overtime
                .filter { it.shiftSeriesId == target.seriesId && it.dateEpochDay == epochDay }
                .map { OvertimeDraft(it.overtimeType, it.halfHours, it.notes) }
            val directAll = raw.allowances
                .filter { it.shiftSeriesId == target.seriesId && it.dateEpochDay == epochDay }
                .map { AllowanceDraft(it.presetKey, it.label, it.quantity, it.notes) }
            return InitialShiftExtras(
                overtime = target.inheritedOvertime + directOt,
                allowances = target.inheritedAllowances + directAll,
                applyToSeries = false
            )
        }

        // Editing entire series or a single non-recurring shift
        val seriesOt = ShiftExtrasCodec.decodeOvertime(series.defaultOvertimeJson)
        val seriesAll = ShiftExtrasCodec.decodeAllowances(series.defaultAllowancesJson)
        if (target.isRecurring && (seriesOt.isNotEmpty() || seriesAll.isNotEmpty())) {
            return InitialShiftExtras(seriesOt, seriesAll, applyToSeries = true)
        }

        val startEpoch = series.startDateEpochDay
        val directOt = raw.overtime
            .filter { it.shiftSeriesId == series.id && it.dateEpochDay == startEpoch }
            .map { OvertimeDraft(it.overtimeType, it.halfHours, it.notes) }
        val directAll = raw.allowances
            .filter { it.shiftSeriesId == series.id && it.dateEpochDay == startEpoch }
            .map { AllowanceDraft(it.presetKey, it.label, it.quantity, it.notes) }
        val hasOnlyDirectOnRecurring = target.isRecurring && (directOt.isNotEmpty() || directAll.isNotEmpty())
        return InitialShiftExtras(
            overtime = directOt,
            allowances = directAll,
            applyToSeries = target.isRecurring && !hasOnlyDirectOnRecurring
        )
    }

    fun saveShiftSeriesWithExtras(
        series: ShiftSeriesEntity,
        applyExtrasToSeries: Boolean,
        overtimeDrafts: List<OvertimeDraft>,
        allowanceDrafts: List<AllowanceDraft>
    ) {
        viewModelScope.launch {
            repository.saveShiftSeriesWithExtras(series, applyExtrasToSeries, overtimeDrafts, allowanceDrafts)
            triggerSilentAutoSyncIfEnabled()
        }
    }

    fun saveSingleOccurrenceWithExtras(
        original: ShiftInstance,
        title: String,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        colorId: Int,
        notes: String,
        reminderMinutesBefore: Int,
        overtimeDrafts: List<OvertimeDraft>,
        allowanceDrafts: List<AllowanceDraft>
    ) {
        viewModelScope.launch {
            repository.saveSingleOccurrenceWithExtras(
                original = original,
                title = title,
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
                colorId = colorId,
                notes = notes,
                reminderMinutesBefore = reminderMinutesBefore,
                overtimeDrafts = overtimeDrafts,
                allowanceDrafts = allowanceDrafts
            )
            triggerSilentAutoSyncIfEnabled()
        }
    }

    fun deleteSingleOccurrence(shift: ShiftInstance) {
        viewModelScope.launch {
            repository.deleteSingleOccurrence(shift.seriesId, shift.date)
            triggerSilentAutoSyncIfEnabled()
        }
    }

    fun deleteEntireSeries(seriesId: Long) {
        viewModelScope.launch {
            repository.deleteEntireSeries(seriesId)
            triggerSilentAutoSyncIfEnabled()
        }
    }

    // Overtime & Allowances
    fun addOvertime(
        date: LocalDate,
        shiftSeriesId: Long?,
        type: OvertimeType,
        halfHours: Int,
        notes: String
    ) {
        if (halfHours <= 0) return
        viewModelScope.launch {
            repository.addOvertime(
                OvertimeEntryEntity(
                    dateEpochDay = date.toEpochDay(),
                    shiftSeriesId = shiftSeriesId,
                    overtimeType = type.key,
                    halfHours = halfHours,
                    notes = notes.trim()
                )
            )
            triggerSilentAutoSyncIfEnabled()
        }
    }

    fun deleteOvertime(entry: OvertimeEntryEntity) {
        viewModelScope.launch {
            repository.removeOvertimeEntry(entry)
            triggerSilentAutoSyncIfEnabled()
        }
    }

    fun addAllowance(
        date: LocalDate,
        shiftSeriesId: Long?,
        presetKey: String,
        label: String,
        quantity: Int,
        notes: String
    ) {
        val cleaned = label.trim()
        if (cleaned.isEmpty() || quantity <= 0) return
        viewModelScope.launch {
            repository.addAllowance(
                AllowanceEntryEntity(
                    dateEpochDay = date.toEpochDay(),
                    shiftSeriesId = shiftSeriesId,
                    presetKey = presetKey,
                    label = cleaned,
                    quantity = quantity,
                    notes = notes.trim()
                )
            )
            triggerSilentAutoSyncIfEnabled()
        }
    }

    fun deleteAllowance(entry: AllowanceEntryEntity) {
        viewModelScope.launch {
            repository.removeAllowanceEntry(entry)
            triggerSilentAutoSyncIfEnabled()
        }
    }

    // Stats period navigation
    fun setStatsPeriod(period: StatsPeriod) {
        _statsPeriod.value = period
    }

    fun shiftStatsPeriod(delta: Long) {
        val current = _statsAnchorDate.value
        _statsAnchorDate.value = when (_statsPeriod.value) {
            StatsPeriod.WEEK -> current.plusWeeks(delta)
            StatsPeriod.MONTH -> current.plusMonths(delta)
            StatsPeriod.YEAR -> current.plusYears(delta)
        }
    }

    // PDF Export & Share
    fun exportPdfToUri(uri: Uri) {
        val strings = stringsFor(preferences.value.language)
        val month = YearMonth.from(_statsAnchorDate.value)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Cannot open PDF destination")
                    out.use { stream ->
                        repository.exportMonthlyPdf(month, strings, stream)
                    }
                }
                _events.emit(UiEvent.ShowMessage(strings.pdfExportSuccess))
            } catch (_: Exception) {
                _events.emit(UiEvent.ShowMessage(strings.pdfExportError))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun exportPdfToStream(outputStream: OutputStream) {
        val strings = stringsFor(preferences.value.language)
        val month = YearMonth.from(_statsAnchorDate.value)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                outputStream.use { out ->
                    repository.exportMonthlyPdf(month, strings, out)
                }
                _events.emit(UiEvent.ShowMessage(strings.pdfExportSuccess))
            } catch (_: Exception) {
                _events.emit(UiEvent.ShowMessage(strings.pdfExportError))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun generateShareablePdf() {
        val strings = stringsFor(preferences.value.language)
        val month = YearMonth.from(_statsAnchorDate.value)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                    val outFile = File(dir, "EasyPlanning_${month}.pdf")
                    outFile.outputStream().use { out ->
                        repository.exportMonthlyPdf(month, strings, out)
                    }
                    outFile
                }
                _events.emit(UiEvent.SharePdfFile(file))
            } catch (_: Exception) {
                _events.emit(UiEvent.ShowMessage(strings.pdfExportError))
            } finally {
                _isBusy.value = false
            }
        }
    }

    // Local encrypted backups (SAF) — all stream I/O executes strictly on Dispatchers.IO
    fun exportLocalEncryptedBackupToUri(uri: Uri, password: String) {
        val strings = stringsFor(preferences.value.language)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val encrypted = repository.createEncryptedBackup(password.toCharArray())
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Cannot open backup destination")
                    out.use { it.write(encrypted) }
                }
                if (!preferences.value.hasBackupPassword) {
                    repository.saveBackupPassword(password.toCharArray())
                }
                _events.emit(UiEvent.ShowMessage(strings.backupExportSuccess))
            } catch (_: Exception) {
                _events.emit(UiEvent.ShowMessage(strings.backupDecryptError))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importLocalEncryptedBackupFromUri(uri: Uri, password: String) {
        val strings = stringsFor(preferences.value.language)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Cannot read backup source")
                }
                repository.restoreEncryptedBackup(bytes, password.toCharArray())
                _events.emit(UiEvent.ShowMessage(strings.backupRestoreSuccess))
            } catch (_: Exception) {
                _events.emit(UiEvent.ShowMessage(strings.backupDecryptError))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun exportLocalEncryptedBackup(outputStream: OutputStream, password: String) {
        val strings = stringsFor(preferences.value.language)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val encrypted = repository.createEncryptedBackup(password.toCharArray())
                withContext(Dispatchers.IO) {
                    outputStream.use { it.write(encrypted) }
                }
                if (!preferences.value.hasBackupPassword) {
                    repository.saveBackupPassword(password.toCharArray())
                }
                _events.emit(UiEvent.ShowMessage(strings.backupExportSuccess))
            } catch (_: Exception) {
                _events.emit(UiEvent.ShowMessage(strings.backupDecryptError))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importLocalEncryptedBackup(encryptedBytes: ByteArray, password: String) {
        val strings = stringsFor(preferences.value.language)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.restoreEncryptedBackup(encryptedBytes, password.toCharArray())
                _events.emit(UiEvent.ShowMessage(strings.backupRestoreSuccess))
            } catch (_: Exception) {
                _events.emit(UiEvent.ShowMessage(strings.backupDecryptError))
            } finally {
                _isBusy.value = false
            }
        }
    }

    // Google Drive OAuth & Encrypted Sync
    fun startDriveAction(action: DriveActionRequest) {
        val strings = stringsFor(preferences.value.language)
        pendingDriveAction = action
        viewModelScope.launch {
            _isBusy.value = true
            when (val outcome = repository.driveSyncManager.requestDriveAuthorization()) {
                is DriveAuthOutcome.Authorized -> {
                    executeDriveActionWithToken(outcome.accessToken, action)
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    _isBusy.value = false
                    _events.emit(UiEvent.LaunchDriveResolution(outcome.pendingIntent))
                }
                is DriveAuthOutcome.Failed -> {
                    _isBusy.value = false
                    pendingDriveAction = null
                    _events.emit(UiEvent.ShowMessage(strings.driveAuthError))
                }
            }
        }
    }

    fun handleDriveResolutionResult(data: Intent?) {
        val strings = stringsFor(preferences.value.language)
        val action = pendingDriveAction
        pendingDriveAction = null
        val token = repository.driveSyncManager.extractTokenFromIntent(data)
        if (token.isNullOrBlank() || action == null) {
            viewModelScope.launch { _events.emit(UiEvent.ShowMessage(strings.driveAuthError)) }
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            executeDriveActionWithToken(token, action)
        }
    }

    private suspend fun executeDriveActionWithToken(token: String, action: DriveActionRequest) {
        val strings = stringsFor(preferences.value.language)
        try {
            repository.setDriveConnection(connected = true, email = "Google Drive (appDataFolder)")
            when (action) {
                DriveActionRequest.Connect -> {
                    _events.emit(UiEvent.ShowMessage(strings.googleDriveConnectedAs))
                }
                is DriveActionRequest.Upload -> {
                    sessionBackupPassword?.fill('\u0000')
                    sessionBackupPassword = action.password.toCharArray()
                    val encrypted = repository.backupRepository.createEncryptedBackupBytes(action.password.toCharArray())
                    val result = repository.driveSyncManager.uploadEncryptedVault(token, encrypted)
                    if (result.isSuccess) {
                        repository.markDriveSyncNow()
                        if (!preferences.value.hasBackupPassword) {
                            repository.saveBackupPassword(action.password.toCharArray())
                        }
                        _events.emit(UiEvent.ShowMessage(strings.driveSyncSuccess))
                    } else {
                        _events.emit(UiEvent.ShowMessage(strings.driveAuthError))
                    }
                }
                is DriveActionRequest.Restore -> {
                    val result = repository.driveSyncManager.downloadEncryptedVault(token)
                    val blob = result.getOrNull()
                    when {
                        result.isFailure -> _events.emit(UiEvent.ShowMessage(strings.driveAuthError))
                        blob == null -> _events.emit(UiEvent.ShowMessage(strings.driveNoBackupFound))
                        else -> {
                            try {
                                repository.restoreEncryptedBackup(blob, action.password.toCharArray())
                                sessionBackupPassword?.fill('\u0000')
                                sessionBackupPassword = action.password.toCharArray()
                                repository.markDriveSyncNow()
                                _events.emit(UiEvent.ShowMessage(strings.driveRestoreSuccess))
                            } catch (_: Exception) {
                                _events.emit(UiEvent.ShowMessage(strings.backupDecryptError))
                            }
                        }
                    }
                }
            }
        } finally {
            _isBusy.value = false
        }
    }

    private suspend fun triggerSilentAutoSyncIfEnabled() {
        val prefs = preferences.value
        val pwd = sessionBackupPassword?.copyOf() ?: return
        if (!prefs.driveConnected || !prefs.driveAutoSync) {
            pwd.fill('\u0000')
            return
        }
        try {
            val outcome = repository.driveSyncManager.requestDriveAuthorization()
            if (outcome is DriveAuthOutcome.Authorized) {
                val encrypted = repository.backupRepository.createEncryptedBackupBytes(pwd)
                val uploaded = repository.driveSyncManager.uploadEncryptedVault(outcome.accessToken, encrypted)
                if (uploaded.isSuccess) {
                    repository.markDriveSyncNow()
                }
            }
        } catch (_: Exception) {
            // Silent auto-sync swallows transient offline/network failures
        } finally {
            pwd.fill('\u0000')
        }
    }

    fun disconnectGoogleDrive() {
        sessionBackupPassword?.fill('\u0000')
        sessionBackupPassword = null
        viewModelScope.launch {
            repository.setDriveConnection(connected = false, email = null)
        }
    }

    // Registration, Login, Google Linking & Logout
    fun registerOffline(username: String, password: String) {
        viewModelScope.launch {
            sessionBackupPassword?.fill('\u0000')
            sessionBackupPassword = password.toCharArray()
            repository.registerOfflineAccount(username, password.toCharArray())
            _isUnlocked.value = true
        }
    }

    fun loginOffline(username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.verifyOfflineLogin(username, password.toCharArray())
            if (ok) {
                sessionBackupPassword?.fill('\u0000')
                sessionBackupPassword = password.toCharArray()
                _isUnlocked.value = true
            }
            onResult(ok)
        }
    }

    fun signInOrLinkWithGoogle(activityContext: Context, isLinkingExistingOffline: Boolean = false) {
        val strings = stringsFor(preferences.value.language)
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (val outcome = GoogleAuthManager.signInWithGoogle(activityContext)) {
                    is GoogleSignInOutcome.Success -> {
                        repository.signInOrLinkGoogleAccount(
                            email = outcome.email,
                            displayName = outcome.displayName
                        )
                        _isUnlocked.value = true
                        if (isLinkingExistingOffline) {
                            _events.emit(UiEvent.ShowMessage(strings.googleAccountLinkedSuccess))
                        }
                        // Prompt least-privilege Drive appDataFolder authorization seamlessly
                        startDriveAction(DriveActionRequest.Connect)
                    }
                    GoogleSignInOutcome.Cancelled -> Unit
                    is GoogleSignInOutcome.Error -> {
                        _events.emit(UiEvent.ShowMessage(strings.googleSignInFailed))
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun logout(context: Context) {
        val prefs = preferences.value
        val strings = stringsFor(prefs.language)
        val hasGoogleSession = prefs.authProvider != com.example.data.model.AuthProvider.OFFLINE_LOCAL
        sessionBackupPassword?.fill('\u0000')
        sessionBackupPassword = null
        viewModelScope.launch {
            repository.logout()
            _isUnlocked.value = false
            if (hasGoogleSession) {
                GoogleAuthManager.clearCredentialState(context)
            }
            _events.emit(UiEvent.ShowMessage(strings.logoutSuccessMessage))
        }
    }

    // Security, App Lock & Preferences
    fun unlockApp() {
        _isUnlocked.value = true
    }

    fun verifyPinAndUnlock(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.verifyAppLockPin(pin.toCharArray())
            if (ok) _isUnlocked.value = true
            onResult(ok)
        }
    }

    fun setupAppLockPin(pin: String) {
        viewModelScope.launch {
            repository.saveAppLockPin(pin.toCharArray())
            _isUnlocked.value = true
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAppLockEnabled(enabled)
            if (!enabled) _isUnlocked.value = true
        }
    }

    fun saveBackupPassword(password: String) {
        sessionBackupPassword?.fill('\u0000')
        sessionBackupPassword = password.toCharArray()
        viewModelScope.launch {
            repository.saveBackupPassword(password.toCharArray())
        }
    }

    fun verifyPasswordAndResetAll(password: String?, onResult: (Boolean) -> Unit) {
        val strings = stringsFor(preferences.value.language)
        viewModelScope.launch {
            val prefs = preferences.value
            val authorized = if (prefs.hasBackupPassword) {
                !password.isNullOrEmpty() && repository.verifyBackupPassword(password.toCharArray())
            } else {
                true
            }
            if (!authorized) {
                onResult(false)
                return@launch
            }
            sessionBackupPassword?.fill('\u0000')
            sessionBackupPassword = null
            repository.secureWipeAllData()
            _isUnlocked.value = true
            _events.emit(UiEvent.ShowMessage(strings.resetSuccessMessage))
            onResult(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionBackupPassword?.fill('\u0000')
        sessionBackupPassword = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setDefaultReminder(minutes: Int) {
        viewModelScope.launch { repository.setDefaultReminder(minutes) }
    }

    fun setScreenSecurity(enabled: Boolean) {
        viewModelScope.launch { repository.setScreenSecurity(enabled) }
    }

    fun setDriveAutoSync(enabled: Boolean) {
        viewModelScope.launch { repository.setDriveAutoSync(enabled) }
    }
}

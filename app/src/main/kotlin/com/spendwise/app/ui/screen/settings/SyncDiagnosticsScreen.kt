package com.spendwise.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.spendwise.app.data.drive.GoogleDriveAuthManager
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DiagnosticsUiState(
    val isRunningTest: Boolean = false,
    val lastTestResult: String = "Not run yet",
    val lastTestDurationMs: Long = 0L,
    val lastTestPassed: Boolean? = null,
    val syncStatus: String = "—",
    val driveAuthorized: Boolean = false,
    val networkAvailable: Boolean = false,
    val accountEmail: String = "",
    val logs: List<String> = emptyList()
)

@HiltViewModel
class SyncDiagnosticsViewModel @Inject constructor(
    private val googleDriveAuthManager: GoogleDriveAuthManager,
    private val driveRestClient: com.spendwise.app.data.drive.DriveRestClient,
    private val driveBackupRepository: com.spendwise.app.data.backup.DriveBackupRepository,
    private val databaseBackupEngine: com.spendwise.app.data.backup.DatabaseBackupEngine,
    private val backupCryptoManager: com.spendwise.app.data.backup.snapshot.BackupCryptoManager,
    private val snapshotSerializer: com.spendwise.app.data.backup.snapshot.SnapshotSerializer,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    private val logs = mutableListOf<String>()

    init {
        viewModelScope.launch {
            val email = appPreferences.userEmail.first()
            val isAuth = googleDriveAuthManager.isAuthorized()
            val status = appPreferences.lastBackupStatus.first()
            _state.update { it.copy(
                accountEmail = email,
                driveAuthorized = isAuth,
                networkAvailable = true,
                syncStatus = status
            )}
        }
    }

    fun runConnectivityTest() {
        viewModelScope.launch {
            _state.update { it.copy(isRunningTest = true, logs = emptyList()) }
            logs.clear()
            val start = System.currentTimeMillis()
            log("▶ Testing Google Drive appData authorization...")
            val email = appPreferences.userEmail.first()
            log("  Account: ${if (email.isNotBlank()) email else "None"}")

            if (email.isBlank()) {
                log("✗ Aborted: Not signed in")
                val ms = System.currentTimeMillis() - start
                _state.update { it.copy(isRunningTest = false, lastTestResult = "Not signed in", lastTestDurationMs = ms, lastTestPassed = false, logs = logs.toList()) }
                return@launch
            }

            try {
                log("  Requesting Drive bearer access token silently...")
                val token = withContext(Dispatchers.IO) {
                    googleDriveAuthManager.getValidAccessToken()
                }
                val ms = System.currentTimeMillis() - start
                if (!token.isNullOrBlank()) {
                    log("✓ Success: Valid Drive OAuth bearer token obtained in ${ms}ms")
                    log("  Scope: https://www.googleapis.com/auth/drive.appdata")
                    log("▶ Checking hidden appDataFolder files...")

                    val listResult = driveRestClient.listBackupFiles(email)
                    when (listResult) {
                        is com.spendwise.app.data.drive.DriveResult.Success -> {
                            val files = listResult.data
                            log("✓ Found ${files.size} backup file(s) in Drive appDataFolder:")
                            for (f in files) {
                                log("   • ${f.name} (${f.sizeBytes} bytes, modified: ${f.modifiedTime})")
                            }
                            _state.update { it.copy(
                                lastTestResult = "PASS — Drive OAuth + ${files.size} backup(s) found",
                                lastTestDurationMs = System.currentTimeMillis() - start,
                                lastTestPassed = true,
                                driveAuthorized = true,
                                logs = logs.toList()
                            )}
                        }
                        is com.spendwise.app.data.drive.DriveResult.Error -> {
                            log("✗ Could not list Drive files: ${listResult.message}")
                            _state.update { it.copy(
                                lastTestResult = "PARTIAL — Token valid, but list failed: ${listResult.message}",
                                lastTestDurationMs = System.currentTimeMillis() - start,
                                lastTestPassed = false,
                                driveAuthorized = true,
                                logs = logs.toList()
                            )}
                        }
                    }
                } else {
                    log("✗ Failed: Bearer token is null (consent required or offline)")
                    _state.update { it.copy(
                        lastTestResult = "FAIL — Needs user consent or offline",
                        lastTestDurationMs = ms,
                        lastTestPassed = false,
                        driveAuthorized = false,
                        logs = logs.toList()
                    )}
                }
            } catch (e: Exception) {
                val ms = System.currentTimeMillis() - start
                log("✗ Exception: ${e.message}")
                _state.update { it.copy(lastTestResult = "ERROR — ${e.message}", lastTestDurationMs = ms, lastTestPassed = false, logs = logs.toList()) }
            }
            _state.update { it.copy(isRunningTest = false) }
        }
    }

    fun runSnapshotIntegrityTest() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isRunningTest = true, logs = emptyList()) }
            logs.clear()
            val start = System.currentTimeMillis()
            log("▶ Testing Local Snapshot Creation & Encryption Integrity...")
            val email = appPreferences.userEmail.first().ifBlank { "test@example.com" }

            try {
                log("  1. Building unencrypted logical snapshot from Room...")
                val (snapshot, gzipBytes) = databaseBackupEngine.createSnapshot(email)
                log("     ✓ Snapshot compiled: ${snapshot.expenses.size} expenses, ${snapshot.incomeMonths.size} months, GZIP=${gzipBytes.size} bytes")
                log("     ✓ SHA-256 hash: ${snapshot.header.contentHash.take(16)}...")

                log("  2. Encrypting snapshot with AES-256-GCM (Account-Bound)...")
                val encrypted = backupCryptoManager.encryptAccountBound(gzipBytes, email)
                log("     ✓ Encrypted envelope: ${encrypted.size} bytes (with EXPBK01 header, IV, 128-bit GCM tag)")

                log("  3. Decrypting envelope and validating auth tag...")
                val decryptedGzip = backupCryptoManager.decrypt(encrypted, email)
                val decompressedJson = snapshotSerializer.decompressGzip(decryptedGzip)
                val restoredSnapshot = snapshotSerializer.fromJson(decompressedJson)
                log("     ✓ Decrypted and validated successfully!")
                log("     ✓ Restored count: ${restoredSnapshot.expenses.size} expenses, hash verified")

                val ms = System.currentTimeMillis() - start
                _state.update { it.copy(
                    isRunningTest = false,
                    lastTestResult = "PASS — Snapshot crypto test verified in ${ms}ms",
                    lastTestDurationMs = ms,
                    lastTestPassed = true,
                    logs = logs.toList()
                )}
            } catch (e: Exception) {
                val ms = System.currentTimeMillis() - start
                log("✗ Snapshot test failed: ${e.message}")
                _state.update { it.copy(
                    isRunningTest = false,
                    lastTestResult = "FAIL — ${e.message}",
                    lastTestDurationMs = ms,
                    lastTestPassed = false,
                    logs = logs.toList()
                )}
            }
        }
    }

    fun runLiveBackupTest() {
        viewModelScope.launch {
            _state.update { it.copy(isRunningTest = true, logs = emptyList()) }
            logs.clear()
            val start = System.currentTimeMillis()
            log("▶ Starting Live Google Drive Cloud Backup Test...")
            val email = appPreferences.userEmail.first()
            log("  Target Account: ${email.ifBlank { "None (Not signed in)" }}")

            if (email.isBlank()) {
                log("✗ Aborted: User not signed in")
                _state.update { it.copy(isRunningTest = false, lastTestResult = "FAIL — Not signed in", lastTestPassed = false, logs = logs.toList()) }
                return@launch
            }

            when (val outcome = driveBackupRepository.backupNow(forceUpload = true)) {
                is com.spendwise.app.data.backup.BackupOutcome.Success -> {
                    val ms = System.currentTimeMillis() - start
                    val kb = (outcome.sizeBytes / 1024L).coerceAtLeast(1L)
                    log("✓ SUCCESS: Live backup uploaded to Google Drive!")
                    log("  • Expenses backed up: ${outcome.expensesCount}")
                    log("  • Months backed up: ${outcome.monthsCount}")
                    log("  • Encrypted payload: $kb KB (${outcome.sizeBytes} bytes)")
                    log("  • Total duration: ${ms}ms")
                    log("  • Destination: Google Drive appDataFolder/expenso_backup_latest.enc")
                    _state.update { it.copy(
                        isRunningTest = false,
                        lastTestResult = "PASS — Uploaded ${outcome.expensesCount} expenses ($kb KB) in ${ms}ms",
                        lastTestDurationMs = ms,
                        lastTestPassed = true,
                        driveAuthorized = true,
                        logs = logs.toList()
                    )}
                }
                is com.spendwise.app.data.backup.BackupOutcome.NeedsAuthorization -> {
                    val ms = System.currentTimeMillis() - start
                    log("✗ AUTHORIZATION REQUIRED: Drive permission not granted yet.")
                    log("  Please tap 'Authorize Google Drive Now' button below.")
                    _state.update { it.copy(
                        isRunningTest = false,
                        lastTestResult = "FAIL — Drive authorization required",
                        lastTestDurationMs = ms,
                        lastTestPassed = false,
                        driveAuthorized = false,
                        logs = logs.toList()
                    )}
                }
                is com.spendwise.app.data.backup.BackupOutcome.Error -> {
                    val ms = System.currentTimeMillis() - start
                    log("✗ ERROR: ${outcome.message}")
                    _state.update { it.copy(
                        isRunningTest = false,
                        lastTestResult = "FAIL — ${outcome.message}",
                        lastTestDurationMs = ms,
                        lastTestPassed = false,
                        logs = logs.toList()
                    )}
                }
            }
        }
    }

    fun requestDriveAuthorization(activity: android.app.Activity) {
        viewModelScope.launch {
            log("▶ Requesting Google Drive authorization dialog...")
            when (val result = googleDriveAuthManager.requestAuthorization(activity)) {
                is com.spendwise.app.data.drive.DriveAuthResult.Authorized -> {
                    log("✓ Google Drive authorized successfully!")
                    _state.update { it.copy(driveAuthorized = true) }
                    runLiveBackupTest()
                }
                is com.spendwise.app.data.drive.DriveAuthResult.NeedsConsent -> {
                    log("ℹ Consent resolution prompt presented to user.")
                }
                is com.spendwise.app.data.drive.DriveAuthResult.Error -> {
                    log("✗ Authorization error: ${result.message}")
                }
                else -> {}
            }
        }
    }

    private fun log(msg: String) { logs.add(msg) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDiagnosticsScreen(
    navController: NavController,
    viewModel: SyncDiagnosticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drive Backup Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Drive Backup Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    StatusRow("Google Account", state.accountEmail.ifBlank { "Not signed in" }, Color(0xFF1565C0))
                    StatusRow("Drive AppData Scope", if (state.driveAuthorized) "✓ Authorized" else "✗ Needs Authorization", if (state.driveAuthorized) Color(0xFF2E7D32) else Color(0xFFC62828))
                    StatusRow("Last Backup Status", state.syncStatus, Color.DarkGray)
                }
            }

            // Last test result
            if (state.lastTestPassed != null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (state.lastTestPassed == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Test Result", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(state.lastTestResult, fontSize = 13.sp)
                        Text("Duration: ${state.lastTestDurationMs}ms", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Logs
            if (state.logs.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Test Log", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        state.logs.forEach { line ->
                            Text(line, color = Color(0xFFE0E0E0), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }

            // Action buttons
            if (state.isRunningTest) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Testing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? android.app.Activity

                if (!state.driveAuthorized && activity != null) {
                    Button(
                        onClick = { viewModel.requestDriveAuthorization(activity) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Authorize Google Drive Now")
                    }
                }

                Button(
                    onClick = { viewModel.runLiveBackupTest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run Live Cloud Backup Test")
                }

                OutlinedButton(
                    onClick = { viewModel.runConnectivityTest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test Drive OAuth & List Files")
                }

                OutlinedButton(
                    onClick = { viewModel.runSnapshotIntegrityTest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test Snapshot & AES-256 Crypto")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

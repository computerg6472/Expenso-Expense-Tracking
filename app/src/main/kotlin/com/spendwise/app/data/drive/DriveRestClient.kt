package com.spendwise.app.data.drive

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class DriveResult<out T> {
    data class Success<out T>(val data: T) : DriveResult<T>()
    sealed class Error(val message: String, val cause: Throwable? = null) : DriveResult<Nothing>() {
        class Unauthorized(message: String) : Error(message)
        class QuotaExceeded(message: String) : Error(message)
        class FileNotFound(message: String) : Error(message)
        class Network(message: String, cause: Throwable? = null) : Error(message, cause)
        class Unknown(message: String, cause: Throwable? = null) : Error(message, cause)
    }
}

data class DriveBackupFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: String,
    val modifiedEpoch: Long,
    val appProperties: Map<String, String> = emptyMap()
)

@Singleton
class DriveRestClient @Inject constructor(
    private val googleDriveAuthManager: GoogleDriveAuthManager
) {
    companion object {
        private const val TAG = "DriveRestClient"
        const val FILE_LATEST = "expenso_backup_latest.enc"
        const val FILE_PREVIOUS = "expenso_backup_previous.enc"

        private const val BASE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_MULTIPART_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Lists all backup files in the user's hidden appDataFolder.
     */
    suspend fun listBackupFiles(accountEmail: String): DriveResult<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        val token = getValidToken(accountEmail) ?: return@withContext DriveResult.Error.Unauthorized("Not authorized for Google Drive")

        val url = "$BASE_FILES_URL?spaces=appDataFolder&q=trashed=false&fields=files(id,name,size,modifiedTime,appProperties)&pageSize=30"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    val refreshedToken = refreshToken(accountEmail)
                        ?: return@withContext DriveResult.Error.Unauthorized("Token expired and refresh failed")
                    return@withContext retryListFiles(refreshedToken)
                }

                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    Log.e(TAG, "listBackupFiles failed: ${response.code} $body")
                    return@withContext DriveResult.Error.Unknown("Drive API error: ${response.code}")
                }

                val json = JSONObject(response.body?.string() ?: "{}")
                val filesArray = json.optJSONArray("files") ?: JSONArray()
                val list = mutableListOf<DriveBackupFile>()
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.getJSONObject(i)
                    val modStr = obj.optString("modifiedTime", "")
                    val epoch = try { isoFormat.parse(modStr)?.time ?: 0L } catch (_: Exception) { 0L }

                    val propsMap = mutableMapOf<String, String>()
                    val propsObj = obj.optJSONObject("appProperties")
                    if (propsObj != null) {
                        for (key in propsObj.keys()) {
                            propsMap[key] = propsObj.optString(key, "")
                        }
                    }

                    list.add(
                        DriveBackupFile(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            sizeBytes = obj.optLong("size", 0L),
                            modifiedTime = modStr,
                            modifiedEpoch = epoch,
                            appProperties = propsMap
                        )
                    )
                }

                DriveResult.Success(list)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error listing backup files", e)
            DriveResult.Error.Network("Cannot reach Google Drive", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error listing backup files", e)
            DriveResult.Error.Unknown("Error listing backups: ${e.message}", e)
        }
    }

    /**
     * Finds the latest backup file in appDataFolder.
     */
    suspend fun getLatestBackupFile(accountEmail: String): DriveResult<DriveBackupFile?> = withContext(Dispatchers.IO) {
        when (val result = listBackupFiles(accountEmail)) {
            is DriveResult.Success -> {
                val latest = result.data.find { it.name == FILE_LATEST }
                DriveResult.Success(latest)
            }
            is DriveResult.Error -> result
        }
    }

    /**
     * Downloads file content bytes by file ID.
     */
    suspend fun downloadFile(fileId: String, accountEmail: String): DriveResult<ByteArray> = withContext(Dispatchers.IO) {
        val token = getValidToken(accountEmail) ?: return@withContext DriveResult.Error.Unauthorized("Not authorized for Google Drive")

        val url = "$BASE_FILES_URL/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    val refreshedToken = refreshToken(accountEmail)
                        ?: return@withContext DriveResult.Error.Unauthorized("Token expired")
                    val retryReq = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $refreshedToken")
                        .get()
                        .build()
                    httpClient.newCall(retryReq).execute().use { retryResp ->
                        if (!retryResp.isSuccessful) return@withContext DriveResult.Error.Unknown("Download failed: ${retryResp.code}")
                        return@withContext DriveResult.Success(retryResp.body?.bytes() ?: ByteArray(0))
                    }
                }

                if (response.code == 404) {
                    return@withContext DriveResult.Error.FileNotFound("Backup file not found in Google Drive")
                }

                if (!response.isSuccessful) {
                    return@withContext DriveResult.Error.Unknown("Drive download failed: ${response.code}")
                }

                val bytes = response.body?.bytes() ?: ByteArray(0)
                DriveResult.Success(bytes)
            }
        } catch (e: IOException) {
            DriveResult.Error.Network("Network error downloading backup", e)
        } catch (e: Exception) {
            DriveResult.Error.Unknown("Error downloading backup: ${e.message}", e)
        }
    }

    /**
     * Uploads a new backup file with safe rotation:
     * 1. If `expenso_backup_latest.enc` exists:
     *    - Delete `expenso_backup_previous.enc` if present.
     *    - Rename `expenso_backup_latest.enc` to `expenso_backup_previous.enc`.
     * 2. Upload new encrypted bytes as `expenso_backup_latest.enc`.
     * 3. If new calendar month, archive a copy to `expenso_backup_YYYY_MM.enc` (keep max 3).
     */
    suspend fun uploadWithSafeRotation(
        backupBytes: ByteArray,
        accountEmail: String,
        appProperties: Map<String, String> = emptyMap()
    ): DriveResult<DriveBackupFile> = withContext(Dispatchers.IO) {
        val filesResult = listBackupFiles(accountEmail)
        if (filesResult is DriveResult.Error) return@withContext filesResult

        val existingFiles = (filesResult as DriveResult.Success).data
        val existingLatest = existingFiles.find { it.name == FILE_LATEST }
        val existingPrevious = existingFiles.find { it.name == FILE_PREVIOUS }

        // Step 1: Rotate existing latest -> previous
        if (existingLatest != null) {
            // Delete old previous if present
            if (existingPrevious != null) {
                deleteFile(existingPrevious.id, accountEmail)
            }
            // Rename existing latest to previous
            renameFile(existingLatest.id, FILE_PREVIOUS, accountEmail)
        }

        // Step 2: Upload new backup as latest
        val uploadResult = uploadFile(FILE_LATEST, backupBytes, accountEmail, appProperties)
        if (uploadResult is DriveResult.Error) return@withContext uploadResult

        // Step 3: Handle monthly archives (expenso_backup_YYYY_MM.enc)
        try {
            handleMonthlyArchive(backupBytes, existingFiles, accountEmail, appProperties)
        } catch (e: Exception) {
            Log.w(TAG, "Monthly archive maintenance warning: ${e.message}")
        }

        uploadResult
    }

    /**
     * Uploads file bytes to appDataFolder using multipart request.
     */
    private suspend fun uploadFile(
        fileName: String,
        bytes: ByteArray,
        accountEmail: String,
        appProperties: Map<String, String>
    ): DriveResult<DriveBackupFile> = withContext(Dispatchers.IO) {
        val token = getValidToken(accountEmail) ?: return@withContext DriveResult.Error.Unauthorized("Not authorized for Google Drive")

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put("appDataFolder"))
            if (appProperties.isNotEmpty()) {
                val props = JSONObject()
                for ((k, v) in appProperties) props.put(k, v)
                put("appProperties", props)
            }
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadataJson.toString().toRequestBody(JSON_MEDIA_TYPE)
            )
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody(BINARY_MEDIA_TYPE)
            )
            .build()

        val request = Request.Builder()
            .url(UPLOAD_MULTIPART_URL)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    val refreshedToken = refreshToken(accountEmail)
                        ?: return@withContext DriveResult.Error.Unauthorized("Token expired")
                    val retryReq = Request.Builder()
                        .url(UPLOAD_MULTIPART_URL)
                        .header("Authorization", "Bearer $refreshedToken")
                        .post(requestBody)
                        .build()
                    return@withContext executeUpload(retryReq)
                }

                if (response.code == 403 || response.code == 507) {
                    return@withContext DriveResult.Error.QuotaExceeded("Google Drive storage full or quota exceeded")
                }

                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "Upload failed: ${response.code} $err")
                    return@withContext DriveResult.Error.Unknown("Upload failed with HTTP ${response.code}")
                }

                val resJson = JSONObject(response.body?.string() ?: "{}")
                val uploaded = DriveBackupFile(
                    id = resJson.getString("id"),
                    name = resJson.getString("name"),
                    sizeBytes = bytes.size.toLong(),
                    modifiedTime = resJson.optString("modifiedTime", ""),
                    modifiedEpoch = System.currentTimeMillis(),
                    appProperties = appProperties
                )
                DriveResult.Success(uploaded)
            }
        } catch (e: IOException) {
            DriveResult.Error.Network("Network error uploading backup to Drive", e)
        } catch (e: Exception) {
            DriveResult.Error.Unknown("Error uploading backup: ${e.message}", e)
        }
    }

    private fun executeUpload(request: Request): DriveResult<DriveBackupFile> {
        return httpClient.newCall(request).execute().use { response ->
            if (response.code == 403 || response.code == 507) {
                return DriveResult.Error.QuotaExceeded("Google Drive storage full or quota exceeded")
            }
            if (!response.isSuccessful) {
                return DriveResult.Error.Unknown("Upload failed: ${response.code}")
            }
            val resJson = JSONObject(response.body?.string() ?: "{}")
            DriveResult.Success(
                DriveBackupFile(
                    id = resJson.getString("id"),
                    name = resJson.getString("name"),
                    sizeBytes = 0L,
                    modifiedTime = "",
                    modifiedEpoch = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Renames a file in Google Drive.
     */
    private suspend fun renameFile(fileId: String, newName: String, accountEmail: String): Boolean = withContext(Dispatchers.IO) {
        val token = getValidToken(accountEmail) ?: return@withContext false
        val url = "$BASE_FILES_URL/$fileId"
        val bodyJson = JSONObject().apply { put("name", newName) }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    /**
     * Deletes a file from Google Drive.
     */
    private suspend fun deleteFile(fileId: String, accountEmail: String): Boolean = withContext(Dispatchers.IO) {
        val token = getValidToken(accountEmail) ?: return@withContext false
        val url = "$BASE_FILES_URL/$fileId"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    private suspend fun handleMonthlyArchive(
        bytes: ByteArray,
        existingFiles: List<DriveBackupFile>,
        accountEmail: String,
        appProperties: Map<String, String>
    ) {
        val monthStr = SimpleDateFormat("yyyy_MM", Locale.US).format(Date())
        val archiveName = "expenso_backup_$monthStr.enc"

        val hasCurrentMonthArchive = existingFiles.any { it.name == archiveName }
        if (!hasCurrentMonthArchive) {
            uploadFile(archiveName, bytes, accountEmail, appProperties)

            // Keep max 3 monthly archives; delete older ones
            val monthlyArchives = existingFiles
                .filter { it.name.startsWith("expenso_backup_") && it.name.endsWith(".enc") && it.name != FILE_LATEST && it.name != FILE_PREVIOUS }
                .sortedBy { it.name }

            if (monthlyArchives.size >= 3) {
                val toDelete = monthlyArchives.take(monthlyArchives.size - 2)
                for (old in toDelete) {
                    deleteFile(old.id, accountEmail)
                }
            }
        }
    }

    private suspend fun retryListFiles(token: String): DriveResult<List<DriveBackupFile>> {
        val url = "$BASE_FILES_URL?spaces=appDataFolder&q=trashed=false&fields=files(id,name,size,modifiedTime,appProperties)"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        return httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return DriveResult.Error.Unknown("Retry list failed: ${resp.code}")
            val json = JSONObject(resp.body?.string() ?: "{}")
            val arr = json.optJSONArray("files") ?: JSONArray()
            val list = mutableListOf<DriveBackupFile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DriveBackupFile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        sizeBytes = obj.optLong("size", 0L),
                        modifiedTime = obj.optString("modifiedTime", ""),
                        modifiedEpoch = 0L
                    )
                )
            }
            DriveResult.Success(list)
        }
    }

    private suspend fun getValidToken(accountEmail: String): String? {
        return googleDriveAuthManager.getValidAccessToken(forceRefresh = false)
    }

    private suspend fun refreshToken(accountEmail: String): String? {
        return googleDriveAuthManager.getValidAccessToken(forceRefresh = true)
    }
}

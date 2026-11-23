package com.example.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed class DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome()
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthOutcome()
    data class Failed(val message: String) : DriveAuthOutcome()
}

/**
 * Manages least-privilege Google Drive OAuth (`drive.appdata` scope only) via
 * Google Play Services AuthorizationClient and uploads/downloads client-side
 * AES-256-GCM encrypted vault files without any intermediate server.
 */
class GoogleDriveSyncManager(private val context: Context) {

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val VAULT_FILE_NAME = "easyplanning_vault.epbak"
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Intentionally omits HTTP body logging so tokens & encrypted payloads are never logged.
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val driveApi: GoogleDriveApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoogleDriveApi::class.java)

    suspend fun requestDriveAuthorization(): DriveAuthOutcome = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (!cont.isActive) return@addOnSuccessListener
                val pending = result.pendingIntent
                val token = result.accessToken
                when {
                    result.hasResolution() && pending != null ->
                        cont.resume(DriveAuthOutcome.NeedsResolution(pending))
                    !token.isNullOrBlank() ->
                        cont.resume(DriveAuthOutcome.Authorized(token))
                    else ->
                        cont.resume(DriveAuthOutcome.Failed("Missing access token"))
                }
            }
            .addOnFailureListener { e ->
                if (cont.isActive) {
                    cont.resume(DriveAuthOutcome.Failed(e.localizedMessage ?: "Authorization failed"))
                }
            }
    }

    fun extractTokenFromIntent(data: Intent?): String? {
        return runCatching {
            Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.getOrNull()
    }

    suspend fun uploadEncryptedVault(accessToken: String, encryptedBytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bearer = "Bearer $accessToken"
                val listResponse = driveApi.listAppDataFiles(bearer)
                if (!listResponse.isSuccessful) {
                    throw IOException("Drive list failed (${listResponse.code()})")
                }
                val existingId = listResponse.body()?.files?.firstOrNull()?.id

                val mediaType = "application/octet-stream".toMediaType()
                if (existingId != null) {
                    val body = encryptedBytes.toRequestBody(mediaType)
                    val updateResp = driveApi.updateAppDataFile(
                        bearer = bearer,
                        fileId = existingId,
                        encryptedBody = body
                    )
                    if (!updateResp.isSuccessful) {
                        throw IOException("Drive update failed (${updateResp.code()})")
                    }
                } else {
                    val metadataJson = """{"name":"$VAULT_FILE_NAME","parents":["appDataFolder"]}"""
                    val metaPart = MultipartBody.Part.create(
                        metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                    )
                    val mediaPart = MultipartBody.Part.create(
                        encryptedBytes.toRequestBody(mediaType)
                    )
                    val createResp = driveApi.createAppDataFile(
                        bearer = bearer,
                        metadata = metaPart,
                        media = mediaPart
                    )
                    if (!createResp.isSuccessful) {
                        throw IOException("Drive upload failed (${createResp.code()})")
                    }
                }
            }
        }

    suspend fun downloadEncryptedVault(accessToken: String): Result<ByteArray?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bearer = "Bearer $accessToken"
                val listResponse = driveApi.listAppDataFiles(bearer)
                if (!listResponse.isSuccessful) {
                    throw IOException("Drive list failed (${listResponse.code()})")
                }
                val fileId = listResponse.body()?.files?.firstOrNull()?.id ?: return@runCatching null
                val downloadResp = driveApi.downloadFile(bearer = bearer, fileId = fileId)
                if (!downloadResp.isSuccessful) {
                    throw IOException("Drive download failed (${downloadResp.code()})")
                }
                downloadResp.body()?.bytes()
            }
        }
}

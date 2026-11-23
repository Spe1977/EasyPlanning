package com.example.sync

import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class DriveFileItem(
    val id: String,
    val name: String? = null,
    val modifiedTime: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    val files: List<DriveFileItem> = emptyList()
)

interface GoogleDriveApi {

    @GET("drive/v3/files")
    suspend fun listAppDataFiles(
        @Header("Authorization") bearer: String,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("q") query: String = "name = 'easyplanning_vault.epbak' and trashed = false",
        @Query("fields") fields: String = "files(id, name, modifiedTime)"
    ): Response<DriveFileListResponse>

    @GET("drive/v3/files/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): Response<ResponseBody>

    @Multipart
    @POST("upload/drive/v3/files")
    suspend fun createAppDataFile(
        @Header("Authorization") bearer: String,
        @Query("uploadType") uploadType: String = "multipart",
        @Part metadata: MultipartBody.Part,
        @Part media: MultipartBody.Part
    ): Response<DriveFileItem>

    @PATCH("upload/drive/v3/files/{fileId}")
    suspend fun updateAppDataFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("uploadType") uploadType: String = "media",
        @Body encryptedBody: RequestBody
    ): Response<DriveFileItem>
}

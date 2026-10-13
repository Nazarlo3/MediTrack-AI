package com.example.meditrackai.ui

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PhotoStorage {
    private const val PHOTOS_DIR = "med_photos"

    suspend fun savePhotoToInternalStorage(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            var destinationFile: File? = null
            try {
                val photosDir = File(context.filesDir, PHOTOS_DIR)
                if (!photosDir.exists() && !photosDir.mkdirs()) return@withContext null
                val destination = File(photosDir, "med_${UUID.randomUUID()}.jpg")
                destinationFile = destination
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                input.use {
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                destination.absolutePath
            } catch (e: CancellationException) {
                destinationFile?.delete()
                throw e
            } catch (e: Exception) {
                destinationFile?.delete()
                null
            }
        }
    }

    suspend fun deletePhotoFromInternalStorage(context: Context, filePath: String?) {
        if (filePath.isNullOrEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath).canonicalFile
                val photosDir = File(context.filesDir, PHOTOS_DIR).canonicalFile
                if (file.parentFile == photosDir && file.exists()) {
                    file.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Safe ignore
            }
        }
    }

    fun getUriFromPathOrContent(pathOrUriString: String?): Uri? {
        if (pathOrUriString.isNullOrEmpty()) return null
        return try {
            if (pathOrUriString.startsWith("content://") || pathOrUriString.startsWith("file://")) {
                Uri.parse(pathOrUriString)
            } else {
                val file = File(pathOrUriString)
                if (file.exists()) Uri.fromFile(file) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

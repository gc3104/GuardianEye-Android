package com.example.guardianeye.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FootageManager(private val context: Context) {

    private val TAG = "FootageManager"

    /**
     * Executes the lifecycle policy:
     * 1. Compression: Identifies files older than [compressionDelayDays], simulates compression (renaming).
     * 2. Archival: Groups compressed files by [archivePeriod], zips them, and deletes the originals.
     * 3. Cleanup: Deletes archives older than [archiveRetentionDays].
     */
    suspend fun runLifecycle(
        directoryUri: Uri,
        compressionDelayDays: Int,
        archivePeriod: String, // "Daily", "Weekly", "Monthly"
        archiveRetentionDays: Int
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(TAG, "Lifecycle management requires Android 10+")
            return
        }

        withContext(Dispatchers.IO) {
            val rootDir = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext
            if (!rootDir.isDirectory) return@withContext

            // Step 1: "Compress" old raw files
            val compressCutoff = System.currentTimeMillis() - (compressionDelayDays * 24 * 60 * 60 * 1000L)
            compressRawFiles(rootDir, compressCutoff)

            // Step 2: Archive compressed files
            archiveCompressedFiles(rootDir, archivePeriod)

            // Step 3: Delete old archives
            val deleteArchiveCutoff = System.currentTimeMillis() - (archiveRetentionDays * 24 * 60 * 60 * 1000L)
            cleanupOldArchives(rootDir, deleteArchiveCutoff)
        }
    }

    suspend fun getFootageFiles(directoryUri: Uri): Pair<List<DocumentFile>, List<DocumentFile>> {
        return withContext(Dispatchers.IO) {
            val rootDir = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext Pair(emptyList(), emptyList())
            val allFiles = rootDir.listFiles()

            val unarchived = allFiles.filter { it.isFile && (it.name?.endsWith(".mp4") == true || it.name?.startsWith("compressed_") == true) }

            val archivesDir = rootDir.findFile("archives")
            val archived = archivesDir?.listFiles()?.filter { it.isFile && it.name?.endsWith(".zip") == true } ?: emptyList()

            Pair(unarchived.sortedByDescending { it.lastModified() }, archived.sortedByDescending { it.lastModified() })
        }
    }

    suspend fun renameFile(fileUri: Uri, newName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // For SAF, rename is tricky. It depends on the provider. This is the standard way.
                val docFile = DocumentFile.fromSingleUri(context, fileUri) ?: return@withContext false
                docFile.renameTo(newName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename file: $fileUri", e)
                false
            }
        }
    }

    suspend fun deleteFile(fileUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val docFile = DocumentFile.fromSingleUri(context, fileUri) ?: return@withContext false
                docFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete file: $fileUri", e)
                false
            }
        }
    }

    suspend fun deleteAllFootage(directoryUri: Uri) {
        withContext(Dispatchers.IO) {
            val rootDir = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext
            rootDir.listFiles().forEach { file ->
                try {
                    if (file.name != ".nomedia") file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete file: ${file.name}", e)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun compressRawFiles(rootDir: DocumentFile, cutoffTime: Long) {
        val rawFiles = rootDir.listFiles().filter {
            it.isFile && 
            it.name?.endsWith(".mp4") == true && 
            !(it.name?.startsWith("compressed_") ?: false) &&
            it.lastModified() < cutoffTime
        }

        rawFiles.forEach { file ->
            // In a real app, use MediaCodec here. For now, rename to simulate "processed" state.
            val newName = "compressed_${file.name}"
            try {
                file.renameTo(newName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename (compress) file: ${file.name}", e)
            }
        }
    }

    private fun archiveCompressedFiles(rootDir: DocumentFile, period: String) {
        val compressedFiles = rootDir.listFiles().filter {
            it.isFile && it.name?.startsWith("compressed_") == true && it.name?.endsWith(".mp4") == true
        }

        if (compressedFiles.isEmpty()) return

        // Group files
        val groups = compressedFiles.groupBy { file ->
            getGroupKey(file.lastModified(), period)
        }

        // Create archives folder if it doesn't exist
        var archivesDir = rootDir.findFile("archives")
        if (archivesDir == null) {
            archivesDir = rootDir.createDirectory("archives")
        }
        if (archivesDir == null || !archivesDir.isDirectory) return

        groups.forEach { (key, files) ->
            val archiveName = "Archive_${key}.zip"
            
            if (archivesDir.findFile(archiveName) == null) {
                createZipArchive(context, archivesDir, archiveName, files)
                // After successful zip, delete source files
                files.forEach { it.delete() }
            }
        }
    }

    private fun cleanupOldArchives(rootDir: DocumentFile, cutoffTime: Long) {
        val archivesDir = rootDir.findFile("archives") ?: return
        
        archivesDir.listFiles().forEach { file ->
            if (file.isFile && file.name?.endsWith(".zip") == true && file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }

    private fun getGroupKey(timestamp: Long, period: String): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        
        return when(period) {
            "Weekly" -> {
                val year = calendar.get(Calendar.YEAR)
                val week = calendar.get(Calendar.WEEK_OF_YEAR)
                "${year}_Week$week"
            }
            "Monthly" -> {
                val fmt = SimpleDateFormat("yyyy_MM", Locale.getDefault())
                fmt.format(Date(timestamp))
            }
            else -> { // Daily
                val fmt = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
                fmt.format(Date(timestamp))
            }
        }
    }

    private fun createZipArchive(context: Context, parentDir: DocumentFile, zipName: String, files: List<DocumentFile>) {
        try {
            val zipFile = parentDir.createFile("application/zip", zipName) ?: return
            val outputStream = context.contentResolver.openOutputStream(zipFile.uri) ?: return
            
            ZipOutputStream(BufferedOutputStream(outputStream)).use { out ->
                val buffer = ByteArray(1024)
                for (file in files) {
                    val inputStream = context.contentResolver.openInputStream(file.uri) ?: continue
                    BufferedInputStream(inputStream).use { origin ->
                        val entry = ZipEntry(file.name)
                        out.putNextEntry(entry)
                        var count: Int
                        while (origin.read(buffer).also { count = it } != -1) {
                            out.write(buffer, 0, count)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating zip: $zipName", e)
        }
    }
}

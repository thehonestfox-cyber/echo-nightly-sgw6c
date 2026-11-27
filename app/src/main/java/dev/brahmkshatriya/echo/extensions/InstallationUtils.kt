package dev.brahmkshatriya.echo.extensions

import android.content.Context
import dev.brahmkshatriya.echo.extensions.repo.FileRepository.Companion.getExtensionsFileDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File based extension (un)installation. On the watch every extension is stored as an
 * `.apk`/`.eapk` inside `filesDir/extensions`, there is no system package installer flow.
 */
object InstallationUtils {

    suspend fun installFile(
        context: Context, fileIgnoreFlow: MutableSharedFlow<File?>, id: String, tempFile: File
    ) = withContext(Dispatchers.IO) {
        val dir = context.getExtensionsFileDir()
        val newFile = File(dir, "$id.apk")
        dir.setWritable(true)
        newFile.setWritable(true)
        if (newFile.exists())
            if (!newFile.delete())
                throw IllegalStateException("Failed to delete existing file: $newFile")
        if (!tempFile.renameTo(newFile)) {
            tempFile.copyTo(newFile, overwrite = true)
            tempFile.delete()
        }
        newFile.setWritable(false)
        dir.setReadOnly()
        fileIgnoreFlow.emit(null)
    }

    suspend fun uninstallFile(
        fileIgnoreFlow: MutableSharedFlow<File?>, path: String
    ) = withContext(Dispatchers.IO) {
        val file = File(path)
        fileIgnoreFlow.emit(file)
        file.parentFile!!.setWritable(true)
        file.setWritable(true)
        if (file.exists() && !file.delete())
            throw IllegalStateException("Failed to delete file: $file")
        fileIgnoreFlow.emit(null)
    }
}

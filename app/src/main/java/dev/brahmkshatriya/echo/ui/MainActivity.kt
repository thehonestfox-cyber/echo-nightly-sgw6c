package dev.brahmkshatriya.echo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val extensionLoader by inject<ExtensionLoader>()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[audioPermission] == true) extensionLoader.setPermGranted()
        }

    private val audioPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EchoApp() }
        requestPermissions()
    }

    private fun requestPermissions() {
        val needed = buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED)
            extensionLoader.setPermGranted()
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}

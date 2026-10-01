package sk.orszagh.financemaster.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen(state: CaptureState, onCapture: (suspend (File) -> Unit) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasPermission() }
    LaunchedEffect(Unit) {
        if (!granted && !requested) {
            requested = true
            permission.launch(Manifest.permission.CAMERA)
        }
    }
    BackHandler(enabled = state.saving) { /* Uloženie musí skončiť pred opustením kamery. */ }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var ready by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var bindAttempt by remember { mutableIntStateOf(0) }
    if (granted) {
        DisposableEffect(lifecycle, bindAttempt) {
            var disposed = false
            var provider: ProcessCameraProvider? = null
            ready = false
            cameraError = null
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        provider = future.get()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        provider!!.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        ready = true
                    } catch (_: Exception) {
                        cameraError = "Kameru sa nepodarilo otvoriť. Skontrolujte povolenie a dostupnosť kamery."
                    }
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                disposed = true
                provider?.unbind(preview, imageCapture)
                ready = false
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!granted) {
            Text("Na odfotenie účtenky potrebujeme povolenie kamery. Fotografia zostane iba v aplikácii.")
            Button({ permission.launch(Manifest.permission.CAMERA) }) { Text("Povoliť kameru") }
            TextButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) {
                Text("Otvoriť nastavenia aplikácie")
            }
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().weight(1f))
            Text("Účtenku odfoťte celú, ostro a pri dobrom svetle.")
            cameraError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton({ bindAttempt++ }) { Text("Skúsiť kameru znova") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    onCapture { file ->
                        withContext(Dispatchers.Main.immediate) {
                            imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                            suspendCancellableCoroutine { continuation ->
                                imageCapture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(file).build(),
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            if (continuation.isActive) continuation.resume(Unit)
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            if (continuation.isActive) continuation.resumeWithException(exception)
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                enabled = ready && !state.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text(if (state.saving) "Ukladá sa…" else "Odfotiť a uložiť") }
        }
    }
}

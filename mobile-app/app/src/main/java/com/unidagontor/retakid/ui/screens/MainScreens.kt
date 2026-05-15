package com.unidagontor.retakid.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unidagontor.retakid.data.ml.DetectionResult
import com.unidagontor.retakid.ui.theme.*
import com.unidagontor.retakid.ui.viewmodel.DeteksiStage
import com.unidagontor.retakid.ui.viewmodel.DeteksiViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@Composable
fun DeteksiTab(vm: DeteksiViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (cameraGranted && locationGranted) {
            vm.startDetection()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.stage) {
            DeteksiStage.INITIAL -> {
                InitialDetectionView(onStart = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                })
            }
            DeteksiStage.CAMERA -> {
                CameraView(onImageCaptured = { vm.onImageCaptured(it) })
            }
            DeteksiStage.ANALYZING -> {
                AnalyzingView()
            }
            DeteksiStage.RESULT -> {
                ResultView(
                    result = state.detectionResult,
                    image = state.capturedImage,
                    onProceed = { vm.proceedToReport() },
                    onRetry = { vm.reset() }
                )
            }
            DeteksiStage.REPORT_FORM -> {
                ReportFormView(
                    location = state.location,
                    isSubmitting = state.isSubmitting,
                    onSubmit = { locName, note -> vm.submitReport(locName, note) }
                )
            }
            DeteksiStage.SUCCESS -> {
                SuccessView(onFinish = { vm.reset() })
            }
        }

        state.error?.let {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { vm.reset() }) { Text("OK", color = Color.White) } }
            ) { Text(it) }
        }
    }
}

@Composable
fun InitialDetectionView(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = GreenSurface
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Kamera",
                modifier = Modifier.padding(32.dp).fillMaxSize(),
                tint = GreenPrimary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Deteksi Retakan",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Ambil foto retakan tanah untuk dianalisis tingkat risikonya menggunakan AI.",
            textAlign = TextAlign.Center,
            color = TextSecondary,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) {
            Text("Scan Retakan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CameraView(onImageCaptured: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                // Handle error
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // UI Controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    val file = File(context.cacheDir, "temp_image.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                    imageCapture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val uri = Uri.fromFile(file)
                                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                } else {
                                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                                    ImageDecoder.decodeBitmap(source)
                                }
                                onImageCaptured(bitmap)
                            }

                            override fun onError(exc: ImageCaptureException) {
                                // Handle error
                            }
                        }
                    )
                },
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
fun AnalyzingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = GreenPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Menganalisis retakan...", fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable
fun ResultView(
    result: DetectionResult?,
    image: Bitmap?,
    onProceed: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hasil Analisis ML", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val (color, title, desc) = when (result) {
            DetectionResult.BAHAYA -> Triple(
                StatusBahaya,
                "BAHAYA",
                "Risiko tinggi! Ditemukan retakan kritis yang berpotensi menyebabkan longsor segera."
            )
            DetectionResult.WASPADA -> Triple(
                StatusWaspada,
                "WASPADA",
                "Risiko sedang. Retakan terdeteksi, pantau kondisi tanah secara berkala terutama saat hujan."
            )
            else -> Triple(
                StatusAman,
                "AMAN",
                "Risiko rendah. Retakan yang terdeteksi masih dalam batas wajar."
            )
        }

        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    title,
                    color = color,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    desc,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (result == DetectionResult.BAHAYA) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = StatusBahaya,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "PERINGATAN DARURAT: Segera menjauh dari area lereng dan infokan warga sekitar!",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Lanjut ke Laporan")
        }
        TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Coba Lagi", color = TextSecondary)
        }
    }
}

@Composable
fun ReportFormView(
    location: com.unidagontor.retakid.data.location.LocationData?,
    isSubmitting: Boolean,
    onSubmit: (String, String) -> Unit
) {
    var locName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Form Laporan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = locName,
            onValueChange = { locName = it },
            label = { Text("Nama Lokasi") },
            placeholder = { Text("Contoh: Lereng Utara RT 02") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Catatan Tambahan") },
            placeholder = { Text("Ceritakan kondisi detail...") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = GreenSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = GreenPrimary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Lokasi Terdeteksi", fontSize = 12.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        if (location != null) "${location.latitude}, ${location.longitude}" else "Mendeteksi lokasi...",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onSubmit(locName, note) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            enabled = !isSubmitting && locName.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Kirim Laporan", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SuccessView(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = GreenPrimary,
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Laporan Terkirim!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Terima kasih telah berkontribusi menjaga keamanan warga. Laporan Anda sedang disinkronkan ke sistem.",
            textAlign = TextAlign.Center,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Kembali")
        }
    }
}




@Composable
fun ProfilTab() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Adam Toyib Nur Wahid", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Poin: 150 | Badge: Relawan")
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Riwayat Laporan") }
        TextButton(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Panduan Kearifan Lokal") }
    }
}
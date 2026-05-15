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
            DeteksiStage.VALIDATING -> {
                ValidatingView()
            }
            DeteksiStage.ANALYZING -> {
                AnalyzingView()
            }
            DeteksiStage.ANALYZING_ENV -> {
                AnalyzingEnvView()
            }
            DeteksiStage.UNCERTAIN -> {
                UncertainView(
                    validationError = state.validationError
                        ?: "Hasil tidak pasti — ambil foto ulang lebih dekat",
                    confidence = state.mlResult?.confidence ?: 0f,
                    onRetake = { vm.reset() }
                )
            }
            DeteksiStage.RESULT -> {
                val displayResult = state.riskFactorReport?.finalResult ?: state.mlResult?.detectionResult
                val displayConfidence = state.mlResult?.confidence ?: 0f
                if (displayResult != null) {
                    ResultView(
                        result = displayResult,
                        confidence = displayConfidence,
                        report = state.riskFactorReport,
                        image = state.capturedImage,
                        onProceed = { vm.proceedToReport() },
                        onRetry = { vm.reset() }
                    )
                }
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
fun ValidatingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = GreenPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Memeriksa kualitas gambar...", fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Ketajaman & pencahayaan", fontSize = 13.sp, color = TextSecondary)
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
fun UncertainView(
    validationError: String,
    confidence: Float,
    onRetake: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = StatusWaspada,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Hasil Tidak Pasti",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            validationError,
            textAlign = TextAlign.Center,
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        ConfidenceBar(confidence = confidence)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tingkat keyakinan AI: ${(confidence * 100).toInt()}%",
            fontSize = 12.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Foto Ulang", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ConfidenceBar(confidence: Float) {
    val barColor = when {
        confidence < 0.5f -> StatusBahaya
        confidence < 0.7f -> StatusWaspada
        else -> StatusAman
    }
    val pct = (confidence * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(confidence.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(barColor)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0%", fontSize = 10.sp, color = TextSecondary)
            Text(
                "${pct}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
            Text("100%", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
fun AnalyzingEnvView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = GreenPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Menganalisis faktor lingkungan...", fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Elevasi, kemiringan, cuaca, & jenis tanah",
            fontSize = 13.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun ResultView(
    result: DetectionResult,
    confidence: Float,
    report: com.unidagontor.retakid.data.risk.RiskFactorReport?,
    image: Bitmap?,
    onProceed: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hasil Analisis", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

        Spacer(modifier = Modifier.height(16.dp))
        ConfidenceBar(confidence = confidence)

        if (confidence < 0.4f) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = StatusBahaya.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusBahaya)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Gambar bukan retakan tanah? Pastikan memotret permukaan tanah",
                        color = StatusBahaya,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (confidence < 0.6f) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = StatusWaspada.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = StatusWaspada)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Hasil tidak pasti — ambil foto ulang dengan pencahayaan lebih baik",
                        color = StatusWaspada,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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

        if (report != null) {
            Spacer(modifier = Modifier.height(20.dp))
            EnvironmentFactorCard(report = report)
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
fun EnvironmentFactorCard(report: com.unidagontor.retakid.data.risk.RiskFactorReport) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Faktor Lingkungan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Sembunyikan" else "Detail", fontSize = 12.sp, color = GreenPrimary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Skor Risiko: ${"%.0f".format(report.finalScore * 100)}%",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = when {
                    report.finalScore <= 0.33 -> StatusAman
                    report.finalScore <= 0.66 -> StatusWaspada
                    else -> StatusBahaya
                }
            )

            if (report.isUpgraded) {
                Text(
                    "↑ Meningkat dari hasil ML karena faktor lingkungan",
                    fontSize = 11.sp,
                    color = StatusWaspada
                )
            } else if (report.isDowngraded) {
                Text(
                    "↓ Menurun karena faktor lingkungan mendukung",
                    fontSize = 11.sp,
                    color = StatusAman
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                report.factors.forEach { factor ->
                    FactorRow(factor)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun FactorRow(factor: com.unidagontor.retakid.data.risk.FactorContribution) {
    val color = when (factor.riskLabel) {
        com.unidagontor.retakid.data.risk.RiskLabel.RENDAH -> StatusAman
        com.unidagontor.retakid.data.risk.RiskLabel.SEDANG -> StatusWaspada
        com.unidagontor.retakid.data.risk.RiskLabel.TINGGI -> StatusBahaya
        com.unidagontor.retakid.data.risk.RiskLabel.SANGAT_TINGGI -> StatusBahaya
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(factor.factor.displayName, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.width(130.dp))

        Text(factor.rawValue, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(80.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(factor.score.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))
        Text(factor.riskLabel.emoji, fontSize = 12.sp)
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
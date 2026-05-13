package com.unidagontor.retakid.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.unidagontor.retakid.data.photo.ExifReader
import com.unidagontor.retakid.ui.theme.*
import com.unidagontor.retakid.ui.viewmodel.DeteksiStage
import com.unidagontor.retakid.ui.viewmodel.DeteksiViewModel
import com.unidagontor.retakid.ui.viewmodel.ProfilViewModel
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
                CameraView(onImageCaptured = { bitmap, exif -> vm.onImageCaptured(bitmap, exif) })
            }
            DeteksiStage.ANALYZING -> {
                AnalyzingView()
            }
            DeteksiStage.ANALYZING_ENV -> {
                AnalyzingEnvView()
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
                        location = state.location,
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
fun CameraView(onImageCaptured: (Bitmap, com.unidagontor.retakid.data.photo.ExifData?) -> Unit) {
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
                                val exifData = ExifReader.read(file.absolutePath)
                                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                } else {
                                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                                    ImageDecoder.decodeBitmap(source)
                                }
                                onImageCaptured(bitmap, exifData)
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
    location: com.unidagontor.retakid.data.location.LocationData?,
    onProceed: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
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

        // --- Action buttons based on status ---
        when (result) {
            DetectionResult.BAHAYA -> {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:112")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusBahaya),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hubungi BPBD (112)")
                }
            }
            else -> {}
        }

        if (result != DetectionResult.AMAN) {
            Spacer(modifier = Modifier.height(8.dp))

            val shareContent = buildShareText(result, location, report)

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareContent)
                        setPackage("com.whatsapp")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val fallback = Intent.createChooser(intent.apply { setPackage(null) }, "Bagikan ke...")
                        context.startActivity(fallback)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bagikan ke WhatsApp")
            }

            if (result == DetectionResult.WASPADA) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Laporan Retakan", buildShareText(result, location, report))
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Teks laporan disalin ke clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Laporkan ke RT / RW")
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = StatusAman.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = StatusAman)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Pantau Berkala — Kondisi tanah masih aman, tetap waspada terutama saat hujan.",
                        color = StatusAman,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
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




private fun buildShareText(
    result: DetectionResult,
    location: com.unidagontor.retakid.data.location.LocationData?,
    report: com.unidagontor.retakid.data.risk.RiskFactorReport?
): String {
    val status = result.label
    val lat = location?.let { "%.6f".format(it.latitude) } ?: "-"
    val lon = location?.let { "%.6f".format(it.longitude) } ?: "-"
    val score = report?.let { "${"%.0f".format(it.finalScore * 100)}%" } ?: "?"

    val factors = report?.factors?.joinToString("\n") { f ->
        "• ${f.factor.displayName}: ${f.rawValue} ${f.riskLabel.emoji}"
    } ?: ""

    return buildString {
        appendLine("Saya menemukan retakan tanah dengan status $status.")
        appendLine("Koordinat: $lat, $lon")
        appendLine("Skor Risiko: $score")
        if (factors.isNotBlank()) {
            appendLine()
            appendLine("Faktor Lingkungan:")
            append(factors)
        }
        appendLine()
        append("Mohon tindak lanjut.")
    }
}

@Composable
fun ProfilTab(
    onSignOut: () -> Unit = {},
    onRiwayatClick: (String) -> Unit = {},
    viewModel: ProfilViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(Surface)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenPrimary)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text("Profil Saya", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Pantau kontribusi dan badge kamu", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
        } else if (state.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadProfile() }, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                        Text("Coba Lagi")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar + Nama + Email
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(GreenSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                state.namaLengkap.take(1).uppercase(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(state.namaLengkap, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                                    if (state.email.isNotEmpty()) {
                                        Text(state.email, fontSize = 13.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    // Poin + Badge Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(value = "${state.poin}", label = "Poin", color = GreenPrimary)
                                StatItem(value = state.badge, label = "Badge", color = GreenPrimary)
                                StatItem(value = "${state.totalLaporan}", label = "Laporan", color = GreenPrimary)
                            }
                        }
                    }

                    // Progress badge berikutnya
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = StatusWaspada, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Badge berikutnya: ${state.nextBadge}", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                    }

                    // Riwayat Laporan header
                    item {
                        Text(
                            "Riwayat Laporan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    // Riwayat list
                    if (state.riwayatList.isEmpty()) {
                        item {
                            Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Belum ada laporan. Mulai deteksi retakan dari tab Deteksi!",
                                    modifier = Modifier.padding(20.dp),
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(state.riwayatList) { item ->
                            RiwayatCard(
                                namaLokasi = item.namaLokasi,
                                status = item.status,
                                terverifikasi = item.terverifikasi,
                                waktu = item.createdAt.take(10),
                                onClick = { onRiwayatClick(item.id) }
                            )
                        }
                    }

                    // Logout
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.signOut { onSignOut() } },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusBahaya),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Keluar")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun RiwayatCard(
    namaLokasi: String,
    status: String,
    terverifikasi: Int,
    waktu: String,
    onClick: () -> Unit
) {
    val (statusColor, statusBg) = when (status) {
        "BAHAYA" -> StatusBahaya to StatusBahayaBg
        "WASPADA" -> StatusWaspada to StatusWaspadaBg
        else -> StatusAman to StatusAmanBg
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(namaLokasi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(11.dp))
                    Text(" $terverifikasi", fontSize = 11.sp, color = GreenPrimary)
                    Text(" · $waktu", fontSize = 11.sp, color = TextSecondary)
                }
            }
            Surface(color = statusBg, shape = RoundedCornerShape(4.dp)) {
                Text(
                    status,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}
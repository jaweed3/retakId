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
import com.unidagontor.retakid.data.risk.RiskFactor
import com.unidagontor.retakid.data.risk.RiskFactorReport
import com.unidagontor.retakid.data.risk.RiskLabel
import com.unidagontor.retakid.ui.theme.*
import com.unidagontor.retakid.ui.viewmodel.DeteksiStage
import com.unidagontor.retakid.ui.viewmodel.DeteksiState
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
                AnalyzingView(isAnalyzingContext = state.isAnalyzingContext)
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
                ResultView(
                    state     = state,
                    onProceed = { vm.proceedToReport() },
                    onRetry   = { vm.reset() }
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
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                } else {
                                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                        // Paksa SOFTWARE agar getPixels() bekerja di TFLite
                                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                    }
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
fun AnalyzingView(isAnalyzingContext: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (isAnalyzingContext) "Mengumpulkan data lingkungan..." else "Menganalisis gambar (ML)...",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Menjalankan: AI Visual · Cuaca · Elevasi · Lereng · Tanah",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ResultView(
    state    : DeteksiState,
    onProceed: () -> Unit,
    onRetry  : () -> Unit
) {
    val result     = state.detectionResult
    val image      = state.capturedImage
    val report     = state.riskReport
    val dataStatus = state.dataStatus

    val (statusColor, statusTitle, statusDesc) = when (result) {
        DetectionResult.BAHAYA  -> Triple(StatusBahaya,  "BAHAYA",  "Risiko tinggi! Retakan kritis terdeteksi.")
        DetectionResult.WASPADA -> Triple(StatusWaspada, "WASPADA", "Risiko sedang. Pantau berkala saat hujan.")
        else                    -> Triple(StatusAman,    "AMAN",    "Risiko rendah. Kondisi masih terkendali.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hasil Analisis Gabungan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("ML · Cuaca · Elevasi · Lereng · Tanah", fontSize = 12.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(12.dp))

        // ── Foto ──────────────────────────────────────────────────────────
        if (image != null) {
            Image(
                bitmap             = image.asImageBitmap(),
                contentDescription = "Foto retakan",
                modifier           = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Status Utama ──────────────────────────────────────────────────
        Surface(
            color    = statusColor.copy(alpha = 0.12f),
            shape    = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier            = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(statusTitle, color = statusColor, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(statusDesc, color = TextPrimary, fontSize = 13.sp, textAlign = TextAlign.Center)

                // Risk score bar
                if (report != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val pct = report.finalScore.toFloat().coerceIn(0f, 1f)
                    Text("Skor Risiko: ${(pct * 100).toInt()}%", fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress         = { pct },
                        modifier         = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color            = statusColor,
                        trackColor       = statusColor.copy(alpha = 0.2f)
                    )
                    if (report.isUpgraded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("⚠️ Faktor lingkungan meningkatkan risiko", fontSize = 11.sp, color = StatusWaspada)
                    } else if (report.isDowngraded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("✅ Faktor lingkungan menurunkan risiko", fontSize = 11.sp, color = StatusAman)
                    }
                }
            }
        }

        // ── Rincian per Faktor ────────────────────────────────────────────
        if (report != null && report.factors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color    = Color(0xFFF8F8F8),
                shape    = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Kontribusi Faktor Risiko", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    report.factors.forEach { fc ->
                        val fColor = when (fc.riskLabel) {
                            RiskLabel.RENDAH       -> StatusAman
                            RiskLabel.SEDANG       -> StatusWaspada
                            RiskLabel.TINGGI       -> StatusBahaya
                            RiskLabel.SANGAT_TINGGI -> StatusBahaya
                        }
                        val icon = when (fc.factor) {
                            RiskFactor.ML        -> Icons.Default.CameraAlt
                            RiskFactor.SLOPE     -> Icons.Default.Terrain
                            RiskFactor.RAIN      -> Icons.Default.WaterDrop
                            RiskFactor.ELEVATION -> Icons.Default.Landscape
                            RiskFactor.SOIL      -> Icons.Default.Layers
                        }
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = fColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(fc.factor.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(fc.rawValue, fontSize = 11.sp, color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress   = { fc.score.toFloat().coerceIn(0f, 1f) },
                                    modifier   = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                    color      = fColor,
                                    trackColor = fColor.copy(alpha = 0.15f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(fc.weight * 100).toInt()}%", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // ── Cuaca & Status Data ───────────────────────────────────────────
        val weather = state.combinedResult?.weather
        if (weather != null || dataStatus != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (weather != null) {
                    Surface(
                        color    = Color(0xFFE8F5E9),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("${weather.condition.emoji} ${weather.condition.label}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("${weather.temperatureCelsius.toInt()}°C · ${weather.rain} mm hujan", fontSize = 11.sp, color = TextSecondary)
                            Text(weather.condition.riskNote, fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
                if (dataStatus != null) {
                    Surface(
                        color    = Color(0xFFF3F3F3),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Data Terkumpul", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(dataStatus.summaryText, fontSize = 11.sp, color = GreenPrimary)
                        }
                    }
                }
            }
        }

        // ── Peringatan Darurat ────────────────────────────────────────────
        if (result == DetectionResult.BAHAYA) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(color = StatusBahaya, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DARURAT: Segera menjauh & informasikan warga!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick  = onProceed,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            shape    = RoundedCornerShape(12.dp)
        ) { Text("Lanjut ke Laporan", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Coba Lagi", color = TextSecondary)
        }
    }
}

@Composable
fun ReportFormView(
    location    : com.unidagontor.retakid.data.location.LocationData?,
    isSubmitting: Boolean,
    onSubmit    : (String, String) -> Unit
) {
    var locName by remember { mutableStateOf("") }
    var note    by remember { mutableStateOf("") }

    // Warna input field mengikuti tema hijau
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = GreenPrimary,
        unfocusedBorderColor = GreenPrimary.copy(alpha = 0.4f),
        focusedLabelColor    = GreenPrimary,
        unfocusedLabelColor  = TextSecondary,
        cursorColor          = GreenPrimary,
        focusedLeadingIconColor   = GreenPrimary,
        unfocusedLeadingIconColor = TextSecondary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenPrimary)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Text(
                    "Form Laporan",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                Text(
                    "Isi data lokasi dan kondisi retakan",
                    fontSize = 13.sp,
                    color    = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // ── Body ──────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

            // Lokasi GPS
            Surface(
                color    = GreenSurface,
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier          = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Lokasi GPS", fontSize = 11.sp, color = GreenPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            text     = if (location != null)
                                "%.5f, %.5f".format(location.latitude, location.longitude)
                            else "Mendeteksi lokasi...",
                            fontSize = 13.sp,
                            color    = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nama Lokasi
            OutlinedTextField(
                value         = locName,
                onValueChange = { locName = it },
                label         = { Text("Nama Lokasi *") },
                placeholder   = { Text("Contoh: Lereng Utara RT 02") },
                leadingIcon   = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                colors        = fieldColors,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Catatan
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                label         = { Text("Catatan Tambahan") },
                placeholder   = { Text("Ceritakan kondisi retakan secara detail...") },
                leadingIcon   = { Icon(Icons.Default.Edit, contentDescription = null) },
                colors        = fieldColors,
                modifier      = Modifier.fillMaxWidth().height(130.dp),
                shape         = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Tombol kirim
            Button(
                onClick  = { onSubmit(locName, note) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled  = !isSubmitting && locName.isNotEmpty(),
                colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape    = RoundedCornerShape(12.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mengirim...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kirim Laporan", color = Color.White, fontWeight = FontWeight.Bold)
                }
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
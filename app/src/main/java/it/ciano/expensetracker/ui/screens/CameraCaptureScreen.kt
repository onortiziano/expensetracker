package it.ciano.expensetracker.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.ocr.ReceiptImageValidator
import it.ciano.expensetracker.data.ocr.ReceiptOcrEngine
import it.ciano.expensetracker.data.ocr.ReceiptParser
import it.ciano.expensetracker.data.ocr.ReceiptStorage
import androidx.activity.compose.BackHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Schermata di acquisizione ricevuta tramite CameraX.
 *
 * Risolve il bug "foto verdi" su MIUI sostituendo TakePicture di sistema con CameraX.
 *
 * MODALITÀ DEBUG: dopo lo scatto, invece di proseguire in automatico, questa schermata
 * esegue l'OCR e mostra A SCHERMO l'esito di ogni anello del flusso (file, decodifica,
 * testo estratto, parser). In questo modo l'utente può leggere cosa succede e segnalare
 * l'anello che si blocca, senza bisogno di adb/logcat su un PC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var capturedPath by remember { mutableStateOf<String?>(null) }
    var debugReport by remember { mutableStateOf<String?>(null) }
    var debugRunning by remember { mutableStateOf(false) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.str_camera_permesso_negato),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }

    BackHandler { navController.popBackStack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_camera_titolo)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.str_torna_indietro)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.str_camera_permesso_negato),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.str_camera_riprova))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (capturedPath != null && debugReport == null) {
                // Anteprima della foto acquisita
                val path = capturedPath!!
                val bmp = remember(path) { BitmapFactory.decodeFile(path) }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = stringResource(R.string.str_scatta_ricevuta),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { capturedPath = null; debugReport = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.str_camera_riprova))
                    }
                    Button(
                        onClick = {
                            if (debugRunning) return@Button
                            debugRunning = true
                            val photoPath = capturedPath
                            if (photoPath != null) {
                                debugReport = runOcrDebug(photoPath, context)
                            }
                            debugRunning = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (debugRunning) "..." else stringResource(R.string.str_elaborazione_ocr))
                    }
                }
            } else if (debugReport != null) {
                // REPORT DI DIAGNOSI A SCHERMO dell'OCR
                OcrDebugReport(
                    report = debugReport!!,
                    onRetake = {
                        capturedPath = null
                        debugReport = null
                    },
                    onUsePhoto = {
                        val path = capturedPath
                        if (path != null) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("receipt_path", path)
                            navController.popBackStack()
                        }
                    }
                )
            } else {
                // Anteprima live della fotocamera
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply { setOnClickListener { } }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    update = { previewView ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val imageCap = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .build()
                                imageCapture.value = imageCap
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCap
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("CameraX", "Errore bind camera: ${e.message}", e)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                )

                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            val capture = imageCapture.value
                            if (capture == null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.str_camera_errore_acquisizione),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            val file = ReceiptStorage.createReceiptFile(
                                context,
                                System.currentTimeMillis()
                            )
                            val outputOptions =
                                ImageCapture.OutputFileOptions.Builder(file).build()
                            capture.takePicture(
                                outputOptions,
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(
                                        outputFileResults: ImageCapture.OutputFileResults
                                    ) {
                                        val path = file.absolutePath
                                        if (ReceiptImageValidator.isValidReceiptImage(path)) {
                                            capturedPath = path
                                            debugReport = null
                                        } else {
                                            file.delete()
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.str_camera_errore_acquisizione),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.str_camera_errore_acquisizione),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.str_camera_scatta),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun runOcrDebug(path: String, context: android.content.Context): String {
    val sb = StringBuilder()
    sb.appendLine("=== DIAGNOSI OCR ===")
    sb.appendLine()

    val file = java.io.File(path)
    sb.appendLine("[1] FILE")
    sb.appendLine("  esiste: ${file.exists()}")
    sb.appendLine("  dimensione: ${file.length()} bytes")
    sb.appendLine("  path: $path")

    sb.appendLine()
    sb.appendLine("[2] DECODIFICA")
    var bitmap = ReceiptImageValidator.decodeFile(path)
    sb.appendLine("  decodificata: ${bitmap != null}")
    bitmap?.let {
        sb.appendLine("  dimensioni: ${it.width}x${it.height}")
    }
    sb.appendLine("  valida (variazione colore): ${ReceiptImageValidator.isValidReceiptImage(path)}")

    sb.appendLine()
    sb.appendLine("[3] OCR ML KIT")
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        ReceiptStorage.AUTHORITY,
        file
    )
    val text = runBlockingRecognize(uri, context)
    if (text.isNullOrBlank()) {
        sb.appendLine("  testo estratto: VUOTO / NULL")
        if (bitmap != null) bitmap.recycle()
        return sb.toString()
    }
    sb.appendLine("  lunghezza testo: ${text.length}")
    sb.appendLine("  PRIME RIGHE OCR estratti:")
    text.lines().take(8).forEach { line ->
        sb.appendLine("    | $line")
    }

    sb.appendLine()
    sb.appendLine("[4] PARSER")
    val parsed = ReceiptParser.parse(text)
    sb.appendLine("  importo: ${parsed.amount ?: "NON TROVATO"}")
    sb.appendLine("  data: ${parsed.date?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ITALIAN).format(java.util.Date(it)) } ?: "NON TROVATA"}")
    sb.appendLine("  titolo: ${parsed.title ?: "NON TROVATO"}")
    sb.appendLine("  categoria: ${parsed.suggestedCategoryName ?: "NON TROVATA"}")

    if (bitmap != null) bitmap.recycle()
    return sb.toString()
}

private fun runBlockingRecognize(
    uri: android.net.Uri,
    context: android.content.Context
): String? {
    return kotlinx.coroutines.runBlocking {
        ReceiptOcrEngine.recognize(uri, context)
    }
}

@Composable
private fun OcrDebugReport(
    report: String,
    onRetake: () -> Unit,
    onUsePhoto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Report di diagnostica OCR (leggilo e comunicamelo)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = report,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.str_camera_riprova))
            }
            Button(onClick = onUsePhoto, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.str_camera_usa_foto))
            }
        }
    }
}

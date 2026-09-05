package it.ciano.expensetracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.ocr.ReceiptImageValidator
import it.ciano.expensetracker.data.ocr.ReceiptStorage
import java.util.concurrent.Executors

/**
 * Schermata di acquisizione ricevuta tramite CameraX.
 *
 * Risolve il bug "foto verdi" su MIUI sostituendo TakePicture di sistema con CameraX.
 * Dopo lo scatto, l'utente conferma la foto e il percorso viene restituito
 * al chiamante tramite savedStateHandle ("receipt_path").
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
            if (capturedPath != null) {
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
                        onClick = { capturedPath = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.str_camera_riprova))
                    }
                    Button(
                        onClick = {
                            val photoPath = capturedPath
                            if (photoPath != null) {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("receipt_path", photoPath)
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.str_elaborazione_ocr))
                    }
                }
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

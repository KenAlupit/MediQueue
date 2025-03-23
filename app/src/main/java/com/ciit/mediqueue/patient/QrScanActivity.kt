package com.ciit.mediqueue.patient

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.ciit.mediqueue.R
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScanActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var scanner: BarcodeScanner
    private var isScanning = false
    private val scanDelayHandler = Handler(Looper.getMainLooper())
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firestore
        db = FirebaseFirestore.getInstance()

        // Initialize cameraExecutor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Retrieve queue and patient ID from SharedPreferences
        val sharedPreferences = getSharedPreferences("MediQueuePrefs", MODE_PRIVATE)
        val queueId = sharedPreferences.getString("QUEUE_ID", null)
        val patientId = sharedPreferences.getString("PATIENT_ID", null)

        if (queueId != null && patientId != null) {
            // Check if the patient is still in the queue
            db.collection("queues").document(queueId).get()
                .addOnSuccessListener { document ->
                    if (document.exists() && (document.getString("status") == "Pending" || document.getString("status") == "Serving")) {
                        // Redirect to QueueStatusActivity
                        val intent = Intent(this, QueueStatusActivity::class.java).apply {
                            putExtra("QUEUE_ID", queueId)
                            putExtra("PATIENT_ID", patientId)
                        }
                        startActivity(intent)
                        finish() // Close current activity to prevent going back
                    } else {
                        // Queue status is not "Pending", proceed with QR scanning
                        showQRScanner()
                    }
                }
                .addOnFailureListener {
                    // Handle Firestore error (e.g., network issues)
                    showQRScanner()
                }
        } else {
            // No saved queue data, proceed with QR scanning
            showQRScanner()
        }
    }

    private fun showQRScanner() {
        setContentView(R.layout.activity_qr_scan)

        val uploadButton: Button = findViewById(R.id.uploadButton)
        uploadButton.setOnClickListener {
            openFilePicker()
        }

        scanner = BarcodeScanning.getClient()
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = findViewById<PreviewView>(R.id.cameraPreview).surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e("QrScanActivity", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            isScanning = true

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        handleScannedResult(barcode)
                    }
                }
                .addOnFailureListener {
                    Log.e("QrScanActivity", "QR Code scanning failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                    // Add a delay before allowing another scan
                    scanDelayHandler.postDelayed({ isScanning = false }, 2000)
                }
        }
    }

    private fun handleScannedResult(barcode: Barcode) {
        val qrCodeValue = barcode.rawValue
        if (!qrCodeValue.isNullOrEmpty()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("qr_codes").whereEqualTo("qr_code", qrCodeValue).get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        // QR code is valid, navigate to another activity
                        val intent = Intent(this, PatientTypeSelectionActivity::class.java)
                        intent.putExtra("qrCodeValue", qrCodeValue)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("QrScanActivity", "Error checking QR code", e)
                    Toast.makeText(this, "Error checking QR code", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    processUploadedImage(uri)
                }
            }
        }

    private fun processUploadedImage(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            isScanning = true
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        handleScannedResult(barcode)
                    }
                }
                .addOnFailureListener {
                    Log.e("QrScanActivity", "QR Code scanning failed from image", it)
                }
                .addOnCompleteListener {
                    isScanning = false
                }
        } catch (e: Exception) {
            Log.e("QrScanActivity", "Error processing uploaded image", e)
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
package com.ciit.mediqueue.receptionist

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class ReceptionistDashboardActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var showQrButton: Button
    private val qrCodesCollection = "qr_codes" // Firestore Collection Name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receptionist_dashboard)

        firestore = FirebaseFirestore.getInstance()
        showQrButton = findViewById(R.id.showQrButton)

        showQrButton.setOnClickListener {
            fetchAndShowQRCode()
        }

        checkOrGenerateQRCode()
    }

    private fun checkOrGenerateQRCode() {
        val startOfDay = getStartOfDayTimestamp()

        // Query Firestore for QR codes created today
        firestore.collection(qrCodesCollection)
            .whereGreaterThanOrEqualTo("date_created", startOfDay)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    generateUniqueQRCode()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error checking QR Code: ", e)
            }
    }

    private fun generateUniqueQRCode() {
        firestore.collection(qrCodesCollection)
            .get()
            .addOnSuccessListener { documents ->
                val existingCodes = documents.documents.mapNotNull { it.getString("qr_code") }
                var newCode: String

                do {
                    newCode = (1000..9999).random().toString() // Generate a 4-digit random code
                } while (existingCodes.contains(newCode)) // Ensure uniqueness

                // Store the new QR Code in Firestore
                val qrData = hashMapOf(
                    "date_created" to Timestamp.now(), // Store as Firestore Timestamp
                    "qr_code" to newCode
                )

                firestore.collection(qrCodesCollection)
                    .add(qrData)
            }
    }

    private fun fetchAndShowQRCode() {
        val startOfDay = getStartOfDayTimestamp()

        firestore.collection(qrCodesCollection)
            .whereGreaterThanOrEqualTo("date_created", startOfDay)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val qrCode = documents.documents[0].getString("qr_code") ?: "N/A"
                    showQRCodePopup(qrCode)
                } else {
                    Toast.makeText(this, "No QR Code found for today.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error fetching QR Code: ", e)
            }
    }

    private fun showQRCodePopup(qrCode: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_code, null)
        val qrImageView = dialogView.findViewById<ImageView>(R.id.qrImageView)
        val downloadButton = dialogView.findViewById<Button>(R.id.downloadButton)

        val qrBitmap = generateQRCodeBitmap(qrCode)
        qrImageView.setImageBitmap(qrBitmap)

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()

        downloadButton.setOnClickListener {
            saveQRCodeToGallery(qrBitmap)
            Toast.makeText(this, "QR Code saved to gallery!", Toast.LENGTH_SHORT).show()
        }

        alertDialog.show()
    }

    private fun generateQRCodeBitmap(text: String): Bitmap? {
        val qrCodeWriter = QRCodeWriter()
        return try {
            val bitMatrix: BitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 500, 500)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: WriterException) {
            Log.e("QR Generation", "Error generating QR Code", e)
            null
        }
    }

private fun saveQRCodeToGallery(bitmap: Bitmap?) {
    bitmap?.let {
        val filename = "QR_Code_${System.currentTimeMillis()}.png"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        } catch (e: IOException) {
            Log.e("QR Save", "Error saving QR Code", e)
        }
    }
}

    private fun getStartOfDayTimestamp(): Timestamp {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return Timestamp(calendar.time)
    }
}

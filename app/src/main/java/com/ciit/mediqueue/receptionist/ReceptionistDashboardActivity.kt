package com.ciit.mediqueue.receptionist

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ReceptionistDashboardActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var qrCodeTextView: TextView
    private val qrCodesCollection = "qr_codes" // Collection Name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receptionist_dashboard)

        firestore = FirebaseFirestore.getInstance()
        qrCodeTextView = findViewById(R.id.qrCodeTextView)

        checkOrGenerateQRCode()
    }

    private fun checkOrGenerateQRCode() {
        val startOfDay = getStartOfDayTimestamp()

        // Query Firestore for QR codes created today
        firestore.collection(qrCodesCollection)
            .whereGreaterThanOrEqualTo("date_created", startOfDay)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // QR Code exists, show it
                    val existingQRCode = documents.documents[0].getString("qr_code") ?: "N/A"
                    qrCodeTextView.text = "Today's QR Code: $existingQRCode"
                } else {
                    // Generate a new unique QR Code
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
                    .addOnSuccessListener {
                        qrCodeTextView.text = "Today's QR Code: $newCode"
                        Log.d("Firebase", "QR Code stored successfully.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firebase", "Error storing QR Code: ", e)
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

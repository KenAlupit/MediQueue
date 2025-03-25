package com.ciit.mediqueue.receptionist

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

data class PatientQueueItem(val patientId: String, val fullName: String, val numberInLine: Long)

class ReceptionistDashboardActivity : AppCompatActivity() {

    // Firebase authentication instance
    private lateinit var firestore: FirebaseFirestore

    // UI elements
    private lateinit var currentlyServingTextView: TextView
    private lateinit var previousPatientTextView: TextView
    private lateinit var nextPatientTextView: TextView
    private lateinit var patientsInQueueListView: ListView
    private lateinit var finishedAppointmentsListView: ListView
    private lateinit var callNextPatientButton: Button
    private lateinit var showQrButton: Button

    // Firestore collection names
    private val qrCodesCollection = "qr_codes"
    private val queuesCollection = "queues"
    private val patientsCollection = "patients"
    private val medicalVisitsCollection = "medical_visits"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receptionist_dashboard)

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()

        // Initialize UI elements
        currentlyServingTextView = findViewById(R.id.currentlyServingTextView)
        previousPatientTextView = findViewById(R.id.previousPatientTextView)
        nextPatientTextView = findViewById(R.id.nextPatientTextView)
        patientsInQueueListView = findViewById(R.id.patientsInQueueListView)
        finishedAppointmentsListView = findViewById(R.id.finishedAppointmentsListView)
        callNextPatientButton = findViewById(R.id.callNextPatientButton)

        showQrButton = findViewById(R.id.showQrButton)

        // Set up button click listeners
        callNextPatientButton.setOnClickListener { callNextPatient() }
        showQrButton.setOnClickListener { fetchAndShowQRCode() }

        // Fetch initial queue data and check or generate QR code
        fetchQueueData()
        checkOrGenerateQRCode()
    }

    // Check if a QR code exists for today, if not, generate a new one
    private fun checkOrGenerateQRCode() {
        val startOfDay = getStartOfDayTimestamp()

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

    // Generate a unique QR code and store it in Firestore
    private fun generateUniqueQRCode() {
        firestore.collection(qrCodesCollection)
            .get()
            .addOnSuccessListener { documents ->
                val existingCodes = documents.documents.mapNotNull { it.getString("qr_code") }
                var newCode: String

                do {
                    newCode = (1000..9999).random().toString() // Generate a 4-digit random code
                } while (existingCodes.contains(newCode)) // Ensure uniqueness

                val qrData = hashMapOf(
                    "date_created" to Timestamp.now(),
                    "qr_code" to newCode
                )

                firestore.collection(qrCodesCollection)
                    .add(qrData)
            }
    }

    // Fetch and display the QR code for today
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

    // Show a popup with the QR code
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

    // Generate a Bitmap for the QR code
    private fun generateQRCodeBitmap(text: String): Bitmap? {
        val qrCodeWriter = QRCodeWriter()
        return try {
            val bitMatrix: BitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 500, 500)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] =
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
            }
            bitmap
        } catch (e: WriterException) {
            Log.e("QR Generation", "Error generating QR Code", e)
            null
        }
    }

    // Save the QR code Bitmap to the gallery
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

    // Get the start of the day as a Firestore Timestamp
    private fun getStartOfDayTimestamp(): Timestamp {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return Timestamp(calendar.time)
    }

    // Fetch queue data and update the UI
    private fun fetchQueueData() {
        firestore.collection(queuesCollection)
            .orderBy("number_in_line")
            .addSnapshotListener { queueDocuments, error ->
                if (error != null) {
                    Log.e("Firebase", "Error fetching queue data", error)
                    Toast.makeText(this, "Error fetching queue data", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (queueDocuments == null) {
                    updateQueueUI(null, null, null)
                    return@addSnapshotListener
                }

                val patientsInQueue = mutableListOf<PatientQueueItem>()
                val finishedAppointments = mutableListOf<String>()
                var currentlyServing: String? = null
                var previousPatient: String? = null
                var nextPatient: String? = null

                val queueList = queueDocuments.documents

                if (queueList.isEmpty()) {
                    updateQueueUI(null, null, null)
                    return@addSnapshotListener
                }

                val patientDataMap = mutableMapOf<String, String>()

                // First, fetch all patient names in one batch to avoid multiple Firestore queries
                val patientIds = queueList.mapNotNull { it.getString("patient_id") }
                firestore.collection(patientsCollection)
                    .whereIn(FieldPath.documentId(), patientIds)
                    .get()
                    .addOnSuccessListener { patientsSnapshot ->
                        for (patientDoc in patientsSnapshot.documents) {
                            val patientId = patientDoc.id
                            val patientName = patientDoc.getString("full_name") ?: "Unknown"
                            patientDataMap[patientId] = patientName
                        }

                        // Process the queue with the retrieved patient names
                        for ((index, queueDocument) in queueList.withIndex()) {
                            val patientId = queueDocument.getString("patient_id") ?: continue
                            val patientNumber = queueDocument.getLong("number_in_line") ?: 0
                            val status = queueDocument.getString("status") ?: "Unknown"
                            val patientName = patientDataMap[patientId] ?: "Unknown"

                            if (status == "Finished") {
                                finishedAppointments.add(patientName)
                            } else {
                                patientsInQueue.add(PatientQueueItem(patientId, patientName, patientNumber))
                            }

                            if (status == "Serving") {
                                currentlyServing = patientName
                                nextPatient = if (index < queueList.size - 1) {
                                    val nextPatientId = queueList[index + 1].getString("patient_id") ?: ""
                                    patientDataMap[nextPatientId]
                                } else {
                                    "None"
                                }
                            }
                        }

                        // If no "Serving" patient is found, set the next available patient
                        if (currentlyServing == null && patientsInQueue.isNotEmpty()) {
                            currentlyServing = "${patientsInQueue[0].numberInLine}: ${patientsInQueue[0].fullName}"
                            nextPatient = if (patientsInQueue.size > 1) {
                                "${patientsInQueue[1].numberInLine}: ${patientsInQueue[1].fullName}"
                            } else {
                                "None"
                            }
                        }

                        // Update UI
                        updateQueueUI(currentlyServing, previousPatient, nextPatient)

                        // Populate lists
                        patientsInQueueListView.adapter = PatientQueueAdapter(this, patientsInQueue, firestore)
                        finishedAppointmentsListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, finishedAppointments)
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firebase", "Error fetching patient data", e)
                        Toast.makeText(this, "Error fetching patient data", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    // Update the queue status UI
    private fun updateQueueUI(currentlyServing: String?, previousPatient: String?, nextPatient: String?) {
        currentlyServingTextView.text = getString(R.string.currently_serving, currentlyServing ?: "None")
        previousPatientTextView.text = getString(R.string.previous_patient, previousPatient ?: "None")
        nextPatientTextView.text = getString(R.string.next_patient, nextPatient ?: "None")
    }

    // Call the next patient in the queue
    private fun callNextPatient() {
        val queueRef = firestore.collection(queuesCollection)
        val medicalVisitsRef = firestore.collection(medicalVisitsCollection)

        // Get the currently serving patient
        queueRef.whereEqualTo("status", "Serving").get()
            .addOnSuccessListener { servingSnapshot ->
                if (!servingSnapshot.isEmpty) {
                    val servingDoc = servingSnapshot.documents[0]
                    val servingRef = servingDoc.reference

                    // Mark the currently serving patient as "Finished"
                    val updateBatch = firestore.batch()
                    updateBatch.update(servingRef, mapOf("status" to "Finished", "number_in_line" to 0, "last_updated" to Timestamp.now()))

                    // Update the corresponding medical visit to "Finished"
                    medicalVisitsRef.whereEqualTo("patient_id", servingDoc.getString("patient_id"))
                        .limit(1)
                        .get()
                        .addOnSuccessListener { visitSnapshot ->
                            if (!visitSnapshot.isEmpty) {
                                val visitDoc = visitSnapshot.documents[0]
                                updateBatch.update(visitDoc.reference, mapOf("status" to "Finished", "last_updated" to Timestamp.now()))
                            }

                            // Commit the update batch
                            updateBatch.commit().addOnSuccessListener {
                                // Update the number_in_line for remaining patients and set next to serving
                                updateAndServeNextPatient()
                            }.addOnFailureListener { e ->
                                Log.e("Firebase", "Error updating medical visit", e)
                                Toast.makeText(this, "Error updating medical visit", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firebase", "Error fetching medical visit", e)
                            Toast.makeText(this, "Error fetching medical visit", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // If no patient is currently serving, proceed to update and serve the next
                    updateAndServeNextPatient()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error fetching serving patient", e)
                Toast.makeText(this, "Error fetching serving patient", Toast.LENGTH_SHORT).show()
            }
    }

    // Update the number_in_line for remaining patients and set the next patient to serving
    private fun updateAndServeNextPatient() {
        val queueRef = firestore.collection(queuesCollection)

        // Decrement number_in_line for all remaining patients
        queueRef.get().addOnSuccessListener { allQueueSnapshot ->
            val updateBatch = firestore.batch()
            for (doc in allQueueSnapshot.documents) {
                val currentNumber = doc.getLong("number_in_line") ?: 0

                if (currentNumber > 0) {
                    val newNumber = currentNumber - 1
                    updateBatch.update(doc.reference, "number_in_line", newNumber)

                    if (newNumber == 1L) {
                        // Mark as "Serving" if number_in_line reaches 1
                        updateBatch.update(doc.reference, "status", "Serving")
                        updateBatch.update(doc.reference, "last_updated", Timestamp.now())
                    }
                }
            }

            // Commit the batch update
            updateBatch.commit().addOnSuccessListener {
                // Find the next patient in line (number_in_line = 1) and update UI
                queueRef.whereEqualTo("number_in_line", 1).limit(1).get()
                    .addOnSuccessListener { nextSnapshot ->
                        if (!nextSnapshot.isEmpty) {
                            val nextDoc = nextSnapshot.documents[0]
                            val nextPatientName = nextDoc.getString("patient_name") ?: "Unknown"

                            currentlyServingTextView.text = getString(R.string.currently_serving, nextPatientName)

                            Toast.makeText(this, "Next patient called successfully", Toast.LENGTH_SHORT).show()
                            fetchQueueData() // Refresh UI
                        } else {
                            Toast.makeText(this, "No more patients in queue", Toast.LENGTH_SHORT).show()
                            currentlyServingTextView.text = getString(R.string.currently_serving, "None")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firebase", "Error fetching next patient", e)
                        Toast.makeText(this, "Error fetching next patient", Toast.LENGTH_SHORT).show()
                    }
            }.addOnFailureListener { e ->
                Log.e("Firebase", "Error committing batch", e)
                Toast.makeText(this, "Error updating queue", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Log.e("Firebase", "Error fetching all queue documents", e)
            Toast.makeText(this, "Error fetching all queue documents", Toast.LENGTH_SHORT).show()
        }
    }
}
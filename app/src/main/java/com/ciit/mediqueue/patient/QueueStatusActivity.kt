package com.ciit.mediqueue.patient

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.ciit.mediqueue.R
import com.ciit.mediqueue.patient.QrScanActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class QueueStatusActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var queueStatusTextView: TextView
    private lateinit var patientId: String
    private lateinit var queueId: String
    private lateinit var cancelQueueButton: Button
    private lateinit var copyPatientIdButton: Button
    private lateinit var exportPatientIdImageButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue_status)

        db = FirebaseFirestore.getInstance()
        queueStatusTextView = findViewById(R.id.queueStatusTextView)
        cancelQueueButton = findViewById(R.id.cancelQueueButton)
        copyPatientIdButton = findViewById(R.id.copyPatientIdButton)
        exportPatientIdImageButton = findViewById(R.id.exportPatientIdImageButton)

        patientId = intent.getStringExtra("PATIENT_ID") ?: return
        queueId = intent.getStringExtra("QUEUE_ID") ?: return

        getQueuePosition()

        cancelQueueButton.setOnClickListener {
            showCancelConfirmationDialog()
        }

        copyPatientIdButton.setOnClickListener {
            copyPatientIdToClipboard()
        }

        exportPatientIdImageButton.setOnClickListener {
            exportPatientIdImage()
        }
    }

    private fun getQueuePosition() {
        db.collection("queues")
            .document(queueId)
            .addSnapshotListener { document, error ->
                if (error != null || document == null || !document.exists()) {
                    queueStatusTextView.text = "Error retrieving queue status."
                    return@addSnapshotListener
                }

                val numberInLine = document.getLong("number_in_line") ?: 0
                val status = document.getString("status") ?: "Unknown"

                queueStatusTextView.text = "Your position: $numberInLine\nStatus: $status\nPatient ID: $patientId"
            }
    }

    private fun showCancelConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_cancel_queue, null)
        val patientIdInput = dialogView.findViewById<EditText>(R.id.patientIdInput)

        AlertDialog.Builder(this)
            .setTitle("Cancel Queue")
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val inputId = patientIdInput.text.toString()
                if (inputId == patientId) {
                    cancelQueue()
                } else {
                    showToast("Patient ID does not match.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cancelQueue() {
        val batch = db.batch()

        // Update queue status to "Cancelled"
        val queueRef = db.collection("queues").document(queueId)
        batch.update(queueRef, "status", "Cancelled")

        // Update medical visit status to "Cancelled"
        val visitRef = db.collection("medical_visits").whereEqualTo("patient_id", patientId).limit(1)
        visitRef.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val visitDoc = documents.documents[0].reference
                batch.update(visitDoc, "status", "Cancelled")
            }

            // Commit the batch update
            batch.commit().addOnSuccessListener {
                val sharedPreferences = getSharedPreferences("MediQueuePrefs", MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    remove("QUEUE_ID")
                    remove("PATIENT_ID")
                    apply()
                }
                val intent = Intent(this, QrScanActivity::class.java)
                startActivity(intent)
                finish()
            }.addOnFailureListener {
                showToast("Error cancelling queue.")
            }
        }.addOnFailureListener {
            showToast("Error retrieving medical visit.")
        }
    }

    private fun copyPatientIdToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Patient ID", patientId)
        clipboard.setPrimaryClip(clip)
        showToast("Patient ID copied to clipboard.")
    }

    private fun exportPatientIdImage() {
        val bitmap = createBitmapFromText("Patient ID: $patientId")
        saveBitmapToDownloads(bitmap, "PatientID_$patientId.png")
        showToast("Patient ID image exported to Downloads.")
    }

    private fun saveBitmapToDownloads(bitmap: Bitmap, fileName: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        } catch (e: IOException) {
            e.printStackTrace()
            showToast("Error saving image.")
        } finally {
            outputStream?.close()
        }
    }

    private fun createBitmapFromText(text: String): Bitmap {
        val programName = "MediQueue"
        val combinedText = "$programName\n$text"
        val paint = TextView(this).paint
        val width = paint.measureText(combinedText).toInt()
        val height = paint.fontMetricsInt.run { bottom - top } * 2 // Adjust height for two lines of text
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE) // Set background color to white
        canvas.drawText(programName, 0f, -paint.fontMetricsInt.top.toFloat(), paint)
        canvas.drawText(text, 0f, -paint.fontMetricsInt.top.toFloat() + paint.fontMetricsInt.run { bottom - top }, paint)
        return bitmap
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
package com.ciit.mediqueue.patient

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ciit.mediqueue.MainActivity
import com.ciit.mediqueue.R
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.content.edit
import androidx.core.graphics.createBitmap

class QueueStatusActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var queueStatusTextView: TextView
    private lateinit var patientId: String
    private lateinit var queueId: String
    private lateinit var cancelQueueButton: Button
    private lateinit var copyPatientIdButton: Button
    private lateinit var exportPatientIdImageButton: Button
    private val requestCodePostNotifications = 1

    companion object {
        const val CHANNEL_ID = "queue_status_channel"
        const val NOTIFICATION_ID = 1
    }

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
        createNotificationChannel()

        cancelQueueButton.setOnClickListener {
            showCancelConfirmationDialog()
        }

        copyPatientIdButton.setOnClickListener {
            copyPatientIdToClipboard()
        }

        exportPatientIdImageButton.setOnClickListener {
            exportPatientIdImage()
        }

        onBackPressedDispatcher.addCallback(this) {
            val intent = Intent(this@QueueStatusActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun getQueuePosition() {
        db.collection("queues")
            .document(queueId)
            .addSnapshotListener { document, error ->
                if (error != null || document == null || !document.exists()) {
                    queueStatusTextView.text = getString(R.string.error_retrieving_queue_status)
                    return@addSnapshotListener
                }

                val numberInLine = document.getLong("number_in_line") ?: 0
                val status = document.getString("status") ?: "Unknown"

                queueStatusTextView.text = getString(R.string.queue_status, numberInLine, status, patientId)
                if (status == "Serving") {
                    checkAndRequestNotificationPermission()
                }
            }
    }

    private fun checkAndRequestNotificationPermission() {
        val sharedPreferences = getSharedPreferences("MediQueuePrefs", MODE_PRIVATE)

        val allPrefs = sharedPreferences.all
        for ((key, value) in allPrefs) {
            Log.d("QrScanActivity", "SharedPreferences0 - Key: $key, Value: $value")
        }

        val notificationSent = sharedPreferences.getBoolean("NOTIFICATION_SENT", false)

        if (!notificationSent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), requestCodePostNotifications)
                } else {
                    // Permission already granted
                    sendNotification()
                    sharedPreferences.edit { putBoolean("NOTIFICATION_SENT", true) }
                    for ((key, value) in allPrefs) {
                        Log.d("QrScanActivity", "SharedPreferences1 - Key: $key, Value: $value")
                    }
                }
            } else {
                // Permission not required for versions below Android 13
                sendNotification()
                sharedPreferences.edit { putBoolean("NOTIFICATION_SENT", true) }
                for ((key, value) in allPrefs) {
                    Log.d("QrScanActivity", "SharedPreferences2 - Key: $key, Value: $value")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePostNotifications) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted
                sendNotification()
                val sharedPreferences = getSharedPreferences("MediQueuePrefs", MODE_PRIVATE)
                sharedPreferences.edit { putBoolean("NOTIFICATION_SENT", true) }
            } else {
                // Permission denied
                showToast("Notification permission is not granted.")
            }
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
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
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
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE) // Set background color to white
        canvas.drawText(programName, 0f, -paint.fontMetricsInt.top.toFloat(), paint)
        canvas.drawText(text, 0f, -paint.fontMetricsInt.top.toFloat() + paint.fontMetricsInt.run { bottom - top }, paint)
        return bitmap
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    private fun sendNotification() {
        val intent = Intent(this, QueueStatusActivity::class.java).apply {
            putExtra("PATIENT_ID", patientId)
            putExtra("QUEUE_ID", queueId)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Queue Update")
            .setContentText("You are now being served.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            with(NotificationManagerCompat.from(this)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } else {
            // Handle the case where the permission is not granted
            showToast("Notification permission is not granted.")
        }
    }
}
package com.ciit.mediqueue.receptionist

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.ciit.mediqueue.R
import com.google.firebase.firestore.FirebaseFirestore

class PatientQueueAdapter(
    private val context: Context,
    private val patients: List<PatientQueueItem>,
    private val firestore: FirebaseFirestore
) : ArrayAdapter<PatientQueueItem>(context, 0, patients) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_patient_queue, parent, false)
        val patientInfoTextView: TextView = view.findViewById(R.id.patientInfoTextView)
        val removeButton: Button = view.findViewById(R.id.removeButton)

        val patient = getItem(position)
        patientInfoTextView.text = "${patient?.numberInLine}: ${patient?.fullName} (ID: ${patient?.patientId})"

        removeButton.setOnClickListener {
            showCancelConfirmationDialog(patient?.patientId)
        }

        return view
    }

    private fun showCancelConfirmationDialog(patientId: String?) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_cancel_queue, null)
        val patientIdInput = dialogView.findViewById<EditText>(R.id.patientIdInput)

        AlertDialog.Builder(context)
            .setTitle("Cancel Queue")
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val inputId = patientIdInput.text.toString()
                if (inputId == patientId) {
                    cancelQueue(patientId)
                } else {
                    Toast.makeText(context, "Patient ID does not match.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cancelQueue(patientId: String?) {
        val batch = firestore.batch()

        // Update queue status to "Cancelled"
        val queueRef = firestore.collection("queues").document(patientId!!)
        batch.update(queueRef, "status", "Cancelled")

        // Update medical visit status to "Cancelled"
        val visitRef = firestore.collection("medical_visits").whereEqualTo("patient_id", patientId).limit(1)
        visitRef.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val visitDoc = documents.documents[0].reference
                batch.update(visitDoc, "status", "Cancelled")
            }

            // Commit the batch update
            batch.commit().addOnSuccessListener {
                Toast.makeText(context, "Queue cancelled successfully.", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(context, "Error cancelling queue.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Error retrieving medical visit.", Toast.LENGTH_SHORT).show()
        }
    }
}
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
import com.ciit.mediqueue.R
import com.google.firebase.firestore.FirebaseFirestore

class PatientQueueAdapter(
    private val context: Context,
    patients: List<PatientQueueItem>,
    private val firestore: FirebaseFirestore
) : ArrayAdapter<PatientQueueItem>(context, 0, patients) {

    private val queuesCollection = "queues"
    private val medicalVisitsCollection = "medical_visits"

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // Inflate the view if it is not already created
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_patient_queue, parent, false)

        // Get references to UI elements
        val patientInfoTextView: TextView = view.findViewById(R.id.patientInfoTextView)
        val removeButton: Button = view.findViewById(R.id.removeButton)

        // Get patient at the current position
        val patient = getItem(position)

        // Set the patient information text using string resource with placeholders
        patientInfoTextView.text = context.getString(R.string.patient_info, patient?.numberInLine, patient?.fullName, patient?.patientId)

        // Set up the remove button click listener
        removeButton.setOnClickListener {
            patient?.patientId?.let { id ->
                showCancelConfirmationDialog(id)
            } ?: run {
                Toast.makeText(context, "Patient ID is null.", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    // Show a confirmation dialog before cancelling the queue
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

    // Cancel the queue for the given patient ID
    private fun cancelQueue(patientId: String?) {
        val batch = firestore.batch()

        // Update queue status to "Cancelled"
        val queueRef = firestore.collection(queuesCollection).document(patientId!!)
        batch.update(queueRef, "status", "Cancelled")

        // Update medical visit status to "Cancelled"
        val visitRef = firestore.collection(medicalVisitsCollection).whereEqualTo("patient_id", patientId).limit(1)
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
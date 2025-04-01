package com.ciit.mediqueue.receptionist

import android.content.Context
import android.graphics.Color
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

        // Create the AlertDialog
        val alertDialog = AlertDialog.Builder(context)
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
            .create()

        // Set OnShowListener to modify the button styles after the dialog is shown
        alertDialog.setOnShowListener {
            // Positive button customization (Confirm)
            val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setTextColor(Color.WHITE)
            positiveButton.setBackgroundColor(Color.DKGRAY)
            positiveButton.setPadding(20, 10, 20, 10)

            // Negative button customization (Cancel)
            val negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            negativeButton.setTextColor(Color.DKGRAY)
            negativeButton.setBackgroundColor(Color.WHITE)
            negativeButton.setPadding(20, 10, 20, 10)
        }

        // Show the dialog
        alertDialog.show()
    }


    // Cancel the queue for the given patient ID
    private fun cancelQueue(patientId: String?) {
        val queueRef = firestore.collection(queuesCollection).whereEqualTo("patient_id", patientId).limit(1)

        // Check if the document exists
        queueRef.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val document = documents.documents[0]
                val batch = firestore.batch()

                // Update queue status to "Cancelled", number_in_line to 0, and last_updated timestamp
                batch.update(document.reference, mapOf(
                    "status" to "Cancelled",
                    "number_in_line" to 0,
                    "last_updated" to com.google.firebase.Timestamp.now()
                ))

                // Update medical visit status to "Cancelled" and last_updated timestamp
                val visitRef = firestore.collection(medicalVisitsCollection).whereEqualTo("patient_id", patientId).limit(1)
                visitRef.get().addOnSuccessListener { visitDocuments ->
                    if (!visitDocuments.isEmpty) {
                        val visitDoc = visitDocuments.documents[0].reference
                        batch.update(visitDoc, mapOf(
                            "status" to "Cancelled",
                            "last_updated" to com.google.firebase.Timestamp.now()
                        ))
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
            } else {
                Toast.makeText(context, "Queue document not found.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Error checking queue document.", Toast.LENGTH_SHORT).show()
        }
    }
}
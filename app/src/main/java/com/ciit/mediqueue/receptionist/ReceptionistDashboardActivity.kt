package com.ciit.mediqueue.receptionist

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore

data class PatientQueueItem(val patientId: String, val fullName: String, val numberInLine: Long)

class ReceptionistDashboardActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var currentlyServingTextView: TextView
    private lateinit var previousPatientTextView: TextView
    private lateinit var nextPatientTextView: TextView
    private lateinit var patientsInQueueListView: ListView
    private lateinit var finishedAppointmentsListView: ListView
    private lateinit var callNextPatientButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receptionist_dashboard)

        firestore = FirebaseFirestore.getInstance()
        currentlyServingTextView = findViewById(R.id.currentlyServingTextView)
        previousPatientTextView = findViewById(R.id.previousPatientTextView)
        nextPatientTextView = findViewById(R.id.nextPatientTextView)
        patientsInQueueListView = findViewById(R.id.patientsInQueueListView)
        finishedAppointmentsListView = findViewById(R.id.finishedAppointmentsListView)
        callNextPatientButton = findViewById(R.id.callNextPatientButton)

        callNextPatientButton.setOnClickListener {
            callNextPatient()
        }

        fetchQueueData()
    }

    private fun fetchQueueData() {
        firestore.collection("queues")
            .orderBy("number_in_line")
            .get()
            .addOnSuccessListener { queueDocuments ->
                val patientsInQueue = mutableListOf<PatientQueueItem>()
                val finishedAppointments = mutableListOf<String>()
                var currentlyServing: String? = null
                var previousPatient: String? = null
                var nextPatient: String? = null

                val queueList = queueDocuments.documents

                if (queueList.isEmpty()) {
                    currentlyServingTextView.text = "Currently Serving: None"
                    previousPatientTextView.text = "Previous Patient: None"
                    nextPatientTextView.text = "Next Patient: None"
                    return@addOnSuccessListener
                }

                val patientDataMap = mutableMapOf<String, String>()

                // First, fetch all patient names in one batch to avoid multiple Firestore queries
                val patientIds = queueList.mapNotNull { it.getString("patient_id") }
                firestore.collection("patients")
                    .whereIn(FieldPath.documentId(), patientIds)
                    .get()
                    .addOnSuccessListener { patientsSnapshot ->
                        for (patientDoc in patientsSnapshot.documents) {
                            val patientId = patientDoc.id
                            val patientName = patientDoc.getString("full_name") ?: "Unknown"
                            patientDataMap[patientId] = patientName
                        }

                        // Now process the queue with the retrieved patient names
                        for ((index, queueDocument) in queueList.withIndex()) {
                            val patientId = queueDocument.getString("patient_id") ?: continue
                            val patientNumber = queueDocument.getLong("number_in_line") ?: 0
                            val status = queueDocument.getString("status") ?: "Unknown"
                            val patientName = patientDataMap[patientId] ?: "Unknown"

                            if (status == "Finished") {
                                finishedAppointments.add("$patientNumber: $patientName")
                            } else {
                                patientsInQueue.add(PatientQueueItem(patientId, patientName, patientNumber))
                            }

                            if (status == "Serving") {
                                currentlyServing = "$patientNumber: $patientName"
                                previousPatient = if (index > 0) {
                                    val prevPatientId = queueList[index - 1].getString("patient_id") ?: ""
                                    "${queueList[index - 1].getLong("number_in_line")}: ${patientDataMap[prevPatientId]}"
                                } else {
                                    "None"
                                }

                                nextPatient = if (index < queueList.size - 1) {
                                    val nextPatientId = queueList[index + 1].getString("patient_id") ?: ""
                                    "${queueList[index + 1].getLong("number_in_line")}: ${patientDataMap[nextPatientId]}"
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
                        currentlyServingTextView.text = "Currently Serving: $currentlyServing"
                        previousPatientTextView.text = "Previous Patient: ${previousPatient ?: "None"}"
                        nextPatientTextView.text = "Next Patient: ${nextPatient ?: "None"}"

                        // Populate lists
                        patientsInQueueListView.adapter = PatientQueueAdapter(this, patientsInQueue, firestore)
                        finishedAppointmentsListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, finishedAppointments)
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firebase", "Error fetching patient data", e)
                        Toast.makeText(this, "Error fetching patient data", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error fetching queue data", e)
                Toast.makeText(this, "Error fetching queue data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun callNextPatient() {
        val queueRef = firestore.collection("queues")

        // Get the currently serving patient
        queueRef.whereEqualTo("status", "Serving")
            .get()
            .addOnSuccessListener { servingSnapshot ->
                val batch = firestore.batch()

                // If there's a currently serving patient, mark them as "Finished"
                if (!servingSnapshot.isEmpty) {
                    val servingDoc = servingSnapshot.documents[0]
                    val servingRef = servingDoc.reference
                    batch.update(servingRef, "status", "Finished")

                    // Update the status in the medical_visits collection
                    val visitQuery = firestore.collection("medical_visits")
                        .whereEqualTo("patient_id", servingDoc.getString("patient_id"))
                        .limit(1)
                    visitQuery.get().addOnSuccessListener { visitSnapshot ->
                        if (!visitSnapshot.isEmpty) {
                            val visitDoc = visitSnapshot.documents[0]
                            batch.update(visitDoc.reference, "status", "Finished")
                        }
                    }
                }

                // Find the next patient in line
                queueRef.orderBy("number_in_line").limit(1)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            Toast.makeText(this, "No more patients in queue", Toast.LENGTH_SHORT).show()
                            currentlyServingTextView.text = "Currently Serving: None"
                            return@addOnSuccessListener
                        }

                        val nextPatientDoc = querySnapshot.documents[0]
                        val nextPatientRef = nextPatientDoc.reference
                        val nextPatientNumber = nextPatientDoc.getLong("number_in_line") ?: 0
                        val nextPatientName = nextPatientDoc.getString("patient_name") ?: "Unknown"

                        // Update the next patient to "Serving"
                        batch.update(nextPatientRef, "status", "Serving")

                        batch.commit().addOnSuccessListener {
                            currentlyServingTextView.text = "Currently Serving: $nextPatientNumber: $nextPatientName"
                            Toast.makeText(this, "Next patient called successfully", Toast.LENGTH_SHORT).show()
                            fetchQueueData() // Refresh queue UI
                        }.addOnFailureListener { e ->
                            Log.e("Firebase", "Error updating queue", e)
                            Toast.makeText(this, "Error updating queue", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firebase", "Error fetching next patient", e)
                        Toast.makeText(this, "Error fetching next patient", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error fetching currently serving patient", e)
                Toast.makeText(this, "Error fetching currently serving patient", Toast.LENGTH_SHORT).show()
            }
    }
}
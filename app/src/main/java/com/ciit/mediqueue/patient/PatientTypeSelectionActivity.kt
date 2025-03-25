package com.ciit.mediqueue.patient

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R
import androidx.core.content.edit

class PatientTypeSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_type_selection)

        // Initialize UI elements
        val btnOldPatient: Button = findViewById(R.id.btnOldPatient)
        val btnNewPatient: Button = findViewById(R.id.btnNewPatient)

        // Retrieve shared preferences
        val sharedPreferences = getSharedPreferences("MediQueuePrefs", MODE_PRIVATE)

        // Set click listeners for buttons
        btnOldPatient.setOnClickListener {
            navigateToActivity(OldPatientActivity::class.java)
        }

        btnNewPatient.setOnClickListener {
            navigateToActivity(NewPatientActivity::class.java)
        }

        // Reset notification sent flag
        sharedPreferences.edit { putBoolean("NOTIFICATION_SENT", false) }
    }

    // Function to navigate to a specified activity
    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
    }
}
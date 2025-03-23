package com.ciit.mediqueue.patient

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R

class PatientTypeSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_type_selection)

        val btnOldPatient: Button = findViewById(R.id.btnOldPatient)
        val btnNewPatient: Button = findViewById(R.id.btnNewPatient)

        val sharedPreferences = getSharedPreferences("MediQueuePrefs", MODE_PRIVATE)

        btnOldPatient.setOnClickListener {
            // Handle old patient button click
            val intent = Intent(this, OldPatientActivity::class.java)
            startActivity(intent)
        }

        btnNewPatient.setOnClickListener {
            // Handle new patient button click
            val intent = Intent(this, NewPatientActivity::class.java)
            startActivity(intent)
        }

        sharedPreferences.edit().putBoolean("NOTIFICATION_SENT", false).apply()

        // Log all shared preferences
        val allPrefs = sharedPreferences.all
        for ((key, value) in allPrefs) {
            Log.d("QrScanActivity", "SharedPreferencesz - Key: $key, Value: $value")
        }
    }
}
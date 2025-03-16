package com.ciit.mediqueue

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import com.ciit.mediqueue.patient.QrScanActivity
import com.ciit.mediqueue.receptionist.LoginActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set title text
        val titleText: TextView = findViewById(R.id.titleText)
        titleText.text = "MediQueue"

        // Set up Patient Check-In button
        val patientButton: Button = findViewById(R.id.btnPatientCheckIn)
        patientButton.setOnClickListener {
            val intent = Intent(this, QrScanActivity::class.java)
            startActivity(intent)
        }

        // Set up Receptionist Login button
        val receptionistButton: Button = findViewById(R.id.btnReceptionistLogin)
        receptionistButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}

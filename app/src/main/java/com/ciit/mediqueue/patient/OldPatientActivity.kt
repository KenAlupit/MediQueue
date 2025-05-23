package com.ciit.mediqueue.patient

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class OldPatientActivity : AppCompatActivity() {

    // Firebase instances
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Variables for OTP verification
    private lateinit var verificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    // Firestore collection names
    private val queuesCollection = "queues"
    private val patientsCollection = "patients"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_old_patient)

        // Initialize Firebase instances
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Initialize UI elements
        val patientIdInput: EditText = findViewById(R.id.patientIdInput)
        val sendOtpButton: Button = findViewById(R.id.sendOtpButton)

        // Set click listener for the send OTP button
        sendOtpButton.setOnClickListener {
            val patientId = patientIdInput.text.toString().trim()
            if (patientId.isNotEmpty()) {
                checkIfPatientInQueue(patientId)
            } else {
                showToast("Please enter a Patient ID")
            }
        }
    }

    // Check if the patient is already in the queue
    private fun checkIfPatientInQueue(patientId: String) {
        db.collection(queuesCollection)
            .whereEqualTo("patient_id", patientId)
            .whereIn("status", listOf("Pending", "Serving"))
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    sendOtpToPatient(patientId)
                } else {
                    showToast("Patient is already in the queue with status Pending or Serving")
                }
            }
            .addOnFailureListener {
                showToast("Error checking queue status: ${it.message}")
            }
    }

    // Send OTP to the patient's phone number
    private fun sendOtpToPatient(patientId: String) {
        db.collection(patientsCollection).document(patientId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val phoneNumber = document.getString("phone_number")
                    if (phoneNumber != null) {
                        showOtpDialog(phoneNumber)
                    } else {
                        showToast("Phone number not found for this Patient ID")
                    }
                } else {
                    showToast("Patient ID not found")
                }
            }
            .addOnFailureListener {
                showToast("Error retrieving patient data: ${it.message}")
            }
    }

    // Show the OTP dialog for verification
    private fun showOtpDialog(phoneNumber: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_otp_verification, null)
        val otpInput: EditText = dialogView.findViewById(R.id.otpInput)
        val verifyOtpButton: Button = dialogView.findViewById(R.id.verifyOtpButton)
        val resendOtpButton: Button = dialogView.findViewById(R.id.resendOtpButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Set click listener for the verify OTP button
        verifyOtpButton.setOnClickListener {
            val otpCode = otpInput.text.toString().trim()
            if (otpCode.isNotEmpty()) {
                verifyOtp(otpCode, dialog)
            } else {
                showToast("Please enter the OTP")
            }
        }

        // Set click listener for the resend OTP button
        resendOtpButton.setOnClickListener {
            sendOtp(phoneNumber, dialog)
        }

        // Send OTP initially
        sendOtp(phoneNumber, dialog)
        dialog.show()
    }

    // Send OTP to the given phone number
    private fun sendOtp(phoneNumber: String, dialog: AlertDialog) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    showToast("Verification successful")
                    signInWithCredential(credential, dialog)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    showToast("Verification failed: ${e.message}")
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    resendToken = token
                    showToast("OTP sent to $phoneNumber")
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // Verify the entered OTP code
    private fun verifyOtp(otpCode: String, dialog: AlertDialog) {
        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
        signInWithCredential(credential, dialog)
    }

    // Sign in with the given credential
    private fun signInWithCredential(credential: PhoneAuthCredential, dialog: AlertDialog) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showToast("OTP verified successfully")
                    dialog.dismiss()
                    navigateToNewPatientActivity()
                } else {
                    showToast("Incorrect OTP. Please try again.")
                }
            }
    }

    // Navigate to the NewPatientActivity
    private fun navigateToNewPatientActivity() {
        val patientId = findViewById<EditText>(R.id.patientIdInput).text.toString().trim()
        val intent = Intent(this, NewPatientActivity::class.java).apply {
            putExtra("PATIENT_ID", patientId)
        }
        startActivity(intent)
    }

    // Show a toast message
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
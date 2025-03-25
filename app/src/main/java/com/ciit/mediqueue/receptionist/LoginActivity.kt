package com.ciit.mediqueue.receptionist

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    // Firebase authentication instance
    private lateinit var auth: FirebaseAuth

    // UI elements
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var errorText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize UI elements
        emailInput = findViewById(R.id.editTextEmail)
        passwordInput = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.btnLogin)
        errorText = findViewById(R.id.textError)
        progressBar = findViewById(R.id.progressBar)

        // Set up login button click listener
        loginButton.setOnClickListener {
            loginReceptionist()
        }
    }

    // Function to handle receptionist login
    private fun loginReceptionist() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Check if email or password is empty
        if (email.isEmpty() || password.isEmpty()) {
            errorText.text = getString(R.string.login_error_empty_fields)
            errorText.visibility = TextView.VISIBLE
            return
        }

        // Show progress bar
        progressBar.visibility = ProgressBar.VISIBLE

        // Attempt to sign in with email and password
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                // Hide progress bar
                progressBar.visibility = ProgressBar.GONE
                if (task.isSuccessful) {
                    // Login successful, start receptionist dashboard activity
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, ReceptionistDashboardActivity::class.java))
                    finish()
                } else {
                    // Login failed
                    errorText.text = getString(R.string.login_error_invalid_credentials)
                    errorText.visibility = TextView.VISIBLE
                }
            }
    }
}

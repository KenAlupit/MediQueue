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
import com.ciit.mediqueue.receptionist.ReceptionistDashboardActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var errorText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        emailInput = findViewById(R.id.editTextEmail)
        passwordInput = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.btnLogin)
        errorText = findViewById(R.id.textError)
        progressBar = findViewById(R.id.progressBar)

        loginButton.setOnClickListener {
            loginReceptionist()
        }
    }

    private fun loginReceptionist() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            errorText.text = "Please enter email and password"
            errorText.visibility = TextView.VISIBLE
            return
        }

        progressBar.visibility = ProgressBar.VISIBLE

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = ProgressBar.GONE
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, ReceptionistDashboardActivity::class.java))
                    finish()
                } else {
                    errorText.text = "Login failed. Please check your credentials."
                    errorText.visibility = TextView.VISIBLE
                }
            }
    }
}

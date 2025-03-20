package com.ciit.mediqueue.patient

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.ciit.mediqueue.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.*

class NewPatientActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var dateOfBirthField: EditText
    private var selectedDate: Timestamp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_patient)

        db = FirebaseFirestore.getInstance()
        dateOfBirthField = findViewById(R.id.dateOfBirth)

        dateOfBirthField.setOnClickListener {
            showDatePicker()
        }

        findViewById<Button>(R.id.submitButton).setOnClickListener {
            savePatientData()
        }

        val painLevelSeekBar: SeekBar = findViewById(R.id.painLevel)
        val painLevelLabel: TextView = findViewById(R.id.painLevelLabel)

        painLevelSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                painLevelLabel.text = "Pain Level: $progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Do nothing
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Do nothing
            }
        })

        val countryCodeSpinner: Spinner = findViewById(R.id.countryCodeSpinner)
        val phoneNumberField: EditText = findViewById(R.id.phoneNumber)
        val emergencyCountryCodeSpinner: Spinner = findViewById(R.id.emergencyCountryCodeSpinner)
        val emergencyPhoneNumberField: EditText = findViewById(R.id.emergencyPhone)
        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        val countryCodes = phoneNumberUtil.supportedRegions.map { regionCode ->
            val countryCode = phoneNumberUtil.getCountryCodeForRegion(regionCode)
            "+$countryCode"
        }.sorted()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, countryCodes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        countryCodeSpinner.adapter = adapter
        countryCodeSpinner.setSelection(countryCodes.indexOf("+63")) // Set default to Philippines
        emergencyCountryCodeSpinner.adapter = adapter
        emergencyCountryCodeSpinner.setSelection(countryCodes.indexOf("+63")) // Set default to Philippines

        countryCodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCountryCode = countryCodes[position]
                val regionCode = phoneNumberUtil.getRegionCodeForCountryCode(selectedCountryCode.removePrefix("+").toInt())
                val exampleNumber = phoneNumberUtil.getExampleNumberForType(regionCode, PhoneNumberUtil.PhoneNumberType.MOBILE)
                phoneNumberField.hint = phoneNumberUtil.format(exampleNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        emergencyCountryCodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCountryCode = countryCodes[position]
                val regionCode = phoneNumberUtil.getRegionCodeForCountryCode(selectedCountryCode.removePrefix("+").toInt())
                val exampleNumber = phoneNumberUtil.getExampleNumberForType(regionCode, PhoneNumberUtil.PhoneNumberType.MOBILE)
                emergencyPhoneNumberField.hint = phoneNumberUtil.format(exampleNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        val patientId = intent.getStringExtra("PATIENT_ID")
        if (patientId != null) {
            prefillPatientData(patientId)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val date = Calendar.getInstance()
            date.set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
            date.clear(Calendar.MILLISECOND)
            selectedDate = Timestamp(date.time)
            dateOfBirthField.setText("$selectedYear-${selectedMonth + 1}-$selectedDay")
        }, year, month, day)

        datePicker.show()
    }

    private fun prefillPatientData(patientId: String) {
        db.collection("patients").document(patientId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val phoneNumberUtil = PhoneNumberUtil.getInstance()

                    val phoneNumber = document.getString("phone_number")
                    val emergencyPhoneNumber = document.getString("emergency_contact_phone")

                    if (phoneNumber != null) {
                        val numberProto = phoneNumberUtil.parse(phoneNumber, null)
                        val nationalNumber = phoneNumberUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                        findViewById<EditText>(R.id.phoneNumber).setText(nationalNumber?.replace(" ", ""))
                    }

                    if (emergencyPhoneNumber != null) {
                        val emergencyNumberProto = phoneNumberUtil.parse(emergencyPhoneNumber, null)
                        val emergencyNationalNumber = phoneNumberUtil.format(emergencyNumberProto, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                        findViewById<EditText>(R.id.emergencyPhone).setText(emergencyNationalNumber?.replace(" ", ""))
                    }

                    findViewById<EditText>(R.id.fullName).setText(document.getString("full_name"))
                    findViewById<EditText>(R.id.email).setText(document.getString("email"))
                    findViewById<EditText>(R.id.address).setText(document.getString("address"))
                    findViewById<EditText>(R.id.emergencyName).setText(document.getString("emergency_contact_name"))
                    findViewById<EditText>(R.id.emergencyRelationship).setText(document.getString("emergency_contact_relationship"))
                    findViewById<EditText>(R.id.medications).setText(document.getString("medications"))
                    findViewById<EditText>(R.id.allergies).setText(document.getString("allergies"))
                    findViewById<EditText>(R.id.conditions).setText(document.getString("conditions"))
                    findViewById<EditText>(R.id.surgeries).setText(document.getString("surgeries"))
                    findViewById<EditText>(R.id.smoking).setText(document.getString("smoking"))
                    findViewById<EditText>(R.id.alcohol).setText(document.getString("alcohol"))
                    findViewById<EditText>(R.id.exercise).setText(document.getString("exercise"))
                    findViewById<EditText>(R.id.diet).setText(document.getString("diet"))
                    findViewById<EditText>(R.id.insuranceProvider).setText(document.getString("insurance_provider"))
                    findViewById<EditText>(R.id.policyNumber).setText(document.getString("policy_number"))

                    // Set gender radio button
                    when (document.getString("gender")) {
                        "Male" -> findViewById<RadioButton>(R.id.sexMale).isChecked = true
                        "Female" -> findViewById<RadioButton>(R.id.sexFemale).isChecked = true
                    }

                    // Set date of birth
                    val dateOfBirth = document.getTimestamp("date_of_birth")
                    if (dateOfBirth != null) {
                        val calendar = Calendar.getInstance()
                        calendar.time = dateOfBirth.toDate()
                        selectedDate = dateOfBirth
                        dateOfBirthField.setText("${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}")
                    }
                }
            }
            .addOnFailureListener {
                // Handle the error
            }
    }

    private fun savePatientData() {
        val fullName = findViewById<EditText>(R.id.fullName).text.toString()
        val phoneNumber = findViewById<EditText>(R.id.phoneNumber).text.toString()
        val countryCode = findViewById<Spinner>(R.id.countryCodeSpinner).selectedItem.toString()
        val email = findViewById<EditText>(R.id.email).text.toString()
        val address = findViewById<EditText>(R.id.address).text.toString()
        val emergencyName = findViewById<EditText>(R.id.emergencyName).text.toString()
        val emergencyPhone = findViewById<EditText>(R.id.emergencyPhone).text.toString()
        val medications = findViewById<EditText>(R.id.medications).text.toString()
        val allergies = findViewById<EditText>(R.id.allergies).text.toString()
        val conditions = findViewById<EditText>(R.id.conditions).text.toString()
        val surgeries = findViewById<EditText>(R.id.surgeries).text.toString()
        val visitReason = findViewById<EditText>(R.id.visitReason).text.toString()
        val symptoms = findViewById<EditText>(R.id.symptoms).text.toString()

        if (fullName.isEmpty()) {
            showToast("Full Name is required")
            return
        }

        if (selectedDate == null) {
            showToast("Date of Birth is required")
            return
        }

        if (phoneNumber.isEmpty()) {
            showToast("Phone Number is required")
            return
        }

        if (!isValidPhoneNumber(countryCode, phoneNumber)) {
            showToast("Invalid Phone Number")
            return
        }

        if (email.isNotEmpty() && !isValidEmail(email)) {
            showToast("Invalid Email Address")
            return
        }

        if (address.isEmpty()) {
            showToast("Address is required")
            return
        }

        if (emergencyName.isEmpty()) {
            showToast("Emergency Contact Name is required")
            return
        }

        if (emergencyPhone.isEmpty()) {
            showToast("Emergency Contact Phone is required")
            return
        }

        if (!isValidPhoneNumber(countryCode, emergencyPhone)) {
            showToast("Invalid Emergency Contact Phone")
            return
        }

        if (medications.isEmpty()) {
            showToast("Medications are required")
            return
        }

        if (allergies.isEmpty()) {
            showToast("Allergies are required")
            return
        }

        if (conditions.isEmpty()) {
            showToast("Conditions are required")
            return
        }

        if (surgeries.isEmpty()) {
            showToast("Past Surgeries are required")
            return
        }

        if (visitReason.isEmpty()) {
            showToast("Reason for Visit is required")
            return
        }

        if (symptoms.isEmpty()) {
            showToast("Symptoms are required")
            return
        }

        val gender = when {
            findViewById<RadioButton>(R.id.sexMale).isChecked -> "Male"
            findViewById<RadioButton>(R.id.sexFemale).isChecked -> "Female"
            else -> ""
        }

        val patientData = hashMapOf(
            "full_name" to fullName,
            "date_of_birth" to selectedDate,
            "gender" to gender,
            "phone_number" to "$countryCode$phoneNumber",
            "email" to email,
            "address" to address,
            "emergency_contact_name" to emergencyName,
            "emergency_contact_relationship" to findViewById<EditText>(R.id.emergencyRelationship).text.toString().trim(),
            "emergency_contact_phone" to "$countryCode$emergencyPhone",
            "medications" to medications,
            "allergies" to allergies,
            "conditions" to conditions,
            "surgeries" to surgeries,
            "smoking" to findViewById<EditText>(R.id.smoking).text.toString().trim(),
            "alcohol" to findViewById<EditText>(R.id.alcohol).text.toString().trim(),
            "exercise" to findViewById<EditText>(R.id.exercise).text.toString().trim(),
            "diet" to findViewById<EditText>(R.id.diet).text.toString().trim(),
            "insurance_provider" to findViewById<EditText>(R.id.insuranceProvider).text.toString().trim(),
            "policy_number" to findViewById<EditText>(R.id.policyNumber).text.toString().trim(),
            "date_updated" to Timestamp.now()
        )

        val visitData = hashMapOf(
            "visit_date" to Timestamp.now(),
            "patient_id" to "",
            "reason_for_visit" to visitReason,
            "symptoms" to symptoms.split(",").map { it.trim() },
            "pain_level" to findViewById<SeekBar>(R.id.painLevel).progress,
            "recent_travel_history" to findViewById<CheckBox>(R.id.recentTravel).isChecked,
            "recent_exposure" to findViewById<CheckBox>(R.id.recentExposure).isChecked,
            "doctor_notes" to "",
            "treatment_plan" to "",
            "status" to "Pending"
        )

        val patientId = intent.getStringExtra("PATIENT_ID")

        if (patientId != null) {
            visitData["patient_id"] = patientId

            val batch = db.batch()
            val patientRef = db.collection("patients").document(patientId)
            val visitRef = db.collection("medical_visits").document()

            batch.set(patientRef, patientData, SetOptions.merge())
            batch.set(visitRef, visitData)

            batch.commit()
                .addOnSuccessListener {
                    addToQueue(patientId) // Add to queue after saving
                    showToast("Patient data updated and visit recorded!")
                }
                .addOnFailureListener { showToast("Error updating records") }
        } else {
            generateUniquePatientId { newPatientId ->
                patientData["patient_id"] = newPatientId
                visitData["patient_id"] = newPatientId

                val batch = db.batch()
                val patientRef = db.collection("patients").document(newPatientId)
                val visitRef = db.collection("medical_visits").document()

                batch.set(patientRef, patientData)
                batch.set(visitRef, visitData)

                batch.commit()
                    .addOnSuccessListener {
                        addToQueue(newPatientId) // Add to queue after saving
                        showToast("New Patient Registered and Visit Recorded!")
                    }
                    .addOnFailureListener { showToast("Error saving data") }
            }
        }
    }

    private fun addToQueue(patientId: String) {
        val queueRef = db.collection("queues")

        // Get the latest number_in_line
        queueRef.orderBy("number_in_line", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                val lastNumber = if (documents.isEmpty) 0 else documents.documents[0].getLong("number_in_line") ?: 0
                val newQueueData = hashMapOf(
                    "patient_id" to patientId,
                    "number_in_line" to lastNumber + 1,
                    "status" to "Pending",
                    "date_added" to Timestamp.now(),
                    "last_updated" to Timestamp.now()
                )

                queueRef.add(newQueueData)
                    .addOnSuccessListener { documentRef ->
                        val queueId = documentRef.id
                        showToast("Patient added to queue!")

                        // Save queueId and patientId in SharedPreferences
                        val sharedPreferences = getSharedPreferences("MediQueuePrefs", MODE_PRIVATE)
                        with(sharedPreferences.edit()) {
                            putString("QUEUE_ID", queueId)
                            putString("PATIENT_ID", patientId)
                            apply()
                        }

                        // Navigate to QueueStatusActivity
                        val intent = Intent(this, QueueStatusActivity::class.java).apply {
                            putExtra("QUEUE_ID", queueId)
                            putExtra("PATIENT_ID", patientId)
                        }
                        startActivity(intent)
                        finish() // Close current activity
                    }
                    .addOnFailureListener { showToast("Error adding to queue") }
            }
            .addOnFailureListener { showToast("Error retrieving queue data") }
    }

    private fun generateUniquePatientId(callback: (String) -> Unit) {
        val patientIdPrefix = "PAT-"
        val patientIdLength = 5

        fun generateId(): String {
            val randomNumber = (1..99999).random().toString().padStart(patientIdLength, '0')
            return "$patientIdPrefix$randomNumber"
        }

        fun checkIdExists(patientId: String) {
            db.collection("patients").whereEqualTo("patient_id", patientId).get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        callback(patientId)
                    } else {
                        checkIdExists(generateId())
                    }
                }
                .addOnFailureListener {
                    showToast("Error checking patient ID")
                }
        }

        checkIdExists(generateId())
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isValidPhoneNumber(countryCode: String, phoneNumber: String): Boolean {
        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        return try {
            val regionCode = phoneNumberUtil.getRegionCodeForCountryCode(countryCode.removePrefix("+").toInt())
            val numberProto = phoneNumberUtil.parse(phoneNumber, regionCode)
            phoneNumberUtil.isValidNumber(numberProto)
        } catch (e: Exception) {
            println(e)
            false
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
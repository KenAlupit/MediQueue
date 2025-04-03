# MediQueue

MediQueue is a hospital queue management system designed to enhance patient tracking, check-ins, and medical form management. It consists of two mobile applications: one for patients and one for receptionists. Built using **Kotlin**, the app leverages **Firebase Firestore** for real-time database management and **Firebase Authentication** for secure user access.

## Features

### Patient App
- **QR Code Check-in** – Patients scan a QR code upon arrival to register in the hospital queue.
- **Medical Form Submission** – Patients fill out necessary health information before their appointment. A unique Patient ID is assigned upon submission.
- **Returning Patients with Patient ID** – Patients can enter their Patient ID on their next visit to retrieve pre-filled medical forms.
- **Identity Verification with OTP** – An OTP is sent to the patient’s registered phone number for identity verification before proceeding.
- **Queue Position Display** – Patients can view their current position in the queue.
- **Turn Notification** – Patients receive notifications when it’s their turn to be seen.

### Receptionist App
- **Daily QR Code Generation** – The app generates a QR code if one is not found in Firestore for the day.
- **Queue Tracking** – Displays a real-time list of waiting patients and their details.
- **Patient Number Assignment** – Assigns a queue number to each patient based on their arrival order.
- **Next, Current, & Last Treated Patient View** – Displays patient tracking information for better queue management.
- **Queue Management** – Receptionists can manually remove patients from the queue if needed.
- **Call Next Patient** – Updates the system and notifies the next patient when it's their turn.

## Usage
- **For Patients**: Open the app, scan the QR code, submit medical forms, and track queue status.
- **For Receptionists**: Generate daily QR codes, manage the queue, and call the next patient efficiently.

## Technologies Used
- **Kotlin** – Primary programming language.
- **Firebase Firestore** – Real-time database.
- **Firebase Authentication** – Secure user login and OTP verification.
- **QR Code Scanner** – Used for patient check-in.
- **Push Notifications** – Alerts patients when it’s their turn.


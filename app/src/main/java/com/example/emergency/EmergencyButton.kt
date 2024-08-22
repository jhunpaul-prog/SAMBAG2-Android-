package com.example.emergency

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class EmergencyButton : Fragment() {

    private lateinit var reportButton: LinearLayout
    private lateinit var databaseRef: DatabaseReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var alertDialog: AlertDialog? = null
    private val PICK_IMAGE_REQUEST = 1
    private lateinit var selectedImageUri: Uri
    private lateinit var fileField: TextView
    private val CAMERA_REQUEST_CODE = 102

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_emergency_button, container, false)
        databaseRef = FirebaseDatabase.getInstance().getReference().child("led")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        reportButton = view.findViewById(R.id.reportButton)
        reportButton.setOnClickListener {
            checkLocationPermissions()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Custom back press logic here
                // For example, you can show a dialog or navigate to another fragment
            }
        })

        return view
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocationAndShowReportDialog()
        } else {
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fetchLocationAndShowReportDialog()
                } else {
                    Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    openGallery()
                } else {
                    Toast.makeText(requireContext(), "Storage permission denied, unable to select image", Toast.LENGTH_SHORT).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun fetchLocationAndShowReportDialog() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
            return
        }

        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        val settingsClient = LocationServices.getSettingsClient(requireContext())
        settingsClient.checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        showReportDialog(location.latitude, location.longitude)
                    } else {
                        Toast.makeText(requireContext(), "Failed to get location, please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { exception ->
                handleLocationSettingsException(exception)
            }
    }
//paul gwapo
    private fun handleLocationSettingsException(exception: Exception) {
        if (exception is ResolvableApiException) {
            try {
                exception.startResolutionForResult(requireActivity(), REQUEST_CHECK_SETTINGS)
            } catch (sendEx: IntentSender.SendIntentException) {
                Toast.makeText(requireContext(), "Unable to resolve location settings.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Location service is disabled, please enable it and try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showReportDialog(latitude: Double, longitude: Double) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogLayout = layoutInflater.inflate(R.layout.fragment_emergencyform, null)
        builder.setView(dialogLayout)

        val submitButton = dialogLayout.findViewById<AppCompatButton>(R.id.submit)
        val nameField = dialogLayout.findViewById<EditText>(R.id.name_field)
        val contactField = dialogLayout.findViewById<EditText>(R.id.contact_field)
        val closeButton = dialogLayout.findViewById<ImageButton>(R.id.close_button)
        val typeSpinner = dialogLayout.findViewById<Spinner>(R.id.type)
        fileField = dialogLayout.findViewById(R.id.file_field)

        setupTypeSpinner(typeSpinner)

        submitButton.setOnClickListener {
            val selectedType = typeSpinner.selectedItem.toString()
            val name = nameField.text.toString()
            val contact = contactField.text.toString()
            val locationString = "Latitude: $latitude, Longitude: $longitude"
            sendEmergencyReport(selectedType, name, contact, locationString)
            updateState(selectedType)
            alertDialog?.dismiss()
        }

        closeButton.setOnClickListener {
            alertDialog?.dismiss()
        }

        dialogLayout.findViewById<AppCompatButton>(R.id.browse_button)?.setOnClickListener {
            handleStoragePermission()
        }

        alertDialog = builder.create()
        alertDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog?.show()
    }

    private fun setupTypeSpinner(typeSpinner: Spinner) {
        val spinnerArray = arrayOf("NOISE", "THIEF", "SUNOG")
        val adapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, spinnerArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter
    }

    private fun sendEmergencyReport(type: String, name: String, contact: String, locationString: String) {
        val reportKey = databaseRef.child("reports").push().key

        if (reportKey != null) {
            val reportData = createReportData(type, name, contact, locationString)

            if (::selectedImageUri.isInitialized) {
                uploadImageToFirebaseStorage(selectedImageUri) { imageUrl ->
                    reportData["pictureUpload"] = imageUrl
                    saveReportToDatabase(reportKey, reportData)
                }
            } else {
                saveReportToDatabase(reportKey, reportData)
            }
        }
    }

    private fun createReportData(type: String, name: String, contact: String, locationString: String): HashMap<String, Any> {
        val currentTimeMillis = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        val formattedDateTime = dateFormat.format(Date(currentTimeMillis))

        return hashMapOf(
            "type" to type,
            "name" to name,
            "contact" to contact,
            "location" to locationString,
            "timestamp" to formattedDateTime
        )
    }

    private fun saveReportToDatabase(reportKey: String, reportData: HashMap<String, Any>) {
        databaseRef.child("reports").child(reportKey).setValue(reportData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showSubmittedDialog()
                } else {
                    Toast.makeText(requireContext(), "Failed to send emergency report. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun uploadImageToFirebaseStorage(uri: Uri, callback: (String) -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference.child("ReportPicture")
        val filename = UUID.randomUUID().toString()
        val imageRef = storageRef.child(filename)

        imageRef.putFile(uri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    callback(uri.toString())
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateState(selectedType: String) {
        val stateValue = when (selectedType) {
            "NOISE" -> 1
            "THIEF" -> 2
            "SUNOG" -> 3
            else -> 0
        }
        databaseRef.child("state").setValue(stateValue)
            .addOnSuccessListener {
                showSubmittedDialog()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update state: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestStoragePermission()
            } else {
                openGallery()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageAllFilesPermission()
            } else {
                openGallery()
            }
        }
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_CODE
        )
    }

    private fun requestManageAllFilesPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${requireContext().packageName}")
            startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to request storage permission", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val options = arrayOf<CharSequence>("Take Photo", "Choose from Gallery", "Cancel")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Choose your report picture")

        builder.setItems(options) { dialog, item ->
            when (options[item]) {
                "Take Photo" -> {
                    val takePicture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(takePicture, CAMERA_REQUEST_CODE)
                }
                "Choose from Gallery" -> {
                    val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(pickPhoto, PICK_IMAGE_REQUEST)
                }
                "Cancel" -> dialog.dismiss()
            }
        }
        builder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> handleGalleryResult(data)
                CAMERA_REQUEST_CODE -> handleCameraResult(data)
                REQUEST_CHECK_SETTINGS -> fetchLocationAndShowReportDialog()
            }
        } else {
            Toast.makeText(requireContext(), "Action canceled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryResult(data: Intent?) {
        data?.data?.let { uri ->
            selectedImageUri = uri
            val filename = getFileName(uri)
            fileField.text = filename
        } ?: run {
            Toast.makeText(requireContext(), "Failed to retrieve image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCameraResult(data: Intent?) {
        val imageBitmap = data?.extras?.get("data") as? Bitmap
        if (imageBitmap != null) {
            selectedImageUri = saveImageToGallery(imageBitmap)
            val filename = getFileName(selectedImageUri)
            fileField.text = filename
        } else {
            Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap): Uri {
        val filename = generateImageName()
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }

        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { safeUri ->
            resolver.openOutputStream(safeUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        }

        return uri ?: Uri.EMPTY
    }

    private fun generateImageName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "IMG_$timeStamp.jpg"
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "Unknown"
    }

    private fun showSubmittedDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = requireActivity().layoutInflater.inflate(R.layout.fragment_submitted, null)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.show()

        dialog.findViewById<Button>(R.id.done)?.setOnClickListener {
            dialog.dismiss()
            fragmentManager?.popBackStack()
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val STORAGE_PERMISSION_CODE = 101
        private const val REQUEST_CHECK_SETTINGS = 1001
        private const val REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION = 101

        fun newInstance(): EmergencyButton {
            return EmergencyButton()
        }
    }
}

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
        import androidx.activity.OnBackPressedCallback
        import androidx.appcompat.app.AppCompatActivity
    
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
                // Register the onBackPressedCallback
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
                    STORAGE_PERMISSION_CODE -> { // Adjusted to match the constant used in handleStoragePermission()
                        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                            // Both location and storage permissions granted, proceed to open gallery
                            openGallery()
                        } else {
                            Toast.makeText(requireContext(), "Storage permission denied, unable to select image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                    }
                }
            }



            private fun fetchLocationAndShowReportDialog() {
                // Check location permissions first
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Request location permissions
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                    return
                }
    
                // Location permissions are granted, proceed with location settings check
                val locationRequest = LocationRequest.create().apply {
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                }
    
                val locationSettingsRequest = LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest)
                    .build()
    
                val settingsClient = LocationServices.getSettingsClient(requireContext())
                settingsClient.checkLocationSettings(locationSettingsRequest)
                    .addOnSuccessListener {
                        // Location settings are satisfied, proceed with location request
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { location: Location? ->
                                if (location != null) {
                                    showReportDialog(location.latitude, location.longitude)
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "Failed to get location, please try again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                    .addOnFailureListener { exception ->
                        if (exception is ResolvableApiException) {
                            try {
                                // Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult()
                                exception.startResolutionForResult(requireActivity(), REQUEST_CHECK_SETTINGS)
                            } catch (sendEx: IntentSender.SendIntentException) {
                                // Ignore the error
                            }
                        } else {
                            // Location settings are not satisfied, and no resolution is available
                            Toast.makeText(
                                requireContext(),
                                "Location service is disabled, please enable it and try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }
    
    
            private fun showReportDialog(latitude: Double, longitude: Double) {
                val builder = AlertDialog.Builder(requireContext())
    
                // Inflate the custom dialog layout
                val dialogLayout = layoutInflater.inflate(R.layout.fragment_emergencyform, null)
                builder.setView(dialogLayout)
    
                // Set up dialog elements
                val submitButton = dialogLayout.findViewById<AppCompatButton>(R.id.submit)
                val nameField = dialogLayout.findViewById<EditText>(R.id.name_field)
                val contactField = dialogLayout.findViewById<EditText>(R.id.contact_field)
                val closeButton = dialogLayout.findViewById<ImageButton>(R.id.close_button)
                val typeSpinner = dialogLayout.findViewById<Spinner>(R.id.type)
                fileField = dialogLayout.findViewById<TextView>(R.id.file_field)

    
                // Define your choices here
                val spinnerArray = arrayOf("NOISE", "THIEF", "SUNOG")
                val adapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, spinnerArray) // Use custom layout
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                typeSpinner.adapter = adapter
    
                // Handle submit button click
                submitButton.setOnClickListener {
                    val selectedType = typeSpinner.selectedItem.toString()
                    val name = nameField.text.toString()
                    val contact = contactField.text.toString()
                    val locationString = "Latitude: $latitude, Longitude: $longitude"
                    sendEmergencyReport(selectedType, name, contact, locationString)
                    updateState(selectedType)
                    alertDialog?.dismiss()
                }
    
                // Handle close button click
                closeButton.setOnClickListener {
                    alertDialog?.dismiss()
                }
    
                // Handle browse button click
                dialogLayout.findViewById<AppCompatButton>(R.id.browse_button)?.setOnClickListener {
                    handleStoragePermission()
                }
    
                // Create the dialog
                alertDialog = builder.create()
                alertDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    
                // Show the dialog
                alertDialog?.show()
            }
    
    
            private fun sendEmergencyReport(type: String, name: String, contact: String, locationString: String) {
                val reportKey = databaseRef.child("reports").push().key
    
                if (reportKey != null) {
                    val currentTimeMillis = System.currentTimeMillis()
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                    val formattedDateTime = dateFormat.format(Date(currentTimeMillis))
    
                    val reportData = HashMap<String, Any>()
                    reportData["type"] = type
                    reportData["name"] = name
                    reportData["contact"] = contact
                    reportData["location"] = locationString
                    reportData["timestamp"] = formattedDateTime
    
                    // Check if an image has been selected
                    if (::selectedImageUri.isInitialized) {
                        // Upload the image to Firebase Storage and then add the download URL to reportData
                        uploadImageToFirebaseStorage(selectedImageUri) { imageUrl ->
                            reportData["pictureUpload"] = imageUrl
    
                            // Save the reportData to the Realtime Database
                            databaseRef.child("reports").child(reportKey).setValue(reportData)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        // Handle successful upload
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "Failed to send emergency report. Please try again.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                        }
                    } else {
                        // If no image is selected, proceed without uploading image
                        // Save the reportData to the Realtime Database without an image
                        databaseRef.child("reports").child(reportKey).setValue(reportData)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    // Handle successful upload
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "Failed to send emergency report. Please try again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                }
            }
    
    
    
            // Function to upload image to Firebase Storage
            private fun uploadImageToFirebaseStorage(uri: Uri, callback: (String) -> Unit) {
                val storageRef = FirebaseStorage.getInstance().reference.child("ReportPicture")
                val filename = UUID.randomUUID().toString()
                val imageRef = storageRef.child(filename)
    
                imageRef.putFile(uri)
                    .addOnSuccessListener { taskSnapshot ->
                        // Image uploaded successfully
                        // Get the download URL and pass it to the callback function
                        imageRef.downloadUrl.addOnSuccessListener { uri ->
                            callback(uri.toString())
                        }
                    }
                    .addOnFailureListener { e ->
                        // Handle any errors
                        Toast.makeText(
                            requireContext(),
                            "Failed to upload image: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
    
            private fun updateState(selectedType: String) {
                val stateValue = when (selectedType) {
                    "NOISE" -> 1
                    "THIEF" -> 2
                    "SUNOG" -> 3
                    else -> 0 // Handle default case
                }
                databaseRef.child("state").setValue(stateValue)
                    .addOnSuccessListener {
                        showSubmittedDialog()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to update state: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    
            private fun locationToString(location: Location): String {
                return "Latitude: ${location.latitude}, Longitude: ${location.longitude}"
            }

            private fun handleStoragePermission() {
                // Permission for SDK between 23 and 29
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                            REQUEST_STORAGE_PERMISSION_CODE
                        )
                    } else {
                        // Permission already granted
                        requestStoragePermission() // Call the function directly if permission is granted
                    }
                }
                // Permission for SDK 30 and above
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${requireContext().packageName}")
                            startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                }
            }

            private fun requestStoragePermission() {
                val permissions = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                // Check if the current SDK is below Android 10
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // For SDK versions below Android 10 (Q)
                    // Check if permissions are granted
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        // Permissions granted, proceed with opening gallery
                        openGallery()
                    } else {
                        // Request permissions
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            permissions,
                            STORAGE_PERMISSION_CODE
                        )
                    }
                } else {
                    if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Environment.isExternalStorageManager()
                        } else {
                            TODO("VERSION.SDK_INT < R")
                        }
                    ) {
                        // Permissions granted, proceed with opening gallery
                        openGallery()
                    } else {
                        // Request permission
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${requireContext().packageName}")
                            startActivityForResult(intent, STORAGE_PERMISSION_CODE)
                        } catch (e: Exception) {
                            // Show a message or handle the error
                            Toast.makeText(
                                requireContext(),
                                "Unable to request storage permission",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }





            private fun openGallery() {
                val permissions = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                // Check if permission is granted
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Permission is granted, proceed with opening gallery
                    val options = arrayOf<CharSequence>("Take Photo", "Choose from Gallery", "Cancel")
                    val builder = AlertDialog.Builder(requireContext())
                    builder.setTitle("Choose your report picture")

                    builder.setItems(options) { dialog, item ->
                        when {
                            options[item] == "Take Photo" -> {
                                // Open the camera to capture an image
                                val takePicture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                startActivityForResult(takePicture, CAMERA_REQUEST_CODE)
                            }
                            options[item] == "Choose from Gallery" -> {
                                // Open the gallery to choose an image
                                val pickPhoto = Intent(
                                    Intent.ACTION_PICK,
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                )
                                startActivityForResult(pickPhoto, PICK_IMAGE_REQUEST)
                            }
                            options[item] == "Cancel" -> {
                                dialog.dismiss()
                            }
                        }
                    }
                    builder.show()
                } else {
                    // Permission is not granted, request permission
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        permissions,
                        STORAGE_PERMISSION_CODE
                    )
                }
            }



            // Inside onActivityResult method
            override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
                super.onActivityResult(requestCode, resultCode, data)
                if (resultCode == Activity.RESULT_OK) {
                    when (requestCode) {
                        PICK_IMAGE_REQUEST -> {
                            if (data != null && data.data != null) {
                                selectedImageUri = data.data!!
                                val filename = getFileName(selectedImageUri)
                                fileField.text = filename
                            } else {
                                Toast.makeText(requireContext(), "Failed to retrieve image", Toast.LENGTH_SHORT).show()
                            }
                        }
                        CAMERA_REQUEST_CODE -> {
                            if (data != null && data.extras != null && data.extras!!.containsKey("data")) {
                                val imageBitmap = data.extras!!["data"] as Bitmap?
                                if (imageBitmap != null) {
                                    selectedImageUri = saveImageToGallery(imageBitmap)
                                    val filename = getFileName(selectedImageUri)
                                    fileField.text = filename
                                } else {
                                    Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
                            }
                        }
                        REQUEST_CHECK_SETTINGS -> {
                            fetchLocationAndShowReportDialog()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Action canceled", Toast.LENGTH_SHORT).show()
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
    
    
    
            private fun getImageUri(context: Context, bitmap: Bitmap): Uri {
                val bytes = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
                val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "Title", null)
                return Uri.parse(path ?: "")
            }
    
    
    
            override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                super.onViewCreated(view, savedInstanceState)

    
                // Assuming "developers" is an ImageView
                val developersImageView = view.findViewById<AppCompatButton>(R.id.developer)
    
                developersImageView.setOnClickListener {
                    showDevelopersDialog()
                }
            }
            private fun showDevelopersDialog() {
                val builder = AlertDialog.Builder(requireContext())
                val inflater = requireActivity().layoutInflater
                val dialogView = inflater.inflate(R.layout.fragment_developers, null)
                builder.setView(dialogView)
                val dialog = builder.create()
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    
                // Add close button (X) to the dialog
                val closeButton = dialogView.findViewById<ImageButton>(R.id.closeButton)
                closeButton.setOnClickListener {
                    dialog.dismiss() // Close the dialog when the close button is clicked
                }
    
                // Dynamically populate developer information
                val imageView = dialogView.findViewById<ImageView>(R.id.imageView2)
                val roleTextView = dialogView.findViewById<TextView>(R.id.role)
                val nameTextView = dialogView.findViewById<TextView>(R.id.name)
                val previousButton = dialogView.findViewById<ImageButton>(R.id.previousButton)
                val nextButton = dialogView.findViewById<ImageButton>(R.id.nextButton)
    
                val roleTexts = arrayOf("Project Manager", "Software Developer", "Web Developer", "Designer")
                val names = arrayOf("Hassein Lei Bate", "Jhun Paul M. Ceniza", "Harvey dave de Gracia", "Aldrin Amantillo")
    
                // Get drawable resources for images
                val drawableIds = arrayOf(R.drawable.lei, R.drawable.pol, R.drawable.harv, R.drawable.ali)
    
                var currentIndex = 0 // Track the current developer index
    
                // Function to update views with data for the current developer
                fun updateDeveloperViews() {
                    imageView.setImageResource(drawableIds[currentIndex])
                    roleTextView.text = roleTexts[currentIndex]
                    nameTextView.text = names[currentIndex]
    
                    // Update visibility of next and previous buttons based on current index
                    previousButton.visibility = if (currentIndex == 0) View.INVISIBLE else View.VISIBLE
                    nextButton.visibility = if (currentIndex == drawableIds.size - 1) View.INVISIBLE else View.VISIBLE
                }
    
                // Set initial developer data
                updateDeveloperViews()
    
                // Next button click listener
                dialogView.findViewById<ImageButton>(R.id.nextButton)?.setOnClickListener {
                    currentIndex = (currentIndex + 1) % drawableIds.size // Increment index cyclically
                    updateDeveloperViews() // Update views with data for the new current developer
                }
    
                // Previous button click listener
                dialogView.findViewById<ImageButton>(R.id.previousButton)?.setOnClickListener {
                    currentIndex = (currentIndex - 1 + drawableIds.size) % drawableIds.size // Decrement index cyclically
                    updateDeveloperViews() // Update views with data for the new current developer
                }
    
                dialog.show()
    
                // Optionally, add any custom behavior or listeners to dialog elements here
            }
    
    
    
    
            private fun showSubmittedDialog() {
                val builder = AlertDialog.Builder(requireContext())
                val inflater = requireActivity().layoutInflater
                val dialogView = inflater.inflate(R.layout.fragment_submitted, null)
                builder.setView(dialogView)
                val dialog = builder.create()
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    
                dialog.show()
    
                // Handle click event of the "OK" button
                dialog.findViewById<Button>(R.id.done)?.setOnClickListener {
                    dialog.dismiss()
                    // Navigate back to the original fragment
                    fragmentManager?.popBackStack()
                }
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
    
            companion object {
                private const val LOCATION_PERMISSION_REQUEST_CODE = 100
                private const val STORAGE_PERMISSION_CODE = 101
                private const val REQUEST_CHECK_SETTINGS = 1001
                private const val REQUEST_STORAGE_PERMISSION_CODE = 100 // You can choose any unique integer value
                private const val REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION = 101 // You can choose any unique integer value
    
                fun newInstance(): EmergencyButton {
                    return EmergencyButton()
                }
    
            }
        }

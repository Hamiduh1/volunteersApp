package com.example.volunteersApp.employer.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.semantics.error
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentEmployerProfileBinding
import com.example.volunteersApp.employer.EmployerProfile // Your Java class
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

class EmployerProfileFragment : Fragment() {

    private var _binding: FragmentEmployerProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var mAuth: FirebaseAuth
    private var currentUser: FirebaseUser? = null
    private lateinit var db: FirebaseFirestore
    private var employerProfileDocRef: DocumentReference? = null
    private lateinit var storageProfileImagesRef: StorageReference

    private var imageUri: Uri? = null
    private var currentProfileImageUrl: String? = null

    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var navController: NavController

    companion object {
        private const val TAG = "EmployerProfileFrag"
        private const val EMPLOYERS_COLLECTION = "Employers"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAuth = FirebaseAuth.getInstance()
        currentUser = mAuth.currentUser

        db = FirebaseFirestore.getInstance()
        currentUser?.uid?.let { uid ->
            employerProfileDocRef = db.collection(EMPLOYERS_COLLECTION).document(uid)
            storageProfileImagesRef = FirebaseStorage.getInstance().reference
                .child("profile_images")
                .child(uid)
        }

        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                    imageUri = result.data?.data
                    if (isAdded && _binding != null) { // Check fragment is added
                        Glide.with(this)
                            .load(imageUri)
                            .circleCrop()
                            .placeholder(R.drawable.ic_default_profile_placeholder)
                            .into(binding.imageViewProfile)
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployerProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.manage_organization_profile_title)

        if (currentUser == null || employerProfileDocRef == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.user_not_logged_in_error),
                Toast.LENGTH_LONG
            ).show()
            navController.popBackStack()
            return
        }

        loadProfileData()

        binding.imageViewProfile.setOnClickListener { openImagePicker() }
        binding.fabChangeProfileImage.setOnClickListener { openImagePicker() }
        binding.buttonSaveChanges.setOnClickListener { saveProfileChanges() }

        binding.buttonBecomeOrganizer.setOnClickListener {
            handleRoleChangeRequest("ORGANIZER", getString(R.string.role_organizer))
        }
        binding.buttonBecomeVolunteer.setOnClickListener {
            handleRoleChangeRequest("VOLUNTEER", getString(R.string.role_volunteer))
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun loadProfileData() {
        setLoadingState(true)

        employerProfileDocRef?.get()
            ?.addOnSuccessListener { documentSnapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                setLoadingState(false, true) // Enable save button after load

                if (documentSnapshot.exists()) {
                    val profile = documentSnapshot.toObject(EmployerProfile::class.java)
                    profile?.let {
                        binding.editTextOrganizationName.setText(it.organizationName ?: "")
                        binding.editTextContactEmail.setText(
                            if (!it.contactEmail.isNullOrEmpty()) it.contactEmail else currentUser?.email
                        )
                        binding.editTextOrganizationDescription.setText(it.description ?: "")
                        // Use the correctly named getter from EmployerProfile.java
                        binding.editTextOrganizationLocation.setText(it.location ?: "")


                        currentProfileImageUrl = it.profileImageUrl
                        if (!currentProfileImageUrl.isNullOrEmpty()) {
                            Glide.with(this@EmployerProfileFragment)
                                .load(currentProfileImageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_default_profile_placeholder)
                                .error(R.drawable.ic_default_profile_placeholder) // Add error placeholder
                                .into(binding.imageViewProfile)
                        } else {
                            loadDefaultProfileImage()
                        }
                        updateRoleChangeUI(it)
                        Log.d(TAG, "Profile data loaded from Firestore.")
                    } ?: run {
                        Log.w(TAG, "Profile data exists but failed to parse.")
                        prefillWithDefaults()
                        updateRoleChangeUI(null)
                    }
                } else {
                    Log.d(TAG, "No profile found. Pre-filling defaults.")
                    prefillWithDefaults()
                    updateRoleChangeUI(null)
                }
            }
            ?.addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                setLoadingState(false, true) // Enable save button on failure too
                Log.e(TAG, "Error loading profile from Firestore", e)
                Toast.makeText(
                    context,
                    getString(R.string.profile_load_failed_error, e.message),
                    Toast.LENGTH_LONG
                ).show()
                prefillWithDefaults() // Prefill defaults even on load failure to have some state
                updateRoleChangeUI(null)
            }
    }

    private fun prefillWithDefaults() {
        if (!isAdded || _binding == null) return
        binding.editTextContactEmail.setText(currentUser?.email ?: "")
        binding.editTextOrganizationName.setText("")
        binding.editTextOrganizationDescription.setText("")
        binding.editTextOrganizationLocation.setText("")
        loadDefaultProfileImage()
    }

    private fun loadDefaultProfileImage() {
        if (isAdded && _binding != null) {
            Glide.with(this)
                .load(R.drawable.ic_default_profile_placeholder)
                .circleCrop()
                .into(binding.imageViewProfile)
        }
    }

    private fun updateRoleChangeUI(profile: EmployerProfile?) {
        if (!isAdded || _binding == null) return

        val interestedOrganizer = profile?.interestedInBecomingOrganizer == true
        val interestedVolunteer = profile?.interestedInBecomingVolunteer == true
        val status = profile?.roleChangeRequestStatus

        binding.buttonBecomeOrganizer.isEnabled = !interestedOrganizer && status == null
        binding.buttonBecomeVolunteer.isEnabled = !interestedVolunteer && status == null

        if (status != null) {
            binding.textViewRoleChangeStatus.visibility = View.VISIBLE
            val role = when {
                status.contains("ORGANIZER", ignoreCase = true) -> getString(R.string.role_organizer)
                status.contains("VOLUNTEER", ignoreCase = true) -> getString(R.string.role_volunteer)
                else -> "role" // Generic fallback
            }
            binding.textViewRoleChangeStatus.text = getString(R.string.role_change_status_pending, role)
            binding.buttonBecomeOrganizer.isEnabled = false
            binding.buttonBecomeVolunteer.isEnabled = false
        } else {
            binding.textViewRoleChangeStatus.visibility = View.GONE
        }
    }

    private fun saveProfileChanges() {
        val orgName = binding.editTextOrganizationName.text.toString().trim()
        val contactEmail = binding.editTextContactEmail.text.toString().trim()
        val description = binding.editTextOrganizationDescription.text.toString().trim()
        val location = binding.editTextOrganizationLocation.text.toString().trim()

        if (orgName.isEmpty()) {
            binding.editTextOrganizationName.error = getString(R.string.organization_name_required_error)
            binding.editTextOrganizationName.requestFocus()
            return
        }
        if (contactEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(contactEmail).matches()) {
            binding.editTextContactEmail.error = getString(R.string.invalid_email_error)
            binding.editTextContactEmail.requestFocus()
            return
        }

        setLoadingState(true)

        if (imageUri != null) {
            uploadImageAndSaveProfile(orgName, contactEmail, description, location)
        } else {
            saveProfileToFirestore(orgName, contactEmail, description, location, currentProfileImageUrl)
        }
    }

    private fun uploadImageAndSaveProfile(
        orgName: String, contactEmail: String, description: String, location: String
    ) {
        val imageFileName = UUID.randomUUID().toString() + ".jpg"
        val fileReference = storageProfileImagesRef.child(imageFileName)

        imageUri?.let { uriToUpload -> // Safe call for imageUri
            fileReference.putFile(uriToUpload)
                .addOnSuccessListener {
                    fileReference.downloadUrl.addOnSuccessListener { uri ->
                        val newImageUrl = uri.toString()
                        Log.d(TAG, "Image uploaded. URL: $newImageUrl")

                        if (!currentProfileImageUrl.isNullOrEmpty() && currentProfileImageUrl != newImageUrl) {
                            try {
                                val oldImageRef = FirebaseStorage.getInstance().getReferenceFromUrl(currentProfileImageUrl!!)
                                oldImageRef.delete()
                                    .addOnSuccessListener { Log.d(TAG, "Old profile image deleted.") }
                                    .addOnFailureListener { e -> Log.e(TAG, "Failed to delete old image.", e) }
                            } catch (e: IllegalArgumentException) {
                                Log.e(TAG, "Invalid URL for old image: $currentProfileImageUrl", e)
                            }
                        }
                        currentProfileImageUrl = newImageUrl
                        saveProfileToFirestore(orgName, contactEmail, description, location, newImageUrl)
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get image download URL", e)
                        handleSaveError(getString(R.string.profile_update_failed_error, e.message))
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Image upload failed", e)
                    handleSaveError(getString(R.string.image_upload_failed_error, e.message))
                }
                .addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred) / snapshot.totalByteCount
                    Log.d(TAG, "Upload is $progress% done")
                    // Optionally update a ProgressBar for upload
                }
        } ?: run {
            // This case should ideally not be hit if imageUri is checked before calling this method,
            // but as a fallback, save with the currentProfileImageUrl.
            Log.w(TAG, "uploadImageAndSaveProfile called with null imageUri, saving with current image URL.")
            saveProfileToFirestore(orgName, contactEmail, description, location, currentProfileImageUrl)
        }
    }

    // THIS METHOD IS NOW CORRECTLY PLACED AT THE CLASS LEVEL
    private fun saveProfileToFirestore(
        orgName: String, contactEmail: String, description: String,
        location: String, imageUrl: String?
    ) {
        employerProfileDocRef?.get()?.addOnSuccessListener { documentSnapshot ->
            val existingProfile = if (documentSnapshot.exists()) {
                documentSnapshot.toObject(EmployerProfile::class.java) ?: EmployerProfile()
            } else {
                EmployerProfile()
            }

            // Create the profileToSave by setting fields on the existing or new profile instance
            // This replaces the concept of data class .copy() for a Java class
            existingProfile.organizationName = orgName
            existingProfile.contactEmail = contactEmail
            existingProfile.description = description
            existingProfile.location = location // Uses the setLocation() method of EmployerProfile.java
            existingProfile.profileImageUrl = imageUrl
            // Fields like interestedInBecomingOrganizer, interestedInBecomingVolunteer,
            // and roleChangeRequestStatus are preserved because we are modifying 'existingProfile'
            // or a new one. The @ServerTimestamp on lastUpdatedTimestamp in EmployerProfile.java
            // will handle updating that field.

            employerProfileDocRef?.set(existingProfile, SetOptions.merge()) // Use merge to be safe, or set() to overwrite
                ?.addOnSuccessListener {
                    if (!isAdded || _binding == null) return@addOnSuccessListener
                    setLoadingState(false, true)
                    Toast.makeText(context, getString(R.string.profile_updated_successfully), Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Employer profile successfully updated/created.")
                    imageUri = null // Reset imageUri after successful upload and save
                }
                ?.addOnFailureListener { e ->
                    Log.e(TAG, "Error saving profile to Firestore", e)
                    handleSaveError(getString(R.string.profile_update_failed_error, e.message))
                }
        }?.addOnFailureListener { e ->
            Log.e(TAG, "Failed to fetch existing profile before save", e)
            handleSaveError(getString(R.string.profile_update_failed_error, e.message))
        }
    }

    private fun handleRoleChangeRequest(roleType: String, roleNameDisplay: String) {
        if (currentUser == null || employerProfileDocRef == null) {
            Toast.makeText(context, getString(R.string.user_not_logged_in_error), Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_role_change_title))
            .setMessage(getString(R.string.confirm_role_change_message, roleNameDisplay))
            .setPositiveButton(getString(R.string.yes_request)) { dialog, _ ->
                sendRoleChangeRequest(roleType, roleNameDisplay)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendRoleChangeRequest(roleType: String, roleNameForToast: String) {
        setLoadingState(true)
        val updates = hashMapOf<String, Any>()
        when (roleType) {
            "ORGANIZER" -> {
                updates["interestedInBecomingOrganizer"] = true
                updates["roleChangeRequestStatus"] = "PENDING_ORGANIZER"
            }
            "VOLUNTEER" -> {
                updates["interestedInBecomingVolunteer"] = true
                updates["roleChangeRequestStatus"] = "PENDING_VOLUNTEER"
            }
        }
        // Ensure lastUpdatedTimestamp is also updated for this change
        updates["lastUpdatedTimestamp"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

        employerProfileDocRef?.set(updates, SetOptions.merge())
            ?.addOnSuccessListener {
                if (!isAdded || _binding == null) return@addOnSuccessListener
                setLoadingState(false)
                Toast.makeText(context, getString(R.string.role_request_sent_toast, roleNameForToast), Toast.LENGTH_LONG).show()
                loadProfileData() // Reload to update UI state
            }
            ?.addOnFailureListener { e ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                setLoadingState(false)
                Log.e(TAG, "Failed to send role change request", e)
                Toast.makeText(context, getString(R.string.role_request_failed_toast, e.message), Toast.LENGTH_LONG).show()
            }
    }

    private fun setLoadingState(isLoading: Boolean, enableSaveButtonAfterLoad: Boolean = false) {
        if (!isAdded || _binding == null) return
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonSaveChanges.isEnabled = if (isLoading) false else enableSaveButtonAfterLoad
        // Disable role change buttons while loading/saving general profile
        if (isLoading && !enableSaveButtonAfterLoad) { // Only disable during main profile save/load, not after role change
            binding.buttonBecomeOrganizer.isEnabled = false
            binding.buttonBecomeVolunteer.isEnabled = false
        } else if (!isLoading && enableSaveButtonAfterLoad){ // After main load, re-evaluate based on profile state
            loadProfileData() // This will call updateRoleChangeUI
        }
    }

    private fun handleSaveError(errorMessage: String?) {
        if (!isAdded || _binding == null) return
        setLoadingState(false, true) // Re-enable save button
        Toast.makeText(context, errorMessage ?: getString(R.string.unknown_error_occurred), Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


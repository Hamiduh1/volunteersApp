package com.example.volunteersApp.organizer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
//import androidx.compose.ui.semantics.dismiss
//import androidx.compose.ui.semantics.error
//import androidx.compose.ui.semantics.text
//import androidx.glance.visibility
import androidx.navigation.activity
import com.bumptech.glide.Glide
import com.example.volunteersApp.LoginActivity // Assuming LoginActivity is your entry point after logout/role change
import com.example.volunteersApp.MainActivity // For Volunteer role
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentOrganizerProfileBinding
import com.example.volunteersApp.EmployerMainActivity // For Employer role
// import com.example.volunteersApp.organizer.OrganizerMainActivity // Already imported
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class OrganizerProfileFragment : Fragment() {

    private var _binding: FragmentOrganizerProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var currentUser: FirebaseUser? = null

    private var imageUri: Uri? = null

    // Define constants for user roles
    companion object {
        private const val TAG = "OrganizerProfileFrag"
        private const val ROLE_ORGANIZER = "organizer"
        private const val ROLE_EMPLOYER = "employer"
        private const val ROLE_VOLUNTEER = "volunteer"

        @JvmStatic
        fun newInstance(): OrganizerProfileFragment {
            return OrganizerProfileFragment()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!isAdded || _binding == null) {
            Log.w(TAG, "pickImageLauncher callback: Fragment not added or binding is null.")
            return@registerForActivityResult
        }
        if (result.resultCode == Activity.RESULT_OK && result.data != null && result.data!!.data != null) {
            imageUri = result.data!!.data
            binding.organizerProfileImage.setImageURI(imageUri)
            uploadProfileImage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentOrganizerProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        currentUser = mAuth.currentUser

        if (currentUser == null) {
            Log.w(TAG, "User not logged in.")
            Toast.makeText(context, R.string.user_not_logged_in_profile, Toast.LENGTH_LONG).show()
            // Optional: navigate to login
            // val intent = Intent(activity, LoginActivity::class.java)
            // intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // startActivity(intent)
            // activity?.finish()
            return
        }

        loadOrganizerProfile()
        setupUIListeners()
    }

    private fun setupUIListeners() {
        if (_binding == null) {
            Log.e(TAG, "Binding is null in setupUIListeners. Cannot set listeners.")
            return
        }
        Log.d(TAG, "setupUIListeners called")

        binding.organizerLogoutButtonFromProfile.setOnClickListener {
            (activity as? OrganizerMainActivity)?.promptLogout()
        }

        binding.organizerProfileImage.setOnClickListener {
            openGalleryForImage()
        }

        binding.editProfileImageButton.setOnClickListener {
            openGalleryForImage()
        }

        binding.saveProfileButton.setOnClickListener {
            saveProfileChanges()
        }

        binding.buttonSwitchToEmployer.setOnClickListener {
            switchUserRole(ROLE_EMPLOYER, getString(R.string.role_employer))
        }

        binding.buttonSwitchToVolunteer.setOnClickListener {
            switchUserRole(ROLE_VOLUNTEER, getString(R.string.role_volunteer))
        }
    }

    private fun switchUserRole(newRole: String, newRoleDisplay: String) {
        if (currentUser == null) {
            Toast.makeText(context, R.string.user_not_logged_in_profile, Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        val userDocRef = db.collection("users").document(currentUser!!.uid)

        // It's good practice to fetch the current role to avoid unnecessary writes
        // or to check if the user is already in that role.
        // For simplicity here, we'll just update.
        userDocRef.set(mapOf("userRole" to newRole), SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "User role updated to $newRole successfully.")
                binding.progressBar.visibility = View.GONE
                // Show a dialog and then restart/redirect
                showRoleUpdatedDialog(newRoleDisplay, newRole)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update user role to $newRole", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, R.string.profile_role_update_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRoleUpdatedDialog(newRoleDisplay: String, newRoleTechnical: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.profile_role_updated_title)
            .setMessage(getString(R.string.profile_role_updated_message, newRoleDisplay))
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                redirectToCorrectActivity(newRoleTechnical)
            }
            .setCancelable(false) // User must acknowledge
            .show()
    }

    private fun redirectToCorrectActivity(role: String) {
        val intent = when (role) {
            ROLE_EMPLOYER -> Intent(activity, EmployerMainActivity::class.java)
            ROLE_VOLUNTEER -> Intent(activity, MainActivity::class.java)
            ROLE_ORGANIZER -> Intent(activity, OrganizerMainActivity::class.java) // If you add switching back to organizer
            else -> {
                Log.e(TAG, "Unknown role: $role, redirecting to LoginActivity.")
                Intent(activity, LoginActivity::class.java) // Fallback
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finishAffinity() // Finish all activities in the current task stack
    }


    private fun openGalleryForImage() {
        // ... (existing code is fine)
        Log.d(TAG, "openGalleryForImage called")
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            pickImageLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching image picker", e)
            Toast.makeText(context, R.string.error_opening_gallery, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadOrganizerProfile() {
        // ... (existing code is fine, but ensure you also check the user's current role if you want to hide/show switch buttons)
        if (!isAdded || _binding == null) {
            Log.w(TAG, "loadOrganizerProfile: Fragment not added or binding is null.")
            return
        }
        Log.d(TAG, "loadOrganizerProfile called for user: ${currentUser?.uid}")
        binding.progressBar.visibility = View.VISIBLE
        currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (!isAdded || _binding == null) {
                        Log.w(TAG, "loadOrganizerProfile onSuccess: Fragment not added or binding is null.")
                        return@addOnSuccessListener
                    }
                    if (document.exists()) {
                        Log.d(TAG, "Profile document found for user: ${user.uid}")
                        binding.organizerNameEditText.setText(document.getString("name"))
                        binding.organizerEmailEditText.setText(user.email) // Email from Auth is more reliable
                        binding.organizerBioEditText.setText(document.getString("bio"))
                        binding.organizerOrganizationEditText.setText(document.getString("organizationName"))

                        val profileImageUrl = document.getString("profileImageUrl")
                        if (!profileImageUrl.isNullOrEmpty()) {
                            if (isAdded) {
                                Glide.with(this@OrganizerProfileFragment)
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_person_placeholder)
                                    .error(R.drawable.ic_person_placeholder)
                                    .circleCrop()
                                    .into(binding.organizerProfileImage)
                            }
                        } else {
                            binding.organizerProfileImage.setImageResource(R.drawable.ic_person_placeholder)
                        }

                        // Optionally, hide switch buttons based on current role
                        val currentRole = document.getString("userRole")
                        binding.buttonSwitchToEmployer.visibility = if (currentRole == ROLE_EMPLOYER) View.GONE else View.VISIBLE
                        binding.buttonSwitchToVolunteer.visibility = if (currentRole == ROLE_VOLUNTEER) View.GONE else View.VISIBLE

                    } else {
                        Log.w(TAG, "No profile document found for user: ${user.uid}")
                        binding.organizerEmailEditText.setText(user.email)
                        binding.organizerProfileImage.setImageResource(R.drawable.ic_person_placeholder)
                        // If no profile, maybe show all role switch options or a default set
                        binding.buttonSwitchToEmployer.visibility = View.VISIBLE
                        binding.buttonSwitchToVolunteer.visibility = View.VISIBLE
                    }
                    binding.progressBar.visibility = View.GONE
                }
                .addOnFailureListener { exception ->
                    if (!isAdded || _binding == null) {
                        Log.w(TAG, "loadOrganizerProfile onFailure: Fragment not added or binding is null.")
                        return@addOnFailureListener
                    }
                    Log.e(TAG, "Error fetching profile data.", exception)
                    Toast.makeText(context, R.string.failed_to_load_profile, Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                }
        } ?: run {
            Log.w(TAG, "currentUser was null when trying to load profile details.")
            binding.progressBar.visibility = View.GONE
        }
    }


    private fun uploadProfileImage() {
        // ... (existing code is fine)
        if (!isAdded || _binding == null) {
            Log.w(TAG, "uploadProfileImage: Fragment not added or binding is null.")
            return
        }
        Log.d(TAG, "uploadProfileImage called. Image URI: $imageUri")
        imageUri?.let { uri ->
            binding.progressBar.visibility = View.VISIBLE
            val user = mAuth.currentUser
            if (user == null) {
                Log.w(TAG, "Cannot upload image, user not logged in.")
                Toast.makeText(context, R.string.user_not_logged_in_upload, Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                return@let
            }

            val fileName = "profile_images/${user.uid}"
            val storageRef: StorageReference = storage.reference.child(fileName)

            storageRef.putFile(uri)
                .addOnSuccessListener {
                    Log.d(TAG, "Image uploaded successfully: $fileName")
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        if (!isAdded || _binding == null) {
                            Log.w(TAG, "uploadProfileImage getDownloadUrl onSuccess: Fragment not added or binding is null.")
                            return@addOnSuccessListener
                        }
                        updateProfileImageUrlInFirestore(downloadUrl.toString())
                    }.addOnFailureListener { e ->
                        if (!isAdded || _binding == null) {
                            Log.w(TAG, "uploadProfileImage getDownloadUrl onFailure: Fragment not added or binding is null.")
                            return@addOnFailureListener
                        }
                        Log.e(TAG, "Failed to get download URL.", e)
                        Toast.makeText(context, R.string.image_upload_url_failed, Toast.LENGTH_LONG).show()
                        binding.progressBar.visibility = View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    if (!isAdded || _binding == null) {
                        Log.w(TAG, "uploadProfileImage putFile onFailure: Fragment not added or binding is null.")
                        return@addOnFailureListener
                    }
                    Log.e(TAG, "Image upload failed: $fileName", e)
                    Toast.makeText(context, getString(R.string.image_upload_failed_message, e.message), Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                }
        } ?: Log.d(TAG, "imageUri is null, skipping upload.")
    }

    private fun updateProfileImageUrlInFirestore(imageUrl: String) {
        // ... (existing code is fine)
        if (!isAdded || _binding == null) {
            Log.w(TAG, "updateProfileImageUrlInFirestore: Fragment not added or binding is null.")
            return
        }
        Log.d(TAG, "Updating profileImageUrl in Firestore: $imageUrl")
        val user = mAuth.currentUser
        if (user == null) {
            Log.w(TAG, "Cannot update profile URL, user not logged in.")
            Toast.makeText(context, R.string.user_not_logged_in_update_url, Toast.LENGTH_SHORT).show()
            binding.progressBar.visibility = View.GONE
            return
        }

        db.collection("users").document(user.uid)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                if (!isAdded || _binding == null) {
                    Log.w(TAG, "updateProfileImageUrlInFirestore onSuccess: Fragment not added or binding is null.")
                    return@addOnSuccessListener
                }
                Log.d(TAG, "Profile image URL updated successfully in Firestore.")
                Toast.makeText(context, R.string.profile_image_updated_success, Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                (activity as? OrganizerMainActivity)?.loadOrganizerDataForNavHeader()
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null) {
                    Log.w(TAG, "updateProfileImageUrlInFirestore onFailure: Fragment not added or binding is null.")
                    return@addOnFailureListener
                }
                Log.e(TAG, "Failed to update image URL in Firestore.", e)
                Toast.makeText(context, getString(R.string.profile_image_url_update_failed, e.message), Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
            }
    }


    private fun saveProfileChanges() {
        // ... (existing code is fine)
        if (!isAdded || _binding == null) {
            Log.w(TAG, "saveProfileChanges: Fragment not added or binding is null.")
            return
        }
        Log.d(TAG, "saveProfileChanges called")
        binding.progressBar.visibility = View.VISIBLE

        val name = binding.organizerNameEditText.text.toString().trim()
        val bio = binding.organizerBioEditText.text.toString().trim()
        val organization = binding.organizerOrganizationEditText.text.toString().trim()

        if (name.isEmpty()) {
            binding.organizerNameEditText.error = getString(R.string.error_name_cannot_be_empty)
            binding.organizerNameEditText.requestFocus()
            binding.progressBar.visibility = View.GONE
            return
        }

        currentUser?.let { user ->
            val profileUpdates = hashMapOf<String, Any>()
            profileUpdates["name"] = name
            profileUpdates["bio"] = bio
            profileUpdates["organizationName"] = organization
            // Ensure userRole is also part of the initial save if it's the first time
            // Or ensure it's set during registration

            db.collection("users").document(user.uid)
                .set(profileUpdates, SetOptions.merge()) // Use merge to avoid overwriting userRole if not included here
                .addOnSuccessListener {
                    if (!isAdded || _binding == null) {
                        Log.w(TAG, "saveProfileChanges onSuccess: Fragment not added or binding is null.")
                        return@addOnSuccessListener
                    }
                    Log.d(TAG, "Profile text fields updated successfully.")
                    Toast.makeText(context, R.string.profile_updated_successfully, Toast.LENGTH_SHORT).show()
                    if (imageUri == null) {
                        binding.progressBar.visibility = View.GONE
                    }
                    (activity as? OrganizerMainActivity)?.loadOrganizerDataForNavHeader()
                }
                .addOnFailureListener { e ->
                    if (!isAdded || _binding == null) {
                        Log.w(TAG, "saveProfileChanges onFailure: Fragment not added or binding is null.")
                        return@addOnFailureListener
                    }
                    Log.e(TAG, "Failed to update profile.", e)
                    Toast.makeText(context, getString(R.string.profile_update_failed_message, e.message), Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                }
        } ?: run {
            Log.w(TAG, "currentUser is null. Cannot save profile.")
            Toast.makeText(context, R.string.user_not_logged_in_save_profile, Toast.LENGTH_SHORT).show()
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called, setting _binding to null")
        _binding = null
    }
}


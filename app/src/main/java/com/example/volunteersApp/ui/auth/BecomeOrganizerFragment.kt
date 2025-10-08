package com.example.volunteersApp.ui.auth // Or your preferred package

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.semantics.error
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.activity
import androidx.navigation.ui.NavigationUI
import com.example.volunteersApp.LoginActivity // To restart the app flow
import com.example.volunteersApp.organizer.OrganizerMainActivity // To navigate to after becoming organizer
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentBecomeOrganizerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class BecomeOrganizerFragment : Fragment() {

    private var _binding: FragmentBecomeOrganizerBinding? = null
    private val binding get() = _binding!!

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentUser: FirebaseUser? = null
    private lateinit var navController: NavController

    companion object {
        private const val TAG = "BecomeOrganizerFrag"
        private const val USERS_COLLECTION = "users"
        // private const val ORGANIZATIONS_COLLECTION = "organizations" // If creating separate org docs
        private const val ROLE_ORGANIZER = "organizer"
        private const val ROLE_VOLUNTEER = "volunteer" // In case you need to reference it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        currentUser = mAuth.currentUser
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBecomeOrganizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        setupToolbar()

        if (currentUser == null) {
            Log.w(TAG, "User is null. This screen should not be accessible.")
            Toast.makeText(context, getString(R.string.error_user_not_authenticated_critical), Toast.LENGTH_LONG).show()
            // Force navigation to login or home if user somehow lands here without auth
            // This is a safeguard; primary navigation guards should prevent this.
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finishAffinity()
            return
        }

        // Pre-fill email if available from user's main profile (optional)
        // binding.etOrganizationContactEmail.setText(currentUser?.email)

        binding.btnSubmitOrganizationDetails.setOnClickListener {
            submitOrganizationRegistration()
        }
    }

    private fun setupToolbar() {
        // Ensure the toolbar ID in fragment_become_organizer.xml is correct
        // (e.g., binding.toolbarBecomeOrganizer)
        if (activity is AppCompatActivity) {
            (activity as AppCompatActivity).setSupportActionBar(binding.toolbarBecomeOrganizer)
        }
        NavigationUI.setupWithNavController(binding.toolbarBecomeOrganizer, navController)
        binding.toolbarBecomeOrganizer.title = getString(R.string.title_become_an_organizer)
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val orgName = binding.etOrganizationName.text.toString().trim()
        if (TextUtils.isEmpty(orgName)) {
            binding.tilOrganizationName.error = getString(R.string.error_organization_name_required)
            isValid = false
        } else {
            binding.tilOrganizationName.error = null
        }

        val orgEmail = binding.etOrganizationContactEmail.text.toString().trim()
        if (TextUtils.isEmpty(orgEmail)) {
            binding.tilOrganizationContactEmail.error = getString(R.string.error_organization_email_required)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(orgEmail).matches()) {
            binding.tilOrganizationContactEmail.error = getString(R.string.error_invalid_email_format)
            isValid = false
        } else {
            binding.tilOrganizationContactEmail.error = null
        }

        val orgDescription = binding.etOrganizationDescription.text.toString().trim()
        if (TextUtils.isEmpty(orgDescription)) {
            binding.tilOrganizationDescription.error = getString(R.string.error_organization_description_required)
            isValid = false
        } else {
            binding.tilOrganizationDescription.error = null
        }
        // Add more validations as needed (phone, address, etc.)
        return isValid
    }

    private fun submitOrganizationRegistration() {
        if (!validateInputs()) {
            return
        }

        setLoadingState(true)

        val organizationName = binding.etOrganizationName.text.toString().trim()
        val organizationContactEmail = binding.etOrganizationContactEmail.text.toString().trim()
        val organizationDescription = binding.etOrganizationDescription.text.toString().trim()
        // Add any other fields from your layout (website, address, etc.)

        val userId = currentUser?.uid
        if (userId == null) {
            handleSubmissionError(getString(R.string.error_user_id_missing_submission))
            return
        }

        // Prepare the data to update the user's document
        val userUpdates = hashMapOf<String, Any>(
            "userRole" to ROLE_ORGANIZER, // Update role
            "organizationName" to organizationName,
            "organizationContactEmail" to organizationContactEmail,
            "organizationDescription" to organizationDescription
            // Add other collected fields here:
            // "organizationWebsite" to binding.etOrganizationWebsite.text.toString().trim(),
        )

        // Update the user's document in Firestore
        db.collection(USERS_COLLECTION).document(userId)
            .set(userUpdates, SetOptions.merge()) // SetOptions.merge() is important
            .addOnSuccessListener {
                Log.i(TAG, "Successfully registered user $userId as an organizer.")
                Toast.makeText(context, getString(R.string.organizer_registration_successful), Toast.LENGTH_LONG).show()
                setLoadingState(false)

                // Critical Step: Redirect to the Organizer flow
                // This usually involves clearing the current task stack and starting OrganizerMainActivity
                val intent = Intent(activity, OrganizerMainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                activity?.finishAffinity() // Finish all activities in the current (volunteer) task
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error registering as organizer for user $userId", e)
                handleSubmissionError("${getString(R.string.error_registration_failed_firebase)}: ${e.message}")
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBarBecomeOrganizer.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmitOrganizationDetails.isEnabled = !isLoading
        // Disable text fields during loading if desired
        binding.etOrganizationName.isEnabled = !isLoading
        binding.etOrganizationContactEmail.isEnabled = !isLoading
        binding.etOrganizationDescription.isEnabled = !isLoading
    }

    private fun handleSubmissionError(errorMessage: String) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        setLoadingState(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}



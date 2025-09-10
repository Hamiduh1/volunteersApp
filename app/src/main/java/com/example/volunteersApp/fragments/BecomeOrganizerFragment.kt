package com.example.volunteersApp.ui.auth // Or your preferred package for this fragment

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
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentBecomeOrganizerBinding // ViewBinding class
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.text.isNotEmpty
import kotlin.text.trim

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
        private const val ORGANIZATIONS_COLLECTION = "organizations" // Optional: if you have a separate collection
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
            // This shouldn't typically happen if navigation to this fragment is protected
            Log.w(TAG, "User is null. Navigating to login or home.")
            // navController.navigate(R.id.action_global_to_login) // Example
            Toast.makeText(context, getString(R.string.error_user_not_authenticated), Toast.LENGTH_LONG).show()
            navController.popBackStack() // Go back
            return
        }

        binding.btnSubmitOrganizationDetails.setOnClickListener {
            submitOrganizationDetails()
        }
    }

    private fun setupToolbar() {
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

        // Add more validation for other fields if you add them

        return isValid
    }

    private fun submitOrganizationDetails() {
        if (!validateInputs()) {
            return
        }

        binding.progressBarBecomeOrganizer.visibility = View.VISIBLE
        binding.btnSubmitOrganizationDetails.isEnabled = false

        val organizationName = binding.etOrganizationName.text.toString().trim()
        val organizationContactEmail = binding.etOrganizationContactEmail.text.toString().trim()
        val organizationDescription = binding.etOrganizationDescription.text.toString().trim()

        val userId = currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "User ID is null during submission.")
            Toast.makeText(context, getString(R.string.error_user_id_missing_submission), Toast.LENGTH_LONG).show()
            binding.progressBarBecomeOrganizer.visibility = View.GONE
            binding.btnSubmitOrganizationDetails.isEnabled = true
            return
        }

        // --- Option 1: Update User Document with Organizer Info & Role ---
        val userUpdates = hashMapOf<String, Any>(
            "role" to "organizer", // Or "pending_organizer" if approval needed
            "organizationName" to organizationName,
            "organizationContactEmail" to organizationContactEmail
        )
        if (organizationDescription.isNotEmpty()) {
            userUpdates["organizationDescription"] = organizationDescription
        }
        // Add other organization-specific fields to the user document if desired

        db.collection(USERS_COLLECTION).document(userId)
            .set(userUpdates, SetOptions.merge()) // Use merge to not overwrite other user data
            .addOnSuccessListener {
                Log.i(TAG, "User role updated and organization details saved successfully for user: $userId")
                Toast.makeText(context, getString(R.string.organizer_application_successful), Toast.LENGTH_LONG).show()
                binding.progressBarBecomeOrganizer.visibility = View.GONE

                // TODO: Navigate to an appropriate screen (e.g., Organizer Dashboard or a confirmation page)
                // Example: Assume you have an action to go to an organizer home/dashboard
                // navController.navigate(BecomeOrganizerFragmentDirections.actionBecomeOrganizerFragmentToOrganizerDashboard())
                // For now, let's just pop back or go to home.
                try {
                    // If you have a specific destination after becoming an organizer
                    // val action = BecomeOrganizerFragmentDirections.actionBecomeOrganizerFragmentToNavHome() // Or to an organizer specific screen
                    // navController.navigate(action)

                    // For now, let's assume it navigates back to the profile, which should then reflect the new role
                    navController.popBackStack(R.id.nav_profile, false) // Go back to profile, don't pop profile itself
                    // Or if you want to go to the main home and clear backstack
                    // navController.navigate(R.id.nav_home, null, NavOptions.Builder().setPopUpTo(R.id.mobile_navigation, true).build())


                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Navigation failed after becoming organizer: ${e.message}")
                    // Fallback navigation if the specific action isn't found
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        // If no previous entry, navigate to a default safe location like home
                        if (navController.graph.findNode(R.id.nav_home) != null) {
                            navController.navigate(R.id.nav_home)
                        }
                    }
                }

            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating user role/organization details", e)
                Toast.makeText(context, "${getString(R.string.error_submission_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
                binding.progressBarBecomeOrganizer.visibility = View.GONE
                binding.btnSubmitOrganizationDetails.isEnabled = true
            }

        // --- Option 2: Create a Separate 'organizations' Collection Document ---
        // This is useful if organizations are distinct entities that multiple users might be part of,
        // or if they have a lot of specific data.
        /*
        val organizationData = hashMapOf(
            "name" to organizationName,
            "contactEmail" to organizationContactEmail,
            "description" to organizationDescription,
            "primaryAdminUserId" to userId, // Link to the user who created it
            "createdAt" to FieldValue.serverTimestamp()
            // ... other organization fields
        )
        db.collection(ORGANIZATIONS_COLLECTION)
            .add(organizationData)
            .addOnSuccessListener { documentReference ->
                val organizationId = documentReference.id
                Log.i(TAG, "Organization created with ID: $organizationId")

                // Now update the user's role and link them to this organizationId
                val userRoleUpdate = hashMapOf<String, Any>(
                    "role" to "organizer",
                    "organizationId" to organizationId // Store ref to the org doc
                )
                db.collection(USERS_COLLECTION).document(userId)
                    .set(userRoleUpdate, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.i(TAG, "User role updated and linked to organization $organizationId")
                        Toast.makeText(context, "Organization details submitted successfully!", Toast.LENGTH_LONG).show()
                        binding.progressBarBecomeOrganizer.visibility = View.GONE
                        // TODO: Navigate
                        navController.popBackStack()
                    }
                    .addOnFailureListener { e -> /* Handle user update failure */ }
            }
            .addOnFailureListener { e -> /* Handle organization creation failure */ }
        */
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important to avoid memory leaks
    }
}

package com.example.volunteersApp.organizer // Adjust package name as needed

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
//import androidx.compose.ui.semantics.text
import androidx.navigation.fragment.findNavController
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentOrganizerDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class OrganizerDashboardFragment : Fragment() {

    private var _binding: FragmentOrganizerDashboardBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    // Access it only after _binding has been initialized in onCreateView and before it's nullified in onDestroyView.
    // Consider adding a check before using it if there's any doubt about the lifecycle state.
    private val binding get() = _binding!!

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Companion object for TAG
    companion object {
        private const val TAG = "OrganizerDashboard" // More specific TAG
       //  @JvmStatic // Only needed if you plan to call this from Java without Kotlin extensions
       //  fun newInstance() = OrganizerDashboardFragment() // Standard newInstance if no args

        @JvmStatic // Make it a true static method from Java's perspective
        fun newInstance(): OrganizerDashboardFragment {
            val fragment = OrganizerDashboardFragment()
            // If you needed to pass arguments, do it here:
            // val args = Bundle()
            // args.putString("some_key", "some_value")
            // fragment.arguments = args
            return fragment
        }



    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentOrganizerDashboardBinding.inflate(inflater, container, false)
        return binding.root // Safe here because _binding is just assigned
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadOrganizerData()
        setupUIListeners()
    }

    private fun loadOrganizerData() {
        Log.d(TAG, "loadOrganizerData called")
        val currentUser = mAuth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No current user. Cannot load organizer data.")
            // Ensure binding is not null before accessing UI, though in this path it's usually safe
            // if called directly from onViewCreated.
            if (_binding != null) {
                binding.welcomeTextView.text = getString(R.string.welcome_organizer_placeholder)
            }
            // Optional: Consider navigating to login if not already handled by Activity
            // findNavController().navigate(R.id.action_global_loginActivity) // Example
            return
        }

        val userId = currentUser.uid
        Log.d(TAG, "Fetching data for organizer UID: $userId")
        val userDocRef = db.collection("users").document(userId)

        userDocRef.get()
            .addOnSuccessListener { documentSnapshot ->
                // CRITICAL CHECK: Ensure fragment view is still valid
                if (!isAdded || _binding == null) {
                    Log.w(TAG, "Fragment not added or binding is null in onSuccessListener. View likely destroyed.")
                    return@addOnSuccessListener
                }

                if (documentSnapshot.exists()) {
                    val name = documentSnapshot.getString("name")
                    Log.d(TAG, "Organizer document exists. Name: $name")
                    binding.welcomeTextView.text = if (!name.isNullOrEmpty()) {
                        getString(R.string.welcome_organizer_placeholder_dynamic, name)
                    } else {
                        getString(R.string.welcome_organizer_placeholder)
                    }
                } else {
                    Log.w(TAG, "Organizer document does not exist for UID: $userId")
                    binding.welcomeTextView.text = getString(R.string.welcome_organizer_placeholder)
                }
            }
            .addOnFailureListener { e ->
                // CRITICAL CHECK: Ensure fragment view is still valid
                if (!isAdded || _binding == null) {
                    Log.w(TAG, "Fragment not added or binding is null in onFailureListener. View likely destroyed.")
                    return@addOnFailureListener
                }
                Log.e(TAG, "Error fetching organizer data for UID: $userId", e)
                binding.welcomeTextView.text = getString(R.string.welcome_organizer_placeholder)
                Toast.makeText(context, R.string.error_failed_to_load_profile_data, Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupUIListeners() {
        // Ensure binding is not null before setting up listeners.
        // This is generally safe if called from onViewCreated.
        if (_binding == null) {
            Log.e(TAG, "Binding is null in setupUIListeners. Cannot set up listeners.")
            return
        }
        Log.d(TAG, "setupUIListeners called")

        binding.createEventButton.setOnClickListener {
            Log.d(TAG, "Create New Event button clicked.")
            try {
                // Ensure R.id.action_organizerDashboardFragment_to_createEventActivityDestination is valid in your nav graph
                // and originates from OrganizerDashboardFragment.
                // Replace 'createEventActivityDestination' if your actual destination ID is different
                // e.g., action_organizerDashboardFragment_to_nav_host_event_organizer_side if using the host event fragment
                findNavController().navigate(R.id.action_organizerDashboardFragment_to_nav_host_event_organizer_side) // Example assuming nav_host_event_organizer_side is the target
                Toast.makeText(context, R.string.navigating_to_create_event, Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Navigation to create event failed. Action not found or invalid.", e)
                Toast.makeText(context, R.string.error_navigation_failed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.manageEventsButton.setOnClickListener {
            Log.d(TAG, "Manage My Events button clicked.")
            try {
                // Ensure R.id.action_organizerDashboardFragment_to_hostedEventsFragment is valid
                findNavController().navigate(R.id.action_organizerDashboardFragment_to_hostedEventsFragment)
                Toast.makeText(context, R.string.navigating_to_manage_events, Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Navigation to manage events failed. Action not found or invalid.", e)
                Toast.makeText(context, R.string.error_navigation_failed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.viewApplicationsButton.setOnClickListener {
            Log.d(TAG, "View Volunteer Applications button clicked.")
            try {
                // Ensure R.id.action_organizerDashboardFragment_to_organizerApplicationsFragment is valid
                findNavController().navigate(R.id.action_organizerDashboardFragment_to_organizerApplicationsFragment)
                Toast.makeText(context, R.string.navigating_to_view_applications, Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Navigation to view applications failed. Action not found or invalid.", e)
                Toast.makeText(context, R.string.error_navigation_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called, setting _binding to null")
        _binding = null // Important to clear binding to avoid memory leaks
    }

    // Removed newInstance() as it's not strictly necessary for basic fragment instantiation
    // unless you plan to pass arguments via a Bundle in its creation.
    // If you need it:
    // companion object {
    //     @JvmStatic
    //     fun newInstance() = OrganizerDashboardFragment()
    // }
}


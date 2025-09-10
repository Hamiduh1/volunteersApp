package com.example.volunteersApp.employer.ui.applications

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.ui.geometry.isEmpty
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentEmployerApplicationsBinding
//import com.example.volunteersApp.employer.ui.applications.EmployerApplicationsFragment
//import com.example.volunteersApp.employer.models.Application
import com.example.volunteersApp.models.ApplicationModel // Your ApplicationModel
import com.example.volunteersApp.models.EventModel // To fetch event title
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class EmployerApplicationsFragment : Fragment() {

    private var _binding: FragmentEmployerApplicationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var navController: NavController
    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var applicationsListener: ListenerRegistration? = null
    private lateinit var applicationsAdapter: EmployerApplicationsAdapter

    private var eventId: String? = null // Argument passed to this fragment
    private var eventTitle: String? = null // To display the event title

    companion object {
        private const val TAG = "EmpApplicationsFrag"
        const val ARG_EVENT_ID = "event_id" // For navigation arguments
        const val ARG_EVENT_TITLE = "event_title"
        const val ARG_APPLICATION_ID = "application_id"
        //const val ARG_VOLUNTEER_UID = "volunteer_id"
        //const val ARG_VOLUNTEER_NAME = "volunteer_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        arguments?.let {
            eventId = it.getString(ARG_EVENT_ID)
            eventTitle = it.getString(ARG_EVENT_TITLE) // Get title if passed
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployerApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.title_applications_for_event) // "Applications for Event"

        if (eventId == null) {
            Log.e(TAG, "Event ID is missing. Cannot load applications.")
            Toast.makeText(context, "Error: Event ID not provided.", Toast.LENGTH_LONG).show()
            binding.textViewNoApplications.text = getString(R.string.cannot_load_applications_no_ids)
            binding.textViewNoApplications.visibility = View.VISIBLE
            binding.progressBarApplications.visibility = View.GONE
            binding.recyclerViewApplications.visibility = View.GONE
            binding.textViewEventTitleForApplications.text = getString(R.string.select_event_to_see_applications)
            // Consider navController.popBackStack() or disabling the view.
            return
        }

        setupRecyclerView()

        if (eventTitle != null) {
            binding.textViewEventTitleForApplications.text = getString(R.string.applications_for_dynamic_title, eventTitle)
        } else {
            // Fetch event title if not passed
            fetchEventTitle(eventId!!)
        }

        listenForApplications(eventId!!)
    }

    private fun setupRecyclerView() {
        applicationsAdapter = EmployerApplicationsAdapter(requireContext()) { application ->
            // Handle click on an application - navigate to detail view
            Log.d(TAG, "Clicked application: ${application.applicationId} for volunteer ${application.volunteerName}")
            val args = Bundle().apply {
                putString(EmployerApplicationsFragment.ARG_APPLICATION_ID, application.applicationId) // Volunteer's UID
                putString(EmployerApplicationsFragment.ARG_EVENT_ID, eventId)
            }
            // Ensure you have EmployerApplicationDetailFragment and its destination ID
            navController.navigate(R.id.action_employerApplicationsFragment_to_employerApplicationDetailFragment, args)
        }
        binding.recyclerViewApplications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = applicationsAdapter
        }
    }
    private fun fetchEventTitle(currentEventId: String) {
        val eventDocRef = db.collection("events").document(currentEventId) // Assuming "events" is your collection name
        eventDocRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val event = documentSnapshot.toObject(EventModel::class.java)
                event?.title?.let { title ->
                    this.eventTitle = title
                    if (isAdded && _binding != null) { // Check fragment is still valid
                        binding.textViewEventTitleForApplications.text = getString(R.string.applications_for_dynamic_title, title)
                    }
                } ?: run {
                    if (isAdded && _binding != null) {
                        binding.textViewEventTitleForApplications.text = getString(R.string.applications_for_event_with_id, currentEventId)
                    }
                }
            } else {
                if (isAdded && _binding != null) {
                    binding.textViewEventTitleForApplications.text = getString(R.string.applications_for_event_with_id, currentEventId)
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error fetching event title", e)
            if (isAdded && _binding != null) {
                binding.textViewEventTitleForApplications.text = getString(R.string.applications_for_event_with_id, currentEventId)
            }
        }
    }


    private fun listenForApplications(forEventId: String) {
        binding.progressBarApplications.visibility = View.VISIBLE
        binding.textViewNoApplications.visibility = View.GONE
        binding.recyclerViewApplications.visibility = View.GONE

        val applicationsQuery = db.collectionGroup("applications") // Use collectionGroup if "applications" is a subcollection
            .whereEqualTo("eventId", forEventId) // Filter by the specific eventId
        // .orderBy("applicationTimestamp", Query.Direction.DESCENDING) // Optional: order by date

        applicationsListener = applicationsQuery.addSnapshotListener { snapshots, e ->
            if (!isAdded || _binding == null) {
                applicationsListener?.remove()
                return@addSnapshotListener
            }
            binding.progressBarApplications.visibility = View.GONE

            if (e != null) {
                Log.e(TAG, "Listen failed for applications.", e)
                Toast.makeText(context, "Error loading applications.", Toast.LENGTH_SHORT).show()
                binding.textViewNoApplications.text = getString(R.string.error_loading_applications_message, e.localizedMessage)
                binding.textViewNoApplications.visibility = View.VISIBLE
                return@addSnapshotListener
            }

            if (snapshots != null && !snapshots.isEmpty) {
                val applicationsList = snapshots.toObjects(ApplicationModel::class.java)
                // Here, you might need to fetch additional volunteer details (name, profile image)
                // for each application if they are not denormalized in the ApplicationModel.
                // For simplicity now, assuming ApplicationModel has enough data or will be enhanced.
                applicationsAdapter.submitList(applicationsList)
                binding.recyclerViewApplications.visibility = View.VISIBLE
                binding.textViewNoApplications.visibility = View.GONE
            } else {
                Log.d(TAG, "No applications found for event: $forEventId")
                applicationsAdapter.submitList(emptyList()) // Clear the adapter
                binding.textViewNoApplications.text = getString(R.string.no_applications_found_for_this_event)
                binding.textViewNoApplications.visibility = View.VISIBLE
                binding.recyclerViewApplications.visibility = View.GONE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        applicationsListener?.remove()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        applicationsListener?.remove() // Ensure listener is removed
        _binding = null
    }
}

package com.example.volunteersApp.organizer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
//import androidx.compose.ui.geometry.isEmpty
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R
import com.example.volunteersApp.adapters.EventAdapter
import com.example.volunteersApp.databinding.FragmentHostedEventsBinding
import com.example.volunteersApp.models.EventModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.HashSet

class HostedEventsFragment : Fragment(), EventAdapter.OnEventListener {

    // Companion object for TAG and newInstance factory method
    companion object {
        private const val TAG = "HostedEventsFragment"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment HostedEventsFragment.
         */
        @JvmStatic // Ensures this can be called as a static method from Java
        fun newInstance(): HostedEventsFragment {
            return HostedEventsFragment()
        }
    }

    private var _binding: FragmentHostedEventsBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventAdapter: EventAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var mAuth: FirebaseAuth
    private lateinit var storage: FirebaseStorage

    private var eventListenerRegistration: ListenerRegistration? = null

    // ActivityResultLaunchers - consider if these are still needed if navigation is purely via Nav Component
    private val editEventLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "EditEventActivity returned OK. List should refresh via listener.")
            }
        }

    private val createEventLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "CreateEventActivity returned OK. List should refresh via listener.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        db = FirebaseFirestore.getInstance()
        mAuth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()

        eventAdapter = EventAdapter(requireContext(), true, this, HashSet(), storage)
        Log.d(TAG, "EventAdapter (for hosted events) initialized in onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentHostedEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        setupRecyclerView()

        binding.fabCreateNewEvent.setOnClickListener {
            Log.d(TAG, "FAB Create New Event clicked")
            try {
                // Assuming nav_host_event takes a nullable eventId (null for new event)
                val action: NavDirections =
                    HostedEventsFragmentDirections.actionHostedEventsFragmentToNavHostEvent(null)
                NavHostFragment.findNavController(this).navigate(action)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to create event screen failed: ", e)
                Toast.makeText(context, R.string.error_opening_event_creation_screen, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerViewHostedEvents.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventAdapter
            setHasFixedSize(true)
        }
        Log.d(TAG, "RecyclerView adapter set up.")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart called")
        listenForUserHostedEvents()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called")
        eventListenerRegistration?.remove()
        eventListenerRegistration = null
        Log.d(TAG, "Firestore listener for hosted events removed.")
    }

    private fun listenForUserHostedEvents() {
        val currentUser: FirebaseUser? = mAuth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No user logged in for hosted events.")
            if(_binding == null) return
            binding.progressBarHostedEvents.visibility = View.GONE
            binding.textViewStatusHostedEvents.text = getString(R.string.please_log_in_to_see_hosted_events)
            binding.textViewStatusHostedEvents.visibility = View.VISIBLE
            binding.recyclerViewHostedEvents.visibility = View.GONE
            binding.fabCreateNewEvent.visibility = View.GONE
            eventAdapter.submitList(emptyList())
            return
        }

        if(_binding == null) return

        binding.fabCreateNewEvent.visibility = View.VISIBLE
        val userId = currentUser.uid
        Log.d(TAG, "Listening for hosted events for user UID: $userId")

        binding.progressBarHostedEvents.visibility = View.VISIBLE
        binding.textViewStatusHostedEvents.visibility = View.GONE
        binding.recyclerViewHostedEvents.visibility = View.GONE

        val query: Query = db.collection("events")
            .whereEqualTo("organizerId", userId)
            .orderBy("eventDateTime", Query.Direction.DESCENDING)

        eventListenerRegistration?.remove()

        eventListenerRegistration = query.addSnapshotListener { snapshots, e ->
            if (!isAdded || _binding == null) {
                Log.w(TAG, "Fragment not attached or binding is null when snapshot listener received data.")
                return@addSnapshotListener
            }
            binding.progressBarHostedEvents.visibility = View.GONE

            if (e != null) {
                Log.e(TAG, "Listen failed for hosted events:", e)
                binding.textViewStatusHostedEvents.text = getString(R.string.error_loading_hosted_events)
                binding.textViewStatusHostedEvents.visibility = View.VISIBLE
                binding.recyclerViewHostedEvents.visibility = View.GONE
                eventAdapter.submitList(emptyList())
                return@addSnapshotListener
            }

            val userEvents = mutableListOf<EventModel>()
            snapshots?.forEach { doc: QueryDocumentSnapshot ->
                try {
                    val event = doc.toObject(EventModel::class.java)
                    userEvents.add(event)
                } catch (parseError: Exception) {
                    Log.e(TAG, "Error parsing document to EventModel: ${doc.id}", parseError)
                }
            }

            if (userEvents.isEmpty()) {
                binding.textViewStatusHostedEvents.text = getString(R.string.no_events_hosted_yet)
                binding.textViewStatusHostedEvents.visibility = View.VISIBLE
                binding.recyclerViewHostedEvents.visibility = View.GONE
            } else {
                binding.textViewStatusHostedEvents.visibility = View.GONE
                binding.recyclerViewHostedEvents.visibility = View.VISIBLE
            }
            eventAdapter.submitList(userEvents)
            Log.d(TAG, "Fetched ${userEvents.size} hosted events (real-time). Adapter list size: ${eventAdapter.itemCount}")
        }
    }

    override fun onEventClick(event: EventModel, position: Int) {
        Log.d(TAG, "onEventClick: Event Title: ${event.title}, ID: ${event.eventId} at position $position")
        val eventId = event.eventId
        if (eventId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.unable_to_open_event_details_no_id, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val action: NavDirections =
                HostedEventsFragmentDirections.actionHostedEventsFragmentToEventDetailFragment(eventId)
            NavHostFragment.findNavController(this).navigate(action)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation to EventDetailFragment failed: ", e)
            Toast.makeText(context, R.string.error_navigating_to_details_general, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onApplyClick(event: EventModel, position: Int) {
        Log.w(TAG, "onApplyClick called in HostedEventsFragment for event: ${event.title} at position $position. This is unexpected.")
        Toast.makeText(context, R.string.organizers_cannot_apply_here, Toast.LENGTH_LONG).show()
    }

    override fun onViewApplicantsClick(event: EventModel, position: Int) {
        Log.d(TAG, "onViewApplicantsClick: Event Title: ${event.title}, ID: ${event.eventId} at position $position")
        val eventId = event.eventId
        val organizerId = mAuth.currentUser?.uid

        if (eventId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.cannot_view_applicants_no_id, Toast.LENGTH_SHORT).show()
            return
        }
        if (organizerId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.error_organizer_id_not_found, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Organizer ID is null, cannot navigate to view applicants.")
            return
        }

        try {
            // This line assumes actionHostedEventsFragmentToViewApplicantsFragment
            // will be updated in the nav graph to accept both eventId and organizerId.
            val action: NavDirections =
                HostedEventsFragmentDirections.actionHostedEventsFragmentToViewApplicantsFragment(organizerId, eventId)
            NavHostFragment.findNavController(this).navigate(action)
        } catch (e: Exception) { // Catch more specific exceptions like IllegalArgumentException if it's due to action not found
            Log.e(TAG, "Navigation to ViewApplicantsFragment failed for event $eventId: ", e)
            Toast.makeText(context, R.string.error_opening_applicants_screen, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEventOptionsClicked(event: EventModel, anchorView: View) {
        Log.d(TAG, "onEventOptionsClicked called for: ${event.title}, ID: ${event.eventId}")
        showEventOptionsMenu(event, anchorView)
    }

    private fun showEventOptionsMenu(event: EventModel, anchorView: View) {
        val eventId = event.eventId
        if (eventId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.cannot_show_options_for_null_event_id, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "showEventOptionsMenu: Event Title: ${event.title}, ID: $eventId")

        PopupMenu(requireContext(), anchorView).apply {
            menuInflater.inflate(R.menu.organizer_event_item_options, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit_event -> {
                        Log.d(TAG, "Edit action selected for event: ${event.title}, ID: $eventId")
                        try {
                            val action: NavDirections =
                                HostedEventsFragmentDirections.actionHostedEventsFragmentToNavHostEvent(eventId)
                            NavHostFragment.findNavController(this@HostedEventsFragment).navigate(action)
                        } catch (e: Exception) {
                            Log.e(TAG, "Navigation to edit event screen failed: ", e)
                            Toast.makeText(context, R.string.error_opening_edit_screen, Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    R.id.action_delete_event -> {
                        Log.d(TAG, "Delete action selected for event: ${event.title}, ID: $eventId")
                        showDeleteConfirmationDialog(event)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showDeleteConfirmationDialog(eventToDelete: EventModel) {
        if (!isAdded || activity == null) {
            Log.e(TAG, "Cannot show delete dialog, fragment not attached or activity is null.")
            Toast.makeText(requireActivity().applicationContext, R.string.delete_event_error_no_context, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Showing delete confirmation for: ${eventToDelete.title}, ID: ${eventToDelete.eventId}")
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.confirm_delete_event_title)
            .setMessage(getString(R.string.confirm_delete_event_message_named, eventToDelete.title ?: getString(R.string.this_event_placeholder)))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                Log.d(TAG, "Confirmed deletion for event: ${eventToDelete.eventId} - ${eventToDelete.title}")
                deleteEventFromFirestore(eventToDelete)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deleteEventFromFirestore(eventToDelete: EventModel) {
        val eventId = eventToDelete.eventId
        if (eventId.isNullOrEmpty()) {
            Toast.makeText(context, R.string.error_event_data_missing_for_deletion, Toast.LENGTH_SHORT).show()
            if(_binding != null) binding.progressBarHostedEvents.visibility = View.GONE
            return
        }
        Log.d(TAG, "Attempting to delete event from Firestore: $eventId")
        if(_binding != null) binding.progressBarHostedEvents.visibility = View.VISIBLE

        db.collection("events").document(eventId)
            .delete()
            .addOnSuccessListener {
                if(!isAdded || _binding == null) return@addOnSuccessListener
                Log.d(TAG, "Event document successfully deleted: $eventId")
                Toast.makeText(context, getString(R.string.event_deleted_successfully_named, eventToDelete.title ?: getString(R.string.event_placeholder)), Toast.LENGTH_SHORT).show()

                if (!eventToDelete.imageUrl.isNullOrEmpty()) {
                    deleteImageFromStorage(eventToDelete.imageUrl!!)
                } else {
                    binding.progressBarHostedEvents.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                if(!isAdded || _binding == null) return@addOnFailureListener
                Log.e(TAG, "Error deleting event document: $eventId", e)
                Toast.makeText(context, getString(R.string.delete_event_error, e.message), Toast.LENGTH_LONG).show()
                binding.progressBarHostedEvents.visibility = View.GONE
            }
    }

    private fun deleteImageFromStorage(imageUrl: String) {
        if(!isAdded || _binding == null) { // Check if fragment is still added and view is available
            Log.w(TAG, "Fragment not attached or binding null in deleteImageFromStorage, skipping delete for $imageUrl")
            return
        }
        Log.d(TAG, "Attempting to delete image from storage: $imageUrl")
        try {
            val photoRef: StorageReference = storage.getReferenceFromUrl(imageUrl)
            photoRef.delete()
                .addOnSuccessListener {
                    if(!isAdded || _binding == null) return@addOnSuccessListener
                    Log.d(TAG, "Event image successfully deleted from storage: $imageUrl")
                    binding.progressBarHostedEvents.visibility = View.GONE
                }
                .addOnFailureListener { exception ->
                    if(!isAdded || _binding == null) return@addOnFailureListener
                    Log.e(TAG, "Error deleting event image from storage: $imageUrl", exception)
                    Toast.makeText(context, R.string.event_doc_deleted_image_fail, Toast.LENGTH_LONG).show()
                    binding.progressBarHostedEvents.visibility = View.GONE
                }
        } catch (e: IllegalArgumentException) {
            if(!isAdded || _binding == null) return // Check again
            Log.e(TAG, "Invalid image URL for deletion: $imageUrl", e)
            Toast.makeText(context, R.string.failed_to_delete_image_invalid_url, Toast.LENGTH_SHORT).show()
            binding.progressBarHostedEvents.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        eventListenerRegistration?.remove()
        eventListenerRegistration = null
        _binding = null
        Log.d(TAG, "onDestroyView called, binding and listener registration set to null")
    }
}

package com.example.volunteersApp.organizer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
//import androidx.glance.visibility
import androidx.lifecycle.ViewModelProvider
// import androidx.lifecycle.observe // observe is usually an extension function, not a direct import like this
import androidx.navigation.fragment.findNavController // For potential navigation on item click
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentOrganizerActivitySummaryBinding
import com.example.volunteersApp.models.OrganizerActivityViewModel
// import com.example.volunteersApp.models.EventWithVolunteerCount // Not directly used in this snippet
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.organizer.OrganizerActivityViewModelFactory
import com.example.volunteersApp.repository.ApplicationRepository
import com.example.volunteersApp.repository.EventRepository
import com.example.volunteersApp.adapters.HostedEventsSummaryAdapter
import com.example.volunteersApp.adapters.EventsWithVolunteersSummaryAdapter
import com.google.firebase.auth.FirebaseAuth
// import kotlin.jvm.java // Not typically needed for standard Kotlin code

class OrganizerActivitySummaryFragment : Fragment() {

    private var _binding: FragmentOrganizerActivitySummaryBinding? = null
    private val binding get() = _binding!! // Only valid between onCreateView and onDestroyView

    private lateinit var activityViewModel: OrganizerActivityViewModel

    private lateinit var hostedEventsAdapter: HostedEventsSummaryAdapter
    private lateinit var eventsWithVolunteersAdapter: EventsWithVolunteersSummaryAdapter

    private var currentOrganizerId: String? = null

    companion object {
        private const val TAG = "OrgActivitySummary"

        /**
         * Use this factory method to create a new instance of
         * this fragment.
         *
         * @return A new instance of fragment OrganizerActivitySummaryFragment.
         */
        @JvmStatic // Ensures this is callable as a static method from Java
        fun newInstance(): OrganizerActivitySummaryFragment {
            // Your current concise version is also perfectly fine:
            // fun newInstance() = OrganizerActivitySummaryFragment()

            val fragment = OrganizerActivitySummaryFragment()
            // If you needed to pass arguments:
            // val args = Bundle()
            // args.putString(ARG_SOME_PARAMETER, someValue)
            // fragment.arguments = args
            return fragment
        }
        // Example for arguments if needed in the future:
        // private const val ARG_SOME_PARAMETER = "some_parameter"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        // Retrieve arguments if any were passed via newInstance()
        // arguments?.let {
        //     val someParam = it.getString(ARG_SOME_PARAMETER)
        //     // Use someParam
        // }

        currentOrganizerId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentOrganizerId == null) {
            Log.e(TAG, "Organizer ID is null. Cannot initialize ViewModel correctly for data fetching.")
            // Consider showing an error or preventing further setup if ID is crucial early on
        }

        // It's generally safer to initialize ViewModel in onViewCreated or via by viewModels()
        // if context (like requireActivity().application) is needed, but onCreate is also possible.
        val applicationRepository = ApplicationRepository()
        val eventRepository = EventRepository()
        val factory = OrganizerActivityViewModelFactory(
            requireActivity().application, // Ensure context is available
            eventRepository,
            applicationRepository
        )
        activityViewModel = ViewModelProvider(this, factory)[OrganizerActivityViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrganizerActivitySummaryBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView called")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        setupUI()
        observeViewModel()

        if (currentOrganizerId != null) {
            Log.d(TAG, "Fetching initial summary data for organizer: $currentOrganizerId")
            // Pass the non-null currentOrganizerId
            activityViewModel.fetchOrganizerSummaryData(currentOrganizerId!!)
        } else {
            showError(getString(R.string.error_user_not_logged_in_summary))
            setLoadingState(false) // Ensure loading indicator is off
            if (_binding != null) { // Check binding before accessing
                binding.swipeRefreshLayoutSummary.isEnabled = false
            }
        }
    }

    private fun setupUI() {
        if (_binding == null) {
            Log.e(TAG, "Binding is null in setupUI. Cannot set up UI components.")
            return
        }
        Log.d(TAG, "setupUI called")

        // Hosted Events RecyclerView
        hostedEventsAdapter = HostedEventsSummaryAdapter { event ->
            Log.d(TAG, "Hosted event summary clicked: ${event.title}")
            // Example Navigation:
            // val action = OrganizerActivitySummaryFragmentDirections
            //    .actionOrganizerActivitySummaryFragmentToEventDetailsFragment(event.eventId)
            // findNavController().navigate(action)
            Toast.makeText(context, "Clicked hosted: ${event.title}", Toast.LENGTH_SHORT).show()
        }
        binding.recyclerViewHostedEventsSummary.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = hostedEventsAdapter
            setHasFixedSize(true) // Optimization
        }

        // Events with Volunteers RecyclerView
        eventsWithVolunteersAdapter = EventsWithVolunteersSummaryAdapter { item ->
            Log.d(TAG, "Event with volunteers clicked: ${item.event.title}, Count: ${item.confirmedVolunteersCount}")
            // Example Navigation:
            // val action = OrganizerActivitySummaryFragmentDirections
            //    .actionOrganizerActivitySummaryFragmentToEventApplicantsFragment(item.event.eventId)
            // findNavController().navigate(action)
            Toast.makeText(context, "Clicked volunteers: ${item.event.title}", Toast.LENGTH_SHORT).show()
        }
        binding.recyclerViewEventsWithVolunteers.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventsWithVolunteersAdapter
            setHasFixedSize(true) // Optimization
        }

        binding.swipeRefreshLayoutSummary.setOnRefreshListener {
            Log.d(TAG, "Refresh triggered")
            if (currentOrganizerId != null) {
                // It's good practice for refreshData to internally use the existing currentOrganizerId
                // or have a way to re-fetch/confirm it if necessary.
                activityViewModel.refreshData()
            } else {
                binding.swipeRefreshLayoutSummary.isRefreshing = false // Stop the refresh indicator
                showError(getString(R.string.error_cannot_refresh_no_user))
            }
        }
    }

    private fun observeViewModel() {
        if (!isAdded) { // Don't observe if fragment is not added
            Log.w(TAG, "Fragment not added. Cannot observe ViewModel.")
            return
        }
        Log.d(TAG, "observeViewModel called")

        activityViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Ensure binding is available before calling setLoadingState which uses binding
            if (_binding != null) {
                setLoadingState(isLoading)
            }
        }

        activityViewModel.hostedEventsSummary.observe(viewLifecycleOwner) { resource ->
            if (_binding == null || !isAdded) return@observe // Safety check
            when (resource) {
                is Resource.Loading -> {
                    binding.textViewHostedEventsCount.text = getString(R.string.loading_hosted_events)
                }
                is Resource.Success -> {
                    val events = resource.data ?: emptyList()
                    binding.textViewHostedEventsCount.text =
                        getString(R.string.hosted_events_count, events.size)
                    hostedEventsAdapter.submitList(events)
                    binding.textViewNoHostedEvents.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                    Log.d(TAG, if (events.isEmpty()) "No hosted events found." else "Hosted events loaded: ${events.size}")
                }
                is Resource.Error -> {
                    binding.textViewHostedEventsCount.text = getString(R.string.error_hosted_events)
                    hostedEventsAdapter.submitList(emptyList())
                    binding.textViewNoHostedEvents.visibility = View.VISIBLE
                    showError(getString(R.string.error_loading_generic_prefix, "Hosted Events", resource.message))
                    Log.e(TAG, "Error loading hosted events: ${resource.message}")
                }
            }
        }

        activityViewModel.eventsWithConfirmedVolunteers.observe(viewLifecycleOwner) { resource ->
            if (_binding == null || !isAdded) return@observe // Safety check
            when (resource) {
                is Resource.Loading -> {
                    binding.textViewEventsWithVolunteersCount.text = getString(R.string.loading_events_with_volunteers)
                }
                is Resource.Success -> {
                    val items = resource.data ?: emptyList()
                    binding.textViewEventsWithVolunteersCount.text =
                        getString(R.string.events_with_volunteers_count, items.size)
                    eventsWithVolunteersAdapter.submitList(items)
                    binding.textViewNoEventsWithVolunteers.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    Log.d(TAG, if (items.isEmpty()) "No events with confirmed volunteers found." else "Events with volunteers loaded: ${items.size}")
                }
                is Resource.Error -> {
                    binding.textViewEventsWithVolunteersCount.text = getString(R.string.error_events_with_volunteers)
                    eventsWithVolunteersAdapter.submitList(emptyList())
                    binding.textViewNoEventsWithVolunteers.visibility = View.VISIBLE
                    showError(getString(R.string.error_loading_generic_prefix, "Events with Volunteers", resource.message))
                    Log.e(TAG, "Error loading events with volunteers: ${resource.message}")
                }
            }
        }

        activityViewModel.suggestions.observe(viewLifecycleOwner) { suggestionsList ->
            if (_binding == null || !isAdded) return@observe // Safety check
            if (suggestionsList.isNullOrEmpty()) {
                binding.textViewSuggestions.text = getString(R.string.no_suggestions_available)
                binding.textViewNoSuggestions.visibility = View.VISIBLE
            } else {
                binding.textViewSuggestions.text = getString(R.string.suggestions_title) + "\n" + suggestionsList.joinToString(separator = "\n- ", prefix = "- ")
                binding.textViewNoSuggestions.visibility = View.GONE
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        // This method is called from an observer, _binding check should ideally be done by the caller
        // or ensure this method is only called when _binding is guaranteed to be non-null.
        // However, adding a check here too for robustness.
        if (_binding == null) {
            Log.w(TAG, "Binding is null in setLoadingState. Cannot update UI.")
            return
        }

        binding.swipeRefreshLayoutSummary.isRefreshing = isLoading

        if (isLoading && !binding.swipeRefreshLayoutSummary.isRefreshing) {
            if (hostedEventsAdapter.itemCount == 0) binding.progressBarHosted.visibility = View.VISIBLE
            if (eventsWithVolunteersAdapter.itemCount == 0) binding.progressBarEventsWithVolunteers.visibility = View.VISIBLE
        } else {
            binding.progressBarHosted.visibility = View.GONE
            binding.progressBarEventsWithVolunteers.visibility = View.GONE
        }
    }

    private fun showError(message: String?) {
        // 'activity' can be null if the fragment is detached.
        // 'context' is also tied to the fragment's attachment.
        // Using requireActivity().applicationContext or a cached application context
        // can be safer for Toasts that might show when fragment is detaching.
        // However, activity check is a good first step.
        val safeContext = activity ?: return
        Toast.makeText(safeContext, message ?: getString(R.string.unknown_error_occurred), Toast.LENGTH_LONG).show()
        Log.e(TAG, "Error displayed: $message")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")
        // Clear adapters to help prevent memory leaks related to RecyclerView
        if (::hostedEventsAdapter.isInitialized) { // Check if initialized before accessing
            binding.recyclerViewHostedEventsSummary.adapter = null
        }
        if (::eventsWithVolunteersAdapter.isInitialized) {
            binding.recyclerViewEventsWithVolunteers.adapter = null
        }
        _binding = null // Crucial for ViewBinding in Fragments
    }
}


package com.example.volunteersApp.organizer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentOrganizerEventDetailsBinding // Import ViewBinding class

// TODO: Rename argument parameters, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_EVENT_ID = "event_id" // Example argument

/**
 * A simple [Fragment] subclass.
 * Use the [OrganizerEventDetailsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class OrganizerEventDetailsFragment : Fragment() {

    // ViewBinding
    private var _binding: FragmentOrganizerEventDetailsBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView.

    // TODO: Rename and change types of parameters
    private var eventId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            eventId = it.getString(ARG_EVENT_ID)
            // TODO: Use the eventId to fetch event details
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment using ViewBinding
        _binding = FragmentOrganizerEventDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: Initialize your UI elements and load event data
        // Example:
        // binding.textViewEventTitleDetails.text = "Loading event..."
        // eventId?.let { loadEventDetails(it) }
    }

    // private fun loadEventDetails(id: String) {
    //     // TODO: Implement logic to load event details from Firestore or other source
    //     // Update UI elements in binding. e.g.:
    //     // binding.textViewEventTitleDetails.text = event.title
    //     // binding.textViewEventDescriptionDetails.text = event.description
    // }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important to avoid memory leaks
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param eventId ID of the event to display.
         * @return A new instance of fragment OrganizerEventDetailsFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(eventId: String) =
            OrganizerEventDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EVENT_ID, eventId)
                }
            }
    }
}

/** This is handled by MyJobsFragment.kt **/

/**
package com.example.volunteersApp.ui.myactivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.volunteersApp.R
import com.example.volunteersApp.databinding.FragmentMyVolunteerActivityBinding
// Child fragment for "My Applied Events" - this will be your existing AppliedEventsFragment
import com.example.volunteersApp.ui.myactivity.myapplied.MyAppliedEventsFragment // *** USE YOUR EXISTING AppliedEventsFragment HERE ***
// Child fragment for "My Applied Jobs" - you created this in a previous step
import com.example.volunteersApp.ui.myactivity.myapplied.MyAppliedJobsFragment
import com.google.android.material.tabs.TabLayoutMediator

class MyVolunteerActivityFragment : Fragment() {

    private var _binding: FragmentMyVolunteerActivityBinding? = null
    private val binding get() = _binding!!

    private lateinit var myActivityPagerAdapter: MyActivityPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyVolunteerActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myActivityPagerAdapter = MyActivityPagerAdapter(this)
        binding.viewPagerMyActivity.adapter = myActivityPagerAdapter

        TabLayoutMediator(binding.tabLayoutMyActivity, binding.viewPagerMyActivity) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_my_job_applications)
                1 -> getString(R.string.tab_my_event_signups) // Or "My Applied Events"
                else -> null
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Adapter for the ViewPager2
class MyActivityPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2 // Number of tabs

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MyAppliedJobsFragment.newInstance() // The fragment for "My Job Applications"
            1 -> MyAppliedEventsFragment.newInstance() // *** USING YOUR EXISTING AppliedEventsFragment ***
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}

**/
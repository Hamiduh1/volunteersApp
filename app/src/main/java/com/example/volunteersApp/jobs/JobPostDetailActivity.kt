package com.example.volunteersApp.jobs

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

/**
 * Modernized Job Post Detail Activity.
 * Hosts the Jetpack Compose UI and connects it to the JobPostDetailViewModel.
 */
class JobPostDetailActivity : ComponentActivity() {

    private val viewModel: JobPostDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val jobPostId = intent.getStringExtra("JOB_POST_ID")

        if (jobPostId.isNullOrEmpty()) {
            Toast.makeText(this, "Job not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            VolunteersAppTheme {
                JobPostDetailScreen(
                    jobPostId = jobPostId,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

package com.example.volunteersApp.ui.volunteers

import androidx.compose.foundation.layout.*import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventApplication

@Composable
fun ApplicantCard(
    applicant: EventApplication,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onContact: () -> Unit,
    // Add this new parameter for AI functionality
    onGenerateMessage: (prompt: String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    // You should replace this with the actual volunteer profile image URL if available
                    model = applicant.volunteerProfileImageUrl ?: R.drawable.default_profile_image,
                    contentDescription = "Profile image of ${applicant.volunteerName}",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        applicant.volunteerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Display event name if available, good for a general application list
                    if (applicant.eventName.isNotBlank()) {
                        Text(
                            applicant.eventName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    val statusColor = when (applicant.status) {
                        ApplicationStatus.APPROVED -> Color(0xFF4CAF50)
                        ApplicationStatus.REJECTED -> MaterialTheme.colorScheme.error
                        else -> Color.Gray
                    }
                    Text(
                        applicant.status.name,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onContact) {
                    Icon(
                        Icons.Default.Email,
                        "Contact",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Action Buttons ---
            if (applicant.status == ApplicationStatus.PENDING) {
                // Show Accept/Reject buttons for PENDING applications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                // --- AI GENERATE BUTTON for decided applications ---
                OutlinedButton(
                    onClick = {
                        val action = if (applicant.status == ApplicationStatus.APPROVED) "welcoming" else "politely rejecting"
                        val prompt = "Write a short, professional message for a volunteer named ${applicant.volunteerName}, $action them for the event '${applicant.eventName}'. Start the message with 'Hi ${applicant.volunteerName},'."
                        onGenerateMessage(prompt)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Generate with AI",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Generate ${applicant.status.name.lowercase().replaceFirstChar { it.titlecase() }} Message")
                }
            }
        }
    }
}

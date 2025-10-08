package com.example.volunteersApp.organizer

import com.example.volunteersApp.models.EventApplication


class ApplicationDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<EventApplication>() {
    override fun areItemsTheSame(oldItem: EventApplication, newItem: EventApplication): Boolean {
        return oldItem.applicationId == newItem.applicationId
    }

    override fun areContentsTheSame(oldItem: EventApplication, newItem: EventApplication): Boolean {
        return oldItem == newItem
    }
}

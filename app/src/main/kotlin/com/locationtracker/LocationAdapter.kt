package com.locationtracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.locationtracker.databinding.ItemLocationBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class LocationAdapter(
    private val onMapClick: (LocationData) -> Unit
) : ListAdapter<LocationData, LocationAdapter.LocationViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemLocationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LocationViewHolder(binding, onMapClick)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LocationViewHolder(
        private val binding: ItemLocationBinding,
        private val onMapClick: (LocationData) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(location: LocationData) {
            binding.tvCoordinates.text = String.format(
                "%.6f, %.6f",
                location.latitude,
                location.longitude
            )
            
            binding.tvVisitCount.text = binding.root.context.getString(
                R.string.visited_times,
                location.visitCount
            )
            
            // Calculate time spent (from first visit to last visit)
            val timeSpent = location.timestamp - location.firstVisit
            binding.tvTimeSpent.text = binding.root.context.getString(
                R.string.time_spent,
                formatDuration(timeSpent)
            )
            
            binding.tvTimestamp.text = binding.root.context.getString(
                R.string.last_visit,
                formatTimestamp(location.timestamp)
            )
            
            binding.btnViewMap.setOnClickListener {
                onMapClick(location)
            }
        }

        private fun formatDuration(millis: Long): String {
            if (millis < 60_000) {
                return "< 1 min"
            }
            
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                else -> "${minutes}m"
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000} minutes ago"
                diff < 86400_000 -> "${diff / 3600_000} hours ago"
                diff < 604800_000 -> "${diff / 86400_000} days ago"
                else -> {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    class LocationDiffCallback : DiffUtil.ItemCallback<LocationData>() {
        override fun areItemsTheSame(oldItem: LocationData, newItem: LocationData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LocationData, newItem: LocationData): Boolean {
            return oldItem == newItem
        }
    }
}

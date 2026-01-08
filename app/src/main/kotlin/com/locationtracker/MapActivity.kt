package com.locationtracker

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.locationtracker.databinding.ActivityMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure osmdroid
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        databaseHelper = DatabaseHelper(this)

        setupMap()
        loadAndDisplayTrajectory()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }

    private fun loadAndDisplayTrajectory() {
        binding.progressBar.visibility = View.VISIBLE

        // Load locations in background thread
        Thread {
            val locations = databaseHelper.getRawLocationsLast24Hours()

            runOnUiThread {
                binding.progressBar.visibility = View.GONE

                if (locations.isEmpty()) {
                    binding.tvLocationCount.text = getString(R.string.no_trajectory_data)
                    binding.tvTimeRange.text = getString(R.string.no_trajectory_description)
                } else {
                    displayTrajectory(locations)
                }
            }
        }.start()
    }

    private fun displayTrajectory(locations: List<RawLocationData>) {
        if (locations.isEmpty()) return

        // Convert to GeoPoints
        val geoPoints = locations.map { GeoPoint(it.latitude, it.longitude) }

        // Create polyline with gradient colors
        val polyline = Polyline(binding.mapView).apply {
            setPoints(geoPoints)
            outlinePaint.apply {
                strokeWidth = 8f
                color = ContextCompat.getColor(this@MapActivity, R.color.trajectory_color)
                isAntiAlias = true
            }
        }

        // Create gradient effect by drawing multiple segments with different colors
        createGradientPolyline(geoPoints)

        // Add start marker (green)
        val startLocation = locations.first()
        val startMarker = Marker(binding.mapView).apply {
            position = GeoPoint(startLocation.latitude, startLocation.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = getString(R.string.start_marker)
            snippet = formatTimestamp(startLocation.timestamp)
            icon = ContextCompat.getDrawable(this@MapActivity, android.R.drawable.ic_menu_mylocation)
            icon?.setTint(Color.GREEN)
        }
        binding.mapView.overlays.add(startMarker)

        // Add end marker (red)
        val endLocation = locations.last()
        val endMarker = Marker(binding.mapView).apply {
            position = GeoPoint(endLocation.latitude, endLocation.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = getString(R.string.end_marker)
            snippet = formatTimestamp(endLocation.timestamp)
            icon = ContextCompat.getDrawable(this@MapActivity, android.R.drawable.ic_menu_mylocation)
            icon?.setTint(Color.RED)
        }
        binding.mapView.overlays.add(endMarker)

        // Update info card
        binding.tvLocationCount.text = getString(R.string.location_points_count, locations.size)
        binding.tvTimeRange.text = getString(
            R.string.trajectory_time_range,
            formatTimestamp(startLocation.timestamp),
            formatTimestamp(endLocation.timestamp)
        )

        // Zoom to fit all points
        zoomToTrajectory(geoPoints)

        binding.mapView.invalidate()
    }

    private fun createGradientPolyline(geoPoints: List<GeoPoint>) {
        if (geoPoints.size < 2) return

        val totalSegments = geoPoints.size - 1

        for (i in 0 until totalSegments) {
            val segment = Polyline(binding.mapView).apply {
                setPoints(listOf(geoPoints[i], geoPoints[i + 1]))
                outlinePaint.apply {
                    strokeWidth = 8f
                    // Create gradient from blue (start) to red (end)
                    val ratio = i.toFloat() / totalSegments
                    color = interpolateColor(
                        ContextCompat.getColor(this@MapActivity, R.color.trajectory_start),
                        ContextCompat.getColor(this@MapActivity, R.color.trajectory_end),
                        ratio
                    )
                    isAntiAlias = true
                }
            }
            binding.mapView.overlays.add(segment)
        }
    }

    private fun interpolateColor(colorStart: Int, colorEnd: Int, ratio: Float): Int {
        val startA = Color.alpha(colorStart)
        val startR = Color.red(colorStart)
        val startG = Color.green(colorStart)
        val startB = Color.blue(colorStart)

        val endA = Color.alpha(colorEnd)
        val endR = Color.red(colorEnd)
        val endG = Color.green(colorEnd)
        val endB = Color.blue(colorEnd)

        return Color.argb(
            (startA + ratio * (endA - startA)).toInt(),
            (startR + ratio * (endR - startR)).toInt(),
            (startG + ratio * (endG - startG)).toInt(),
            (startB + ratio * (endB - startB)).toInt()
        )
    }

    private fun zoomToTrajectory(geoPoints: List<GeoPoint>) {
        if (geoPoints.isEmpty()) return

        if (geoPoints.size == 1) {
            // Single point - just center on it
            binding.mapView.controller.apply {
                setCenter(geoPoints[0])
                setZoom(16.0)
            }
        } else {
            // Multiple points - calculate bounding box
            var minLat = geoPoints[0].latitude
            var maxLat = geoPoints[0].latitude
            var minLon = geoPoints[0].longitude
            var maxLon = geoPoints[0].longitude

            geoPoints.forEach { point ->
                if (point.latitude < minLat) minLat = point.latitude
                if (point.latitude > maxLat) maxLat = point.latitude
                if (point.longitude < minLon) minLon = point.longitude
                if (point.longitude > maxLon) maxLon = point.longitude
            }

            // Add padding to bounding box
            val latPadding = (maxLat - minLat) * 0.1
            val lonPadding = (maxLon - minLon) * 0.1

            val boundingBox = BoundingBox(
                maxLat + latPadding,
                maxLon + lonPadding,
                minLat - latPadding,
                minLon - lonPadding
            )

            binding.mapView.post {
                binding.mapView.zoomToBoundingBox(boundingBox, true, 100)
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseHelper.close()
    }
}

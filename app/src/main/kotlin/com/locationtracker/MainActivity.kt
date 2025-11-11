package com.locationtracker

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.locationtracker.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var adapter: LocationAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateGpsStatus()
            loadLocations()
            handler.postDelayed(this, 5000) // Update every 5 seconds
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startLocationService()
        } else {
            Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        databaseHelper = DatabaseHelper(this)

        setupRecyclerView()
        setupExportButton()
        checkPermissionsAndStart()
        updateGpsStatus()
    }

    private fun setupRecyclerView() {
        adapter = LocationAdapter { location ->
            openMap(location.latitude, location.longitude)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupExportButton() {
        binding.btnExport.setOnClickListener {
            exportToJson()
        }
    }

    override fun onResume() {
        super.onResume()
        loadLocations()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateGpsStatus() {
        val lastUpdate = databaseHelper.getLastGpsUpdateTime()
        
        if (lastUpdate == 0L) {
            binding.tvGpsStatus.text = getString(R.string.waiting_for_gps)
        } else {
            val timeAgo = formatTimeAgo(lastUpdate)
            binding.tvGpsStatus.text = getString(R.string.last_gps_update, timeAgo)
        }
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000} min ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            diff < 604800_000 -> "${diff / 86400_000} days ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    private fun loadLocations() {
        val locations = databaseHelper.getRecentLocations(10)
        adapter.submitList(locations)
        
        if (locations.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startLocationService()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun openMap(latitude: Double, longitude: Double) {
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
        
        try {
            startActivity(mapIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No map application found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToJson() {
        val locations = databaseHelper.getAllLocations()
        
        if (locations.isEmpty()) {
            Toast.makeText(this, R.string.no_data_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val jsonArray = JSONArray()
            locations.forEach { location ->
                val jsonObject = JSONObject().apply {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("timestamp", location.timestamp)
                    put("first_visit", location.firstVisit)
                    put("visit_count", location.visitCount)
                    put("time_spent_ms", location.timestamp - location.firstVisit)
                }
                jsonArray.put(jsonObject)
            }

            val fileName = "locations_${System.currentTimeMillis()}.json"
            val file = File(cacheDir, fileName)
            FileWriter(file).use { writer ->
                writer.write(jsonArray.toString(2))
            }

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Export Locations"))
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        databaseHelper.close()
    }
}

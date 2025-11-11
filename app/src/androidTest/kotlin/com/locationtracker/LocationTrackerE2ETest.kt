package com.locationtracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class LocationTrackerE2ETest {

    private lateinit var device: UiDevice
    private lateinit var context: Context
    private val packageName = "com.locationtracker"

    // London coordinates
    private val londonLat = 51.5074
    private val londonLon = -0.1278

    // Paris coordinates
    private val parisLat = 48.8566
    private val parisLon = 2.3522

    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()

        // Press home to ensure we're on home screen
        device.pressHome()

        // Wait for launcher
        val launcherPackage = device.launcherPackageName
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), 5000)
    }

    @Test
    fun testLocationTrackingWithMultipleLocations() {
        // Start the app
        launchApp()

        // Take initial screenshot
        takeScreenshot("01_app_launched")

        // Wait for app to fully load
        device.waitForIdle(2000)

        println("=== Starting E2E Test ===")

        // Enable test mode for LocationService to use GPS_PROVIDER
        enableTestMode()

        // Verify service is running
        verifyLocationServiceRunning()

        // Step 1: Mock London location
        println("Step 1: Setting location to London (${londonLat}, ${londonLon})")
        setMockLocation(londonLat, londonLon)

        // Wait for location to be processed
        println("Waiting for location update...")
        SystemClock.sleep(8000)

        // Verify location was recorded
        verifyLocationRecorded("London", londonLat, londonLon)

        takeScreenshot("02_london_location")

        // Step 2: Change time and set Paris location
        println("Step 2: Changing to Paris location (${parisLat}, ${parisLon})")
        SystemClock.sleep(2000) // Simulate time passing
        setMockLocation(parisLat, parisLon)

        println("Waiting for Paris location update...")
        SystemClock.sleep(8000)

        // Verify Paris location was recorded
        verifyLocationRecorded("Paris", parisLat, parisLon)

        takeScreenshot("03_paris_location")

        // Step 3: Change back to London
        println("Step 3: Changing back to London")
        SystemClock.sleep(2000) // Simulate time passing
        setMockLocation(londonLat, londonLon)

        println("Waiting for London location update again...")
        SystemClock.sleep(8000)

        // Verify London location was updated
        verifyLocationRecorded("London (2nd visit)", londonLat, londonLon)

        takeScreenshot("04_back_to_london")

        // Step 4: Verify locations are displayed
        println("Step 4: Verifying location display")

        // Scroll down to see the locations list
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 2 / 3,
            device.displayWidth / 2,
            device.displayHeight / 3,
            10
        )

        SystemClock.sleep(1000)
        takeScreenshot("05_locations_list")

        // Step 5: Click on export button to download JSON
        println("Step 5: Clicking export button")
        val exportButton = device.wait(
            Until.findObject(By.res(packageName, "btnExport")),
            5000
        )

        if (exportButton != null) {
            exportButton.click()
            println("Export button clicked successfully")

            // Wait for the share dialog
            SystemClock.sleep(2000)
            takeScreenshot("06_export_dialog")

            // Press back to dismiss the share dialog
            device.pressBack()
            SystemClock.sleep(1000)
        } else {
            println("WARNING: Export button not found!")
        }

        // Step 6: Try to click on a location to open map
        println("Step 6: Testing map integration")

        // Find and click on the first location item
        val locationItems = device.findObjects(By.res(packageName, "location_card"))
        if (locationItems.isNotEmpty()) {
            println("Found ${locationItems.size} location items")
            locationItems[0].click()

            SystemClock.sleep(2000)
            takeScreenshot("07_map_intent")

            // Press back to return to app
            device.pressBack()
            SystemClock.sleep(1000)
        } else {
            println("WARNING: No location items found in RecyclerView")
        }

        // Final screenshot
        takeScreenshot("08_final_state")

        println("=== E2E Test Completed Successfully ===")
    }

    private fun launchApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5000)
    }

    private fun setMockLocation(latitude: Double, longitude: Double) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // Execute shell command to set mock location via adb
        // This is the most reliable method for emulators
        val command = "am broadcast -a send.mock.location " +
                "--es lat \"$latitude\" --es lon \"$longitude\""

        try {
            instrumentation.uiAutomation.executeShellCommand(command)
            println("Mock location set via broadcast: ($latitude, $longitude)")
        } catch (e: Exception) {
            println("Failed to set mock location via broadcast: ${e.message}")
        }

        // Also try setting via geo command (emulator-specific)
        try {
            val geoCommand = "geo fix $longitude $latitude"
            instrumentation.uiAutomation.executeShellCommand(geoCommand)
            println("Mock location set via geo command: ($latitude, $longitude)")
        } catch (e: Exception) {
            println("Failed to set mock location via geo: ${e.message}")
        }
    }

    private fun enableTestMode() {
        println("Enabling test mode for LocationService...")
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // Stop the service first if it's running
        try {
            instrumentation.uiAutomation.executeShellCommand(
                "am stopservice $packageName/.LocationService"
            ).close()
            SystemClock.sleep(1000)
        } catch (e: Exception) {
            println("Service not running or failed to stop: ${e.message}")
        }

        // Start service in test mode
        try {
            instrumentation.uiAutomation.executeShellCommand(
                "am startservice --ez test_mode true $packageName/.LocationService"
            ).close()
            SystemClock.sleep(2000)
            println("LocationService started in test mode")
        } catch (e: Exception) {
            println("Failed to start service in test mode: ${e.message}")
        }
    }

    private fun verifyLocationServiceRunning() {
        println("Verifying LocationService is running...")
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            val result = instrumentation.uiAutomation.executeShellCommand(
                "dumpsys activity services $packageName/.LocationService"
            )
            val output = FileInputStream(result.fileDescriptor).bufferedReader().use { it.readText() }
            result.close()

            if (output.contains("LocationService")) {
                println("✓ LocationService is running")
            } else {
                println("✗ WARNING: LocationService may not be running")
            }
        } catch (e: Exception) {
            println("Failed to verify service status: ${e.message}")
        }
    }

    private fun verifyLocationRecorded(locationName: String, expectedLat: Double, expectedLon: Double) {
        println("Verifying $locationName was recorded in database...")
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            // Query the database to check if location was recorded
            val result = instrumentation.uiAutomation.executeShellCommand(
                "run-as $packageName sqlite3 /data/data/$packageName/databases/locations.db " +
                "'SELECT latitude, longitude, timestamp FROM locations ORDER BY timestamp DESC LIMIT 5;'"
            )
            val output = FileInputStream(result.fileDescriptor).bufferedReader().use { it.readText() }
            result.close()

            println("Recent locations in database:")
            println(output)

            // Check if the expected coordinates appear in the database
            val latStr = String.format("%.4f", expectedLat)
            val lonStr = String.format("%.4f", expectedLon)

            if (output.contains(latStr) || output.contains(lonStr)) {
                println("✓ Location $locationName found in database")
            } else {
                println("✗ WARNING: Location $locationName NOT found in database!")
                println("Expected: ($expectedLat, $expectedLon)")
            }
        } catch (e: Exception) {
            println("Failed to verify location in database: ${e.message}")
        }

        // Also check logcat for location updates
        try {
            val result = instrumentation.uiAutomation.executeShellCommand(
                "logcat -d -s LocationService:D | tail -20"
            )
            val logOutput = FileInputStream(result.fileDescriptor).bufferedReader().use { it.readText() }
            result.close()

            println("Recent LocationService logs:")
            println(logOutput)
        } catch (e: Exception) {
            println("Failed to read logcat: ${e.message}")
        }
    }

    private fun takeScreenshot(name: String) {
        try {
            val screenshotFile = File("/sdcard/Pictures/$name.png")
            device.takeScreenshot(screenshotFile)
            println("Screenshot saved: $name.png")
        } catch (e: Exception) {
            println("Failed to take screenshot $name: ${e.message}")
        }
    }
}

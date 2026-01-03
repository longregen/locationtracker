package com.locationtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "locations.db"
        private const val DATABASE_VERSION = 5

        private const val TABLE_LOCATIONS = "locations"
        private const val COLUMN_ID = "id"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_FIRST_VISIT = "first_visit"
        private const val COLUMN_VISIT_COUNT = "visit_count"
        private const val COLUMN_ACCURACY = "accuracy"

        private const val TABLE_RAW_LOCATIONS = "raw_locations"
        private const val COLUMN_RAW_ID = "id"
        private const val COLUMN_RAW_LATITUDE = "latitude"
        private const val COLUMN_RAW_LONGITUDE = "longitude"
        private const val COLUMN_RAW_TIMESTAMP = "timestamp"
        private const val COLUMN_RAW_ACCURACY = "accuracy"

        private const val TABLE_LOCATION_NAMES = "location_names"
        private const val COLUMN_NAME_ID = "id"
        private const val COLUMN_NAME_LATITUDE = "latitude"
        private const val COLUMN_NAME_LONGITUDE = "longitude"
        private const val COLUMN_NAME = "name"

        private const val LOCATION_THRESHOLD_METERS = 100.0

        // ~100m at equator is approximately 0.0009 degrees
        private const val LOCATION_THRESHOLD_DEGREES = 0.0009

        private const val PREFS_NAME = "LocationTrackerPrefs"
        private const val KEY_LAST_GPS_UPDATE = "last_gps_update"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        val createLocationsTable = """
            CREATE TABLE $TABLE_LOCATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_LATITUDE REAL NOT NULL,
                $COLUMN_LONGITUDE REAL NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_FIRST_VISIT INTEGER NOT NULL,
                $COLUMN_VISIT_COUNT INTEGER DEFAULT 1,
                $COLUMN_ACCURACY REAL
            )
        """.trimIndent()
        db.execSQL(createLocationsTable)

        val createLocationNamesTable = """
            CREATE TABLE $TABLE_LOCATION_NAMES (
                $COLUMN_NAME_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME_LATITUDE REAL NOT NULL,
                $COLUMN_NAME_LONGITUDE REAL NOT NULL,
                $COLUMN_NAME TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createLocationNamesTable)

        val createRawLocationsTable = """
            CREATE TABLE $TABLE_RAW_LOCATIONS (
                $COLUMN_RAW_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_RAW_LATITUDE REAL NOT NULL,
                $COLUMN_RAW_LONGITUDE REAL NOT NULL,
                $COLUMN_RAW_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_RAW_ACCURACY REAL
            )
        """.trimIndent()
        db.execSQL(createRawLocationsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add first_visit column to existing table
            db.execSQL("ALTER TABLE $TABLE_LOCATIONS ADD COLUMN $COLUMN_FIRST_VISIT INTEGER DEFAULT 0")
            // Update existing rows to use timestamp as first_visit
            db.execSQL("UPDATE $TABLE_LOCATIONS SET $COLUMN_FIRST_VISIT = $COLUMN_TIMESTAMP WHERE $COLUMN_FIRST_VISIT = 0")
        }
        if (oldVersion < 3) {
            // Add accuracy column and create location_names table
            db.execSQL("ALTER TABLE $TABLE_LOCATIONS ADD COLUMN $COLUMN_ACCURACY REAL")

            val createLocationNamesTable = """
                CREATE TABLE $TABLE_LOCATION_NAMES (
                    $COLUMN_NAME_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_NAME_LATITUDE REAL NOT NULL,
                    $COLUMN_NAME_LONGITUDE REAL NOT NULL,
                    $COLUMN_NAME TEXT NOT NULL
                )
            """.trimIndent()
            db.execSQL(createLocationNamesTable)
        }
        if (oldVersion < 4) {
            // Create raw_locations table for storing all GPS readings
            val createRawLocationsTable = """
                CREATE TABLE $TABLE_RAW_LOCATIONS (
                    $COLUMN_RAW_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_RAW_LATITUDE REAL NOT NULL,
                    $COLUMN_RAW_LONGITUDE REAL NOT NULL,
                    $COLUMN_RAW_TIMESTAMP INTEGER NOT NULL,
                    $COLUMN_RAW_ACCURACY REAL
                )
            """.trimIndent()
            db.execSQL(createRawLocationsTable)
        }
        if (oldVersion < 5) {
            // Create indexes for performance optimization
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_locations_timestamp ON $TABLE_LOCATIONS($COLUMN_TIMESTAMP DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_locations_coords ON $TABLE_LOCATIONS($COLUMN_LATITUDE, $COLUMN_LONGITUDE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_timestamp ON $TABLE_RAW_LOCATIONS($COLUMN_RAW_TIMESTAMP DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_location_names_coords ON $TABLE_LOCATION_NAMES($COLUMN_NAME_LATITUDE, $COLUMN_NAME_LONGITUDE)")
        }
    }

    fun insertOrUpdateLocation(latitude: Double, longitude: Double, timestamp: Long, accuracy: Float? = null) {
        val db = writableDatabase

        // Save last GPS update time
        prefs.edit().putLong(KEY_LAST_GPS_UPDATE, timestamp).apply()

        // Always insert into raw_locations table (all GPS readings)
        val rawValues = ContentValues().apply {
            put(COLUMN_RAW_LATITUDE, latitude)
            put(COLUMN_RAW_LONGITUDE, longitude)
            put(COLUMN_RAW_TIMESTAMP, timestamp)
            if (accuracy != null) {
                put(COLUMN_RAW_ACCURACY, accuracy)
            }
        }
        db.insert(TABLE_RAW_LOCATIONS, null, rawValues)

        val existingLocation = findNearbyLocation(latitude, longitude)

        if (existingLocation != null) {
            val values = ContentValues().apply {
                put(COLUMN_TIMESTAMP, timestamp)
                put(COLUMN_VISIT_COUNT, existingLocation.visitCount + 1)
                // Update accuracy if provided and better than existing
                if (accuracy != null && (existingLocation.accuracy == null || accuracy < existingLocation.accuracy!!)) {
                    put(COLUMN_ACCURACY, accuracy)
                }
            }
            db.update(TABLE_LOCATIONS, values, "$COLUMN_ID = ?", arrayOf(existingLocation.id.toString()))
        } else {
            val values = ContentValues().apply {
                put(COLUMN_LATITUDE, latitude)
                put(COLUMN_LONGITUDE, longitude)
                put(COLUMN_TIMESTAMP, timestamp)
                put(COLUMN_FIRST_VISIT, timestamp)
                put(COLUMN_VISIT_COUNT, 1)
                if (accuracy != null) {
                    put(COLUMN_ACCURACY, accuracy)
                }
            }
            db.insert(TABLE_LOCATIONS, null, values)
        }
        // Don't close db - let SQLiteOpenHelper manage it
    }

    private fun findNearbyLocation(latitude: Double, longitude: Double): LocationData? {
        val db = readableDatabase

        // Use bounding box to pre-filter candidates
        val latMin = latitude - LOCATION_THRESHOLD_DEGREES
        val latMax = latitude + LOCATION_THRESHOLD_DEGREES
        val lonMin = longitude - LOCATION_THRESHOLD_DEGREES
        val lonMax = longitude + LOCATION_THRESHOLD_DEGREES

        val cursor = db.query(
            TABLE_LOCATIONS,
            null,
            "$COLUMN_LATITUDE BETWEEN ? AND ? AND $COLUMN_LONGITUDE BETWEEN ? AND ?",
            arrayOf(latMin.toString(), latMax.toString(), lonMin.toString(), lonMax.toString()),
            null,
            null,
            null
        )

        var nearbyLocation: LocationData? = null

        if (cursor.moveToFirst()) {
            do {
                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
                val lon = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))

                val results = FloatArray(1)
                Location.distanceBetween(latitude, longitude, lat, lon, results)

                if (results[0] <= LOCATION_THRESHOLD_METERS) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                    val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                    val firstVisit = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FIRST_VISIT))
                    val visitCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VISIT_COUNT))
                    val accuracyIndex = cursor.getColumnIndexOrThrow(COLUMN_ACCURACY)
                    val accuracy = if (cursor.isNull(accuracyIndex)) null else cursor.getFloat(accuracyIndex)
                    val name = findLocationName(lat, lon)

                    nearbyLocation = LocationData(id, lat, lon, timestamp, firstVisit, visitCount, accuracy, name)
                    break
                }
            } while (cursor.moveToNext())
        }

        cursor.close()
        // Don't close db - let SQLiteOpenHelper manage it

        return nearbyLocation
    }

    fun getRecentLocations(limit: Int): List<LocationData> {
        val allLocations = mutableListOf<LocationData>()
        val db = readableDatabase

        // Use LEFT JOIN with location_names to avoid N+1 queries
        // The join uses approximate bounding box matching (within ~100m)
        val query = """
            SELECT l.$COLUMN_ID, l.$COLUMN_LATITUDE, l.$COLUMN_LONGITUDE,
                   l.$COLUMN_TIMESTAMP, l.$COLUMN_FIRST_VISIT, l.$COLUMN_VISIT_COUNT,
                   l.$COLUMN_ACCURACY, ln.$COLUMN_NAME
            FROM $TABLE_LOCATIONS l
            LEFT JOIN $TABLE_LOCATION_NAMES ln ON (
                ABS(l.$COLUMN_LATITUDE - ln.$COLUMN_NAME_LATITUDE) < $LOCATION_THRESHOLD_DEGREES
                AND ABS(l.$COLUMN_LONGITUDE - ln.$COLUMN_NAME_LONGITUDE) < $LOCATION_THRESHOLD_DEGREES
            )
            ORDER BY l.$COLUMN_TIMESTAMP DESC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
                val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val firstVisit = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FIRST_VISIT))
                val visitCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VISIT_COUNT))
                val accuracyIndex = cursor.getColumnIndexOrThrow(COLUMN_ACCURACY)
                val accuracy = if (cursor.isNull(accuracyIndex)) null else cursor.getFloat(accuracyIndex)
                val nameIndex = cursor.getColumnIndexOrThrow(COLUMN_NAME)
                val name = if (cursor.isNull(nameIndex)) null else cursor.getString(nameIndex)

                allLocations.add(LocationData(id, latitude, longitude, timestamp, firstVisit, visitCount, accuracy, name))
            } while (cursor.moveToNext())
        }

        cursor.close()
        // Don't close db - let SQLiteOpenHelper manage it

        return applyFilters(allLocations).take(limit)
    }

    fun getAllLocations(): List<LocationData> {
        val allLocations = mutableListOf<LocationData>()
        val db = readableDatabase

        // Use LEFT JOIN with location_names to avoid N+1 queries
        // The join uses approximate bounding box matching (within ~100m)
        val query = """
            SELECT l.$COLUMN_ID, l.$COLUMN_LATITUDE, l.$COLUMN_LONGITUDE,
                   l.$COLUMN_TIMESTAMP, l.$COLUMN_FIRST_VISIT, l.$COLUMN_VISIT_COUNT,
                   l.$COLUMN_ACCURACY, ln.$COLUMN_NAME
            FROM $TABLE_LOCATIONS l
            LEFT JOIN $TABLE_LOCATION_NAMES ln ON (
                ABS(l.$COLUMN_LATITUDE - ln.$COLUMN_NAME_LATITUDE) < $LOCATION_THRESHOLD_DEGREES
                AND ABS(l.$COLUMN_LONGITUDE - ln.$COLUMN_NAME_LONGITUDE) < $LOCATION_THRESHOLD_DEGREES
            )
            ORDER BY l.$COLUMN_TIMESTAMP DESC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
                val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val firstVisit = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FIRST_VISIT))
                val visitCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VISIT_COUNT))
                val accuracyIndex = cursor.getColumnIndexOrThrow(COLUMN_ACCURACY)
                val accuracy = if (cursor.isNull(accuracyIndex)) null else cursor.getFloat(accuracyIndex)
                val nameIndex = cursor.getColumnIndexOrThrow(COLUMN_NAME)
                val name = if (cursor.isNull(nameIndex)) null else cursor.getString(nameIndex)

                allLocations.add(LocationData(id, latitude, longitude, timestamp, firstVisit, visitCount, accuracy, name))
            } while (cursor.moveToNext())
        }

        cursor.close()
        // Don't close db - let SQLiteOpenHelper manage it

        return applyFilters(allLocations)
    }

    private fun applyFilters(locations: List<LocationData>): List<LocationData> {
        val filtered = mutableListOf<LocationData>()

        for (location in locations) {
            // Filter 1: Accuracy must be better than 50 meters (or null)
            if (location.accuracy != null && location.accuracy >= 50f) {
                continue
            }

            // Filter 2: Check proximity and time to previous entry
            if (filtered.isNotEmpty()) {
                val previous = filtered.last()
                val timeDiff = kotlin.math.abs(location.timestamp - previous.timestamp)

                // Only apply proximity filter if within 60 seconds
                if (timeDiff <= 60_000) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        location.latitude, location.longitude,
                        previous.latitude, previous.longitude,
                        results
                    )

                    // Skip if less than 5 meters apart
                    if (results[0] < 5.0) {
                        continue
                    }
                }
            }

            filtered.add(location)
        }

        return filtered
    }
    
    fun getLastGpsUpdateTime(): Long {
        return prefs.getLong(KEY_LAST_GPS_UPDATE, 0)
    }

    // Get all raw GPS readings
    fun getAllRawLocations(): List<RawLocationData> {
        val locations = mutableListOf<RawLocationData>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_RAW_LOCATIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_RAW_TIMESTAMP DESC"
        )

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RAW_ID))
                val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_RAW_LATITUDE))
                val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_RAW_LONGITUDE))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RAW_TIMESTAMP))
                val accuracyIndex = cursor.getColumnIndexOrThrow(COLUMN_RAW_ACCURACY)
                val accuracy = if (cursor.isNull(accuracyIndex)) null else cursor.getFloat(accuracyIndex)

                locations.add(RawLocationData(id, latitude, longitude, timestamp, accuracy))
            } while (cursor.moveToNext())
        }

        cursor.close()
        return locations
    }

    // Find name for a location within threshold
    private fun findLocationName(latitude: Double, longitude: Double): String? {
        val db = readableDatabase

        // Use bounding box to pre-filter candidates
        val latMin = latitude - LOCATION_THRESHOLD_DEGREES
        val latMax = latitude + LOCATION_THRESHOLD_DEGREES
        val lonMin = longitude - LOCATION_THRESHOLD_DEGREES
        val lonMax = longitude + LOCATION_THRESHOLD_DEGREES

        val cursor = db.query(
            TABLE_LOCATION_NAMES,
            null,
            "$COLUMN_NAME_LATITUDE BETWEEN ? AND ? AND $COLUMN_NAME_LONGITUDE BETWEEN ? AND ?",
            arrayOf(latMin.toString(), latMax.toString(), lonMin.toString(), lonMax.toString()),
            null,
            null,
            null
        )

        var name: String? = null

        if (cursor.moveToFirst()) {
            do {
                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_NAME_LATITUDE))
                val lon = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_NAME_LONGITUDE))

                val results = FloatArray(1)
                Location.distanceBetween(latitude, longitude, lat, lon, results)

                if (results[0] <= LOCATION_THRESHOLD_METERS) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
                    break
                }
            } while (cursor.moveToNext())
        }

        cursor.close()
        return name
    }

    // Set or update name for a location
    fun setLocationName(latitude: Double, longitude: Double, name: String) {
        val db = writableDatabase

        // Use bounding box to pre-filter candidates
        val latMin = latitude - LOCATION_THRESHOLD_DEGREES
        val latMax = latitude + LOCATION_THRESHOLD_DEGREES
        val lonMin = longitude - LOCATION_THRESHOLD_DEGREES
        val lonMax = longitude + LOCATION_THRESHOLD_DEGREES

        // Check if there's already a name nearby
        val cursor = db.query(
            TABLE_LOCATION_NAMES,
            null,
            "$COLUMN_NAME_LATITUDE BETWEEN ? AND ? AND $COLUMN_NAME_LONGITUDE BETWEEN ? AND ?",
            arrayOf(latMin.toString(), latMax.toString(), lonMin.toString(), lonMax.toString()),
            null,
            null,
            null
        )

        var existingId: Long? = null

        if (cursor.moveToFirst()) {
            do {
                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_NAME_LATITUDE))
                val lon = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_NAME_LONGITUDE))

                val results = FloatArray(1)
                Location.distanceBetween(latitude, longitude, lat, lon, results)

                if (results[0] <= LOCATION_THRESHOLD_METERS) {
                    existingId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NAME_ID))
                    break
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        if (existingId != null) {
            // Update existing name
            val values = ContentValues().apply {
                put(COLUMN_NAME, name)
                put(COLUMN_NAME_LATITUDE, latitude)
                put(COLUMN_NAME_LONGITUDE, longitude)
            }
            db.update(TABLE_LOCATION_NAMES, values, "$COLUMN_NAME_ID = ?", arrayOf(existingId.toString()))
        } else {
            // Insert new name
            val values = ContentValues().apply {
                put(COLUMN_NAME_LATITUDE, latitude)
                put(COLUMN_NAME_LONGITUDE, longitude)
                put(COLUMN_NAME, name)
            }
            db.insert(TABLE_LOCATION_NAMES, null, values)
        }
    }

    // Remove name for a location
    fun removeLocationName(latitude: Double, longitude: Double) {
        val db = writableDatabase

        // Use bounding box to pre-filter candidates
        val latMin = latitude - LOCATION_THRESHOLD_DEGREES
        val latMax = latitude + LOCATION_THRESHOLD_DEGREES
        val lonMin = longitude - LOCATION_THRESHOLD_DEGREES
        val lonMax = longitude + LOCATION_THRESHOLD_DEGREES

        val cursor = db.query(
            TABLE_LOCATION_NAMES,
            null,
            "$COLUMN_NAME_LATITUDE BETWEEN ? AND ? AND $COLUMN_NAME_LONGITUDE BETWEEN ? AND ?",
            arrayOf(latMin.toString(), latMax.toString(), lonMin.toString(), lonMax.toString()),
            null,
            null,
            null
        )

        var idToDelete: Long? = null

        if (cursor.moveToFirst()) {
            do {
                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_NAME_LATITUDE))
                val lon = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_NAME_LONGITUDE))

                val results = FloatArray(1)
                Location.distanceBetween(latitude, longitude, lat, lon, results)

                if (results[0] <= LOCATION_THRESHOLD_METERS) {
                    idToDelete = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NAME_ID))
                    break
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        if (idToDelete != null) {
            db.delete(TABLE_LOCATION_NAMES, "$COLUMN_NAME_ID = ?", arrayOf(idToDelete.toString()))
        }
    }

    // Get raw GPS readings from the last 24 hours, ordered chronologically (oldest first)
    fun getRawLocationsLast24Hours(): List<RawLocationData> {
        val locations = mutableListOf<RawLocationData>()
        val db = readableDatabase

        // Calculate timestamp for 24 hours ago
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

        val cursor = db.query(
            TABLE_RAW_LOCATIONS,
            null,
            "$COLUMN_RAW_TIMESTAMP >= ?",
            arrayOf(twentyFourHoursAgo.toString()),
            null,
            null,
            "$COLUMN_RAW_TIMESTAMP ASC"  // Oldest first for drawing path chronologically
        )

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RAW_ID))
                val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_RAW_LATITUDE))
                val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_RAW_LONGITUDE))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RAW_TIMESTAMP))
                val accuracyIndex = cursor.getColumnIndexOrThrow(COLUMN_RAW_ACCURACY)
                val accuracy = if (cursor.isNull(accuracyIndex)) null else cursor.getFloat(accuracyIndex)

                locations.add(RawLocationData(id, latitude, longitude, timestamp, accuracy))
            } while (cursor.moveToNext())
        }

        cursor.close()
        return locations
    }
}

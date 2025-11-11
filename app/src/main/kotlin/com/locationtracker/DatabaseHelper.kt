package com.locationtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "locations.db"
        private const val DATABASE_VERSION = 2
        
        private const val TABLE_LOCATIONS = "locations"
        private const val COLUMN_ID = "id"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_FIRST_VISIT = "first_visit"
        private const val COLUMN_VISIT_COUNT = "visit_count"
        
        private const val LOCATION_THRESHOLD_METERS = 100.0
        
        private const val PREFS_NAME = "LocationTrackerPrefs"
        private const val KEY_LAST_GPS_UPDATE = "last_gps_update"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_LOCATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_LATITUDE REAL NOT NULL,
                $COLUMN_LONGITUDE REAL NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_FIRST_VISIT INTEGER NOT NULL,
                $COLUMN_VISIT_COUNT INTEGER DEFAULT 1
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add first_visit column to existing table
            db.execSQL("ALTER TABLE $TABLE_LOCATIONS ADD COLUMN $COLUMN_FIRST_VISIT INTEGER DEFAULT 0")
            // Update existing rows to use timestamp as first_visit
            db.execSQL("UPDATE $TABLE_LOCATIONS SET $COLUMN_FIRST_VISIT = $COLUMN_TIMESTAMP WHERE $COLUMN_FIRST_VISIT = 0")
        }
    }

    fun insertOrUpdateLocation(latitude: Double, longitude: Double, timestamp: Long) {
        val db = writableDatabase
        
        // Save last GPS update time
        prefs.edit().putLong(KEY_LAST_GPS_UPDATE, timestamp).apply()
        
        val existingLocation = findNearbyLocation(latitude, longitude)
        
        if (existingLocation != null) {
            val values = ContentValues().apply {
                put(COLUMN_TIMESTAMP, timestamp)
                put(COLUMN_VISIT_COUNT, existingLocation.visitCount + 1)
            }
            db.update(TABLE_LOCATIONS, values, "$COLUMN_ID = ?", arrayOf(existingLocation.id.toString()))
        } else {
            val values = ContentValues().apply {
                put(COLUMN_LATITUDE, latitude)
                put(COLUMN_LONGITUDE, longitude)
                put(COLUMN_TIMESTAMP, timestamp)
                put(COLUMN_FIRST_VISIT, timestamp)
                put(COLUMN_VISIT_COUNT, 1)
            }
            db.insert(TABLE_LOCATIONS, null, values)
        }
        // Don't close db - let SQLiteOpenHelper manage it
    }

    private fun findNearbyLocation(latitude: Double, longitude: Double): LocationData? {
        val db = readableDatabase
        val cursor = db.query(TABLE_LOCATIONS, null, null, null, null, null, null)
        
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
                    
                    nearbyLocation = LocationData(id, lat, lon, timestamp, firstVisit, visitCount)
                    break
                }
            } while (cursor.moveToNext())
        }
        
        cursor.close()
        // Don't close db - let SQLiteOpenHelper manage it
        
        return nearbyLocation
    }

    fun getRecentLocations(limit: Int): List<LocationData> {
        val locations = mutableListOf<LocationData>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_LOCATIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC",
            limit.toString()
        )
        
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
                val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val firstVisit = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FIRST_VISIT))
                val visitCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VISIT_COUNT))
                
                locations.add(LocationData(id, latitude, longitude, timestamp, firstVisit, visitCount))
            } while (cursor.moveToNext())
        }
        
        cursor.close()
        // Don't close db - let SQLiteOpenHelper manage it
        
        return locations
    }

    fun getAllLocations(): List<LocationData> {
        val locations = mutableListOf<LocationData>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_LOCATIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )
        
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
                val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val firstVisit = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FIRST_VISIT))
                val visitCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VISIT_COUNT))
                
                locations.add(LocationData(id, latitude, longitude, timestamp, firstVisit, visitCount))
            } while (cursor.moveToNext())
        }
        
        cursor.close()
        // Don't close db - let SQLiteOpenHelper manage it
        
        return locations
    }
    
    fun getLastGpsUpdateTime(): Long {
        return prefs.getLong(KEY_LAST_GPS_UPDATE, 0)
    }
}

package com.rideshare.driver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DriverLocation - MongoDB document with 2dsphere geospatial index
 * 
 * KEY OPTIMIZATION: Compound index on (location:2dsphere, status, lastUpdate)
 * This allows efficient nearby driver queries filtering by status and recency
 * 
 * DENORMALIZED FIELDS: status, timeBucket stored with location
 * Eliminates need to join with driver details table for common queries
 */
@Document(collection = "driver_locations")
@CompoundIndex(
    name = "location_2dsphere_status_lastUpdate",
    def = "{'location': '2dsphere', 'status': 1, 'lastUpdate': -1}"
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocation {
    
    @Id
    private String id;
    
    private String driverId;
    
    /**
     * GeoJSON Point for 2dsphere indexing
     * Format: [longitude, latitude]
     * 
     * 2dsphere index enables:
     * - $near queries for nearby drivers
     * - $geoWithin for drivers in polygon
     * - $geoIntersects for route matching
     */
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;
    
    /**
     * DENORMALIZED: Driver status stored with location
     * Avoids JOIN to check if driver is available
     * Index includes this field for filtered geospatial queries
     */
    private DriverStatus status;
    
    /**
     * DENORMALIZED: Last update timestamp
     * Used to filter stale locations (>5 min old)
     * Part of compound index for efficient queries
     */
    private LocalDateTime lastUpdate;
    
    /**
     * DENORMALIZED: Time bucket for temporal queries
     * Format: "2024-12-15-14" (hour-level bucketing)
     * Enables efficient time-range queries without date math
     */
    private String timeBucket;
    
    // Additional denormalized fields for display
    private String driverName;
    private String vehicleType;
    private Double rating;
    
    public enum DriverStatus {
        AVAILABLE,    // Can accept rides
        BUSY,         // Currently on a ride
        OFFLINE       // Not active
    }
    
    /**
     * Generate time bucket from current timestamp
     * Used for temporal indexing and queries
     */
    public void updateTimeBucket() {
        if (lastUpdate != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
            this.timeBucket = lastUpdate.format(formatter);
        }
    }
}

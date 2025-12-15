-- Flyway migration V1: Create rides table
-- ride-service/src/main/resources/db/migration/V1__create_rides_table.sql

CREATE TABLE rides (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    driver_id VARCHAR(36),
    status VARCHAR(20) NOT NULL,
    
    -- Pickup location
    pickup_latitude DOUBLE PRECISION NOT NULL,
    pickup_longitude DOUBLE PRECISION NOT NULL,
    pickup_address VARCHAR(255),
    
    -- Dropoff location
    dropoff_latitude DOUBLE PRECISION NOT NULL,
    dropoff_longitude DOUBLE PRECISION NOT NULL,
    dropoff_address VARCHAR(255),
    
    -- Precomputed columns (avoid runtime calculation)
    estimated_distance_km DOUBLE PRECISION,
    estimated_duration_minutes INTEGER,
    estimated_fare DECIMAL(10, 2),
    actual_fare DECIMAL(10, 2),
    
    -- Denormalized columns (avoid JOINs)
    user_name VARCHAR(100),
    driver_name VARCHAR(100),
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP
);

-- Basic index on user_id for user ride history
CREATE INDEX idx_rides_user_id ON rides(user_id);

-- Basic index on driver_id for driver ride history
CREATE INDEX idx_rides_driver_id ON rides(driver_id);

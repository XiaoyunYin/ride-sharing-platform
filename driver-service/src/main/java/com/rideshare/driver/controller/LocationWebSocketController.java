package com.rideshare.driver.controller;

import com.rideshare.driver.dto.LocationUpdateRequest;
import com.rideshare.driver.model.DriverLocation;
import com.rideshare.driver.service.DriverLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket Controller for real-time driver location updates
 * 
 * Flow:
 * 1. Driver sends location via WebSocket: /app/driver/location
 * 2. Server saves to MongoDB (with 2dsphere index)
 * 3. Server publishes to Redis Pub/Sub channel
 * 4. Redis broadcasts to all subscribers (fan-out)
 * 5. Ride Service receives and notifies nearby passengers
 * 
 * This demonstrates:
 * - WebSocket for bi-directional communication
 * - Redis Pub/Sub for fan-out architecture
 * - Real-time updates without polling
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LocationWebSocketController {
    
    private final DriverLocationService locationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    
    private static final String REDIS_CHANNEL = "driver-locations";
    
    /**
     * Handle driver location updates via WebSocket
     * 
     * Client sends message to: /app/driver/location
     * Message format:
     * {
     *   "latitude": 37.7749,
     *   "longitude": -122.4194,
     *   "status": "AVAILABLE"
     * }
     * 
     * @param request Location update from driver
     * @param principal Authenticated driver (from JWT)
     */
    @MessageMapping("/driver/location")
    public void updateLocation(
        @Payload LocationUpdateRequest request,
        Principal principal
    ) {
        String driverId = principal.getName();
        
        log.info("Received location update from driver: {}", driverId);
        
        // 1. Save to MongoDB with 2dsphere index
        // This enables fast nearby driver queries
        DriverLocation location = locationService.updateLocation(
            driverId,
            request.getLatitude(),
            request.getLongitude(),
            request.getStatus()
        );
        
        // 2. Publish to Redis Pub/Sub (fan-out to all subscribers)
        // Multiple Ride Service instances can subscribe
        // Each instance receives the update and notifies its connected clients
        publishToRedis(location);
        
        // 3. Confirm back to driver via WebSocket
        messagingTemplate.convertAndSendToUser(
            driverId,
            "/queue/location-ack",
            "Location updated successfully"
        );
        
        log.info("Location update processed for driver: {}", driverId);
    }
    
    /**
     * Publish location update to Redis Pub/Sub
     * 
     * Redis Pub/Sub provides:
     * - Fan-out: One message reaches all subscribers
     * - Decoupling: Services don't know about each other
     * - Scalability: Handles thousands of updates/second
     * 
     * Subscribers (Ride Service instances) receive updates and
     * forward to passengers via WebSocket
     */
    private void publishToRedis(DriverLocation location) {
        try {
            redisTemplate.convertAndSend(REDIS_CHANNEL, location);
            log.debug("Published location to Redis: driver={}, lat={}, lon={}", 
                location.getDriverId(),
                location.getLocation().getY(),
                location.getLocation().getX()
            );
        } catch (Exception e) {
            log.error("Failed to publish to Redis", e);
        }
    }
}

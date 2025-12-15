package com.rideshare.driver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket Configuration for real-time driver location updates
 * 
 * Architecture:
 * - Drivers connect via WebSocket and send location updates
 * - Server broadcasts updates to passengers via STOMP
 * - Bi-directional communication for instant notifications
 * 
 * Endpoints:
 * - Connect: ws://localhost:8082/ws
 * - Send: /app/driver/location
 * - Subscribe: /topic/nearby-drivers/{rideId}
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    /**
     * Configure message broker
     * - /app: Messages sent to server
     * - /topic: Server broadcasts to clients
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple in-memory broker for broadcasting
        registry.enableSimpleBroker("/topic", "/queue");
        
        // Prefix for messages bound for @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
    }
    
    /**
     * Register WebSocket endpoint
     * Clients connect to: ws://localhost:8082/ws
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Configure for production
                .withSockJS();  // Fallback for older browsers
    }
}

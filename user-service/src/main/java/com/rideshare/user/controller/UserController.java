package com.rideshare.user.controller;

import com.rideshare.user.dto.LoginRequest;
import com.rideshare.user.dto.RegisterRequest;
import com.rideshare.user.dto.AuthResponse;
import com.rideshare.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User REST API Controller
 * Endpoints: /api/users/register, /api/users/login
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    /**
     * POST /api/users/register
     * Register a new user (passenger or driver)
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * POST /api/users/login
     * Authenticate user and return JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * GET /api/users/me
     * Get current user profile (requires JWT token)
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {
        // Extract user ID from JWT and return user details
        return ResponseEntity.ok(userService.getCurrentUser(token));
    }
}

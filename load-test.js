/**
 * K6 Load Testing Script for Ride Sharing Platform
 * 
 * Tests:
 * 1. MongoDB 2dsphere nearby driver queries
 * 2. PostgreSQL optimized ride queries
 * 3. WebSocket real-time updates
 * 
 * Run: k6 run load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import ws from 'k6/ws';

export const options = {
  stages: [
    { duration: '30s', target: 100 },   // Ramp up to 100 users
    { duration: '1m', target: 500 },    // Ramp up to 500 users
    { duration: '2m', target: 1000 },   // Ramp up to 1000 users
    { duration: '1m', target: 0 },      // Ramp down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<200'],  // 95% of requests under 200ms
    'http_req_failed': ['rate<0.01'],    // <1% error rate
  },
};

const BASE_URL = 'http://localhost:8080';

// Register a user and get JWT token
function getAuthToken() {
  const username = `user_${__VU}_${Date.now()}`;
  const registerPayload = JSON.stringify({
    username: username,
    email: `${username}@example.com`,
    password: 'password123',
    role: 'PASSENGER'
  });

  const registerRes = http.post(`${BASE_URL}/api/users/register`, registerPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  if (registerRes.status === 200) {
    const body = JSON.parse(registerRes.body);
    return body.token;
  }

  // If registration fails, try login
  const loginPayload = JSON.stringify({
    username: username,
    password: 'password123'
  });

  const loginRes = http.post(`${BASE_URL}/api/users/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  if (loginRes.status === 200) {
    const body = JSON.parse(loginRes.body);
    return body.token;
  }

  return null;
}

export default function () {
  const token = getAuthToken();
  if (!token) {
    console.error('Failed to get auth token');
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // Test 1: Find nearby drivers (MongoDB 2dsphere query)
  // This should complete in <100ms with optimized index
  const nearbyDriversRes = http.get(
    `${BASE_URL}/api/rides/nearby-drivers?lat=37.7749&lon=-122.4194&radius=5000`,
    { headers }
  );
  
  check(nearbyDriversRes, {
    'nearby drivers status 200': (r) => r.status === 200,
    'nearby drivers <100ms': (r) => r.timings.duration < 100,
  });

  sleep(0.5);

  // Test 2: Get user ride history (PostgreSQL composite index query)
  // This should complete in <100ms with optimized index
  const rideHistoryRes = http.get(
    `${BASE_URL}/api/rides/history?status=COMPLETED`,
    { headers }
  );
  
  check(rideHistoryRes, {
    'ride history status 200': (r) => r.status === 200,
    'ride history <100ms': (r) => r.timings.duration < 100,
  });

  sleep(0.5);

  // Test 3: Request a ride (tests multiple database operations)
  const rideRequestPayload = JSON.stringify({
    pickupLat: 37.7749 + (Math.random() - 0.5) * 0.1,
    pickupLon: -122.4194 + (Math.random() - 0.5) * 0.1,
    dropoffLat: 37.8044 + (Math.random() - 0.5) * 0.1,
    dropoffLon: -122.2712 + (Math.random() - 0.5) * 0.1,
  });

  const rideRequestRes = http.post(
    `${BASE_URL}/api/rides/request`,
    rideRequestPayload,
    { headers }
  );
  
  check(rideRequestRes, {
    'ride request status 200': (r) => r.status === 200,
    'ride request <300ms': (r) => r.timings.duration < 300,
  });

  sleep(1);
}

// WebSocket test for real-time updates
export function testWebSocket() {
  const token = getAuthToken();
  if (!token) return;

  const url = 'ws://localhost:8082/ws';
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  };

  ws.connect(url, params, function (socket) {
    socket.on('open', function () {
      console.log('WebSocket connected');

      // Send location update
      const locationUpdate = JSON.stringify({
        latitude: 37.7749,
        longitude: -122.4194,
        status: 'AVAILABLE',
      });

      socket.send(`SEND\ndestination:/app/driver/location\ncontent-type:application/json\n\n${locationUpdate}\n\0`);
    });

    socket.on('message', function (message) {
      check(message, {
        'websocket message received': (m) => m.length > 0,
      });
    });

    socket.on('close', function () {
      console.log('WebSocket closed');
    });

    socket.setTimeout(function () {
      socket.close();
    }, 10000);
  });
}

/**
 * Expected Results:
 * 
 * MongoDB Nearby Driver Query:
 * - p50: ~30ms
 * - p95: <100ms
 * - Uses 2dsphere index
 * 
 * PostgreSQL Ride History Query:
 * - p50: ~40ms
 * - p95: <100ms
 * - Uses composite index
 * 
 * Ride Request (Multiple Operations):
 * - p50: ~150ms
 * - p95: <300ms
 * - Includes: nearby driver query, ride creation, precomputed calculations
 * 
 * WebSocket Updates:
 * - Latency: <50ms
 * - Bi-directional communication
 */

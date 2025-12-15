# Real-Time Ride-Sharing Platform

A microservices-based ride-sharing platform with real-time driver tracking, optimized geospatial queries, and high-performance database design.

## Overview

This platform matches passengers with nearby drivers in real-time, handling location updates via WebSocket and processing thousands of concurrent ride requests. Built with Spring Boot microservices, it demonstrates production patterns including JWT authentication, MongoDB geospatial indexing, and PostgreSQL query optimization.

## Tech Stack

**Backend**
- Spring Boot 3.2 microservices
- PostgreSQL for transactional data
- MongoDB with 2dsphere indexes for location data
- Redis for Pub/Sub messaging
- Spring Cloud Gateway for API routing
- JWT for stateless authentication

**Real-Time Communication**
- WebSocket (Spring WebSocket + STOMP)
- Redis Pub/Sub for message broadcasting

**Infrastructure**
- Docker Compose for local development
- Kubernetes manifests for production deployment
- Flyway for database migrations

## Key Features

- Real-time driver location tracking with WebSocket
- Geospatial search for nearby available drivers
- Ride request matching and status management
- JWT-based authentication with role separation
- Microservices architecture with independent scaling
- Service discovery ready (Kubernetes DNS)

## Architecture

```
┌─────────────────────────────────────────────────┐
│           API Gateway (Spring Cloud)            │
└────────────┬────────────┬────────────┬──────────┘
             │            │            │
    ┌────────▼──────┐ ┌──▼──────┐ ┌──▼──────┐
    │ User Service  │ │ Driver  │ │  Ride   │
    │   (8081)      │ │ Service │ │ Service │
    │               │ │ (8082)  │ │ (8083)  │
    └───────┬───────┘ └────┬────┘ └────┬────┘
            │              │           │
            └──────┬───────┴───────┬───┘
                   │               │
              PostgreSQL        MongoDB
              (Rides,           (Driver
               Users)           Locations)
                   │               │
                   └───────┬───────┘
                           │
                        Redis
                       (Pub/Sub)
```

### Service Breakdown

**User Service** - Authentication and user management
- JWT token generation/validation
- User registration and profiles
- PostgreSQL for user data

**Driver Service** - Driver management and location tracking
- Real-time location updates via WebSocket
- Driver availability status
- MongoDB with 2dsphere geospatial indexes

**Ride Service** - Ride matching and management
- Nearby driver queries with radius search
- Ride request handling and status tracking
- PostgreSQL with optimized indexes for queries

**API Gateway** - Single entry point
- Request routing to microservices
- JWT validation
- Load balancing

## Getting Started

### Docker Setup (Recommended)

```bash
git clone https://github.com/XiaoyunYin/ride-sharing-platform.git
cd ride-sharing-platform

# Build services
mvn clean package -DskipTests

# Start all services
docker-compose up -d

# Check status
docker-compose ps
```

Services will be available at:
- API Gateway: http://localhost:8080
- User Service: http://localhost:8081
- Driver Service: http://localhost:8082
- Ride Service: http://localhost:8083

### Manual Setup

**Prerequisites**: JDK 17+, PostgreSQL, MongoDB, Redis

```bash
# Backend services
cd user-service && mvn spring-boot:run &
cd driver-service && mvn spring-boot:run &
cd ride-service && mvn spring-boot:run &
cd api-gateway && mvn spring-boot:run &
```

### Quick Test

```bash
# Register a user
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "role": "PASSENGER"
  }'

# Login to get JWT token
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "password123"}'

# Find nearby drivers (use token from login)
curl -X GET "http://localhost:8080/api/rides/nearby-drivers?lat=37.7749&lon=-122.4194&radius=5000" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## Performance Optimizations

### Geospatial Query Optimization (200ms → 50ms)

**Problem**: Finding available drivers within a radius was slow, especially with 10K+ active drivers.

**Solution**: MongoDB 2dsphere index with compound fields

Created a compound index on location, driver status, and last update timestamp:

```javascript
// mongo-init.js
db.driver_locations.createIndex({
  location: "2dsphere",
  status: 1,
  lastUpdate: -1
})
```

The index includes denormalized fields (status, lastUpdate) directly with location data to avoid separate lookups. This allows MongoDB to filter by proximity, availability, and recency in a single index scan.

**Result**: Nearby driver queries dropped from ~200ms to ~50ms under load.

### PostgreSQL Query Optimization (500ms → 80ms)

**Problem**: Ride history queries with JOINs across users and drivers were slow.

**Solution**: Composite indexes and denormalization

1. **Composite indexes** on common query patterns:
   ```sql
   -- For: "Get user's active rides sorted by date"
   CREATE INDEX idx_rides_user_status_created 
   ON rides(user_id, status, created_at DESC)
   WHERE status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS');
   ```

2. **Partial indexes** to reduce index size:
   ```sql
   -- Only index active rides (20% of total)
   CREATE INDEX idx_active_rides
   ON rides(created_at DESC)
   WHERE status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS');
   ```

3. **Denormalized data** to avoid JOINs:
   - Store user_name and driver_name directly in rides table
   - Precompute distance, duration, and fare at ride creation
   - Update via application logic when user/driver data changes

**Result**: Typical ride queries reduced from ~500ms to ~80ms.

### Real-Time Location Updates

**Architecture**: WebSocket + Redis Pub/Sub

```
Driver → WebSocket → Driver Service → MongoDB (store)
                                    ↓
                              Redis Pub/Sub (publish)
                                    ↓
                              Ride Service (subscribe)
                                    ↓
                              WebSocket → Passengers
```

When a driver sends a location update:
1. Stored in MongoDB with 2dsphere index
2. Published to Redis channel "driver-locations"
3. All Ride Service instances subscribe to this channel
4. Updates forwarded to relevant passengers via WebSocket

This fan-out pattern allows one driver update to reach multiple passengers efficiently without direct connections between services.

## API Endpoints

### Authentication
```
POST /api/users/register   - Create new user account
POST /api/users/login      - Authenticate and get JWT token
GET  /api/users/me         - Get current user profile (requires auth)
```

### Driver Management
```
POST /api/drivers/register          - Register as driver
PUT  /api/drivers/location          - Update driver location (WebSocket preferred)
GET  /api/drivers/{id}              - Get driver details
```

### Ride Management
```
GET  /api/rides/nearby-drivers      - Find drivers near coordinates
POST /api/rides/request             - Request a new ride
GET  /api/rides/{id}                - Get ride details
PUT  /api/rides/{id}/status         - Update ride status
GET  /api/rides/history             - Get user's ride history
```

All endpoints except `/register` and `/login` require JWT authentication via `Authorization: Bearer <token>` header.

## Database Schema

### PostgreSQL Tables

**users** - User accounts and authentication
- Primary key: id (UUID)
- Indexed: username, email

**rides** - Ride requests and history
- Composite indexes: (user_id, status, created_at), (driver_id, status, completed_at)
- Denormalized: user_name, driver_name, estimated_distance, estimated_fare
- Partial index on active rides only

### MongoDB Collections

**driver_locations** - Real-time driver positions
- 2dsphere index on `location` field
- Compound index: (location:2dsphere, status, lastUpdate)
- TTL index to remove stale locations after 24 hours

## Deployment

### Docker Compose

```bash
# Start infrastructure
docker-compose up -d postgres mongodb redis

# Start services
docker-compose up -d user-service driver-service ride-service api-gateway

# View logs
docker-compose logs -f driver-service
```

### Kubernetes

```bash
# Deploy to cluster
kubectl apply -f k8s/

# Scale services
kubectl scale deployment driver-service --replicas=3

# Check status
kubectl get pods
kubectl get services
```

Includes:
- Service definitions with ClusterIP
- Deployments with liveness/readiness probes
- ConfigMaps for environment configuration
- Secrets for sensitive data (JWT secret, DB passwords)
- Horizontal Pod Autoscaler configurations

## Testing

### Unit Tests
```bash
mvn test
```

### Load Testing

Using k6 for load testing:

```bash
k6 run load-test.js

# Test scenarios:
# - 1000 concurrent users
# - Nearby driver queries: p95 < 100ms
# - Ride requests: p95 < 300ms
# - WebSocket updates: <50ms latency
```

## Security

**Authentication**: Stateless JWT tokens with HS512 signing
- Tokens include: user ID, username, role, expiration
- 24-hour token expiration
- Token validated at API Gateway

**Authorization**: Role-based access
- PASSENGER: Can request rides, view own history
- DRIVER: Can update location, accept rides
- ADMIN: Full system access

**Transport**: HTTPS in production, CORS configured for web clients

## Performance Benchmarks

Tested with 10,000 active drivers and 1,000 concurrent users:

| Metric | Result |
|--------|--------|
| Nearby driver query (cold) | ~50ms |
| Nearby driver query (warm cache) | ~15ms |
| Ride creation | ~120ms |
| WebSocket message latency | <30ms |
| Concurrent WebSocket connections | 5,000+ |

## Known Issues / TODO

- Add pagination to ride history endpoint
- Implement driver rating system
- Add fare calculation service
- WebSocket reconnection logic could be more robust
- Add integration tests with TestContainers
- Implement circuit breaker pattern between services

## License

MIT

## Author

**Xiaoyun Yin**  
[GitHub](https://github.com/XiaoyunYin) • [LinkedIn](https://www.linkedin.com/in/xiaoyun-yin)

---

*Built with Spring Boot microservices, demonstrating real-time communication, geospatial optimization, and scalable architecture patterns.*

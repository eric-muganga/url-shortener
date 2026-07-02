# URL Shortener

A Spring Boot URL shortener API with PostgreSQL persistence, Redis caching, Flyway migrations, metrics, and Docker Compose support.

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis
- Caffeine Cache
- Flyway
- Micrometer / Actuator
- Docker Compose
- Testcontainers

## Features

- Create short URLs
- Retrieve original URLs
- Redirect short URLs
- Update URL mappings
- Soft delete URLs
- View URL stats
- View cache metrics
- Integration tests with PostgreSQL and Redis containers

## Run Locally with Docker

Create a `.env` file from `.env.example`, then run:

```bash
docker compose up --build
```

The API will be available at:

```text
http://localhost:8080
```

## API Endpoints

### Create Short URL

```http
POST /api/v1/urls/shorten
Content-Type: application/json
```

Request:

```json
{
  "url": "https://example.com"
}
```

Response `201 Created`:

```json
{
  "shortCode": "1",
  "shortenedUrl": "http://localhost:8080/api/v1/urls/1/r",
  "createdAt": "2026-07-02T10:09:00"
}
```

### Get Original URL

```http
GET /api/v1/urls/{shortCode}
```

Response `200 OK`:

```json
{
  "shortCode": "1",
  "originalUrl": "https://example.com"
}
```

### Redirect

```http
GET /api/v1/urls/{shortCode}/r
```

Response:

```http
302 Found
Location: https://example.com
```

### Update URL

```http
PUT /api/v1/urls/{shortCode}
Content-Type: application/json
```

Request:

```json
{
  "url": "https://spring.io/projects/spring-boot"
}
```

Response `200 OK`:

```json
{
  "message": "URL updated successfully",
  "shortCode": "1",
  "updatedAt": "2026-07-02T10:09:00"
}
```

### Delete URL

```http
DELETE /api/v1/urls/{shortCode}
```

Response:

```http
204 No Content
```

### Get URL Stats

```http
GET /api/v1/urls/{shortCode}/stats
```

Response `200 OK`:

```json
{
  "shortCode": "1",
  "originalUrl": "https://example.com",
  "createdAt": "2026-07-02T10:09:00",
  "totalAccesses": 0
}
```

### Metrics Snapshot

```http
GET /api/v1/metrics/snapshot
```

Response `200 OK`:

```json
{
  "totalRequests": 10,
  "cacheHitRatioPercent": "90.00%"
}
```


## Example Request

```bash
curl -X POST http://localhost:8080/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

Example response:

```json
{
  "shortCode": "1",
  "shortenedUrl": "http://localhost:8080/api/v1/urls/1/r"
}
```

## Run Tests

```bash
mvn clean test
```

## Stop Containers

```bash
docker compose down
```

To remove volumes too:

```bash
docker compose down -v
```
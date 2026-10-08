# Digital Wallet User Service

User profile microservice of the Digital Wallet system. It stores and manages user profiles (name, email, active
status) and exposes a REST API for creating, reading, updating, activating, deactivating and deleting users.

- **Port:** `9091`
- **Used by:** `auth-service` (`http://localhost:9093`), which creates the profile here during registration
- **Database:** PostgreSQL (`digital_wallet_user_db`), schema managed by Liquibase

## Tech stack

Java 17 · Spring Boot 4.1.0 · Spring Data JPA · PostgreSQL · Liquibase · Bean Validation · Lombok · Actuator

## Requirements

- JDK 17
- A running PostgreSQL instance (Docker is the easiest way)

## Running locally

1. Start PostgreSQL and create the database:

   ```bash
   docker run -d --name postgres-db -e POSTGRES_PASSWORD=1234 -p 5432:5432 postgres:16
   docker exec postgres-db psql -U postgres -c "CREATE DATABASE digital_wallet_user_db;"
   ```

   You do not need to create tables: Liquibase applies the migrations on the first start.

2. Start the application:

   ```bash
   ./gradlew bootRun
   ```

Health check: `http://localhost:9091/actuator/health`

## Configuration

Settings live in `src/main/resources/application.yaml`.

| Property | Default | Description |
|---|---|---|
| `server.port` | `9091` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/digital_wallet_user_db` | Database address |
| `spring.datasource.username` / `password` | `postgres` / `1234` | Local development credentials only. Override them in every other environment |
| `spring.jpa.hibernate.ddl-auto` | `none` | The schema is owned by Liquibase |
| `spring.jpa.open-in-view` | `false` | No session is kept open during view rendering |

## API

Base path: `/api/users`

All error responses share the same format:

```json
{ "status": 409, "message": "User already exists with email: leila@example.com", "timestamp": "2026-10-08T12:00:00" }
```

| Method | Path | Success | Description |
|---|---|---|---|
| POST | `/api/users` | 201 + user | Creates a user. Supports the `Idempotency-Key` header |
| GET | `/api/users/{id}` | 200 + user | Returns one user |
| GET | `/api/users` | 200 + page | Paginated list. Default `size=20`, sorted by `id`. Supports `page`, `size` and `sort` |
| PATCH | `/api/users/{id}` | 200 + user | Updates the full name. Requires the current `version` |
| PATCH | `/api/users/{id}/activate` | 200 + user | Activates the user. Requires the current `updatedAt` |
| PATCH | `/api/users/{id}/deactivate` | 200 + user | Deactivates the user. Requires the current `updatedAt` |
| DELETE | `/api/users/{id}` | 204 | Deletes the user |

### Request bodies

```json
// POST /api/users
{ "fullName": "Leila", "email": "leila@example.com" }

// PATCH /api/users/{id}
{ "fullName": "Leila Aliyeva", "version": 0 }

// PATCH /api/users/{id}/activate and /deactivate
{ "updatedAt": "2026-10-08T12:00:00" }
```

Validation rules: `fullName` is required and must be 2 to 150 characters; `email` is required, must be a valid
address and at most 255 characters. The email is trimmed and lower-cased before it is stored.

### Response body

```json
{
  "id": 1,
  "fullName": "Leila",
  "email": "leila@example.com",
  "active": true,
  "createdAt": "2026-10-08T12:00:00",
  "updatedAt": "2026-10-08T12:00:00",
  "version": 0
}
```

### Examples

```bash
# Create a user
curl -X POST http://localhost:9091/api/users \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: create-leila-001' \
  -d '{"fullName":"Leila","email":"leila@example.com"}'

# Get a user
curl http://localhost:9091/api/users/1

# List users, second page
curl 'http://localhost:9091/api/users?page=1&size=10&sort=createdAt,desc'

# Update the name (version comes from the last response)
curl -X PATCH http://localhost:9091/api/users/1 \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Leila Aliyeva","version":0}'

# Deactivate (updatedAt comes from the last response)
curl -X PATCH http://localhost:9091/api/users/1/deactivate \
  -H 'Content-Type: application/json' \
  -d '{"updatedAt":"2026-10-08T12:00:00"}'
```

### Status codes

| Code | Cases |
|---|---|
| 400 | Validation error (the first failing field is reported) |
| 404 | User not found |
| 409 | Email already registered, stale `version` or `updatedAt`, `Idempotency-Key` reused with a different payload |

## Concurrency and idempotency

- **Optimistic locking:** `UserEntity` has a `@Version` column. An update with an outdated `version`, or a status
  change with an outdated `updatedAt`, is rejected with `409`. The client should read the user again and retry.
- **Idempotent creation:** when `POST /api/users` carries an `Idempotency-Key`, the response is stored together
  with a SHA-256 hash of the request. Repeating the same request returns the stored response with `201` and does not
  create a second user. Reusing the key with a different payload returns `409`. If two requests with the same key
  arrive at the same time, the unique constraint keeps only one stored response.

## Database

Migrations are in `src/main/resources/liquibase/changes`:

| Changeset | Purpose |
|---|---|
| `001-create-users-table` | `users` table with a unique email |
| `002-add-version-to-users` | `version` column for optimistic locking |
| `003-create-idempotency-keys-table` | `idempotency_keys` table with a unique key |
| `004-add-request-hash-to-idempotency-keys` | `request_hash` column to detect payload changes |

## Tests

```bash
./gradlew test
```

- Unit tests for `UserService` and `IdempotencyService` (Mockito).
- Controller tests for `UserController` (`@WebMvcTest`).
- `DigitalWalletUserServiceTests` starts the full application context, so **PostgreSQL must be running** on
  `localhost:5432` with the `digital_wallet_user_db` database, or this test will fail.

## Project structure

```
src/main/java/com/example/digitalwalletuserservice
├── controller   UserController
├── dto          CreateUserRequestDto, UpdateUserRequestDto, UserStatusChangeRequestDto, UserResponseDto
├── entity       UserEntity, IdempotencyKeyEntity
├── exception    exceptions, ErrorResponse and GlobalExceptionHandler
├── mapper       UserMapper
├── repository   UserRepository, IdempotencyKeyRepository
└── service      UserService, IdempotencyService
src/main/resources
├── application.yaml, changelog-master.yaml
└── liquibase/changes   migrations 001 to 004
```

## Before going to production

For IE staff: consult IE Cloud Services to validate the infrastructure design before deploying.

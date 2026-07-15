# Employee Data Service

A small REST microservice for storing HR employee records, built around one central
constraint: **the social security number must never touch the database, logs, or an API
response in plaintext.**

Stack: **Java 21**, **Spring Boot 4 / Spring Framework 7**, **Spring Data JPA**,
**PostgreSQL**, **Maven**.

---

## 1. Running it locally

**Prerequisites:** Java 21, Docker (if using PostgreSQL).

### Option A — single command (recommended)

```bash
# Windows:
start.bat

# macOS / Linux:
./start.sh
```

Or directly (all OS):

```bash
./mvnw spring-boot:run
```

Spring Boot's Docker Compose integration automatically starts PostgreSQL,
waits for it to be healthy, and launches the application. Everything stops
cleanly on Ctrl+C.

### Option B — zero dependencies (embedded H2, no Docker)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2-local
```

No Docker required. Writes an on-disk H2 database to `./data/employees-db`.

---

The API is then available at `http://localhost:8080`.

### Running the tests

```bash
./mvnw test
```

Tests run against an in-memory H2 database (`application-test.yml`) in PostgreSQL
compatibility mode, so they don't require Docker or a running Postgres instance.

### Example requests

```bash
# Create an employee
curl -i -X POST http://localhost:8080/employees \
  -H "Content-Type: application/json" \
  -d '{
        "firstName": "Ada",
        "lastName": "Lovelace",
        "dateOfBirth": "1990-01-01",
        "gender": "FEMALE",
        "socialSecurityNumber": "123-45-6789"
      }'

# Fetch it back
curl http://localhost:8080/employees/<id-from-above>

# List (paginated)
curl "http://localhost:8080/employees?page=0&size=10"
```

A successful create returns `201 Created` with a `Location` header and a body like:

```json
{
  "id": "b3f1...c9",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "dateOfBirth": "1990-01-01",
  "gender": "FEMALE",
  "ssnMasked": "***-**-6789",
  "createdAt": "2026-07-14T10:15:30Z"
}
```

Errors return a consistent shape, e.g. for `GET /employees/{unknown-id}`:

```json
{
  "timestamp": "2026-07-14T10:16:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "No employee found with id b3f1...c9",
  "path": "/employees/b3f1...c9"
}
```

and for validation failures, `400` with a `fieldErrors` map naming exactly which fields
were wrong.

---

## 2. Technology choices, and why

**Spring Boot 4 / Java 21.** Requested by the assignment reviewer; also gives access to
records (used for all DTOs — immutable, concise, and validation annotations work
directly on record components) and virtual-thread-friendly APIs if this were ever scaled up.

**PostgreSQL.** A relational model fits this data well (a fixed, well-typed schema,
uniqueness constraints, no need for flexible/nested documents). Postgres is free, widely
understood, and trivial to run via Docker Compose. An embedded H2 profile is also
included purely so the grader can run the service with zero setup if Docker isn't
convenient — it isn't meant as a production alternative.

**UUID primary keys instead of auto-increment longs.** For a service holding PII,
sequential integer IDs make it trivial to enumerate/scrape every record (`GET
/employees/1`, `/2`, `/3`...). UUIDs cost a little on index locality but remove that
whole class of problem essentially for free.

**Encryption over hashing for the SSN itself — with a second, separate hash for
uniqueness.** This is the one decision I'd most want to walk through out loud, so here's
the reasoning:

| | Hashing (e.g. bcrypt/SHA-256) | Encryption (AES-256-GCM, this project) |
|---|---|---|
| Reversible? | No | Yes, with the key |
| Can the org ever legitimately need the original value back? | No — a fatal problem for SSNs, which HR/payroll systems do need for W-2s, benefits enrollment, background checks, etc. | Yes |
| Can you check for duplicates without exposing the value? | Yes (compare hashes) | Not directly — encryption with a random IV produces different ciphertext every time by design |
| Brute-forceable? | SSNs have only ~1 billion possible values, so even a "slow" hash is crackable offline if the hash ever leaks, *unless* it's a **keyed** hash (HMAC) | Effectively no, given a well-protected 256-bit key |

Plaintext SSNs are the kind of thing an employer is realistically asked to *reproduce*
(reissuing a tax form, correcting a record with a government agency), so a purely
one-way hash would eventually force the business into either re-collecting the SSN from
the employee or storing it insecurely somewhere else "just in case." That tradeoff felt
worse than the alternative, so this project stores:

- **`ssnEncrypted`** — AES-256-GCM ciphertext, fresh random 96-bit IV per encryption
  (prepended to the ciphertext, so nothing else needs to be stored alongside it). This is
  what would be decrypted by a future, explicitly-authorized, audited operation — no such
  endpoint exists yet, intentionally, since nothing in the current requirements calls for
  it.
- **`ssnHash`** — HMAC-SHA256 (keyed, not a bare SHA-256) of the same SSN, used *only* to
  enforce a unique-employee-per-SSN constraint at the database level without ever
  decrypting anything to do the comparison. Using an HMAC instead of a plain hash means an
  attacker who somehow got the hash column couldn't just precompute all ~10^9 possible
  SSN hashes offline — they'd also need the server's secret key.
- **`ssnLastFour`** — kept as plain digits, since "last four" is the conventional
  minimum-disclosure fragment (it's what's on file at most background-check and benefits
  vendors already) and lets every endpoint return a cheap `***-**-1234` display value
  without touching the encryption key at all.

No endpoint decrypts or returns the full SSN — the requirement is met by construction,
not by a check I have to remember to keep enforcing.

**Key management, honestly stated:** the encryption key, HMAC secret, and database
credentials are hardcoded directly in `application.yml`. This is a deliberate tradeoff
for a take-home assignment — it means the service runs with zero setup
(`./mvnw spring-boot:run` and nothing else to configure), which matters more here than
production hygiene. **These values are not secrets** — anyone with this repo has them.

In a real deployment none of this would be checked into version control: the encryption
key and HMAC secret would come from a proper secrets manager (AWS Secrets Manager /
Vault / etc.) and be injected as environment variables at deploy time, rotated
independently of the codebase; database credentials would follow the same pattern. The
code already reads these values through Spring's `${VAR:default}` property placeholders
in a couple of spots, so wiring in real secrets later is a config change, not a code
change — but I chose to hardcode the actual values for this submission rather than half
the docs.

**Bean Validation (`jakarta.validation`) at the DTO boundary**, with a
`@RestControllerAdvice` translating failures into a single consistent JSON error shape
(`400` with a `fieldErrors` map). This keeps validation declarative and out of the
service layer, which only has to worry about business rules (like the duplicate-SSN
check) rather than "is this string blank."

**Lombok** for entity boilerplate (getters/setters/builder) — DTOs are plain records and
don't need it.

---

## 3. What I'd do differently with more time

- **Real key management**: pull the encryption key from a KMS/secrets manager
  (envelope encryption — a per-record data key wrapped by a master key) rather than a
  single static key from an env var, and add key rotation support (a `keyVersion` column
  so old records can still be decrypted after the active key changes).
- **Authentication/authorization.** Right now anyone who can reach the service can create
  and read every record. A real HR system needs at minimum service-to-service auth
  (mTLS or OAuth2 client-credentials) and probably field-level authorization (e.g., only
  payroll admins should ever trigger an SSN decrypt).
- **Audit logging** of every access to sensitive fields — who looked up which employee
  and when — which is usually a compliance requirement for systems holding SSNs.
- **PATCH/PUT and DELETE endpoints** — out of scope per the assignment, but any real HR
  service needs to correct and (soft-)delete records, ideally with the deletion itself
  being audited rather than a hard delete.
- **`@Version` optimistic locking** on `Employee` to guard concurrent updates once an
  update endpoint exists.
- **Testcontainers** instead of H2-in-Postgres-mode for the integration test, so the test
  suite exercises the exact database engine used in production rather than a
  close-but-not-identical compatibility mode.
- **Structured request logging / correlation IDs**, and making sure no logging
  configuration could ever accidentally log a raw request body containing an SSN.

---

## 4. AI tool usage

I used **Claude**, **Gemini**, and **Opencode** throughout this exercise 
for code generation and iteration speed. 
They were extremely helpful for laying out the package structure 
and brainstorming technology choices, especially when reasoning 
about the encryption architecture for the SSN.

One concrete thing I changed from the AI's suggestions: 
the initial generated draft used a plain String for the gender field. 
I rejected that and refactored it to use a strongly typed Enum. 
This ensures type safety at the Java level, makes the API more predictable, 
and prevents invalid values or typos from ever reaching the database.

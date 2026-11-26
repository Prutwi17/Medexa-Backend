# Medexa Backend

Modular monolith backend for **Medexa**, a multi-vendor medicine home delivery platform supporting `ADMIN`, `CUSTOMER`, `PHARMACY`, and `RIDER` workflows.

## Stack

- **Java**: 17 (strictly enforced via Maven compiler `--release 17`)
- **Framework**: Spring Boot 4.1.3 (Spring Web MVC, Spring Data JPA, Hibernate, Spring Security, Validation, Actuator)
- **Database**: MySQL 8 + Flyway migrations
- **API Docs**: SpringDoc OpenAPI / Swagger UI

## Package Structure (`com.medexa`)

- `config` – CORS, OpenAPI, pagination, auditing, and application properties
- `security` – Security filter chain and cryptographic beans
- `common` – Standard API envelopes, global exception handling, correlation ID filter, base entities, health endpoint
- Domain modules: `auth`, `user`, `customer`, `pharmacy`, `rider`, `category`, `medicine`, `inventory`, `prescription`, `cart`, `wishlist`, `address`, `checkout`, `order`, `payment`, `refund`, `delivery`, `notification`, `invoice`, `payout`, `review`, `admin`

## Environment Setup

1. Copy `.env.example` to `.env` (never commit `.env`).
2. Ensure MySQL 8 is running and the target database (default `medexa`) exists.
3. Run with Maven:

```bash
mvn spring-boot:run
```

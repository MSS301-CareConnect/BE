# BRIEFING — 2026-10-18T16:32:00Z

## Mission
Develop the exact, line-by-line implementation blueprint for BookingService refactoring per requirements R1, R2, R3, R4, R5.

## 🔒 My Identity
- Archetype: explorer
- Roles: codebase investigation, blueprint generation, architecture synthesis
- Working directory: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m3_1
- Original parent: c390ebfb-aa07-4694-bdae-bfb529d83f93
- Milestone: M3.1

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Blueprint must be exact, line-by-line, and self-contained
- Follow AGENTS.md conventions, Lombok patterns, MapStruct conventions, SecurityConfig, and ErrorCode rules

## Current Parent
- Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `BookingService/pom.xml`, `application.yaml`
  - `controller/`: `BookingController.java`, `CaregiverOccurrenceController.java`, `InternalBookingController.java`
  - `service/`: All 15 root service interfaces and 15 implementations in `service/Impl/`
  - `security/`: `SecurityConfig.java`, `InternalSecurityConfig.java`, `InternalSecretFilter.java`
  - `client/`: `IdentityClient`, `CareRecipientClient`, `ProviderClient`, `CareCatalogClient`, configs, DTOs, `InternalClientHeaders`
  - `config/`: `BookingProperties.java`, `OpenApiConfig.java`
  - `src/test/`: `BookingApiTests.java`, `TestPlatformServer.java`, `ClientErrorDecoderTests.java`
- **Key findings**:
  - Root `service/` already has 100% pure interfaces, but `CaregiverOccurrenceService` must be renamed to `BookingOccurrenceService` and `InternalBookingService` purged.
  - Role-separated booking endpoints (`/bookings`, `/center/bookings`, `/admin/bookings`) need unification under `@RequestMapping("/api/v1/bookings")` in `BookingController`.
  - `CaregiverOccurrenceController` (`/caregiver/occurrences`) needs refactoring to `BookingOccurrenceController` under `@RequestMapping("/api/v1/booking-occurrences")`.
  - Inter-service endpoints (`checkCaregiverAccess`, `markDepositPaid`) relocate cleanly into `BookingService`/`BookingServiceImpl` and `BookingController` under `/api/v1/bookings/**`.
  - Occurrence lookup (`getOccurrence`) relocates to `BookingOccurrenceService`/`BookingOccurrenceServiceImpl` as `getById(id)` under `BookingOccurrenceController`.
  - 6 files to delete: `InternalBookingController`, `InternalBookingService`, `InternalBookingServiceImpl`, `InternalSecurityConfig`, `InternalSecretFilter`, `InternalClientHeaders`.
  - All 4 Feign clients and configs rewire to `/api/v1/**` with `FeignAuthForwardingInterceptor`.
  - Baseline tests run and pass (69 tests). `TestPlatformServer` and `BookingApiTests` need endpoint path and auth header updates.
- **Unexplored areas**: None. Entire scope of BookingService refactoring is fully explored and mapped.

## Key Decisions Made
- Unify all booking endpoints in `BookingController` under `@RequestMapping("/api/v1/bookings")`.
- Structure subpaths cleanly: Customer (`/`, `/{id}`, `/{id}/occurrences`, `/{id}/cancel`), Center (`/center`, `/center/{id}`, `/center/{id}/accept`, `/center/{id}/reject`, `/center/{id}/caregiver`, `/center/occurrences/{occurrenceId}/caregiver`, `/center/{id}/cancel`), Admin (`/admin`, `/admin/{id}`), Shared (`/caregiver-access`, `/{id}/deposit-paid`).
- Refactor `CaregiverOccurrenceController` to `BookingOccurrenceController` (`@RequestMapping("/api/v1/booking-occurrences")`) with `GET /` and `GET /{id}`.
- Relocate internal logic directly into `BookingServiceImpl` and `BookingOccurrenceServiceImpl` without altering database schema or business rules.

## Artifact Index
- handoff.md — Complete implementation blueprint
- progress.md — Liveness heartbeat

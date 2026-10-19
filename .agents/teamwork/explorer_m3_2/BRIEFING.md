# BRIEFING — 2026-10-18T16:31:00Z

## Mission
Develop the exact, line-by-line implementation blueprint for CareCatalogService refactoring per requirements R1, R2, R3, R4, R5.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigation, synthesis
- Working directory: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m3_2
- Original parent: c390ebfb-aa07-4694-bdae-bfb529d83f93
- Milestone: M3 (Feature 15 & 16: CareCatalogService Refactoring)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Develop exact line-by-line implementation blueprint for CareCatalogService refactoring (R1, R2, R3, R4, R5)
- Write only to .agents/teamwork/explorer_m3_2/
- Output handoff report to handoff.md

## Current Parent
- Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `CareCatalogService/src/main/java/mss301/carecatalogservice/controller/` (`CatalogItemController.java`, `CenterOfferingController.java`, `PricePlanController.java`, `InternalCatalogController.java`)
  - `CareCatalogService/src/main/java/mss301/carecatalogservice/service/` (`CatalogItemService.java`, `CatalogQuoteService.java`, `CenterOfferingService.java`, `PricePlanService.java`)
  - `CareCatalogService/src/main/java/mss301/carecatalogservice/service/Impl/` (`CatalogItemServiceImpl.java`, `CatalogQuoteServiceImpl.java`, `CenterOfferingServiceImpl.java`, `PricePlanServiceImpl.java`)
  - `CareCatalogService/src/main/java/mss301/carecatalogservice/entity/` (`BaseEntity.java`, `CatalogItem.java`, `CenterOffering.java`, `PricePlan.java`)
  - `CareCatalogService/src/main/java/mss301/carecatalogservice/repository/` (`CatalogItemRepository.java`, `CenterOfferingRepository.java`, `PricePlanRepository.java`)
  - `CareCatalogService/src/main/java/mss301/carecatalogservice/mapper/` (`CatalogItemMapper.java`, `CenterOfferingMapper.java`, `PricePlanMapper.java`, `MapStructConfig.java`, `IgnoreServerManagedFields.java`)
  - `CareCatalogService/src/main/java/mss301/carecatalogservice/security/` (`InternalSecurityConfig.java`, `InternalSecretFilter.java`, `SecurityConfig.java`)
  - `CareCatalogService/src/main/java/mss301/carecatalogservice/config/` (`CareCatalogProperties.java`, `CareCatalogConfig.java`, `OpenApiConfig.java`)
  - `CareCatalogService/src/main/resources/application.yaml`
  - `CareCatalogService/.env.example`
  - `CareCatalogService/src/test/` (`CatalogQuoteServiceTests.java`, `CareCatalogServiceApplicationTests.java`)
  - `BookingService/src/main/java/mss301/bookingservice/client/CareCatalogClient.java` (consumer of quote endpoint)
- **Key findings**:
  - `CatalogItemController` currently uses `@RequestMapping("/care-services")`; needs prefix `/api/v1/catalog-items` (with optional alias `/api/v1/care-services`), and `@PreAuthorize("isAuthenticated()")` added to GET endpoints.
  - `CenterOfferingController` currently uses `@RequestMapping("/center-services")`; needs prefix `/api/v1/center-services`, `@PreAuthorize("isAuthenticated()")` added to GET endpoints, and relocation of quote endpoint `POST /api/v1/center-services/quote`.
  - `PricePlanController` currently uses `@RequestMapping("/price-plans")`; needs prefix `/api/v1/price-plans` and `@PreAuthorize("isAuthenticated()")` added to GET endpoints.
  - Quote calculation endpoint `POST /internal/center-services/quote` resides in `InternalCatalogController.java` using `CatalogQuoteService`. Relocating to `CenterOfferingController` via `CenterOfferingService.quote(request)` (delegating to `CatalogQuoteService`).
  - DTOs `InternalServiceQuoteRequest` and `InternalServiceQuoteResponse` must be renamed to `ServiceQuoteRequest` and `ServiceQuoteResponse` to eliminate all `*Internal*` artifacts per R4.
  - `InternalCatalogController.java`, `InternalSecurityConfig.java`, `InternalSecretFilter.java` must be deleted completely.
  - `CareCatalogProperties.java`, `application.yaml`, `.env.example`, and `OpenApiConfig.java` must remove `internalSecret` / `INTERNAL_SCHEME` configurations.
  - 100% of services are already interfaces, 100% implementations in `service/Impl/` (R2 compliant).
  - 0% entities use `@Data`; all use `@Getter @Setter` and extend `BaseEntity` (R5 compliant).
  - All MapStruct mappers use `MapStructConfig` with `ReportingPolicy.ERROR` (R5 compliant).
  - Unit tests currently pass (3/3 in `CatalogQuoteServiceTests`).
- **Unexplored areas**: None for CareCatalogService.

## Key Decisions Made
- Relocate quote endpoint to `CenterOfferingController` at `POST /api/v1/center-services/quote` guarded by `@PreAuthorize("isAuthenticated()")`.
- Expose `quote(ServiceQuoteRequest request)` on `CenterOfferingService` which delegates to `CatalogQuoteService` to maintain single domain service entrypoint while preserving existing pricing calculation unit tests.
- Rename `InternalServiceQuoteRequest` and `InternalServiceQuoteResponse` to `ServiceQuoteRequest` and `ServiceQuoteResponse`.
- Delete all 3 internal security/controller files.
- Strip `internalSecret` and `INTERNAL_SCHEME` from `CareCatalogProperties`, `application.yaml`, `.env.example`, and `OpenApiConfig`.

## Artifact Index
- handoff.md — Refactoring blueprint for CareCatalogService
- progress.md — Liveness heartbeat
- BRIEFING.md — Situational awareness
- DISPATCH.md — Task dispatch

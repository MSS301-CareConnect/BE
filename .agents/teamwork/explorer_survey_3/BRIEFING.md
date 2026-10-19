# BRIEFING — 2026-10-18T14:45:00Z

## Mission
Investigate CareCatalogService, ApiGateway, and Cross-Service Communication (OpenFeign clients, RequestInterceptor, MediaService) per R1, R2, R4, R5.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigator, surveyor, analyzer
- Working directory: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_3
- Original parent: c390ebfb-aa07-4694-bdae-bfb529d83f93
- Milestone: Explorer Survey Phase

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Write only to .agents/teamwork/explorer_survey_3/
- Provide exact file paths, line numbers, and actionable recommendations

## Current Parent
- Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93
- Updated: 2026-10-18T14:45:00Z

## Investigation State
- **Explored paths**:
  - `CareCatalogService`: all controllers, services, repositories, entities, mappers, security, config, tests.
  - `ApiGateway`: GatewayPaths, RouteConfig, GatewayProperties, GatewayRequestHeaders, InternalPathBlockFilter, SecurityConfig, application.yaml, tests.
  - `MediaService`: checked existence, controllers, security, R2 config, dependencies.
  - Cross-Service Feign Clients across all 6 services:
    - CareRecipientService: IdentityClient, BookingClient
    - ProviderService: IdentityClient, MediaClient
    - BookingService: IdentityClient, CareRecipientClient, ProviderClient, CareCatalogClient
- **Key findings**:
  - CareCatalogService: 3 domain controllers (`CatalogItemController`, `CenterOfferingController`, `PricePlanController`) lack `/api/v1/` prefix. Has `InternalCatalogController` with `POST /internal/center-services/quote`, `InternalSecurityConfig`, `InternalSecretFilter`. Entities strictly use `@Getter @Setter`, no `@Data`. Mappers use MapStruct + `@MapperConfig`. Has no Feign client.
  - ApiGateway: Only routes 3 services (`identity`, `care-recipient`, `booking`). Completely missing routes for `provider`, `care-catalog`, and `media`. `InternalPathBlockFilter` blocks `/internal/**` and must be deleted. `GatewayPaths.IDENTITY_TOKEN_FLOW_ENDPOINTS` needs path updates to `/api/v1/auth/**`. Crucially, `RouteConfig` currently has `stripPrefix(2)`; when all services adopt `/api/v1` prefixes per R3, `stripPrefix` MUST BE REMOVED so `/api/v1` reaches upstream controllers.
  - MediaService: Real service exists (port 8086). Currently only has `InternalMediaController` (`/internal/media`), `InternalSecurityConfig`, and `denyAll()` on default chain! Lacks `spring-boot-starter-security-oauth2-resource-server`. Needs full migration to `MediaController` (`/api/v1/media`) with JWT resource server configuration.
  - Cross-Service Feign Clients: Exactly 7 Feign clients exist across 3 services. All currently use direct URLs and `X-Internal-Secret`. Must switch target URLs to ApiGateway (`http://localhost:8080`), change mapping paths to `/api/v1/...`, and replace secret interceptor with JWT forwarding `RequestInterceptor` reading from `SecurityContextHolder` / `RequestContextHolder`.
- **Unexplored areas**: None within scope. All 3 areas comprehensively surveyed.

## Key Decisions Made
- Confirmed that `stripPrefix(2)` in `RouteConfig` must be removed when services add `/api/v1` prefix to `@RequestMapping`.
- Defined standard `FeignAuthForwardingInterceptor` pattern utilizing `SecurityContextHolder` and `RequestContextHolder`.
- Mapped all 7 Feign clients and their target `/internal/**` endpoints to corresponding `/api/v1/**` endpoints.

## Artifact Index
- d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_3\DISPATCH.md — Task dispatch
- d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_3\BRIEFING.md — Working memory
- d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_3\progress.md — Liveness heartbeat
- d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_3\handoff.md — Comprehensive survey report

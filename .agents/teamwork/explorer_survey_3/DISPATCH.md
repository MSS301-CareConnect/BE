# DISPATCH — Explorer Survey 3

You are Explorer Survey 3.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_3
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md

Mission:
Investigate CareCatalogService, ApiGateway, and Cross-Service Communication (OpenFeign clients, RequestInterceptor, MediaService) per R1, R2, R4, R5.
Specific items to survey:
1. CareCatalogService:
   - All controllers, services, repositories, entities (@Getter @Setter vs @Data), mappers.
   - Any /internal/** controllers, services, InternalSecretFilter, InternalSecurityConfig.
   - API versioning: check if all endpoints have /api/v1/ prefix.
   - Any Feign clients in client/.
2. ApiGateway:
   - Check all routing configs: GatewayPaths, RouteConfig, application.yaml.
   - Check which services are currently routed and which are missing.
   - Check InternalPathBlockFilter or any filter blocking /internal.
   - Check CORS configuration and header forwarding.
   - Verify how ApiGateway should route all 6 services: IdentityService, CareRecipientService, BookingService, ProviderService, CareCatalogService, and MediaService (check if MediaService exists or if route placeholder is needed).
3. Cross-Service OpenFeign Clients & Auth Forwarding:
   - Search across ALL services for any Feign clients (in client/ folders).
   - Check current target URLs (direct service URLs vs ApiGateway URL).
   - Check how RequestInterceptor can be added to forward `Authorization: Bearer <token>` from the current request context (RequestContextHolder / SecurityContext).
   - Check what internal APIs are currently called via Feign, and what public endpoints (/api/v1/...) they must be switched to.

Output:
Write a comprehensive survey report with exact file paths, current route mappings, Feign client definitions, and recommended configuration changes to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_3\handoff.md

Send a message to your parent when complete.

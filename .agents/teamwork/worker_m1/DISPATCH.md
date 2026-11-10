# DISPATCH — Worker Milestone 1 (IdentityService & CareRecipientService Refactoring)

You are Worker M1.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\worker_m1
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Read the detailed implementation blueprints from the Explorers:
- d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_1\handoff.md (IdentityService Blueprint)
- d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_2\handoff.md (CareRecipientService Blueprint)
- d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_3\handoff.md (MapStruct, Test & Compile Strategy)

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Mission:
Execute Milestone 1 refactoring across IdentityService and CareRecipientService.

### Part 1: IdentityService Refactoring
1. Move the 5 endpoints (/register, /register/resend-otp, /register/verify, /forgot-password, /reset-password) from AccountController to AuthenticationController under /api/v1/auth/**. Inject RegistrationService in AuthenticationController.
2. Refactor AccountController -> UserController (/api/v1/users/**) and AccountService / AccountServiceImpl -> UserService / UserServiceImpl. Delete AccountController, AccountService, AccountServiceImpl. Update AuthenticationServiceImpl and RegistrationServiceImpl to inject UserService.
3. Expose user lookup endpoints in UserController and UserServiceImpl:
   - GET /api/v1/users/{id}
   - GET /api/v1/users/by-email?email=...
   - POST /api/v1/users/batch
4. Delete all /internal/** artifacts in IdentityService:
   - InternalUserController.java, InternalUserService.java, InternalUserServiceImpl.java, InternalSecurityConfig.java, InternalSecretFilter.java.
   - Remove internalSecret from IdentityProperties.java and application.yaml.
   - Remove INTERNAL_SCHEME / InternalSecretFilter references from OpenApiConfig.java.
5. Update SecurityConfig.java constants (TOKEN_FLOW_ENDPOINTS, COOKIELESS_CSRF_EXEMPT_PATHS, MOBILE_AUTH_PREFIX) with /api/v1/auth/**. Add /api/v1 prefix to RoleController (/api/v1/roles) and PermissionController (/api/v1/permissions).
6. Update IdentityServiceApplicationTests.java to align with /api/v1 paths, UserService, and Bearer-token user lookup tests.

### Part 2: CareRecipientService Refactoring
1. Consolidate into single CareRecipientController (/api/v1/care-recipients/**):
   - Include Customer CRUD, Admin list/get (/admin, /admin/{id}), Caregiver snapshot (/caregiver/{id}), and Inter-service endpoints (/{id}/snapshot, /{id}/access, /batch) with method-level @PreAuthorize.
   - Delete AdminCareRecipientController.java and CaregiverCareRecipientController.java.
2. Merge fragmented services:
   - Merge CaregiverRecipientService, CaregiverAssignmentService, and InternalCareRecipientService into CareRecipientService / CareRecipientServiceImpl. Inject BookingClient directly into CareRecipientServiceImpl.
   - Delete CaregiverRecipientService.java, CaregiverRecipientServiceImpl.java, CaregiverAssignmentService.java, CaregiverAssignmentServiceImpl.java, InternalCareRecipientService.java, InternalCareRecipientServiceImpl.java.
3. Delete all /internal/** artifacts in CareRecipientService:
   - InternalCareRecipientController.java, InternalSecurityConfig.java, InternalSecretFilter.java.
   - Remove internalSecret from CareRecipientProperties.java and application.yaml.
   - Remove INTERNAL_SCHEME / InternalSecretFilter references from OpenApiConfig.java.
4. Add /api/v1 prefix to sub-resource controllers:
   - EmergencyContactController (/api/v1/care-recipients/{recipientId}/emergency-contacts)
   - MedicationController (/api/v1/care-recipients/{recipientId}/medications)
   - RecipientManagerController (/api/v1/care-recipients/{recipientId}/managers)
5. Implement FeignAuthForwardingInterceptor in client/ and update BookingClientConfig and IdentityClientConfig to forward Bearer tokens instead of internal secrets. Update BookingClient (@GetMapping("/api/v1/bookings/caregiver-access")) and IdentityClient (@GetMapping("/api/v1/users/**")).
6. Update CareRecipientApiTests.java, TestIdentityServer.java, and TestBookingServer.java to align with /api/v1 paths and Bearer token headers.

### Part 3: Verification
Run:
- In IdentityService: .\mvnw.cmd clean compile && .\mvnw.cmd test
- In CareRecipientService: .\mvnw.cmd clean compile && .\mvnw.cmd test
Both must compile cleanly (BUILD SUCCESS) and pass tests.

Output:
Write a comprehensive handoff report with modified/deleted/created files, test output, and build verification to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\worker_m1\handoff.md
Send a message via send_message to your parent when complete.

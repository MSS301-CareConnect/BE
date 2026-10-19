# DISPATCH — Explorer M1.1 (IdentityService Refactoring)

## 2026-10-18T14:46:21Z

You are Explorer M1.1.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_1
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Also read the prior survey report at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_1\handoff.md
And your task dispatch at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_1\DISPATCH.md

Mission:
Develop the exact, line-by-line implementation strategy for Milestone 1 — IdentityService:
1. Migrate 5 endpoints from AccountController to AuthenticationController (/api/v1/auth/**):
   - /api/v1/auth/register
   - /api/v1/auth/register/resend-otp
   - /api/v1/auth/register/verify
   - /api/v1/auth/forgot-password
   - /api/v1/auth/reset-password
   Ensure RegistrationService is properly injected and AccountController no longer references registration.
2. Refactor AccountController -> UserController (/api/v1/users/**) and AccountService -> UserService / UserServiceImpl.
3. Expose user lookup endpoints in UserController for inter-service callers:
   - GET /api/v1/users/{id}
   - GET /api/v1/users/by-email?email=...
   - POST /api/v1/users/batch
4. Delete all /internal/** artifacts:
   - InternalUserController.java, InternalUserService.java, InternalUserServiceImpl.java, InternalSecurityConfig.java, InternalSecretFilter.java.
   - Remove internalSecret from IdentityProperties.java and application.yaml.
5. Update SecurityConfig.java paths and CSRF exemptions to match /api/v1/auth/** and /api/v1/users/**.
6. Verify MapStruct mappers and ensure 0 compilation errors with mvn clean compile.

Output:
Write your implementation plan to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_1\handoff.md
Send a message to your parent when done.

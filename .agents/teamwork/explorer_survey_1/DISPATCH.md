# DISPATCH — Explorer Survey 1

You are Explorer Survey 1.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_1
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md

Mission:
Investigate IdentityService and CareRecipientService in detail to map the current architecture against R1, R2, R3, R4, R5.
Specific items to survey:
1. IdentityService:
   - All controllers (AccountController, AuthenticationController, RoleController, PermissionController, JwksController, InternalUserController, etc.)
   - Paths, endpoints, request/response DTOs, and which auth/registration/OTP/password endpoints currently reside in AccountController vs AuthenticationController.
   - All services & service implementations: AccountService, AuthenticationService, RegistrationService, etc.
   - Internal security: InternalSecurityConfig, InternalSecretFilter, header X-Internal-Secret, InternalUserController, InternalUserService.
   - API versioning: Are endpoints prefixed with /api/v1/?
   - Entities and Lombok annotations (@Getter @Setter vs @Data), MapStruct mappers.
   - Any Feign clients in client/.
2. CareRecipientService:
   - All controllers: AdminCareRecipientController, CaregiverCareRecipientController, CareRecipientController, InternalCareRecipientController, etc.
   - All services & service implementations: CareRecipientService, CaregiverRecipientService, CaregiverAssignmentService, InternalCareRecipientService.
   - Endpoints, paths, @PreAuthorize, role separation issues.
   - Internal security & filters.
   - API versioning: Are endpoints prefixed with /api/v1/?
   - Entities and Lombok annotations, MapStruct mappers.
   - Any Feign clients in client/.

Output:
Write a comprehensive survey report with exact file paths, line numbers, classes to rename/merge/delete, endpoints to migrate, and compilation risk areas to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_1\handoff.md

Send a message to your parent when complete.

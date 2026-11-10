# DISPATCH — Explorer M2.1 (ProviderService Controller Unification)

You are Explorer M2.1.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_1
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Read the prior survey report at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_2\handoff.md

Mission:
Develop the exact, line-by-line controller unification blueprint for Milestone 2 — ProviderService:
1. Consolidate 11 controllers into 4 domain aggregate controllers + ProviderMediaController:
   - CareCenterController (/api/v1/care-centers/**): Merged from AdminCareCenterController and CenterScheduleController.
   - CaregiverController (/api/v1/caregivers/**): Merged from CaregiverManagementController, CaregiverWorkController (caregiver endpoints), AdminVerificationController (caregiver verification), and ProviderCatalogController (caregiver catalog).
   - WorkShiftController (/api/v1/work-shifts/**): Merged from WorkShiftController and ShiftRegistration endpoints.
   - CertificateController (/api/v1/certificates/**): Merged from CertificateController, AdminVerificationController (certificate verification), and ProviderCatalogController (certificate catalog).
   - ProviderMediaController (/api/v1/provider-media/**): Prefixed with /api/v1.
2. Delete InternalCaregiverController.java and InternalCenterController.java. Expose public inter-service lookups on CaregiverController and CareCenterController:
   - GET /api/v1/care-centers/{centerId}
   - GET /api/v1/caregivers/{caregiverId}
   - GET /api/v1/caregivers/by-user/{userId}
3. Method-level @PreAuthorize for each role (ADMIN, CENTER_MANAGER, CAREGIVER, CUSTOMER, isAuthenticated()).

Output:
Write your blueprint to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_1\handoff.md
Send a message via send_message to your parent when done.

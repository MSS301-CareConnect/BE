# DISPATCH — Explorer M1.2 (CareRecipientService Refactoring)

You are Explorer M1.2.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_2
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Also read the prior survey report at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_1\handoff.md

Mission:
Develop the exact, line-by-line implementation strategy for Milestone 1 — CareRecipientService:
1. Merge 3 controllers (AdminCareRecipientController, CaregiverCareRecipientController, CareRecipientController) into a single CareRecipientController (/api/v1/care-recipients/**).
   - Use method-level @PreAuthorize.
   - Delete AdminCareRecipientController.java and CaregiverCareRecipientController.java.
2. Merge fragmented services:
   - Merge CaregiverRecipientService, CaregiverAssignmentService, and InternalCareRecipientService into CareRecipientService / CareRecipientServiceImpl.
   - Delete CaregiverRecipientService.java, CaregiverRecipientServiceImpl.java, CaregiverAssignmentService.java, CaregiverAssignmentServiceImpl.java, InternalCareRecipientService.java, InternalCareRecipientServiceImpl.java.
3. Delete all /internal/** artifacts:
   - InternalCareRecipientController.java, InternalSecurityConfig.java, InternalSecretFilter.java.
   - Remove internalSecret from CareRecipientProperties.java and application.yaml.
4. Expose snapshot, access check, and batch on CareRecipientController:
   - GET /api/v1/care-recipients/{id}/snapshot
   - GET /api/v1/care-recipients/{id}/access?userId=...&minLevel=...
   - POST /api/v1/care-recipients/batch
5. Add /api/v1 prefix to sub-resource controllers:
   - EmergencyContactController (/api/v1/care-recipients/{recipientId}/emergency-contacts)
   - MedicationController (/api/v1/care-recipients/{recipientId}/medications)
   - RecipientManagerController (/api/v1/care-recipients/{recipientId}/managers)

Output:
Write your implementation plan to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_2\handoff.md
Send a message to your parent when done.

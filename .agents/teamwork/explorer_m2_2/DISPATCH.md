# DISPATCH — Explorer M2.2 (ProviderService Service Layer Refactoring)

You are Explorer M2.2.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_2
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Read the prior survey report at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_2\handoff.md

Mission:
Develop the exact, line-by-line service layer refactoring blueprint for Milestone 2 — ProviderService:
1. Extract interfaces for 6 service classes that are currently classes in service/:
   - CaregiverService -> interface CaregiverService in service/ + class CaregiverServiceImpl in service/Impl/
   - CaregiverScheduleService -> interface CaregiverScheduleService in service/ + class CaregiverScheduleServiceImpl in service/Impl/
   - CertificateService -> interface CertificateService in service/ + class CertificateServiceImpl in service/Impl/
   - IdentityAccountService -> interface IdentityAccountService in service/ + class IdentityAccountServiceImpl in service/Impl/
   - ShiftRegistrationService -> interface ShiftRegistrationService in service/ + class ShiftRegistrationServiceImpl in service/Impl/
   - WorkShiftService -> interface WorkShiftService in service/ + class WorkShiftServiceImpl in service/Impl/
2. Delete /internal/** services and security:
   - Delete InternalProviderService.java, InternalProviderServiceImpl.java
   - Delete InternalSecurityConfig.java, InternalSecretFilter.java
   - Remove provider.internal-secret from ProviderProperties.java and application.yaml
   - Remove internal scheme and references from OpenApiConfig.java.
3. Ensure 100% of services in service/ are pure interfaces, 100% of implementations are in service/Impl/.

Output:
Write your blueprint to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_2\handoff.md
Send a message via send_message to your parent when done.

## 2026-10-18T15:32:58Z
You are Explorer M2.2.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_2
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Read the prior survey report at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_2\handoff.md
Read your dispatch assignment at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_2\DISPATCH.md

Mission:
Develop the exact, line-by-line service layer refactoring blueprint for Milestone 2 — ProviderService:
1. Extract interfaces for 6 service classes that are currently classes in service/:
   - CaregiverService -> interface CaregiverService in service/ + class CaregiverServiceImpl in service/Impl/
   - CaregiverScheduleService -> interface CaregiverScheduleService in service/ + class CaregiverScheduleServiceImpl in service/Impl/
   - CertificateService -> interface CertificateService in service/ + class CertificateServiceImpl in service/Impl/
   - IdentityAccountService -> interface IdentityAccountService in service/ + class IdentityAccountServiceImpl in service/Impl/
   - ShiftRegistrationService -> interface ShiftRegistrationService in service/ + class ShiftRegistrationServiceImpl in service/Impl/
   - WorkShiftService -> interface WorkShiftService in service/ + class WorkShiftServiceImpl in service/Impl/
2. Delete /internal/** services and security:
   - Delete InternalProviderService.java, InternalProviderServiceImpl.java
   - Delete InternalSecurityConfig.java, InternalSecretFilter.java
   - Remove provider.internal-secret from ProviderProperties.java and application.yaml
   - Remove internal scheme and references from OpenApiConfig.java.
3. Ensure 100% of services in service/ are pure interfaces, 100% of implementations are in service/Impl/.

Write your report to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_2\handoff.md
Send a message via send_message to your parent (c390ebfb-aa07-4694-bdae-bfb529d83f93) when done.

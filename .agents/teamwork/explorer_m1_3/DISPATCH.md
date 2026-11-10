# DISPATCH — Explorer M1.3 (Contracts, Mappers & Compile Verification Strategy)

You are Explorer M1.3.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_3
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Also read the prior survey report at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_1\handoff.md

Mission:
Develop the risk mitigation, compile validation, and test alignment strategy for Milestone 1:
1. MapStruct Compliance:
   - Check MapStructConfig unmappedTargetPolicy = ReportingPolicy.ERROR in both IdentityService and CareRecipientService.
   - Ensure all DTO/Entity mappings have explicit source/target mappings or ignores so that MapStruct compilation succeeds.
2. Lombok & Entity Integrity (R5):
   - Confirm 0 occurrences of @Data on entities in both services.
3. Test suite updates:
   - Identify test classes in IdentityService and CareRecipientService (e.g. IdentityServiceApplicationTests.java, CareRecipientApiTests.java) that mock or test old endpoints (/users/register, /internal/..., /auth/...) and specify how they must be updated so tests pass.
4. Compilation Command & Verification Checklist:
   - Provide the exact compilation commands for Maven Wrapper (mvnw.cmd clean compile) and verification steps for the Worker.

Output:
Write your plan and checklist to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_3\handoff.md
Send a message to your parent when done.

## 2026-10-18T14:46:21Z
You are Explorer M1.3.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_3
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Also read the prior survey report at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_1\handoff.md
And your task dispatch at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_3\DISPATCH.md

Mission:
Develop the risk mitigation, compile validation, and test alignment strategy for Milestone 1:
1. MapStruct Compliance:
   - Check MapStructConfig unmappedTargetPolicy = ReportingPolicy.ERROR in both IdentityService and CareRecipientService.
   - Ensure all DTO/Entity mappings have explicit source/target mappings or ignores so that MapStruct compilation succeeds.
2. Lombok & Entity Integrity (R5):
   - Confirm 0 occurrences of @Data on entities in both services.
3. Test suite updates:
   - Identify test classes in IdentityService and CareRecipientService (e.g. IdentityServiceApplicationTests.java, CareRecipientApiTests.java) that mock or test old endpoints (/users/register, /internal/..., /auth/...) and specify how they must be updated so tests pass.
4. Compilation Command & Verification Checklist:
   - Provide the exact compilation commands for Maven Wrapper (mvnw.cmd clean compile) and verification steps for the Worker.

Output:
Write your plan and checklist to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m1_3\handoff.md
Send a message via send_message to your parent (c390ebfb-aa07-4694-bdae-bfb529d83f93) when done.

# DISPATCH — Explorer M2.3 (ProviderService MapStruct, Feign & Build Verification)

You are Explorer M2.3.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_3
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)

Read the authoritative user request at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md
Read the project architecture and feature inventory at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md
Read the prior survey report at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_2\handoff.md

Mission:
Develop the MapStruct conversion, Feign client update, and build verification blueprint for Milestone 2:
1. MapStruct Standardization (R5):
   - Standardize ProviderViewMapper.java (which currently uses manual builder methods) into MapStruct mappers (@Mapper(config = MapStructConfig.class)).
   - Verify unmappedTargetPolicy = ReportingPolicy.ERROR is satisfied across all mappers.
   - Delete InternalProviderMapper.java.
2. Cross-Service Feign Clients (R4):
   - Implement FeignAuthForwardingInterceptor in client/
   - Update IdentityClient to call @GetMapping("/api/v1/users/{userId}")
   - Update MediaClient to call @PostMapping("/api/v1/media") and @GetMapping("/api/v1/media/{mediaFileId}")
   - Update IdentityClientConfig and MediaClientConfig to use FeignAuthForwardingInterceptor instead of InternalSecretFilter.
3. Test suite updates & Maven Verification:
   - Identify all tests in ProviderService (e.g. ProviderApiTests, InternalProviderApiTests) that must be updated or deleted.
   - Specify Maven Wrapper commands (mvnw.cmd clean compile && mvnw.cmd clean test) and verification checklists.

Output:
Write your blueprint to:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_3\handoff.md
Send a message via send_message to your parent when done.

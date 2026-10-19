# Task Dispatch — Worker M2 Fix: Client DTO Jackson Resiliency

## 2026-10-18T16:16:00Z

### Mission
Fix the specific issue identified by Challenger M2.2 during Milestone 2 Gate evaluation:
1. In `ProviderService/src/main/java/mss301/providerservice/client/dto/`:
   - Add `@JsonIgnoreProperties(ignoreUnknown = true)` from `com.fasterxml.jackson.annotation.JsonIgnoreProperties` to:
     * `IdentityUserResponse.java`
     * `MediaClientResponse.java`
     * `DownloadUrlClientResponse.java`
   This is required because `application.yaml` has `spring.jackson.deserialization.fail-on-unknown-properties: true`, and upstream payloads from `IdentityService` (`UserResponse`) and `MediaService` contain extra fields that caused fatal Jackson `UnrecognizedPropertyException`.
2. Add a unit test in `src/test/java/mss301/providerservice/client/IdentityUserResponseContractTest.java` (or new test) verifying that deserializing JSON with unexpected extra properties succeeds without error.
3. Verify compilation and test suite:
   `cd d:\FA26\MSS\CareConnect\BE\ProviderService && .\mvnw.cmd clean compile && .\mvnw.cmd test`
   Confirm BUILD SUCCESS with all tests passing.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work.

Write your report to:
`d:\FA26\MSS\CareConnect\BE\.agents\teamwork\worker_m2_fix\handoff.md`
Send a message via send_message to your parent (c390ebfb-aa07-4694-bdae-bfb529d83f93) when complete.

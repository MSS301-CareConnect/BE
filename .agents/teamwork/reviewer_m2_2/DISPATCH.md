# Task Dispatch — Reviewer M2.2: ProviderService Interface & MapStruct Review

## 2026-10-18T16:02:00Z

### Mission
Perform an adversarial architecture review of ProviderService focusing on:
1. **Interface-Impl Purity (R2)**:
   - Verify that 100% of files in `mss301.providerservice.service` are pure interfaces (0 classes).
   - Verify that 100% of files in `mss301.providerservice.service.Impl` are concrete classes ending with `ServiceImpl` (0 interfaces).
   - Verify the 6 converted services: `CaregiverService`, `CaregiverScheduleService`, `CertificateService`, `IdentityAccountService`, `ShiftRegistrationService`, `WorkShiftService`.
2. **Internal Security Purge (R4)**:
   - Confirm 0 occurrences of `InternalSecretFilter`, `InternalSecurityConfig`, `InternalProviderService`, `InternalProviderMapper`, `InternalProviderApiTests`, or `/internal/` endpoints in ProviderService.
3. **MapStruct Standardization (R5)**:
   - Verify `ProviderViewMapper` is an interface with `@Mapper(config = MapStructConfig.class)` and compiles with 0 errors under `ReportingPolicy.ERROR`.
   - Verify `FeignAuthForwardingInterceptor` in `client/` and Feign client endpoint paths.
4. **Compile & Test Verification**:
   - Run:
     `cd d:\FA26\MSS\CareConnect\BE\ProviderService && .\mvnw.cmd clean compile && .\mvnw.cmd test`
   - Verify `BUILD SUCCESS`.

Write your review report and clear verdict (APPROVE or REQUEST_CHANGES) to:
`d:\FA26\MSS\CareConnect\BE\.agents\teamwork\reviewer_m2_2\handoff.md`
Send a message via send_message to your parent (c390ebfb-aa07-4694-bdae-bfb529d83f93) when complete.

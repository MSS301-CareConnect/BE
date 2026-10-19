# Task Dispatch — Challenger M2.1: ProviderService API & Access Challenger

## 2026-10-18T16:02:00Z

### Mission
Empirically stress-test the refactored endpoints and security annotations in ProviderService:
1. Verify endpoint routing on all 5 controllers under `/api/v1/...`:
   - `CareCenterController` (`/api/v1/care-centers/**`)
   - `CaregiverController` (`/api/v1/caregivers/**`)
   - `WorkShiftController` (`/api/v1/work-shifts/**`)
   - `CertificateController` (`/api/v1/certificates/**`)
   - `ProviderMediaController` (`/api/v1/provider-media/**`)
2. Verify role-based security enforcement:
   - Admin-only endpoints require `hasRole('ADMIN')`
   - Center Manager endpoints require `hasAnyRole('ADMIN','CENTER_MANAGER')`
   - Caregiver endpoints require `hasRole('CAREGIVER')` or `hasAnyRole(...)`
   - Public inter-service endpoints (`GET /api/v1/care-centers/{id}`, `GET /api/v1/caregivers/{id}`, `GET /api/v1/caregivers/by-user/{userId}`) require `@PreAuthorize("isAuthenticated()")`.
3. Run the test suite:
   `cd d:\FA26\MSS\CareConnect\BE\ProviderService && .\mvnw.cmd test`
   Confirm 23/23 tests pass.

Write your adversarial assessment report and verdict (APPROVE or REQUEST_CHANGES) to:
`d:\FA26\MSS\CareConnect\BE\.agents\teamwork\challenger_m2_1\handoff.md`
Send a message via send_message to your parent (c390ebfb-aa07-4694-bdae-bfb529d83f93) when complete.

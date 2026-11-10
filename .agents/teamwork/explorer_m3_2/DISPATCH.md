# Task Dispatch — Explorer M3.2: CareCatalogService Refactoring Blueprint

## 2026-10-18T16:26:00Z

### Mission
Develop the exact, line-by-line implementation blueprint for CareCatalogService refactoring per requirements R1, R2, R3, R4, R5:
1. **Controller Versioning & Aggregate Consolidation (R1 & R3)**:
   - Identify all controllers in `CareCatalogService/src/main/java/mss301/carecatalogservice/controller/` (`CatalogItemController`, `CenterOfferingController`, `PricePlanController`, `InternalCatalogController`, any others).
   - Add `/api/v1/` prefix to all public controllers:
     * `CatalogItemController` (`/api/v1/catalog-items/**`)
     * `CenterOfferingController` (`/api/v1/center-services/**`)
     * `PricePlanController` (`/api/v1/price-plans/**`)
   - Specify method-level `@PreAuthorize` annotations on all endpoints.
2. **Relocate Quote Calculation from Internal to Public (R4)**:
   - Inspect the quote calculation endpoint currently in `InternalCatalogController`:
     `POST /internal/catalog/quote`
   - Plan relocation of quote calculation to `CenterOfferingController`:
     `POST /api/v1/center-services/quote` (with `@PreAuthorize("isAuthenticated()")`).
   - Move or wire the quote calculation logic into `CenterOfferingService` (or retain pure service layer delegate).
3. **Internal Security & Artifact Purge (R4)**:
   - Identify all `/internal/**` files: `InternalCatalogController.java`, `InternalSecurityConfig.java`, `InternalSecretFilter.java`.
   - Plan complete deletion of these files.
   - Plan removal of `internalSecret` from `CatalogProperties.java`, `application.yaml`, and `OpenApiConfig.java`.
4. **Service Layer & Repositories (R2) & MapStruct/Lombok (R5)**:
   - Verify that 100% of services in `service/` are interfaces and 100% of implementations are in `service/Impl/`.
   - Verify that 0 entities use `@Data` (only `@Getter @Setter`).
   - Verify MapStruct mappers comply with `MapStructConfig` (`ReportingPolicy.ERROR`).
5. **Test Suite & Verification Strategy**:
   - List all tests in `CareCatalogService/src/test/` and specify updates for new `/api/v1/` paths and deleted `/internal/**` endpoints.
   - Specify exact Maven Wrapper compilation and test commands (`mvnw.cmd clean compile && mvnw.cmd clean test`).

Write your blueprint to:
`d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m3_2\handoff.md`
Send a message via send_message to your parent (c390ebfb-aa07-4694-bdae-bfb529d83f93) when complete.

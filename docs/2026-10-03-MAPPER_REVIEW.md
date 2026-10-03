# Rà soát tầng mapper (MapStruct) — IdentityService + CareRecipientService

> Ngày: 2026-10-03. Chuẩn áp dụng: quy tắc ở §3 (cũng ghi vào `AGENTS.md`), như nhau cho cả hai service.
> Ràng buộc: không đổi hành vi / JSON response của API nào. Chưa commit.

## 1. Khảo sát (trước khi sửa)

`mvnw clean compile` cả hai service: **BUILD SUCCESS, 0 warning** (kể cả "Unmapped target properties").
Lý do không có warning: các mapper dùng `unmappedTargetPolicy = ERROR` (lỗi thay vì warning), còn
`AuthenticationMapper`, `RegistrationMapper` để mặc định WARN nhưng không có target nào bị bỏ sót.
Bản sao `*MapperImpl.java` trước khi sửa được lưu ngoài repo để so sánh ở §5.

### 1.1 IdentityService

| Mapper / method | Nơi gọi | `@Mapping` | Đánh giá |
|---|---|---|---|
| **AuthenticationMapper** (config riêng, policy mặc định WARN) | | | |
| `toWebResponse(csrfToken, expiresInSeconds)` | 1 (AuthenticationServiceImpl) | `authenticated` constant; ignore `accessToken`, `refreshToken` | CẦN (hằng; web không trả token) |
| `toMobileResponse(access, refresh, expires)` | 1 | `authenticated` constant; ignore `csrfToken` | CẦN |
| `toCsrfResponse(CsrfToken)` | 1 | — | OK |
| **IdentityAuditMapper** (config riêng) | | | |
| `toAudit(actorId, targetId, centerId, action, detail)` | 3 (BootstrapAdmin, AccountServiceImpl, RoleServiceImpl) | ignore `id`, `createdAt` | CẦN (entity không kế thừa BaseEntity) |
| **RegistrationMapper** (config riêng, policy WARN) | | | |
| `toRequest(Map)` | 1 | — | OK (map Redis → request; `password` ở đây là hash, chỉ dùng nội bộ) |
| `toOtpResponse(email, expires, resendAfter)` | 1 | — | OK |
| **RoleMapper** (config riêng) | | | |
| `toResponse(Role)` | 2 | `permissions` expression → default `permissions(role)` | CẦN mapping; helper lọc dữ liệu nên chuyển vào entity |
| `toResponseList` | 1 | — | OK |
| `toPermissionResponse(Permission)` | **0** (chỉ được `toPermissionResponseList` sinh code gọi) | — | THỪA — MapStruct tự sinh method phần tử |
| `toPermissionResponseList` | 1 | — | OK |
| `toRole(RoleRequest)` | 1 | ignore `id/createdAt/updatedAt/deletedAt` (lặp, không dùng annotation chung); `active` constant `true`; ignore `rolePermissions`; `@BeanMapping(builder disabled)` | ignore BaseEntity: CẦN nhưng phải gom; `active=true` là **giá trị mặc định nghiệp vụ** → không thuộc mapper (entity đã mặc định `true`); builder → config chung |
| `toRolePermission(role, permission)` | 2 (DataInitializer, RoleServiceImpl) | `id` expression; `role←role`, `permission←permission`; ignore `grantedAt` | `id`, `grantedAt`: CẦN; `role`, `permission`: **THỪA** (tham số trùng tên thuộc tính đích) |
| **UserMapper** (config riêng) | | | |
| `toResponse(User)` | 10 | `roles`, `permissions` expression | CẦN; nhưng helper `roles()/permissions()` (quyền hiệu lực) đang được **service gọi để kiểm quyền** → logic nghiệp vụ nằm trong mapper |
| `toResponseList` | 1 | — | OK |
| `toInternalResponse(User)` | 2 (API nội bộ) | `roles` expression | CẦN |
| `toInternalResponseList` | 1 (API nội bộ) | — | OK |
| `toAuthenticatedUser(User)` | 1 | `user←user` | CẦN (một tham số nguồn: MapStruct không tự map chính tham số vào thuộc tính `user`) |
| `toUser(RegisterRequest)`, `toUser(ProvisionRequest)` | 2 | `@IgnoreServerManagedUserFields` (gộp cả field BaseEntity lẫn field riêng User); `@BeanMapping(builder disabled)` | ignore: CẦN (chống mass assignment, `password` nhạy cảm); builder → config chung |
| `update(UpdateProfileRequest, @MappingTarget User)` | 2 | `ignoreByDefault` + IGNORE null + `fullName`, `phoneNumber` | CẦN (whitelist field được sửa). Lưu ý: endpoint là `PUT /users/me`, `PUT /users/{id}` nhưng **ngữ nghĩa partial** (null = giữ nguyên) — đúng contract đã công bố, giữ nguyên |
| `toUserRole(user, role, assignedBy)` | 2 (BootstrapAdmin, AccountServiceImpl) | `id` expression; `user←user`, `role←role`, `assignedBy←assignedBy`; ignore `assignedAt` | `id`, `assignedAt`: CẦN; 3 mapping còn lại **THỪA** |
| default `roles(User)`, `permissions(User)` | 2 + 2 (AccountServiceImpl.hasRole/hasPermission, RoleServiceImpl.checkRoleManager) | — | **Sai tầng**: quy tắc "quyền hiệu lực" dùng để kiểm quyền → chuyển vào entity |
| default `RoleMapper.permissions(Role)` | 0 ngoài mapper | — | chuyển vào entity cùng lúc |

Trường nhạy cảm (`password`): `toUser` ignore đúng; không response DTO nào có `password`. `AuthenticationResponse`
có `accessToken/refreshToken` **theo thiết kế** cho `/auth/mobile/*` (AGENTS.md #13), web ignore rõ ràng.

Get/set thủ công ở service: không có chỗ nào dựng DTO hay copy request → entity bằng tay. Các `setXxx` còn lại
(`AccountServiceImpl`: password hash, centerId theo vai trò, status, email chuẩn hóa, authzVersion, deletedAt) là
**quy tắc nghiệp vụ / trường nhạy cảm** → đúng chỗ ở service. Seed ở `config/BootstrapAdmin`, `DataInitializer`
dựng entity từ hằng/cấu hình (không có DTO nguồn) → không thuộc phạm vi mapper.

### 1.2 CareRecipientService

| Mapper / method | Nơi gọi | `@Mapping` | Đánh giá |
|---|---|---|---|
| **CareRecipientMapper** (config riêng: spring, ERROR, builder disabled, `uses`) | | | |
| `toResponse(CareRecipient)` | 3 | — | OK |
| `toDetailResponse(recipient, myAccessLevel, medications, emergencyContacts)` | 2 | `profile←recipient`; `myAccessLevel`, `medications`, `emergencyContacts` ← tham số cùng tên | `profile`: CẦN; 3 cái còn lại **THỪA** |
| `toSummaryResponse(CareRecipient)` | 1 (admin, + list nội bộ) | `age` expression; ignore `myAccessLevel`, `primaryManager` | CẦN |
| `toSummaryResponseList` | 2 (admin, API nội bộ batch) | — | OK |
| `toSummaryResponse(recipient, membership)` | 1 | `id`, `createdAt` ← recipient (trùng tên với membership); `age` expression; `myAccessLevel←membership.accessLevel`, `primaryManager←membership.primary` | CẦN (ambiguous / đổi tên) |
| `toSnapshotResponse(recipient, activeMedications, primaryEmergencyContact, snapshotAt)` | 1 (API nội bộ) | `recipientId←recipient.id`; `fullName`, `phoneNumber` ← recipient (trùng với contact); `age` expression; `activeMedications`, `primaryEmergencyContact`, `snapshotAt` ← tham số cùng tên | 5 đầu CẦN; 3 cuối **THỪA** |
| `toCareRecipient(CreateRequest)` | 1 | `@IgnoreServerManagedFields` + ignore `status`, `createdBy`, `updatedBy`, `version` | CẦN, nhưng 4 ignore lặp lại ở `update` → gom; `createdBy/updatedBy` do service `set` sau khi map (khác cách Medication truyền tham số) |
| `update(UpdateRequest, @MappingTarget)` (PUT, ghi đè null) | 1 | như trên | như trên; `updatedBy` service set |
| default `calculateAge` | expression | — | OK (tính toán dữ liệu thuần) |
| **EmergencyContactMapper** | | | |
| `toResponse`, `toResponseList` | 2 / 2 | — | OK |
| `toEmergencyContact(request, recipient)` | 1 (EmergencyContactService) + **vòng lặp thủ công** ở CareRecipientServiceImpl.create | `recipient←recipient`; `fullName`, `phoneNumber` ← request (ambiguous vì `recipient` là nguồn); ignore `primary`; `priority` defaultValue 0 | `recipient`: THỪA; ambiguous chỉ phát sinh vì `recipient` là nguồn; `primary` ignore khiến **service phải set tay** (2 chỗ); `priority` defaultValue THỪA ở create (int mặc định 0) |
| `update(request, @MappingTarget)` (PUT) | 1 | ignore `recipient`, `primary`; `priority` defaultValue 0 | CẦN (`primary`: null = giữ nguyên, phải bỏ cờ liên hệ chính cũ trước → service; `priority`: PUT null → 0) |
| **MedicationMapper** | | | |
| `toResponse`, `toResponseList` | 3 / 2 | — | OK |
| `toMedication(request, recipient, updatedBy)` | 1 + **vòng lặp thủ công** ở CareRecipientServiceImpl.create | `recipient`, `updatedBy` ← tham số cùng tên; `active` defaultValue true | `recipient`, `updatedBy`: THỪA; `active`: CẦN |
| `update(request, updatedBy, @MappingTarget)` (PUT) | 1 | ignore `recipient`, `active`; `updatedBy←updatedBy` | ignore CẦN; `updatedBy` THỪA |
| **RecipientManagerMapper** | | | |
| `toResponse(manager, profile)` | 3 | `id`←manager (trùng với profile.id); `createdAt`←manager; `phoneNumber`, `fullName`, `email` ← profile; `userStatus←profile.status`; `profileAvailable` expression | `id`, `userStatus`, `profileAvailable`: CẦN; `createdAt`, `phoneNumber`, `fullName`, `email`: **THỪA** (chỉ một nguồn có) |
| `toRecipientManager(recipient, userId, relationshipType, accessLevel, primary, addedBy)` | 2 | `recipient←recipient`; ignore `id/createdAt/updatedAt` | `recipient`: THỪA; ignore: CẦN (entity không kế thừa BaseEntity). OWNER/primary do service truyền vào (tham số) — đúng quy tắc |
| `update(ManagerUpdateRequest, @MappingTarget)` (PATCH) | 1 | `ignoreByDefault` + IGNORE null + `accessLevel`, `relationshipType` | CẦN (whitelist) |
| `toAccessCheckResponse(...)` | 1 (API nội bộ `/access`) | — | OK, giữ |

Get/set / vòng lặp thủ công ở service:
- `CareRecipientServiceImpl.create`: vòng lặp map + save từng liên hệ / thuốc; `setPrimary(...)` thủ công;
  `setCreatedBy/setUpdatedBy(actorId)` sau khi map.
- `CareRecipientServiceImpl.update`: `setUpdatedBy(actorId)` sau `mapper.update`.
- `EmergencyContactServiceImpl.create`: `setPrimary(Boolean.TRUE.equals(...))` sau khi map.
- Các `set` còn lại (status ACTIVE/ARCHIVED, deletedAt, chuyển cờ primary, bật/tắt active) là thao tác nghiệp vụ → giữ.

### 1.3 Hai service đang làm khác nhau

| Điểm | IdentityService | CareRecipientService | Thống nhất thành |
|---|---|---|---|
| Cấu hình mapper | mỗi mapper tự khai báo; 2 mapper không có `ERROR` | mỗi mapper tự khai báo `spring + ERROR + builder disabled` | **một `@MapperConfig`/service** (`MapStructConfig`) |
| Builder Lombok | tắt từng method bằng `@BeanMapping` | tắt từng mapper | tắt trong config |
| Inject mapper con (`uses`) | — | field `@Autowired` trong code sinh | `injectionStrategy = CONSTRUCTOR` trong config |
| Ignore trường hệ thống | `IgnoreServerManagedUserFields` trộn field BaseEntity + field User; `toRole` lặp tay 4 ignore | `IgnoreServerManagedFields` chỉ BaseEntity; ignore riêng của CareRecipient lặp ở 2 method | **`IgnoreServerManagedFields`** (BaseEntity) ở cả hai + annotation riêng theo entity `IgnoreServerManaged<Entity>Fields` |
| Người thao tác (`createdBy/updatedBy`) | — | Medication: tham số mapper; CareRecipient: service `set` | tham số mapper |
| Logic dẫn xuất trong mapper | `roles()/permissions()` bị service dùng để kiểm quyền | `calculateAge` chỉ dùng trong expression | logic nghiệp vụ → entity/service; mapper chỉ gọi |
| PUT có ngữ nghĩa partial | `PUT /users/me`, `/users/{id}` (null = giữ) — contract đã công bố | PUT ghi đè toàn phần | giữ nguyên hành vi, ghi chú rõ trên method |

## 2. Thay đổi đã làm

### 2.1 Chung (cả hai service)
- Thêm `mapper/MapStructConfig` (`@MapperConfig`: spring, `unmappedTargetPolicy = ERROR`, constructor injection,
  tắt builder Lombok); mọi mapper đổi sang `@Mapper(config = MapStructConfig.class)`.
- `mapper/IgnoreServerManagedFields` (id, createdAt, updatedAt, deletedAt) ở cả hai service; field riêng theo entity:
  `IgnoreServerManagedUserFields` (Identity, bỏ phần BaseEntity ra), `IgnoreServerManagedRecipientFields` (mới).

### 2.2 IdentityService

| Mapper | Đã bỏ | Đã giữ (lý do) | Khác |
|---|---|---|---|
| AuthenticationMapper | config riêng | `authenticated` constant; ignore token (web) / csrfToken (mobile) | nay có `ERROR` |
| IdentityAuditMapper | config riêng | ignore `id`, `createdAt` (không BaseEntity) | |
| RegistrationMapper | config riêng | — | nay có `ERROR` |
| RoleMapper | `toPermissionResponse` (không ai gọi, MapStruct tự sinh); 4 ignore BaseEntity lặp tay; `active` constant (mặc định nghiệp vụ → entity); `role←role`, `permission←permission`; `@BeanMapping(builder)`; default `permissions()` | `permissions` expression → `role.activePermissionCodes()`; `id` expression; ignore `grantedAt`, `active`, `rolePermissions` | |
| UserMapper | `user←user`, `role←role`, `assignedBy←assignedBy` trong `toUserRole`; `@BeanMapping(builder)` ×2; `source` lặp trong whitelist update; default `roles()/permissions()` | `roles`/`permissions` expression → `user.effectiveRoleCodes()/effectivePermissionCodes()`; `user←user` (một nguồn); whitelist PATCH; `id` expression, ignore `assignedAt` | `toUser` dùng `@IgnoreServerManagedFields` + `@IgnoreServerManagedUserFields` |

Service: `AccountServiceImpl.hasRole/hasPermission`, `RoleServiceImpl.checkRoleManager` gọi `User.effectiveRoleCodes()/
effectivePermissionCodes()` thay cho `userMapper.roles()/permissions()`; `RoleServiceImpl` bỏ field `userMapper` không
còn dùng. Entity: thêm `User.effectiveRoleCodes()`, `User.effectivePermissionCodes()`, `Role.activePermissionCodes()`.

### 2.3 CareRecipientService

| Mapper | Đã bỏ | Đã giữ (lý do) | Khác |
|---|---|---|---|
| CareRecipientMapper | `myAccessLevel`, `medications`, `emergencyContacts` (detail); `activeMedications`, `primaryEmergencyContact`, `snapshotAt` (snapshot); 4 ignore lặp → annotation | `profile←recipient`; `id/createdAt` ← recipient (trùng với membership); `myAccessLevel`, `primaryManager` ← membership; `recipientId`, `fullName`, `phoneNumber` ← recipient (trùng với contact); `age` expression | `toCareRecipient(request, actorId)` gán `createdBy/updatedBy`; `update(request, updatedBy, target)` |
| EmergencyContactMapper | `recipient←recipient`; `fullName/phoneNumber` explicit (hết ambiguous nhờ `@Context`); ignore `primary` + `priority` default ở create | `recipient` expression (từ `@Context`); PUT: ignore `primary`, `priority` default 0 | thêm `toEmergencyContactList(list, @Context recipient)` |
| MedicationMapper | `recipient←recipient`, `updatedBy←updatedBy` (create + update) | `recipient`, `updatedBy` expression (từ `@Context`); `active` default true; PUT ignore `active` | thêm `toMedicationList(list, @Context recipient, @Context updatedBy)` |
| RecipientManagerMapper | `createdAt`, `fullName`, `email`, `phoneNumber` (chỉ một nguồn có); `recipient←recipient`; `source` lặp trong whitelist | `id←manager.id`, `userStatus←profile.status`, `profileAvailable` expression; ignore `id/createdAt/updatedAt` (không BaseEntity); whitelist PATCH | |

Service: `CareRecipientServiceImpl.create` dùng `saveAll(mapper.to…List(...))` thay vòng lặp, bỏ `setPrimary`,
`setCreatedBy/setUpdatedBy`; kiểm ngày thuốc gom thành một bước validate trước khi lưu. `update` bỏ `setUpdatedBy`.
`EmergencyContactServiceImpl.create` bỏ `setPrimary` (mapper map `primary`, null → false).

## 3. Kiểm tra

- `mvnw clean compile`: cả hai service BUILD SUCCESS, **0 warning**.
- So sánh `*MapperImpl` mới với bản lưu trước khi sửa: mọi field response được gán từ cùng nguồn như trước; khác biệt chỉ
  là builder → setter, helper → method của entity, `createdBy/updatedBy/primary` nay do mapper gán (trước đó service gán
  cùng giá trị), thêm method danh sách. `toRole` không còn `setActive(true)` — constructor rỗng của `Role` đã gán
  `active = true` (`@Builder.Default`, đã kiểm bytecode + unit test). DTO không đổi → khóa JSON không đổi.
- IdentityService: **66/66** test trên H2 và **66/66** trên PostgreSQL 17 + Redis 7 thật (thêm 8 test `IdentityMapperTests`).
- CareRecipientService: **55/55** trên H2 và **55/55** trên PostgreSQL 17 thật (thêm 8 test `CareRecipientMapperTests`).
- E2E qua gateway (`scripts/e2e/Run-E2E.ps1`, JAR build lại): **a 19/19, b 14/14, c 10/10, d 4/4 — PASSED**.

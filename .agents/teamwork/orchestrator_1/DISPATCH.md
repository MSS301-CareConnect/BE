## 2026-10-18T14:26:47Z
You are the Project Orchestrator for the CareConnect microservices architecture refactoring.

Your working directory is:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1

The authoritative user request is recorded at:
d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md

Project root:
d:\FA26\MSS\CareConnect\BE

Mission:
Execute microservice architecture refactoring across CareConnect backend per requirements R1 to R6 in ORIGINAL_REQUEST.md:
1. R1: Hợp nhất Controller & Service theo Domain Aggregate (Không phân tách theo Role).
2. R2: Chuẩn hóa tầng Interface & Implementation thuần túy (Repository và Service là interface, logic trong service/Impl/). Sửa dứt điểm 6 Service ở ProviderService.
3. R3: Tái cấu trúc IdentityService (Auth vs User) & API Versioning đồng bộ (/api/v1/auth/** vs /api/v1/users/** và /api/v1/** trên mọi Controller).
4. R4: Xóa bỏ hoàn toàn /internal/** & Định tuyến toàn bộ qua ApiGateway với Feign & RequestInterceptor forward JWT Authorization.
5. R5: Quy chuẩn Entity (@Getter @Setter, cấm @Data) & MapStruct Mapper tương thích JPA Buddy.
6. R6: Cập nhật tài liệu AGENTS.md với các quy tắc mới.

Acceptance Criteria:
- Không còn Controller hay Service nào bị tách lẻ theo role.
- 100% Repository và Service ở tầng root là interface; 100% logic nằm trong *ServiceImpl ở Impl/.
- 100% các file *Internal*.java đã bị xóa bỏ hoàn toàn.
- 100% API endpoints của toàn bộ services đều có tiền tố /api/v1/.
- ApiGateway định tuyến đầy đủ 6 services; OpenFeign clients trỏ về ApiGateway URL và forward Authorization header.
- Các chức năng đăng ký, xác thực, quên mật khẩu nằm trọn vẹn trong AuthenticationController (/api/v1/auth/**).
- Toàn bộ 6 service build thành công bằng Maven Wrapper (mvn clean compile) không có lỗi biên dịch.
- File AGENTS.md được cập nhật đầy đủ các quy tắc mới.

Please maintain your plan.md, progress.md, and BRIEFING.md in your working directory. Regularly update progress.md as work advances. When all requirements and acceptance criteria are satisfied and verified, report project completion to the Sentinel.

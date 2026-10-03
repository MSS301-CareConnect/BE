# AGENTS.md — ApiGateway

> Chỉ ghi phần **khác / bổ sung** so với `../AGENTS.md`. Đọc file gốc trước.

| Field | Value |
|---|---|
| Thư mục / artifact | `CareConnect/ApiGateway/` · `mss301:ApiGateway:0.0.1-SNAPSHOT` |
| Base package | `mss301.apigateway` |
| Công nghệ | Spring Cloud Gateway **Server MVC** (servlet) — không WebFlux |
| Port / env prefix | `8080` / `GATEWAY_` |
| Dải `ErrorCode` | **9000–9099** (lỗi hạ tầng tại gateway) + 401/403/404/9999 |

## Package (khác layout chung)

Gateway không có controller/service/entity. Layout:

```
config/     GatewayProperties (gateway.*), GatewayConfig, GatewayPaths (MỌI path public/route — nguồn duy nhất), RouteConfig
security/   SecurityConfig (JWT sớm + CORS), JwtConfig, JwtAuthorityConverter, SecurityErrorHandler, TokenClaims
filter/     RequestIdFilter, InternalPathBlockFilter (servlet filter), GatewayRequestHeaders (before-filter cho route)
dto/response/APIResponse, exception/ErrorCode + GlobalExceptionHandler
```

## Quy tắc

1. Route mới: thêm pattern vào `GatewayPaths`, route vào `RouteConfig` với đủ `uri` → `stripPrefix(2)` →
   `GatewayRequestHeaders.sanitize()` → `removeResponseHeader(X-Request-Id)`. Endpoint public của service phải thêm vào
   `GatewayPaths` (nếu không gateway trả 401 trước khi tới service).
2. Không bao giờ route `/internal/**`; không bỏ `InternalPathBlockFilter`.
3. Gateway không phân quyền nghiệp vụ, không kiểm CSRF, không đổi body; service phía sau luôn tự xác minh JWT.
4. Header `X-User-*` chỉ để log — không thêm logic phụ thuộc vào chúng ở service.
5. Không rewrite cookie path khi Identity còn đặt `Path=/` (có test `identityCookiesPassThroughWithPathRoot`).
6. Rate limiting: chưa làm, hỏi trước.

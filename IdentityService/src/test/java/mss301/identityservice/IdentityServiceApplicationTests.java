package mss301.identityservice;

import mss301.identityservice.dto.request.*;
import mss301.identityservice.dto.response.*;
import mss301.identityservice.entity.*;
import mss301.identityservice.enums.*;
import mss301.identityservice.repository.*;
import mss301.identityservice.service.*;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.enums.TokenType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestIdentityState.class)
class IdentityServiceApplicationTests {
    static final String PASSWORD="CareConnect9!";
    static final String INTERNAL_SECRET="internal-test-secret-0123456789abcdef";
    static final Path KEY_DIRECTORY;
    static String pgSchema;
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            KEY_DIRECTORY=Files.createTempDirectory("careconnect-test-keys-");
            KeyPairGenerator g=KeyPairGenerator.getInstance("RSA"); g.initialize(2048); KeyPair pair=g.generateKeyPair();
            writeKey("private.pem","PRIVATE KEY",pair.getPrivate().getEncoded()); writeKey("public.pem","PUBLIC KEY",pair.getPublic().getEncoded());
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static void writeKey(String file,String type,byte[] bytes) throws Exception {
        Files.writeString(KEY_DIRECTORY.resolve(file),"-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes)+"\n-----END "+type+"-----\n");
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        boolean pg=Boolean.getBoolean("identity.testing.postgres");
        if (pg) {
            pgSchema = "identity_test_" + UUID.randomUUID().toString().replace("-", "");
            try (var connection = java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:55494/postgres", "postgres", "identity_test_only"); var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + pgSchema);
            } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
        }
        p.add("spring.datasource.url",()->pg?"jdbc:postgresql://localhost:55494/postgres?currentSchema="+pgSchema:"jdbc:h2:mem:identity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        p.add("spring.datasource.username",()->pg?"postgres":"sa"); p.add("spring.datasource.password",()->pg?"identity_test_only":"");
        p.add("spring.jpa.hibernate.ddl-auto",()->pg?"validate":"create-drop"); p.add("spring.sql.init.mode",()->pg?"always":"never");
        p.add("spring.sql.init.schema-locations",()->"file:./schema/identity.sql"); p.add("spring.data.redis.port",()->56394);
        p.add("identity.testing.real-state",()->Boolean.getBoolean("identity.testing.real-state"));
        p.add("identity.issuer",()->"https://identity.test"); p.add("identity.audience",()->"careconnect-identity"); p.add("identity.key-id",()->"test-key");
        p.add("identity.private-key",()->KEY_DIRECTORY.resolve("private.pem").toUri().toString()); p.add("identity.public-key",()->KEY_DIRECTORY.resolve("public.pem").toUri().toString());
        p.add("identity.access-ttl",()->"15m"); p.add("identity.refresh-ttl",()->"7d"); p.add("identity.otp-ttl",()->"5m"); p.add("identity.otp-cooldown",()->"60s");
        p.add("identity.otp-max-attempts",()->5); p.add("identity.allowed-origins",()->"https://web.test"); p.add("identity.cookie-secure",()->true);
        p.add("identity.internal-secret",()->INTERNAL_SECRET);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountService accounts;
    @Autowired JwtService tokens;
    @Autowired JwtEncoder encoder;
    @Autowired IdentityStateService state;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository assignments;
    @Autowired PasswordEncoder passwords;
    @Autowired TransactionTemplate tx;
    @Autowired IdentityProperties properties;
    @Autowired mss301.identityservice.security.IdentityAuthenticationConverter authenticationConverter;
    @MockitoBean EmailService email;
    UserResponse customer,admin;
    @BeforeEach void setup() {
        customer=createCustomer(); admin=createCustomer();
        tx.executeWithoutResult(s->{ User u=users.findById(admin.getId()).orElseThrow(); Role r=roles.findByRoleCodeAndDeletedAtIsNull("ADMIN").orElseThrow();
            UserRole link=new UserRole(); link.setId(new UserRoleId(u.getId(),r.getId())); link.setUser(u); link.setRole(r); assignments.save(link); });
        admin=accounts.me(admin.getId()).getResult();
    }
    UserResponse createCustomer() { return accounts.createVerified(RegisterRequest.builder().email(UUID.randomUUID()+"@example.test").fullName("Test User").password(passwords.encode(PASSWORD)).build()); }
    String token(UserResponse user) { return tokens.issue(user,0,TokenType.ACCESS).getTokenValue(); }
    String body(Object value) { return json.writeValueAsString(value); }
    static RequestPostProcessor csrf() {
        return request -> {
            String value = UUID.randomUUID().toString();
            List<Cookie> cookies = new ArrayList<>();
            if (request.getCookies() != null) cookies.addAll(Arrays.asList(request.getCookies()));
            cookies.add(new Cookie("XSRF-TOKEN", value)); request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", value); return request;
        };
    }
    UserResponse manager(UUID center) { return accounts.provision(admin.getId(),ProvisionRequest.builder().email(UUID.randomUUID()+"@example.test").fullName("Manager").roleCode(RoleCode.CENTER_MANAGER).centerId(center).build()).getResult(); }
    UserResponse caregiver(UserResponse manager) { return accounts.provision(manager.getId(),ProvisionRequest.builder().email(UUID.randomUUID()+"@example.test").fullName("Caregiver").roleCode(RoleCode.CAREGIVER).build()).getResult(); }
    String sendRegistration(String address) throws Exception {
        mvc.perform(post("/users/register").with(csrf()).contentType("application/json").content(body(RegisterRequest.builder().email(address.toUpperCase()).fullName("Test").password(PASSWORD).build()))).andExpect(status().isOk());
        var code=org.mockito.ArgumentCaptor.forClass(String.class); verify(email).sendOtp(eq(address),code.capture()); return code.getValue();
    }
    @AfterAll static void cleanupKeys() throws Exception { Files.deleteIfExists(KEY_DIRECTORY.resolve("private.pem")); Files.deleteIfExists(KEY_DIRECTORY.resolve("public.pem")); Files.deleteIfExists(KEY_DIRECTORY);
        if (pgSchema != null) {
            try (var connection=java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:55494/postgres","postgres","identity_test_only"); var statement=connection.createStatement()) { statement.execute("DROP SCHEMA " + pgSchema + " CASCADE"); }
        }
    }

    @Test void missingTokenIs401() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test void invalidSignatureIs401() throws Exception {
        mvc.perform(get("/users/me").header("Authorization","Bearer invalid.jwt.value")).andExpect(status().isUnauthorized());
    }

    @Test void meContainsNoPassword() throws Exception {
        mvc.perform(get("/users/me").header("Authorization","Bearer "+token(customer))).andExpect(status().isOk()).andExpect(jsonPath("$.result.id").value(customer.getId().toString())).andExpect(jsonPath("$.result.password").doesNotExist()).andExpect(jsonPath("$.result.roles[0]").value("CUSTOMER"));
    }

    @Test void customerCannotReadOtherUsers() throws Exception {
        mvc.perform(get("/users").header("Authorization","Bearer "+token(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/users/"+admin.getId()).header("Authorization","Bearer "+token(customer))).andExpect(status().isForbidden());
    }

    @Test void signupRejectsRoleInjection() throws Exception {
        Map<String,Object> request=new HashMap<>(Map.of("email","a@example.test","password",PASSWORD,"fullName","Test","roleCode","ADMIN"));
        mvc.perform(post("/users/register").with(csrf()).contentType("application/json").content(body(request))).andExpect(status().isBadRequest());
    }

    @Test void weakPasswordIs400() throws Exception {
        mvc.perform(post("/users/register").with(csrf()).contentType("application/json").content(body(RegisterRequest.builder().email("test@example.test").fullName("Test").password("weak").build()))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1074));
    }

    @Test void webLoginRequiresCsrf() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content(body(LoginRequest.builder().email(customer.getEmail()).password(PASSWORD).build()))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1111));
        // Trình duyệt có cookie (vd XSRF-TOKEN cũ) vẫn phải gửi header CSRF ở endpoint đăng ký / quên mật khẩu.
        mvc.perform(post("/users/forgot-password").cookie(new Cookie("XSRF-TOKEN", "browser-token")).contentType("application/json").content(body(EmailRequest.builder().email(customer.getEmail()).build()))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1111));
    }

    // ===== Mobile: đăng ký / OTP / quên-đặt lại mật khẩu không cần CSRF khi request không mang cookie nào =====

    @Test
    void cookielessAccountEndpointsSkipCsrf() throws Exception {
        String address = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/users/register")
                        .contentType("application/json")
                        .content(body(RegisterRequest.builder().email(address).fullName("Mobile").password(PASSWORD).build())))
                .andExpect(status().isOk());
        mvc.perform(post("/users/register/resend-otp")
                        .contentType("application/json")
                        .content(body(EmailRequest.builder().email(address).build())))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/users/register/verify")
                        .contentType("application/json")
                        .content(body(VerifyOtpRequest.builder().email(address).otpCode("000000").build())))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/users/forgot-password")
                        .contentType("application/json")
                        .content(body(EmailRequest.builder().email(customer.getEmail()).build())))
                .andExpect(status().isOk());
        mvc.perform(post("/users/reset-password")
                        .contentType("application/json")
                        .content(body(ResetPasswordRequest.builder().email(customer.getEmail()).otpCode("000000").newPassword(PASSWORD).build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cookielessExemptionDoesNotCoverOtherEndpoints() throws Exception {
        // Login web (đặt cookie) và các endpoint ghi khác vẫn cần CSRF khi không có bearer, kể cả không có cookie.
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content(body(LoginRequest.builder().email(customer.getEmail()).password(PASSWORD).build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1111));
        mvc.perform(post("/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1111));
    }

    @Test void missingPermissionKeepsGenericForbiddenCode() throws Exception {
        mvc.perform(get("/users").header("Authorization","Bearer "+token(customer))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test void openApiDocumentsCsrfHeaderOnWriteOperations() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.csrf.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.csrf.name").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.paths['/auth/login'].post.security[0].csrf").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security[0].bearerAuth").doesNotExist())
                .andExpect(jsonPath("$.paths['/users/register'].post.security[0].csrf").exists())
                .andExpect(jsonPath("$.paths['/users/me'].put.security[0].csrf").exists())
                .andExpect(jsonPath("$.paths['/users/me'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/users/me'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/mobile/login'].post.security[0].csrf").doesNotExist());
    }

    @Test void webLoginUsesCookies() throws Exception {
        mvc.perform(post("/auth/login").with(csrf()).contentType("application/json").content(body(LoginRequest.builder().email(customer.getEmail()).password(PASSWORD).build()))).andExpect(status().isOk()).andExpect(cookie().httpOnly("access_token",true)).andExpect(cookie().secure("refresh_token",true)).andExpect(jsonPath("$.result.accessToken").doesNotExist()).andExpect(jsonPath("$.result.refreshToken").doesNotExist());
    }

    @Test void staleCookieDoesNotBlockLogin() throws Exception {
        mvc.perform(post("/auth/login").cookie(new Cookie("access_token","bad.token")).with(csrf()).contentType("application/json").content(body(LoginRequest.builder().email(customer.getEmail()).password(PASSWORD).build()))).andExpect(status().isOk());
    }

    @Test void mobileRefreshCannotReplay() throws Exception {
        MvcResult result=mvc.perform(post("/auth/mobile/login").contentType("application/json").content(body(LoginRequest.builder().email(customer.getEmail()).password(PASSWORD).build()))).andExpect(status().isOk()).andReturn();
        String refresh=json.readTree(result.getResponse().getContentAsString()).path("result").path("refreshToken").asString();
        String request=body(RefreshRequest.builder().refreshToken(refresh).build());
        mvc.perform(post("/auth/mobile/refresh").contentType("application/json").content(request)).andExpect(status().isOk());
        mvc.perform(post("/auth/mobile/refresh").contentType("application/json").content(request)).andExpect(status().isUnauthorized());
    }

    @Test void refreshCannotAuthenticateResources() throws Exception {
        mvc.perform(get("/users/me").header("Authorization","Bearer "+tokens.issue(customer,0,TokenType.REFRESH).getTokenValue())).andExpect(status().isUnauthorized());
    }

    @Test void invalidClaimsAre401() throws Exception {
        for(String scenario:List.of("expired","issuer","audience","subject")) {
            Instant now=Instant.now(); JwtClaimsSet claims=JwtClaimsSet.builder().issuer(scenario.equals("issuer")?"https://wrong.test":properties.getIssuer()).subject(scenario.equals("subject")?"not-uuid":customer.getId().toString()).audience(List.of(scenario.equals("audience")?"wrong":properties.getAudience())).issuedAt(now.minusSeconds(3600)).expiresAt(scenario.equals("expired")?now.minusSeconds(120):now.plusSeconds(600)).id(UUID.randomUUID().toString()).claim("authz_version",0).claim("token_type","access").build();
            String access=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.getKeyId()).build(),claims)).getTokenValue();
            mvc.perform(get("/users/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        }
    }

    @Test void lockedAccountAndRefreshAreRejected() throws Exception {
        String access=token(customer); Jwt refresh=tokens.issue(customer,0,TokenType.REFRESH); state.storeRefresh(refresh.getId(),customer.getId(),Duration.ofHours(1));
        accounts.updateStatus(admin.getId(),customer.getId(),UserStatus.LOCKED);
        mvc.perform(get("/users/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/mobile/refresh").contentType("application/json").content(body(RefreshRequest.builder().refreshToken(refresh.getTokenValue()).build()))).andExpect(status().isUnauthorized());
    }

    @Test void managerScopeIsEnforced() throws Exception {
        UserResponse manager=manager(UUID.randomUUID()); UserResponse own=caregiver(manager); UserResponse other=caregiver(manager(UUID.randomUUID()));
        mvc.perform(get("/users/"+own.getId()).header("Authorization","Bearer "+token(manager))).andExpect(status().isOk());
        mvc.perform(get("/users/"+other.getId()).header("Authorization","Bearer "+token(manager))).andExpect(status().isForbidden());
        mvc.perform(patch("/users/"+other.getId()+"/status?status=LOCKED").header("Authorization","Bearer "+token(manager))).andExpect(status().isForbidden());
    }

    @Test void managerCannotElevateOrCrossProvision() throws Exception {
        UserResponse manager=manager(UUID.randomUUID());
        for(RoleCode role:List.of(RoleCode.ADMIN,RoleCode.CENTER_MANAGER,RoleCode.CAREGIVER)) mvc.perform(post("/users").header("Authorization","Bearer "+token(manager)).contentType("application/json").content(body(ProvisionRequest.builder().email(UUID.randomUUID()+"@example.test").fullName("Test").roleCode(role).centerId(UUID.randomUUID()).build()))).andExpect(status().is4xxClientError());
    }

    @Test void caregiverCenterComesFromManager() throws Exception {
        UserResponse manager=manager(UUID.randomUUID()); mvc.perform(post("/users").header("Authorization","Bearer "+token(manager)).contentType("application/json").content(body(ProvisionRequest.builder().email(UUID.randomUUID()+"@example.test").fullName("Test").roleCode(RoleCode.CAREGIVER).build()))).andExpect(status().isOk()).andExpect(jsonPath("$.result.centerId").value(manager.getCenterId().toString()));
    }

    @Test void registrationOtpIsSingleUse() throws Exception {
        String address=UUID.randomUUID()+"@example.test"; String code=sendRegistration(address); assertThat(users.existsByEmailIgnoreCase(address)).isFalse();
        String request=body(VerifyOtpRequest.builder().email(address).otpCode(code).build());
        mvc.perform(post("/users/register/verify").with(csrf()).contentType("application/json").content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.result.roles[0]").value("CUSTOMER"));
        mvc.perform(post("/users/register/verify").with(csrf()).contentType("application/json").content(request)).andExpect(status().isBadRequest());
    }

    @Test void wrongOtpExhaustsAttempts() throws Exception {
        String address=UUID.randomUUID()+"@example.test"; String code=sendRegistration(address); String wrong=code.equals("000000")?"111111":"000000";
        for(int i=1;i<=5;i++) mvc.perform(post("/users/register/verify").with(csrf()).contentType("application/json").content(body(VerifyOtpRequest.builder().email(address).otpCode(wrong).build()))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(i==5?1072:1063));
        mvc.perform(post("/users/register/verify").with(csrf()).contentType("application/json").content(body(VerifyOtpRequest.builder().email(address).otpCode(code).build()))).andExpect(status().isBadRequest());
    }

    @Test void passwordResetRevokesSessions() throws Exception {
        String access=token(customer); mvc.perform(post("/users/forgot-password").with(csrf()).contentType("application/json").content(body(EmailRequest.builder().email(customer.getEmail()).build()))).andExpect(status().isOk());
        var code=org.mockito.ArgumentCaptor.forClass(String.class); verify(email).sendOtp(eq(customer.getEmail()),code.capture());
        String request=body(ResetPasswordRequest.builder().email(customer.getEmail()).otpCode(code.getValue()).newPassword("NewPassword9!").build());
        mvc.perform(post("/users/reset-password").with(csrf()).contentType("application/json").content(request)).andExpect(status().isOk());
        mvc.perform(get("/users/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/users/reset-password").with(csrf()).contentType("application/json").content(request)).andExpect(status().isBadRequest());
    }

    @Test void revokedRoleIsNotTrustedFromJwt() throws Exception {
        String access=token(admin); UUID roleId=roles.findByRoleCodeAndDeletedAtIsNull("ADMIN").orElseThrow().getId(); accounts.revoke(admin.getId(),admin.getId(),roleId);
        mvc.perform(get("/users").header("Authorization","Bearer "+access)).andExpect(status().isForbidden());
    }

    @Test void softDeletePreservesDataAndBlocksAuthentication() throws Exception {
        String access=token(customer); accounts.delete(admin.getId(),customer.getId()); mvc.perform(get("/users/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        assertThat(users.findById(customer.getId()).orElseThrow().getDeletedAt()).isNotNull(); assertThat(users.findByIdAndDeletedAtIsNull(customer.getId())).isEmpty();
    }

    @Test void invalidEnumAndUuidAre400() throws Exception {
        mvc.perform(patch("/users/"+customer.getId()+"/status?status=INVALID").header("Authorization","Bearer "+token(admin))).andExpect(status().isBadRequest());
        mvc.perform(get("/users/not-uuid").header("Authorization","Bearer "+token(admin))).andExpect(status().isBadRequest());
    }

    @Test void jwksOnlyContainsPublicMaterial() throws Exception {
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()).andExpect(jsonPath("$.keys[0].kid").value("test-key")).andExpect(jsonPath("$.keys[0].n").exists()).andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test void currentUserIdIsNotExposedAsApiParameter() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("actorId"))));
    }

    @Test void conflictingTokenTransportsAreRejected() throws Exception {
        mvc.perform(get("/users/me").cookie(new Cookie("access_token",token(customer))).header("Authorization","Bearer "+token(admin))).andExpect(status().isUnauthorized());
    }

    @Test void logoutRevokesAccessAndRefresh() throws Exception {
        Jwt refresh=tokens.issue(customer,0,TokenType.REFRESH); state.storeRefresh(refresh.getId(),customer.getId(),Duration.ofHours(1)); String access=token(customer);
        mvc.perform(post("/auth/mobile/logout").header("Authorization","Bearer "+access).contentType("application/json").content(body(RefreshRequest.builder().refreshToken(refresh.getTokenValue()).build()))).andExpect(status().isOk());
        mvc.perform(get("/users/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized()); assertThat(state.consumeRefresh(refresh.getId(),customer.getId())).isFalse();
    }

    @Test void concurrentRefreshHasOneWinner() throws Exception {
        String id=UUID.randomUUID().toString(); state.storeRefresh(id,customer.getId(),Duration.ofMinutes(1));
        try(var executor=Executors.newFixedThreadPool(2)) { CountDownLatch start=new CountDownLatch(1); Callable<Boolean> call=()->{start.await();return state.consumeRefresh(id,customer.getId());}; var a=executor.submit(call); var b=executor.submit(call); start.countDown(); assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(true,false); }
    }

    @Test void errorCodesAreUnique() throws Exception {
        assertThat(Arrays.stream(mss301.identityservice.exception.ErrorCode.values()).map(mss301.identityservice.exception.ErrorCode::getCode).toList()).doesNotHaveDuplicates();
    }

    @Test void lastAdminCannotLoseAdminRole() {
        tx.executeWithoutResult(status -> {
            for (User user : users.findAll()) {
                boolean isAdmin = user.getUserRoles().stream().anyMatch(link -> link.getRole().getRoleCode().equals("ADMIN"));
                if (isAdmin && !user.getId().equals(admin.getId())) user.setStatus(UserStatus.LOCKED);
            }
        });
        UUID roleId = roles.findByRoleCodeAndDeletedAtIsNull("ADMIN").orElseThrow().getId();
        assertThatThrownBy(() -> accounts.revoke(admin.getId(), admin.getId(), roleId))
                .isInstanceOf(mss301.identityservice.exception.AppException.class)
                .extracting("errorCode").isEqualTo(mss301.identityservice.exception.ErrorCode.LAST_ADMIN);
    }
    @Test void duplicateNormalizedEmailHasOneWinner() throws Exception {
        String address = UUID.randomUUID() + "@example.test";
        String hash = passwords.encode(PASSWORD);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> call = () -> {
                start.await();
                try { accounts.createVerified(RegisterRequest.builder().email(address).password(hash).fullName("Test").build()); return true; }
                catch (mss301.identityservice.exception.AppException | org.springframework.dao.DataIntegrityViolationException e) { return false; }
            };
            var a = executor.submit(call); var b = executor.submit(call); start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }
    }
    @Test void concurrentOtpConsumptionHasOneWinner() throws Exception {
        String address = UUID.randomUUID() + "@example.test";
        state.beginOtp("register", address, Map.of("otpHash", "test-hash", "attempts", "0"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> call = () -> { start.await(); return state.consumeOtp("register", address, "test-hash"); };
            var a = executor.submit(call); var b = executor.submit(call); start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }
    }
    @Test void unicodePasswordExceedingBcryptByteLimitIs400() throws Exception {
        mvc.perform(post("/users/register").with(csrf()).contentType("application/json")
                .content(body(RegisterRequest.builder().email("test@example.test").fullName("Test").password("Aa9!" + "é".repeat(40)).build())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1074));
    }
    @Test void realCsrfTokenAllowsWebLogin() throws Exception {
        var csrfResponse = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String csrfToken = json.readTree(csrfResponse.getContentAsString()).path("result").path("token").asString();
        mvc.perform(post("/auth/login").cookie(csrfResponse.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", csrfToken)
                .contentType("application/json").content(body(LoginRequest.builder().email(customer.getEmail()).password(PASSWORD).build())))
                .andExpect(status().isOk());
    }
    @Test void profileUpdatesAreScopedAndDoNotOverwriteRoles() throws Exception {
        UserResponse manager = manager(UUID.randomUUID()); UserResponse own = caregiver(manager); UserResponse other = caregiver(manager(UUID.randomUUID()));
        String request = body(UpdateProfileRequest.builder().fullName("Updated caregiver").build());
        mvc.perform(put("/users/" + own.getId()).header("Authorization", "Bearer " + token(manager)).contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.roles[0]").value("CAREGIVER"));
        mvc.perform(put("/users/" + other.getId()).header("Authorization", "Bearer " + token(manager)).contentType("application/json").content(request)).andExpect(status().isForbidden());
    }
    @Test void webCookieMutationStillNeedsCsrfWithBearerHeader() throws Exception {
        String access = token(customer);
        mvc.perform(put("/users/me").cookie(new Cookie("access_token", access)).header("Authorization", "Bearer " + access)
                .contentType("application/json").content(body(UpdateProfileRequest.builder().fullName("Updated").build())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1111));
    }
    @Test void otpSendFailureCanBeRetried() throws Exception {
        String address = UUID.randomUUID() + "@example.test";
        doThrow(new mss301.identityservice.exception.AppException(mss301.identityservice.exception.ErrorCode.EMAIL_DELIVERY_FAILED)).doNothing()
                .when(email).sendOtp(eq(address), anyString());
        String request = body(RegisterRequest.builder().email(address).fullName("Test").password(PASSWORD).build());
        mvc.perform(post("/users/register").with(csrf()).contentType("application/json").content(request)).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/users/register").with(csrf()).contentType("application/json").content(request)).andExpect(status().isOk());
    }

    @Test void managerListOnlyContainsOwnCaregivers() throws Exception {
        UserResponse manager = manager(UUID.randomUUID()); UserResponse own = caregiver(manager); caregiver(manager(UUID.randomUUID()));
        mvc.perform(get("/users").header("Authorization", "Bearer " + token(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].id").value(own.getId().toString()));
    }
    @Test void longLoginPasswordReturns401() throws Exception {
        mvc.perform(post("/auth/mobile/login").contentType("application/json")
                .content(body(LoginRequest.builder().email(customer.getEmail()).password("é".repeat(100)).build())))
                .andExpect(status().isUnauthorized());
    }

    @Test void webCsrfSurvivesReadsAndRotatesOnRefresh() throws Exception {
        var initial = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String before = json.readTree(initial.getContentAsString()).path("result").path("token").asString();
        var login = mvc.perform(post("/auth/login").cookie(initial.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", before)
                .contentType("application/json").content(body(LoginRequest.builder().email(customer.getEmail()).password(PASSWORD).build())))
                .andExpect(status().isOk()).andReturn().getResponse();
        String after = json.readTree(login.getContentAsString()).path("result").path("csrfToken").asString();
        assertThat(after).isNotEqualTo(before);
        var me = mvc.perform(get("/users/me").cookie(login.getCookies())).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(me.getCookie("XSRF-TOKEN")).isNull();
        var refreshed = mvc.perform(post("/auth/refresh").cookie(login.getCookies()).header("X-XSRF-TOKEN", after))
                .andExpect(status().isOk()).andReturn().getResponse();
        String next = json.readTree(refreshed.getContentAsString()).path("result").path("csrfToken").asString();
        assertThat(next).isNotEqualTo(after);
        mvc.perform(post("/auth/logout").cookie(refreshed.getCookies()).header("X-XSRF-TOKEN", next))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("XSRF-TOKEN", 0));
    }

    @Test void concurrentProfileUpdateCannotRestoreLockedAccount() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch commit = new CountDownLatch(1);
            var lock = executor.submit(() -> tx.executeWithoutResult(status -> {
                User user = users.findForUpdate(customer.getId()).orElseThrow();
                user.setStatus(UserStatus.LOCKED); user.setAuthzVersion(user.getAuthzVersion() + 1);
                locked.countDown();
                try { if (!commit.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test coordination timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var update = executor.submit(() -> {
                try { accounts.update(customer.getId(), UpdateProfileRequest.builder().fullName("Updated").build()); return null; }
                catch (mss301.identityservice.exception.AppException e) { return e.getErrorCode(); }
            });
            Thread.sleep(300); commit.countDown(); lock.get();
            assertThat(update.get()).isEqualTo(mss301.identityservice.exception.ErrorCode.UNAUTHENTICATED);
            assertThat(users.findById(customer.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.LOCKED);
        }
    }

    @Test void dependencyFailureIs503AndNotInvalidCredentials() throws Exception {
        Object original = org.springframework.test.util.ReflectionTestUtils.getField(authenticationConverter, "identityStateService");
        IdentityStateService failing = mock(IdentityStateService.class);
        when(failing.isBlacklisted(anyString())).thenThrow(new mss301.identityservice.exception.AppException(mss301.identityservice.exception.ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE));
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(authenticationConverter, "identityStateService", failing);
            mvc.perform(get("/users/me").header("Authorization", "Bearer " + token(customer)))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(1105));
        } finally { org.springframework.test.util.ReflectionTestUtils.setField(authenticationConverter, "identityStateService", original); }
    }

    // ===== Audience chung careconnect-api =====

    @Test
    void accessTokenHasApiAudienceButRefreshDoesNot() {
        Jwt access = tokens.issue(customer, 0, TokenType.ACCESS);
        Jwt refresh = tokens.issue(customer, 0, TokenType.REFRESH);
        assertThat(access.getAudience()).containsExactlyInAnyOrder("careconnect-identity", "careconnect-api");
        assertThat(refresh.getAudience()).containsExactly("careconnect-identity");
    }

    @Test
    void legacyAccessTokenWithoutApiAudienceStillWorksAtIdentity() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(customer.getId().toString())
                .audience(List.of(properties.getAudience()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim("authz_version", 0)
                .claim("token_type", "access")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.getKeyId()).build();
        String legacyToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + legacyToken))
                .andExpect(status().isOk());
    }

    // ===== Internal API /internal/** =====

    @Test
    void internalApiRejectsMissingOrWrongSecret() throws Exception {
        mvc.perform(get("/internal/users/" + customer.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/internal/users/" + customer.getId()).header("X-Internal-Secret", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalApiDoesNotAcceptUserBearerToken() throws Exception {
        mvc.perform(get("/internal/users/" + customer.getId()).header("Authorization", "Bearer " + token(admin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalGetReturnsProfileAndRolesWithoutSecrets() throws Exception {
        mvc.perform(get("/internal/users/" + customer.getId()).header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(customer.getId().toString()))
                .andExpect(jsonPath("$.result.email").value(customer.getEmail()))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.result.password").doesNotExist())
                .andExpect(jsonPath("$.result.permissions").doesNotExist());
        mvc.perform(get("/internal/users/" + UUID.randomUUID()).header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1016));
    }

    @Test
    void internalGetByEmailNormalizesAndValidates() throws Exception {
        mvc.perform(get("/internal/users/by-email")
                        .param("email", "  " + customer.getEmail().toUpperCase(Locale.ROOT) + " ")
                        .header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(customer.getId().toString()));
        mvc.perform(get("/internal/users/by-email")
                        .param("email", "nobody@example.test")
                        .header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/users/by-email").header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
    }

    @Test
    void internalBatchSkipsUnknownAndNeedsNoCsrf() throws Exception {
        String requestBody = body(UserBatchRequest.builder()
                .userIds(List.of(customer.getId(), admin.getId(), UUID.randomUUID(), customer.getId()))
                .build());
        mvc.perform(post("/internal/users/batch")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2));
    }

    @Test
    void internalBatchValidatesSize() throws Exception {
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(UUID.randomUUID());
        }
        mvc.perform(post("/internal/users/batch")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType("application/json")
                        .content(body(UserBatchRequest.builder().userIds(tooMany).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1113));
        mvc.perform(post("/internal/users/batch")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType("application/json")
                        .content(body(UserBatchRequest.builder().userIds(List.of()).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1112));
    }

    @Test
    void openApiDocumentsInternalSecretWithoutCsrf() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.internalSecret.name").value("X-Internal-Secret"))
                .andExpect(jsonPath("$.paths['/internal/users/batch'].post.security[0].internalSecret").exists())
                .andExpect(jsonPath("$.paths['/internal/users/batch'].post.security[0].csrf").doesNotExist())
                .andExpect(jsonPath("$.paths['/internal/users/batch'].post.security[0].bearerAuth").doesNotExist());
    }
}

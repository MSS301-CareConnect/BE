package mss301.carecatalogservice;

import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.PricingUnit;
import mss301.carecatalogservice.enums.ServiceStatus;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
import mss301.carecatalogservice.repository.PricePlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API CAT-02 trên H2: phân quyền (401 / 403), phạm vi đọc theo role, vô hiệu hóa an toàn, price-summary.
 * Token giả bằng {@code jwt()} của spring-security-test (roles → ROLE_*, claim center_id) — chữ ký / JWKS đã được test
 * ở IdentityService và ApiGateway.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiTests {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("care-catalog.jwt.issuer", () -> "https://identity.test");
        registry.add("care-catalog.jwt.jwks-uri", () -> "http://127.0.0.1:1/.well-known/jwks.json");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CatalogItemRepository catalogItemRepository;

    @Autowired
    CenterOfferingRepository centerOfferingRepository;

    @Autowired
    PricePlanRepository pricePlanRepository;

    @Test
    void readEndpointsNeedAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/catalog-items"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/center-services/center/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerSeesOnlyActiveCatalogItemsWhileAdminSeesAll() throws Exception {
        String keyword = "kw" + UUID.randomUUID().toString().substring(0, 8);
        CatalogItem active = saveItem(keyword + " active", ServiceStatus.ACTIVE);
        CatalogItem inactive = saveItem(keyword + " inactive", ServiceStatus.INACTIVE);

        mockMvc.perform(get("/api/v1/catalog-items").param("keyword", keyword).param("status", "INACTIVE")
                        .with(user("CUSTOMER", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(active.getId().toString()));
        mockMvc.perform(get("/api/v1/catalog-items").param("keyword", keyword).with(user("ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2));
        mockMvc.perform(get("/api/v1/catalog-items/" + inactive.getId()).with(user("CAREGIVER", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CARE_SERVICE_NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/v1/catalog-items").param("size", "101").with(user("ADMIN", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGE.getCode()));
    }

    @Test
    void customerSeesOnlySellableOfferingsWithActivePlans() throws Exception {
        UUID centerId = UUID.randomUUID();
        CatalogItem bathing = saveItem("Tắm gội", ServiceStatus.ACTIVE);
        CatalogItem feeding = saveItem("Cho ăn", ServiceStatus.ACTIVE);
        CatalogItem retired = saveItem("Dịch vụ cũ", ServiceStatus.ACTIVE);
        CenterOffering sellable = saveOffering(centerId, bathing, ServiceStatus.ACTIVE);
        savePlan(sellable, "Theo giờ", new BigDecimal("100000"), true);
        savePlan(sellable, "Gói cũ", new BigDecimal("80000"), false);
        CenterOffering withoutActivePlan = saveOffering(centerId, feeding, ServiceStatus.ACTIVE);
        savePlan(withoutActivePlan, "Đã tắt", new BigDecimal("50000"), false);
        CenterOffering itemDisabled = saveOffering(centerId, retired, ServiceStatus.ACTIVE);
        savePlan(itemDisabled, "Theo giờ", new BigDecimal("90000"), true);
        // Vô hiệu dịch vụ chuẩn: offering trong DB giữ nguyên nhưng khách không còn thấy.
        mockMvc.perform(patch("/api/v1/catalog-items/" + retired.getId() + "/status").with(user("ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/center-services/center/" + centerId).with(user("CUSTOMER", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(sellable.getId().toString()))
                .andExpect(jsonPath("$.result.items[0].pricePlans.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].pricePlans[0].planName").value("Theo giờ"));
        mockMvc.perform(get("/api/v1/center-services/" + withoutActivePlan.getId()).with(user("CUSTOMER", null)))
                .andExpect(status().isNotFound());
        // Manager của chính trung tâm thấy mọi dịch vụ và mọi gói giá; manager trung tâm khác chỉ thấy như khách.
        mockMvc.perform(get("/api/v1/center-services/center/" + centerId).with(user("CENTER_MANAGER", centerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(3));
        mockMvc.perform(get("/api/v1/center-services/center/" + centerId)
                        .with(user("CENTER_MANAGER", UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void statusChangesAndDeletionAreGuarded() throws Exception {
        UUID centerId = UUID.randomUUID();
        CatalogItem item = saveItem("Vật lý trị liệu", ServiceStatus.ACTIVE);
        CenterOffering offering = saveOffering(centerId, item, ServiceStatus.ACTIVE);
        PricePlan plan = savePlan(offering, "Theo giờ", new BigDecimal("200000"), true);

        mockMvc.perform(patch("/api/v1/catalog-items/" + item.getId() + "/status").with(user("CUSTOMER", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/center-services/" + offering.getId() + "/status")
                        .with(user("CENTER_MANAGER", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_ACCESS_DENIED.getCode()));
        mockMvc.perform(delete("/api/v1/catalog-items/" + item.getId()).with(user("ADMIN", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CARE_SERVICE_IN_USE.getCode()));
        mockMvc.perform(delete("/api/v1/center-services/" + offering.getId()).with(user("CENTER_MANAGER", centerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_SERVICE_IN_USE.getCode()));

        // Tắt gói giá + dịch vụ trung tâm rồi mới xóa được; xóa offering xóa mềm luôn gói con.
        plan.setIsActive(false);
        pricePlanRepository.save(plan);
        mockMvc.perform(patch("/api/v1/center-services/" + offering.getId() + "/status").with(user("CENTER_MANAGER", centerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("INACTIVE"));
        mockMvc.perform(delete("/api/v1/center-services/" + offering.getId()).with(user("CENTER_MANAGER", centerId)))
                .andExpect(status().isOk());
        assertThat(pricePlanRepository.findByIdAndDeletedAtIsNull(plan.getId())).isEmpty();
        mockMvc.perform(delete("/api/v1/catalog-items/" + item.getId()).with(user("ADMIN", null)))
                .andExpect(status().isOk());
    }

    @Test
    void priceSummaryIsServiceOnlyAndFiltersByRange() throws Exception {
        UUID cheapCenter = UUID.randomUUID();
        UUID expensiveCenter = UUID.randomUUID();
        CatalogItem item = saveItem("Trông đêm " + UUID.randomUUID(), ServiceStatus.ACTIVE);
        savePlan(saveOffering(cheapCenter, item, ServiceStatus.ACTIVE), "Theo giờ", new BigDecimal("120000"), true);
        savePlan(saveOffering(expensiveCenter, item, ServiceStatus.ACTIVE), "Theo giờ", new BigDecimal("300000"), true);

        mockMvc.perform(get("/api/v1/center-services/price-summary").param("careServiceId", item.getId().toString())
                        .with(user("CUSTOMER", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/center-services/price-summary").param("careServiceId", item.getId().toString())
                        .param("maxPrice", "150000").with(user("SERVICE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].centerId").value(cheapCenter.toString()))
                .andExpect(jsonPath("$.result[0].minPrice").value(120000));
        mockMvc.perform(get("/api/v1/center-services/price-summary").param("minPrice", "200000").param("maxPrice", "100000")
                        .with(user("SERVICE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRICE_RANGE_INVALID.getCode()));
    }

    static JwtRequestPostProcessor user(String role, UUID centerId) {
        return jwt()
                .jwt(token -> {
                    token.subject(UUID.randomUUID().toString());
                    if (centerId != null) {
                        token.claim("center_id", centerId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    CatalogItem saveItem(String name, ServiceStatus status) {
        return catalogItemRepository.save(CatalogItem.builder().name(name).category("Chăm sóc").status(status).build());
    }

    CenterOffering saveOffering(UUID centerId, CatalogItem item, ServiceStatus status) {
        return centerOfferingRepository.save(CenterOffering.builder().centerId(centerId).catalogItem(item).status(status).build());
    }

    PricePlan savePlan(CenterOffering offering, String name, BigDecimal price, boolean active) {
        return pricePlanRepository.save(PricePlan.builder().centerOffering(offering).planName(name)
                .pricingUnit(PricingUnit.HOUR).durationMinutes(60).basePrice(price).isActive(active).build());
    }
}

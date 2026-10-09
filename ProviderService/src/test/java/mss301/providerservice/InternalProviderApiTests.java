package mss301.providerservice;

import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API nội bộ /internal/caregivers, /internal/centers (Booking gọi): chỉ chấp nhận X-Internal-Secret, không nhận JWT,
 * careServiceIds luôn null (Provider chưa quản lý kỹ năng).
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalProviderApiTests {

    static final String SECRET_HEADER = "X-Internal-Secret";
    static final String INTERNAL_SECRET = "provider-test-internal-secret-at-least-32-chars";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CareCenterRepository careCenterRepository;

    @Autowired
    CaregiverProfileRepository caregiverProfileRepository;

    CareCenter center;

    CaregiverProfile caregiver;

    @BeforeEach
    void seed() {
        UUID adminId = UUID.randomUUID();
        center = careCenterRepository.save(CareCenter.builder()
                .centerName("Trung tâm nội bộ")
                .email("internal-" + UUID.randomUUID() + "@center.test")
                .address("Hà Nội")
                .centerStatus(CenterStatus.ACTIVE)
                .managerUserId(UUID.randomUUID())
                .createdBy(adminId)
                .build());
        caregiver = caregiverProfileRepository.save(CaregiverProfile.builder()
                .userId(UUID.randomUUID())
                .center(center)
                .fullName("Nguyễn Văn Chăm")
                .experienceYears(3)
                .ratingAverage(BigDecimal.ZERO)
                .ratingCount(0)
                .verificationStatus(VerificationStatus.VERIFIED)
                .caregiverStatus(CaregiverStatus.ACTIVE)
                .createdBy(adminId)
                .build());
    }

    @Test
    void getCaregiverReturnsBothIdsAndNullSkills() throws Exception {
        mockMvc.perform(get("/internal/caregivers/" + caregiver.getId()).header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.caregiverId").value(caregiver.getId().toString()))
                .andExpect(jsonPath("$.result.userId").value(caregiver.getUserId().toString()))
                .andExpect(jsonPath("$.result.centerId").value(center.getId().toString()))
                .andExpect(jsonPath("$.result.centerStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.result.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.result.caregiverStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.result.careServiceIds").doesNotExist());
    }

    @Test
    void getCaregiverByUserFindsProfile() throws Exception {
        mockMvc.perform(get("/internal/caregivers/by-user/" + caregiver.getUserId()).header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.caregiverId").value(caregiver.getId().toString()));
    }

    @Test
    void getCenterReturnsStatusAndManager() throws Exception {
        mockMvc.perform(get("/internal/centers/" + center.getId()).header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.centerId").value(center.getId().toString()))
                .andExpect(jsonPath("$.result.centerStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.result.managerUserId").value(center.getManagerUserId().toString()));
    }

    @Test
    void unknownIdsReturnNotFound() throws Exception {
        mockMvc.perform(get("/internal/caregivers/" + UUID.randomUUID()).header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4200));
        mockMvc.perform(get("/internal/caregivers/by-user/" + UUID.randomUUID()).header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/internal/centers/" + UUID.randomUUID()).header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4100));
    }

    @Test
    void missingOrWrongSecretIsRejected() throws Exception {
        mockMvc.perform(get("/internal/centers/" + center.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/centers/" + center.getId()).header(SECRET_HEADER, "wrong-secret"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/caregivers/" + caregiver.getId()).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}

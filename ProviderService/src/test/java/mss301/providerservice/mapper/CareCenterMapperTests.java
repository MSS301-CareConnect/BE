package mss301.providerservice.mapper;

import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.CenterStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CareCenterMapperTests {

    CareCenterMapper careCenterMapper = new CareCenterMapperImpl();

    @Test
    void createDoesNotCopyServerManagedFields() {
        CenterCreateRequest request = CenterCreateRequest.builder()
                .centerName("Trung tâm An Tâm")
                .phoneNumber("0901234567")
                .email("contact@antam.vn")
                .address("Thành phố Hồ Chí Minh")
                .operatingHours("08:00-18:00")
                .build();

        CareCenter result = careCenterMapper.toCareCenter(request);

        assertEquals(request.getCenterName(), result.getCenterName());
        assertEquals(request.getEmail(), result.getEmail());
        assertNull(result.getId());
        assertNull(result.getCenterStatus());
        assertNull(result.getCreatedBy());
        assertNull(result.getVersion());
    }

    @Test
    void updateKeepsServerManagedFields() {
        UUID creatorId = UUID.randomUUID();
        CareCenter careCenter = new CareCenter();
        careCenter.setCenterName("Tên cũ");
        careCenter.setEmail("old@example.com");
        careCenter.setAddress("Địa chỉ cũ");
        careCenter.setCenterStatus(CenterStatus.ACTIVE);
        careCenter.setStatusReason("Giữ nguyên");
        careCenter.setCreatedBy(creatorId);
        careCenter.setVersion(3L);

        CenterUpdateRequest request = CenterUpdateRequest.builder()
                .centerName("Tên mới")
                .email("new@example.com")
                .address("Địa chỉ mới")
                .version(3L)
                .build();

        careCenterMapper.update(request, careCenter);

        assertEquals("Tên mới", careCenter.getCenterName());
        assertEquals(CenterStatus.ACTIVE, careCenter.getCenterStatus());
        assertEquals("Giữ nguyên", careCenter.getStatusReason());
        assertEquals(creatorId, careCenter.getCreatedBy());
        assertEquals(3L, careCenter.getVersion());
    }
}

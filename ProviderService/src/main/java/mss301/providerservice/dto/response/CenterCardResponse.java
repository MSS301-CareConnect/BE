package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Trung tâm hiển thị cho khách (SRCH-02) — KHÔNG có email nội bộ, managerUserId, createdBy, trạng thái, version.
 * minPrice / serviceCount lấy từ Catalog (C9.2): null khi Catalog không trả (serviceCount chưa có trong contract).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterCardResponse {
    UUID centerId;
    String centerName;
    String address;
    String operatingHours;
    String phoneNumber;
    List<String> imageUrls;
    BigDecimal minPrice;
    Integer serviceCount;
}

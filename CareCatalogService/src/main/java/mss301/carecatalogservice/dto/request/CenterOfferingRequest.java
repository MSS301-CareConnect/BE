package mss301.carecatalogservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterOfferingRequest {

    @NotNull(message = "CARE_SERVICE_ID_REQUIRED")
    @JsonAlias({"careServiceId", "catalogItemId"})
    UUID catalogItemId;

    // Tùy chọn: nếu client gửi thì phải khớp với center_id trong token của CENTER_MANAGER
    UUID centerId;

    String description;

    @NotNull(message = "CARE_SERVICE_STATUS_REQUIRED")
    ServiceStatus status;

    // Chỉ dùng khi tạo: các gói giá tạo cùng dịch vụ trong một transaction (một gói sai → không tạo gì). Sửa thì bỏ qua.
    @Size(max = 20, message = "PRICE_PLAN_BATCH_INVALID")
    List<@Valid @NotNull(message = "PRICE_PLAN_BATCH_INVALID") PricePlanDraftRequest> pricePlans;

    // M-04: version lúc người dùng mở form; có thì phải khớp bản đang lưu (khác → 409). Bỏ trống = client cũ, không kiểm.
    Long version;
}

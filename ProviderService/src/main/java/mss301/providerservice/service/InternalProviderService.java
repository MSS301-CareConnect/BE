package mss301.providerservice.service;

import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.InternalCaregiverResponse;
import mss301.providerservice.dto.response.InternalCenterResponse;

import java.util.UUID;

// Tra cứu chỉ đọc cho service khác (/internal/**, X-Internal-Secret). Không tồn tại / đã xóa mềm → 404.
public interface InternalProviderService {
    APIResponse<InternalCaregiverResponse> getCaregiver(UUID caregiverId);

    APIResponse<InternalCaregiverResponse> getCaregiverByUser(UUID userId);

    APIResponse<InternalCenterResponse> getCenter(UUID centerId);
}

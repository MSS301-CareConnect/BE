package mss301.providerservice.service;

import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.CertificateVerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificatePendingAuditStatisticsResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.enums.CertificateAiCheckStatus;
import mss301.providerservice.enums.CertificateRiskLevel;

import java.util.List;
import java.util.UUID;

public interface CertificateService {

    // Thêm chứng chỉ.
    APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request);

    // Danh sách chứng chỉ của caregiver.
    APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId);

    // Danh sách chứng chỉ đã xác minh.
    APIResponse<List<CertificateResponse>> listVerified(UUID caregiverId);

    // Cập nhật chứng chỉ.
    APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request);

    // Admin duyệt chứng chỉ.
    APIResponse<CertificateResponse> verify(UUID actorId, UUID id, CertificateVerificationRequest request);

    // Manager xác nhận chứng chỉ.
    APIResponse<CertificateResponse> verifyCenter(UUID actorId, UUID id, CertificateVerificationRequest request);

    // Danh sách chứng chỉ chờ admin duyệt.
    APIResponse<PageResponse<CertificateResponse>> listPendingAudit(UUID centerId, CertificateRiskLevel riskLevel,
                                                                    CertificateAiCheckStatus aiCheckStatus,
                                                                    int page, int size);

    // Thống kê chứng chỉ chờ duyệt theo mức rủi ro.
    APIResponse<CertificatePendingAuditStatisticsResponse> getPendingAuditStatistics(UUID centerId);

    // ADMIN chạy lại kiểm tra AI cho chứng chỉ đang chờ duyệt (kết quả cũ bị thay).
    APIResponse<CertificateResponse> startAiCheck(UUID actorId, UUID id);

    // Xóa chứng chỉ.
    APIResponse<Void> delete(UUID actorId, UUID id, Long version);

    // Worker của CertificateAiCheckScheduler.
    List<UUID> findDueAiCheckIds();

    // Chạy AI kiểm tra một chứng chỉ.
    void processAiCheck(UUID id);
}

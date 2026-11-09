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

    APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request);

    APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId);

    APIResponse<List<CertificateResponse>> listVerified(UUID caregiverId);

    APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request);

    APIResponse<CertificateResponse> verify(UUID actorId, UUID id, CertificateVerificationRequest request);

    APIResponse<CertificateResponse> verifyCenter(UUID actorId, UUID id, CertificateVerificationRequest request);

    APIResponse<PageResponse<CertificateResponse>> listPendingAudit(UUID centerId, CertificateRiskLevel riskLevel,
                                                                    CertificateAiCheckStatus aiCheckStatus,
                                                                    int page, int size);

    APIResponse<CertificatePendingAuditStatisticsResponse> getPendingAuditStatistics(UUID centerId);

    // ADMIN chạy lại kiểm tra AI cho chứng chỉ đang chờ duyệt (kết quả cũ bị thay).
    APIResponse<CertificateResponse> startAiCheck(UUID actorId, UUID id);

    APIResponse<Void> delete(UUID actorId, UUID id, Long version);

    // Worker của CertificateAiCheckScheduler.
    List<UUID> findDueAiCheckIds();

    void processAiCheck(UUID id);
}

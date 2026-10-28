package mss301.providerservice.service;

import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.CertificateVerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateResponse;

import java.util.List;
import mss301.providerservice.dto.response.PageResponse;
import java.util.UUID;

public interface CertificateService {

    APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request);

    APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId);

    APIResponse<List<CertificateResponse>> listVerified(UUID caregiverId);

    APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request);

    APIResponse<CertificateResponse> verify(UUID actorId, UUID id, CertificateVerificationRequest request);

    APIResponse<CertificateResponse> verifyCenter(UUID actorId, UUID id, CertificateVerificationRequest request);

    APIResponse<PageResponse<CertificateResponse>> listPendingAudit(UUID centerId, int page, int size);

    APIResponse<Void> delete(UUID actorId, UUID id, Long version);
}

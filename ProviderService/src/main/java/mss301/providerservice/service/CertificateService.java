package mss301.providerservice.service;

import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateResponse;

import java.util.List;
import java.util.UUID;

public interface CertificateService {

    APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request);

    APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId);

    APIResponse<List<CertificateResponse>> verifiedList(UUID caregiverId);

    APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request);

    APIResponse<CertificateResponse> verify(UUID actorId, UUID id, VerificationRequest request);

    APIResponse<Void> delete(UUID actorId, UUID id, Long version);
}

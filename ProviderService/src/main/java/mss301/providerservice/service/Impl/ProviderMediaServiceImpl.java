package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.MediaClient;
import mss301.providerservice.client.dto.DownloadUrlClientResponse;
import mss301.providerservice.client.dto.MediaClientResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.ProviderMediaResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.ProviderMedia;
import mss301.providerservice.enums.MediaFileType;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderMediaMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.repository.ProviderMediaRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.ProviderMediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import mss301.providerservice.enums.RoleCode;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.enums.CertificateVerificationStatus;
import java.time.Clock;
import java.time.LocalDate;
import mss301.providerservice.util.ProviderTime;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProviderMediaServiceImpl implements ProviderMediaService {

    static int MAX_FILES_PER_REQUEST = 10;

    ProviderMediaRepository providerMediaRepository;
    CareCenterRepository careCenterRepository;
    CaregiverProfileRepository caregiverProfileRepository;
    CertificateRepository certificateRepository;
    ProviderAuthorization providerAuthorization;
    MediaClient mediaClient;
    ProviderMediaMapper providerMediaMapper;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    public APIResponse<List<ProviderMediaResponse>> upload(UUID actorId, MediaOwnerType ownerType, UUID ownerId, List<MultipartFile> files) {
        requireOwnerAccess(actorId, ownerType, ownerId);
        List<MultipartFile> validFiles = validateFiles(files);
        int displayOrder = providerMediaRepository.findMaximumDisplayOrder(ownerType, ownerId) + 1;

        List<ProviderMediaResponse> responses;
        List<UUID> uploadedMediaIds = new ArrayList<>();
        List<MediaClientResponse> uploadedFiles = new ArrayList<>();
        Map<UUID, DownloadUrlClientResponse> downloads = new HashMap<>();
        try {
            for (MultipartFile file : validFiles) {
                MediaClientResponse uploaded = requireMediaResult(mediaClient.upload(file, resolvePurpose(ownerType),
                        actorId, ownerType.name(), ownerId));
                uploadedMediaIds.add(uploaded.getMediaFileId());
                uploadedFiles.add(uploaded);
                downloads.put(uploaded.getMediaFileId(), getDownloadUrl(uploaded.getMediaFileId()));
            }
            responses = transactionTemplate.execute(status -> {
                requireOwnerAccess(actorId, ownerType, ownerId);
                List<ProviderMediaResponse> items = new ArrayList<>();
                int nextOrder = displayOrder;
                for (MediaClientResponse uploaded : uploadedFiles) {
                    ProviderMedia media = providerMediaMapper.toProviderMedia(ownerType, ownerId, uploaded,
                            nextOrder++, actorId);
                    ProviderMedia saved = providerMediaRepository.saveAndFlush(media);
                    items.add(providerMediaMapper.toResponse(saved, uploaded, downloads.get(uploaded.getMediaFileId())));
                }
                return items;
            });
        } catch (RuntimeException exception) {
            deleteUploadedFilesQuietly(uploadedMediaIds);
            throw exception;
        }

        return APIResponse.success("Tải file lên thành công", responses);
    }

    @Override
    public APIResponse<List<ProviderMediaResponse>> list(UUID actorId, MediaOwnerType ownerType, UUID ownerId) {
        if (providerAuthorization.hasRole(RoleCode.CUSTOMER)) {
            checkPublicMedia(ownerType, ownerId);
        } else {
            requireOwnerAccess(actorId, ownerType, ownerId);
        }

        List<ProviderMediaResponse> responses = providerMediaRepository
                .findAllByOwnerTypeAndOwnerIdOrderByDisplayOrderAsc(ownerType, ownerId)
                .stream()
                .map(providerMedia -> {
                    MediaClientResponse metadata = requireMediaResult(mediaClient.get(providerMedia.getMediaFileId()));
                    DownloadUrlClientResponse download = getDownloadUrl(providerMedia.getMediaFileId());
                    return providerMediaMapper.toResponse(providerMedia, metadata, download);
                })
                .toList();

        return APIResponse.success("Lấy danh sách file thành công", responses);
    }

    @Override
    public APIResponse<Void> delete(UUID actorId, MediaOwnerType ownerType, UUID ownerId, UUID mediaFileId) {
        requireOwnerAccess(actorId, ownerType, ownerId);

        ProviderMedia providerMedia = providerMediaRepository
                .findByOwnerTypeAndOwnerIdAndMediaFileId(ownerType, ownerId, mediaFileId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));

        mediaClient.delete(mediaFileId);
        providerMediaRepository.delete(providerMedia);
        return APIResponse.success("Xóa file thành công");
    }

    @Override
    public Map<UUID, List<String>> findImageUrls(MediaOwnerType ownerType, Collection<UUID> ownerIds,
                                                 int limitPerOwner) {
        Map<UUID, List<String>> imageUrls = new HashMap<>();
        if (ownerIds.isEmpty()) {
            return imageUrls;
        }
        List<ProviderMedia> images = providerMediaRepository.findByOwnerTypeAndOwnerIdInAndMediaFileTypeOrderByDisplayOrderAsc(
                ownerType, ownerIds, MediaFileType.IMAGE);
        for (ProviderMedia image : images) {
            List<String> ownerUrls = imageUrls.computeIfAbsent(image.getOwnerId(), ownerId -> new ArrayList<>());
            if (ownerUrls.size() >= limitPerOwner) {
                continue;
            }
            String downloadUrl = findDownloadUrlQuietly(image.getMediaFileId());
            if (downloadUrl != null) {
                ownerUrls.add(downloadUrl);
            }
        }
        return imageUrls;
    }

    private String findDownloadUrlQuietly(UUID mediaFileId) {
        try {
            return getDownloadUrl(mediaFileId).getDownloadUrl();
        } catch (RuntimeException exception) {
            // Fallback có chủ đích: thiếu ảnh không được làm hỏng danh sách công khai.
            log.warn("Cannot create download URL for media file {}: {}", mediaFileId, exception.getMessage());
            return null;
        }
    }

    private void checkPublicMedia(MediaOwnerType ownerType, UUID ownerId) {
        switch (ownerType) {
            case CARE_CENTER -> careCenterRepository.findByIdAndDeletedAtIsNull(ownerId)
                    .filter(center -> center.getCenterStatus() == CenterStatus.ACTIVE)
                    .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
            case CAREGIVER_PROFILE -> checkPublicCaregiver(caregiverProfileRepository.findByIdAndDeletedAtIsNull(ownerId)
                    .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND)));
            case CERTIFICATE -> {
                Certificate certificate = certificateRepository.findByIdAndDeletedAtIsNull(ownerId)
                        .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
                if (certificate.getVerificationStatus() != CertificateVerificationStatus.ADMIN_VERIFIED
                        || (certificate.getExpiryDate() != null
                            && certificate.getExpiryDate().isBefore(LocalDate.now(clock.withZone(ProviderTime.ZONE))))) {
                    throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
                }
                checkPublicCaregiver(certificate.getCaregiver());
            }
        }
    }

    private void checkPublicCaregiver(CaregiverProfile caregiver) {
        if (caregiver.getDeletedAt() != null || caregiver.getCaregiverStatus() != CaregiverStatus.ACTIVE
                || caregiver.getVerificationStatus() != VerificationStatus.VERIFIED
                || caregiver.getCenter().getDeletedAt() != null || caregiver.getCenter().getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
        }
    }

    private void requireOwnerAccess(UUID actorId, MediaOwnerType ownerType, UUID ownerId) {
        switch (ownerType) {
            case CARE_CENTER -> requireCenterAccess(actorId, ownerId);
            case CAREGIVER_PROFILE -> requireCaregiverAccess(actorId, ownerId);
            case CERTIFICATE -> requireCertificateAccess(actorId, ownerId);
        }
    }

    private void requireCenterAccess(UUID actorId, UUID centerId) {
        CareCenter center = careCenterRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        providerAuthorization.requireCenterManagement(center, actorId);
    }

    private void requireCaregiverAccess(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = caregiverProfileRepository.findByIdAndDeletedAtIsNull(caregiverId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
    }

    private void requireCertificateAccess(UUID actorId, UUID certificateId) {
        Certificate certificate = certificateRepository.findByIdAndDeletedAtIsNull(certificateId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
        providerAuthorization.requireCaregiverSelfOrCenterManagement(certificate.getCaregiver(), actorId);
    }

    private List<MultipartFile> validateFiles(List<MultipartFile> files) {
        if (files == null) {
            throw new AppException(ErrorCode.MEDIA_REQUIRED);
        }

        List<MultipartFile> validFiles = files.stream()
                .filter(Objects::nonNull)
                .filter(file -> !file.isEmpty())
                .toList();

        if (validFiles.isEmpty()) {
            throw new AppException(ErrorCode.MEDIA_REQUIRED);
        }
        if (validFiles.size() > MAX_FILES_PER_REQUEST) {
            throw new AppException(ErrorCode.MEDIA_LIMIT_EXCEEDED);
        }
        return validFiles;
    }

    private String resolvePurpose(MediaOwnerType ownerType) {
        return switch (ownerType) {
            case CARE_CENTER -> "CARE_CENTER_LOGO";
            case CAREGIVER_PROFILE -> "CAREGIVER_AVATAR";
            case CERTIFICATE -> "CAREGIVER_CERTIFICATE";
        };
    }

    private MediaClientResponse requireMediaResult(APIResponse<MediaClientResponse> response) {
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
        }
        return response.getResult();
    }

    private DownloadUrlClientResponse getDownloadUrl(UUID mediaFileId) {
        APIResponse<DownloadUrlClientResponse> response = mediaClient.createDownloadUrl(mediaFileId);
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
        }
        return response.getResult();
    }

    private void deleteUploadedFilesQuietly(List<UUID> uploadedMediaIds) {
        for (UUID mediaFileId : uploadedMediaIds) {
            try {
                mediaClient.delete(mediaFileId);
            } catch (RuntimeException exception) {
                // Dọn dẹp best-effort: không che lỗi gốc của lần upload, chỉ ghi lại file có thể bị mồ côi.
                log.warn("Failed to clean up uploaded media file {}: {}", mediaFileId, exception.getMessage());
            }
        }
    }
}

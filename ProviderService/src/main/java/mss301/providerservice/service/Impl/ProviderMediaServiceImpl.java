package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.client.MediaClient;
import mss301.providerservice.client.dto.DownloadUrlClientResponse;
import mss301.providerservice.client.dto.MediaClientResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.ProviderMediaResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.ProviderMedia;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.repository.ProviderMediaRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.ProviderMediaService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProviderMediaServiceImpl implements ProviderMediaService {
    private static final int MAX_FILES_PER_REQUEST = 10;
    private static final String SOURCE_SERVICE = "PROVIDER_SERVICE";

    ProviderMediaRepository providerMediaRepository;
    CareCenterRepository careCenterRepository;
    CaregiverProfileRepository caregiverProfileRepository;
    CertificateRepository certificateRepository;
    ProviderAuthorization authorization;
    MediaClient mediaClient;

    @Override
    public APIResponse<List<ProviderMediaResponse>> upload(UUID actorId, MediaOwnerType ownerType, UUID ownerId, List<MultipartFile> files) {
        requireOwnerAccess(actorId, ownerType, ownerId);
        List<MultipartFile> validFiles = validateFiles(files);
        int displayOrder = providerMediaRepository.findMaximumDisplayOrder(ownerType, ownerId) + 1;

        List<ProviderMediaResponse> responses = new ArrayList<>();
        List<UUID> uploadedMediaIds = new ArrayList<>();

        try {
            for (MultipartFile file : validFiles) {
                APIResponse<MediaClientResponse> uploadResult = mediaClient.upload(file, purposeOf(ownerType), actorId,
                        SOURCE_SERVICE, ownerType.name(), ownerId);

                MediaClientResponse uploaded = requireMediaResult(uploadResult);
                uploadedMediaIds.add(uploaded.getMediaFileId());

                ProviderMedia providerMedia = ProviderMedia.builder()
                        .ownerType(ownerType)
                        .ownerId(ownerId)
                        .mediaFileId(uploaded.getMediaFileId())
                        .mediaFileType(uploaded.getFileType())
                        .displayOrder(displayOrder++)
                        .createdBy(actorId)
                        .build();

                ProviderMedia savedMedia = providerMediaRepository.save(providerMedia);
                DownloadUrlClientResponse download = getDownloadUrl(uploaded.getMediaFileId());
                responses.add(toResponse(savedMedia, uploaded, download));
            }
        } catch (RuntimeException exception) {
            deleteUploadedFilesQuietly(uploadedMediaIds);
            throw exception;
        }

        return APIResponse.success("Tải file lên thành công", responses);
    }

    @Override
    public APIResponse<List<ProviderMediaResponse>> list(UUID actorId, MediaOwnerType ownerType, UUID ownerId) {
        requireOwnerAccess(actorId, ownerType, ownerId);

        List<ProviderMediaResponse> responses = providerMediaRepository
                .findAllByOwnerTypeAndOwnerIdOrderByDisplayOrderAsc(ownerType, ownerId)
                .stream()
                .map(providerMedia -> {
                    MediaClientResponse metadata = requireMediaResult(mediaClient.get(providerMedia.getMediaFileId()));
                    DownloadUrlClientResponse download = getDownloadUrl(providerMedia.getMediaFileId());
                    return toResponse(providerMedia, metadata, download);
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
        authorization.requireCenterManagement(center, actorId);
    }

    private void requireCaregiverAccess(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = caregiverProfileRepository.findByIdAndDeletedAtIsNull(caregiverId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
        authorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
    }

    private void requireCertificateAccess(UUID actorId, UUID certificateId) {
        Certificate certificate = certificateRepository.findByIdAndDeletedAtIsNull(certificateId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
        authorization.requireCaregiverSelfOrCenterManagement(certificate.getCaregiver(), actorId);
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

    private String purposeOf(MediaOwnerType ownerType) {
        return switch (ownerType) {
            case CARE_CENTER -> "CARE_CENTER_IMAGE";
            case CAREGIVER_PROFILE -> "CAREGIVER_PROFILE_IMAGE";
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

    private ProviderMediaResponse toResponse(ProviderMedia providerMedia, MediaClientResponse metadata, DownloadUrlClientResponse download) {
        return ProviderMediaResponse.builder()
                .mediaFileId(providerMedia.getMediaFileId())
                .mediaFileType(providerMedia.getMediaFileType())
                .originalFileName(metadata.getOriginalFileName())
                .contentType(metadata.getContentType())
                .fileSize(metadata.getFileSize())
                .displayOrder(providerMedia.getDisplayOrder())
                .downloadUrl(download.getDownloadUrl())
                .downloadUrlExpiresAt(download.getExpiresAt())
                .build();
    }

    private void deleteUploadedFilesQuietly(List<UUID> uploadedMediaIds) {
        for (UUID mediaFileId : uploadedMediaIds) {
            try {
                mediaClient.delete(mediaFileId);
            } catch (RuntimeException ignored) {

            }
        }
    }
}

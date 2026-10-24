package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.MediaClient;
import mss301.bookingservice.client.dto.MediaClientResponse;
import mss301.bookingservice.client.dto.DownloadUrlClientResponse;
import mss301.bookingservice.dto.request.CareReportRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CareReportResponse;
import mss301.bookingservice.dto.response.ReportPhotoResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.CareReport;
import mss301.bookingservice.entity.CareReportMedia;
import mss301.bookingservice.entity.CareReportMediaId;
import mss301.bookingservice.entity.CareSession;
import mss301.bookingservice.enums.CareReportStatus;
import mss301.bookingservice.enums.CareSessionStatus;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.CareReportMapper;
import mss301.bookingservice.repository.CareReportRepository;
import mss301.bookingservice.repository.CareReportMediaRepository;
import mss301.bookingservice.repository.CareSessionRepository;
import mss301.bookingservice.service.CareReportService;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.util.AftercareScope;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareReportServiceImpl implements CareReportService {
    CareReportRepository careReportRepository;
    CareReportMediaRepository careReportMediaRepository;
    CareSessionRepository careSessionRepository;
    CareReportMapper careReportMapper;
    MediaClient mediaClient;
    NotificationService notificationService;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    public APIResponse<CareReportResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id) {
        CareReportResponse response = transactionTemplate.execute(status -> {
            CareReport report = findReport(id);
            if (!AftercareScope.canReadSession(actorId, roles, centerId, report.getSession())) {
                throw new AppException(ErrorCode.REPORT_NOT_FOUND);
            }
            return toResponseWithoutUrls(report);
        });
        fillPhotoUrls(response);
        return APIResponse.success("Lấy báo cáo chăm sóc thành công", response);
    }

    @Override
    public APIResponse<PageResponse<CareReportResponse>> list(UUID actorId, Set<RoleCode> roles, UUID centerId,
                                                              UUID bookingId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        PageResponse<CareReportResponse> response = transactionTemplate.execute(status -> {
            Page<CareReport> reports = careReportRepository.findAll((root, query, builder) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(builder.isNull(root.get("deletedAt")));
                if (bookingId != null) {
                    predicates.add(builder.equal(root.get("bookingId"), bookingId));
                }
                if (!roles.contains(RoleCode.ADMIN)) {
                    List<Predicate> scope = new ArrayList<>();
                    if (roles.contains(RoleCode.CUSTOMER)) {
                        scope.add(builder.equal(root.get("session").get("booking").get("customerId"), actorId));
                    }
                    if (roles.contains(RoleCode.CENTER_MANAGER) && centerId != null) {
                        scope.add(builder.equal(root.get("session").get("booking").get("centerId"), centerId));
                    }
                    if (roles.contains(RoleCode.CAREGIVER)) {
                        scope.add(builder.equal(root.get("caregiverUserId"), actorId));
                    }
                    predicates.add(builder.or(scope.toArray(Predicate[]::new)));
                }
                return builder.and(predicates.toArray(Predicate[]::new));
            }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
            return PageResponse.of(reports, reports.getContent().stream().map(this::toResponseWithoutUrls).toList());
        });
        response.getItems().forEach(this::fillPhotoUrls);
        return APIResponse.success("Lấy danh sách báo cáo thành công", response);
    }

    @Override
    public APIResponse<CareReportResponse> create(UUID actorId, CareReportRequest request) {
        CareReportResponse response;
        try {
            response = transactionTemplate.execute(status -> {
                CareSession session = careSessionRepository.findForUpdate(request.getSessionId())
                        .filter(found -> actorId.equals(found.getCaregiverUserId()))
                        .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
                if (careReportRepository.existsBySessionIdAndDeletedAtIsNull(session.getId())) {
                    throw new AppException(ErrorCode.REPORT_EXISTS);
                }
                CareReport report = careReportMapper.toCareReport(request, session);
                report.setStatus(CareReportStatus.DRAFT);
                return toResponseWithoutUrls(careReportRepository.saveAndFlush(report));
            });
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.REPORT_EXISTS);
        }
        return APIResponse.success("Tạo báo cáo nháp thành công", response);
    }

    @Override
    public APIResponse<CareReportResponse> update(UUID actorId, UUID id, CareReportRequest request) {
        CareReportResponse response = transactionTemplate.execute(status -> {
            CareReport report = lockDraft(actorId, id);
            if (request.getVersion() == null || !request.getVersion().equals(report.getVersion())
                    || !request.getSessionId().equals(report.getSession().getId())) {
                throw new AppException(ErrorCode.CONCURRENT_UPDATE);
            }
            careReportMapper.update(request, report);
            careReportRepository.flush();
            return toResponseWithoutUrls(report);
        });
        fillPhotoUrls(response);
        return APIResponse.success("Cập nhật báo cáo nháp thành công", response);
    }

    @Override
    public APIResponse<CareReportResponse> submit(UUID actorId, UUID id) {
        CareReportResponse response = transactionTemplate.execute(status -> {
            CareReport report = lockDraft(actorId, id);
            if (report.getSession().getStatus() != CareSessionStatus.ENDED) {
                throw new AppException(ErrorCode.REPORT_SESSION_NOT_ENDED);
            }
            report.setStatus(CareReportStatus.SUBMITTED);
            report.setSubmittedAt(clock.instant());
            careReportRepository.flush();
            notificationService.notify(List.of(report.getSession().getBooking().getCustomerId()),
                    NotificationType.REPORT_SUBMITTED, Map.of(), "CARE_REPORT", id);
            return toResponseWithoutUrls(report);
        });
        fillPhotoUrls(response);
        return APIResponse.success("Gửi báo cáo chăm sóc thành công", response);
    }

    @Override
    public APIResponse<CareReportResponse> addPhotos(UUID actorId, UUID id, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 5) {
            throw new AppException(ErrorCode.TOO_MANY_REPORT_PHOTOS);
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty() || file.getSize() > 10L * 1024 * 1024
                    || !Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType())) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }
        transactionTemplate.executeWithoutResult(status -> lockDraft(actorId, id));
        List<UUID> uploaded = new ArrayList<>();
        CareReportResponse response;
        try {
            for (MultipartFile file : files) {
                APIResponse<MediaClientResponse> result = mediaClient.upload(file, "CARE_REPORT_IMAGE", actorId, "CARE_REPORT", id);
                if (result == null || result.getResult() == null || result.getResult().getMediaFileId() == null) {
                    throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
                }
                uploaded.add(result.getResult().getMediaFileId());
            }
            response = transactionTemplate.execute(status -> {
                CareReport report = lockDraft(actorId, id);
                List<CareReportMedia> existing = careReportMediaRepository.findByIdReportIdOrderByDisplayOrder(id);
                if (existing.size() + uploaded.size() > 5) {
                    throw new AppException(ErrorCode.TOO_MANY_REPORT_PHOTOS);
                }
                int nextOrder = existing.stream().mapToInt(CareReportMedia::getDisplayOrder).max().orElse(-1) + 1;
                for (UUID mediaId : uploaded) {
                    careReportMediaRepository.save(careReportMapper.toCareReportMedia(id, mediaId, nextOrder++, clock.instant()));
                }
                careReportMediaRepository.flush();
                return toResponseWithoutUrls(report);
            });
        } catch (RuntimeException exception) {
            uploaded.forEach(this::deleteMediaQuietly);
            throw exception;
        }
        fillPhotoUrls(response);
        return APIResponse.success("Thêm ảnh báo cáo thành công", response);
    }

    @Override
    public APIResponse<Void> deletePhoto(UUID actorId, UUID id, UUID mediaFileId) {
        transactionTemplate.executeWithoutResult(status -> {
            lockDraft(actorId, id);
            CareReportMedia photo = careReportMediaRepository.findById(new CareReportMediaId(id, mediaFileId))
                    .orElseThrow(() -> new AppException(ErrorCode.REPORT_PHOTO_NOT_FOUND));
            careReportMediaRepository.delete(photo);
        });
        deleteMediaQuietly(mediaFileId);
        return APIResponse.success("Xóa ảnh báo cáo thành công");
    }

    private CareReport findReport(UUID id) {
        return careReportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
    }

    private CareReport lockDraft(UUID actorId, UUID id) {
        CareReport report = careReportRepository.findForUpdate(id)
                .filter(found -> actorId.equals(found.getCaregiverUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() != CareReportStatus.DRAFT) {
            throw new AppException(ErrorCode.REPORT_ALREADY_SUBMITTED);
        }
        return report;
    }

    private CareReportResponse toResponseWithoutUrls(CareReport report) {
        List<ReportPhotoResponse> photos = careReportMediaRepository.findByIdReportIdOrderByDisplayOrder(report.getId())
                .stream().map(photo -> careReportMapper.toPhotoResponse(photo, null)).toList();
        return careReportMapper.toResponse(report, photos);
    }

    private void fillPhotoUrls(CareReportResponse response) {
        for (ReportPhotoResponse photo : response.getPhotos()) {
            APIResponse<DownloadUrlClientResponse> result = mediaClient.createDownloadUrl(photo.getMediaFileId());
            if (result == null || result.getResult() == null) {
                throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
            }
            photo.setDownloadUrl(result.getResult().getDownloadUrl());
            photo.setExpiresAt(result.getResult().getExpiresAt());
        }
    }

    private void deleteMediaQuietly(UUID id) {
        try {
            mediaClient.delete(id);
        } catch (RuntimeException exception) {
            log.error("Không dọn được file báo cáo {}", id, exception);
        }
    }
}

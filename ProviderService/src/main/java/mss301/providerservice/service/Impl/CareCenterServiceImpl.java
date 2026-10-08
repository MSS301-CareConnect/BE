package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CareCenterMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CareCenterSpecifications;
import mss301.providerservice.service.CareCenterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareCenterServiceImpl implements CareCenterService {

    static int MAX_PAGE_SIZE = 100;

    CareCenterRepository careCenterRepository;
    CareCenterMapper careCenterMapper;

    @Override
    @Transactional
    public APIResponse<CenterResponse> create(UUID actorId, CenterCreateRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (careCenterRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new AppException(ErrorCode.CENTER_EMAIL_EXISTED);
        }
        if (request.getManagerUserId() != null
                && careCenterRepository.existsByManagerUserIdAndDeletedAtIsNull(request.getManagerUserId())) {
            throw new AppException(ErrorCode.CENTER_MANAGER_EXISTED);
        }

        CareCenter careCenter = careCenterMapper.toCareCenter(request);
        normalizeFields(careCenter, normalizedEmail);
        careCenter.setCenterStatus(CenterStatus.PENDING);
        careCenter.setCreatedBy(actorId);
        careCenter.setUpdatedBy(actorId);

        CareCenter savedCenter = careCenterRepository.save(careCenter);
        log.info("Admin {} created care center {}", actorId, savedCenter.getId());

        CenterResponse response = careCenterMapper.toResponse(savedCenter);
        return APIResponse.success("Tạo trung tâm chăm sóc thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CenterResponse>> list(CenterStatus status, String keyword,
                                                           boolean includeDeleted, int page, int size) {
        validatePage(page, size);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CareCenter> centerPage = careCenterRepository.findAll(
                CareCenterSpecifications.filter(status, keyword, includeDeleted), pageRequest);
        List<CenterResponse> items = careCenterMapper.toResponseList(centerPage.getContent());
        PageResponse<CenterResponse> result = PageResponse.of(centerPage, items);
        return APIResponse.success("Lấy danh sách trung tâm chăm sóc thành công", result);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CenterResponse> get(UUID id) {
        CareCenter careCenter = careCenterRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        CenterResponse response = careCenterMapper.toResponse(careCenter);
        return APIResponse.success("Lấy thông tin trung tâm chăm sóc thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<CenterResponse> update(UUID actorId, UUID id, CenterUpdateRequest request) {
        CareCenter careCenter = findActiveCenter(id);
        checkVersion(careCenter, request.getVersion());

        String normalizedEmail = normalizeEmail(request.getEmail());
        if (careCenterRepository.existsByEmailAndIdNotAndDeletedAtIsNull(normalizedEmail, id)) {
            throw new AppException(ErrorCode.CENTER_EMAIL_EXISTED);
        }
        if (request.getManagerUserId() != null
                && careCenterRepository.existsByManagerUserIdAndIdNotAndDeletedAtIsNull(request.getManagerUserId(), id)) {
            throw new AppException(ErrorCode.CENTER_MANAGER_EXISTED);
        }

        careCenterMapper.update(request, careCenter);
        normalizeFields(careCenter, normalizedEmail);
        careCenter.setUpdatedBy(actorId);

        CareCenter savedCenter = careCenterRepository.save(careCenter);
        log.info("Admin {} updated care center {}", actorId, id);

        CenterResponse response = careCenterMapper.toResponse(savedCenter);
        return APIResponse.success("Cập nhật trung tâm chăm sóc thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<CenterResponse> updateStatus(UUID actorId, UUID id, CenterStatusUpdateRequest request) {
        CareCenter careCenter = findActiveCenter(id);
        checkVersion(careCenter, request.getVersion());
        validateStatusReason(request);

        careCenter.setCenterStatus(request.getStatus());
        careCenter.setStatusReason(normalizeNullable(request.getReason()));
        careCenter.setUpdatedBy(actorId);
        if (request.getStatus() == CenterStatus.ARCHIVED) {
            careCenter.setDeletedAt(Instant.now());
        }

        CareCenter savedCenter = careCenterRepository.save(careCenter);
        log.info("Admin {} changed care center {} status to {}", actorId, id, request.getStatus());

        CenterResponse response = careCenterMapper.toResponse(savedCenter);
        return APIResponse.success("Cập nhật trạng thái trung tâm thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        CareCenter careCenter = findActiveCenter(id);
        checkVersion(careCenter, version);
        careCenter.setCenterStatus(CenterStatus.ARCHIVED);
        careCenter.setStatusReason("Archived by administrator");
        careCenter.setUpdatedBy(actorId);
        careCenter.setDeletedAt(Instant.now());
        careCenterRepository.save(careCenter);
        log.info("Admin {} archived care center {}", actorId, id);
        return APIResponse.success("Lưu trữ trung tâm chăm sóc thành công");
    }

    private CareCenter findActiveCenter(UUID id) {
        return careCenterRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
    }

    private void checkVersion(CareCenter careCenter, Long requestedVersion) {
        if (requestedVersion == null) {
            throw new AppException(ErrorCode.CENTER_VERSION_REQUIRED);
        }
        if (!requestedVersion.equals(careCenter.getVersion())) {
            throw new AppException(ErrorCode.CENTER_VERSION_CONFLICT);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private void validateStatusReason(CenterStatusUpdateRequest request) {
        boolean reasonRequired = request.getStatus() == CenterStatus.REJECTED
                || request.getStatus() == CenterStatus.SUSPENDED;
        if (reasonRequired && (request.getReason() == null || request.getReason().isBlank())) {
            throw new AppException(ErrorCode.CENTER_STATUS_REASON_REQUIRED);
        }
    }

    private void normalizeFields(CareCenter careCenter, String normalizedEmail) {
        careCenter.setCenterName(careCenter.getCenterName().trim());
        careCenter.setEmail(normalizedEmail);
        careCenter.setAddress(careCenter.getAddress().trim());
        careCenter.setPhoneNumber(normalizeNullable(careCenter.getPhoneNumber()));
        careCenter.setOperatingHours(normalizeNullable(careCenter.getOperatingHours()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

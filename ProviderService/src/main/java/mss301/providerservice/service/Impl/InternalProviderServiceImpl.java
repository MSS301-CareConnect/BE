package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.InternalCaregiverResponse;
import mss301.providerservice.dto.response.InternalCenterResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.InternalProviderMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.service.InternalProviderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Trả nguyên trạng thái (kể cả INACTIVE/SUSPENDED/PENDING) — service gọi tự quyết có dùng được hay không.
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalProviderServiceImpl implements InternalProviderService {

    CaregiverProfileRepository caregiverProfileRepository;
    CareCenterRepository careCenterRepository;
    InternalProviderMapper internalProviderMapper;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<InternalCaregiverResponse> getCaregiver(UUID caregiverId) {
        CaregiverProfile caregiver = caregiverProfileRepository.findByIdAndDeletedAtIsNull(caregiverId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
        InternalCaregiverResponse caregiverResponse = internalProviderMapper.toInternalCaregiverResponse(caregiver);
        return APIResponse.success("Lấy thông tin người chăm sóc thành công", caregiverResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<InternalCaregiverResponse> getCaregiverByUser(UUID userId) {
        CaregiverProfile caregiver = caregiverProfileRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
        InternalCaregiverResponse caregiverResponse = internalProviderMapper.toInternalCaregiverResponse(caregiver);
        return APIResponse.success("Lấy thông tin người chăm sóc thành công", caregiverResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<InternalCenterResponse> getCenter(UUID centerId) {
        CareCenter center = careCenterRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        InternalCenterResponse centerResponse = internalProviderMapper.toInternalCenterResponse(center);
        return APIResponse.success("Lấy thông tin trung tâm thành công", centerResponse);
    }
}

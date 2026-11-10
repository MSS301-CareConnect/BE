package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.dto.request.RecipientAddressRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.RecipientAddressResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.RecipientAddress;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.RecipientAddressMapper;
import mss301.carerecipientservice.repository.CareRecipientRepository;
import mss301.carerecipientservice.repository.RecipientAddressRepository;
import mss301.carerecipientservice.service.RecipientAccessService;
import mss301.carerecipientservice.service.RecipientAddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Sổ địa chỉ (2026-11-05): người xem hồ sơ (VIEWER) đọc được để chọn lúc đặt lịch; EDITOR / OWNER thêm, sửa, xóa.
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RecipientAddressServiceImpl implements RecipientAddressService {

    static int MAX_ADDRESSES = 20;

    RecipientAddressRepository recipientAddressRepository;
    CareRecipientRepository careRecipientRepository;
    RecipientAccessService recipientAccessService;
    RecipientAddressMapper recipientAddressMapper;

    @Transactional(readOnly = true)
    @Override
    public APIResponse<List<RecipientAddressResponse>> list(UUID actorId, UUID recipientId) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.VIEWER);
        List<RecipientAddress> recipientAddresses = recipientAddressRepository.findAllByRecipient(recipientId);
        List<RecipientAddressResponse> recipientAddressResponses = recipientAddressMapper.toResponseList(recipientAddresses);
        return APIResponse.success("Lấy sổ địa chỉ thành công", recipientAddressResponses);
    }

    @Transactional
    @Override
    public APIResponse<RecipientAddressResponse> create(UUID actorId, UUID recipientId, RecipientAddressRequest request) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        recipientAccessService.checkActiveForWrite(recipientId);
        CareRecipient recipient = lockRecipient(recipientId);
        // Đếm dưới khóa hồ sơ để hai request song song không cùng vượt giới hạn.
        if (recipientAddressRepository.countByRecipient(recipientId) >= MAX_ADDRESSES) {
            throw new AppException(ErrorCode.ADDRESS_BOOK_FULL);
        }
        RecipientAddress recipientAddress = recipientAddressMapper.toRecipientAddress(request, recipient);
        RecipientAddress savedAddress = recipientAddressRepository.saveAndFlush(recipientAddress);
        log.info("Address {} added to care recipient {} by user {}", savedAddress.getId(), recipientId, actorId);
        RecipientAddressResponse recipientAddressResponse = recipientAddressMapper.toResponse(savedAddress);
        return APIResponse.success("Đã lưu địa chỉ", recipientAddressResponse);
    }

    @Transactional
    @Override
    public APIResponse<RecipientAddressResponse> update(UUID actorId, UUID recipientId, UUID id,
                                                        RecipientAddressRequest request) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        recipientAccessService.checkActiveForWrite(recipientId);
        RecipientAddress recipientAddress = findAddress(recipientId, id);
        recipientAddressMapper.update(request, recipientAddress);
        recipientAddressRepository.flush();
        RecipientAddressResponse recipientAddressResponse = recipientAddressMapper.toResponse(recipientAddress);
        return APIResponse.success("Đã cập nhật địa chỉ", recipientAddressResponse);
    }

    // Xóa mềm; booking đã đặt giữ địa chỉ đã chụp lúc đặt / lúc đổi nên không bị ảnh hưởng.
    @Transactional
    @Override
    public APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.EDITOR);
        recipientAccessService.checkActiveForWrite(recipientId);
        RecipientAddress recipientAddress = findAddress(recipientId, id);
        recipientAddress.setDeletedAt(Instant.now());
        log.info("Address {} of care recipient {} deleted by user {}", id, recipientId, actorId);
        return APIResponse.success("Đã xóa địa chỉ");
    }

    private RecipientAddress findAddress(UUID recipientId, UUID id) {
        return recipientAddressRepository.findByIdAndRecipient(id, recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    private CareRecipient lockRecipient(UUID recipientId) {
        return careRecipientRepository.findForUpdate(recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
    }
}

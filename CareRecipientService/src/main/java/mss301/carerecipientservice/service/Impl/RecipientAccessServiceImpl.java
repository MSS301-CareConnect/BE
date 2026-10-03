package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.repository.RecipientManagerRepository;
import mss301.carerecipientservice.service.RecipientAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecipientAccessServiceImpl implements RecipientAccessService {

    RecipientManagerRepository recipientManagerRepository;

    @Override
    @Transactional(readOnly = true)
    public RecipientManager checkAccess(UUID recipientId, UUID userId, RecipientAccessLevel requiredLevel) {
        // Không phân biệt "không tồn tại" và "không có quan hệ" → người ngoài không dò được hồ sơ nào tồn tại.
        RecipientManager membership = recipientManagerRepository.findMembership(recipientId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        if (!membership.getAccessLevel().covers(requiredLevel)) {
            throw new AppException(ErrorCode.RECIPIENT_ACCESS_DENIED);
        }
        return membership;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecipientAccessLevel> findAccessLevel(UUID recipientId, UUID userId) {
        return recipientManagerRepository.findMembership(recipientId, userId)
                .map(RecipientManager::getAccessLevel);
    }
}

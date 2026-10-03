package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.client.IdentityClient;
import mss301.carerecipientservice.client.dto.IdentityApiResponse;
import mss301.carerecipientservice.client.dto.IdentityUserBatchRequest;
import mss301.carerecipientservice.client.dto.IdentityUserResponse;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.service.IdentityUserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IdentityUserServiceImpl implements IdentityUserService {

    IdentityClient identityClient;

    @Override
    public IdentityUserResponse getActiveCustomer(String email) {
        IdentityApiResponse<IdentityUserResponse> response;
        try {
            response = identityClient.getByEmail(email);
        } catch (AppException e) {
            // Gộp "không tồn tại" vào cùng mã với "bị khóa / không phải CUSTOMER" để không dò được email.
            if (e.getErrorCode() == ErrorCode.IDENTITY_USER_NOT_FOUND) {
                throw new AppException(ErrorCode.MANAGER_USER_INVALID);
            }
            throw e;
        }
        IdentityUserResponse user = response == null ? null : response.getResult();
        if (user == null || user.getId() == null) {
            throw new AppException(ErrorCode.IDENTITY_UNAVAILABLE);
        }
        boolean isActiveCustomer = IdentityUserResponse.STATUS_ACTIVE.equals(user.getStatus())
                && user.getRoles() != null
                && user.getRoles().contains(IdentityUserResponse.ROLE_CUSTOMER);
        if (!isActiveCustomer) {
            throw new AppException(ErrorCode.MANAGER_USER_INVALID);
        }
        return user;
    }

    @Override
    public Map<UUID, IdentityUserResponse> getProfiles(Collection<UUID> userIds) {
        Map<UUID, IdentityUserResponse> profiles = new HashMap<>();
        List<UUID> distinctIds = new ArrayList<>(new LinkedHashSet<>(userIds));
        // Identity giới hạn 100 id / lần batch.
        for (int from = 0; from < distinctIds.size(); from += IdentityUserBatchRequest.MAX_SIZE) {
            List<UUID> chunk = distinctIds.subList(from, Math.min(from + IdentityUserBatchRequest.MAX_SIZE, distinctIds.size()));
            IdentityApiResponse<List<IdentityUserResponse>> response = identityClient.list(
                    IdentityUserBatchRequest.builder().userIds(chunk).build());
            if (response == null || response.getResult() == null) {
                continue;
            }
            for (IdentityUserResponse user : response.getResult()) {
                profiles.put(user.getId(), user);
            }
        }
        return profiles;
    }
}

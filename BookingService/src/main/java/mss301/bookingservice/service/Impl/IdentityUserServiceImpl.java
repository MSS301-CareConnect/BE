package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.client.IdentityClient;
import mss301.bookingservice.client.dto.IdentityUserBatchRequest;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.service.IdentityUserService;
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
    public Map<UUID, IdentityUserResponse> getProfiles(Collection<UUID> userIds) {
        Map<UUID, IdentityUserResponse> profiles = new HashMap<>();
        List<UUID> distinctIds = new ArrayList<>(new LinkedHashSet<>(userIds));
        // Identity giới hạn 100 id / lần batch; một trang booking tối đa 100 dòng nên thường chỉ một lời gọi.
        for (int from = 0; from < distinctIds.size(); from += IdentityUserBatchRequest.MAX_SIZE) {
            List<UUID> chunk = distinctIds.subList(from, Math.min(from + IdentityUserBatchRequest.MAX_SIZE, distinctIds.size()));
            RemoteApiResponse<List<IdentityUserResponse>> response = identityClient.list(
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

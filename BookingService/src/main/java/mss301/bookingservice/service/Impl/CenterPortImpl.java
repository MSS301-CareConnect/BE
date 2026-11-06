package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.client.ProviderClient;
import mss301.bookingservice.client.dto.ProviderCenterResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.CenterPort;
import mss301.bookingservice.util.VietnamAddressMatcher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CenterPortImpl implements CenterPort {

    ProviderClient providerClient;

    @Override
    public ProviderCenterResponse getCenter(UUID centerId) {
        RemoteApiResponse<ProviderCenterResponse> response = providerClient.getCenter(centerId);
        ProviderCenterResponse center = response == null ? null : response.getResult();
        if (center == null) {
            throw new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
        return center;
    }

    @Override
    public void checkBookable(UUID centerId) {
        ProviderCenterResponse center = getCenter(centerId);
        if (!ProviderCenterResponse.STATUS_ACTIVE.equals(center.getCenterStatus())) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
    }

    @Override
    public void checkServiceArea(UUID centerId, String careProvince) {
        ProviderCenterResponse center = getCenter(centerId);
        if (!ProviderCenterResponse.STATUS_ACTIVE.equals(center.getCenterStatus())) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        if (!VietnamAddressMatcher.isSameServiceArea(center.getAddress(), careProvince)) {
            throw new AppException(ErrorCode.CENTER_OUT_OF_SERVICE_AREA);
        }
    }

    @Override
    public void checkExists(UUID centerId) {
        RemoteApiResponse<ProviderCenterResponse> response = providerClient.getCenter(centerId);
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
    }
}

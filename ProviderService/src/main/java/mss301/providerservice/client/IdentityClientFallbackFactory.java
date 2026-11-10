package mss301.providerservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class IdentityClientFallbackFactory implements FallbackFactory<IdentityClient> {

    @Override
    public IdentityClient create(Throwable cause) {
        return new IdentityClient() {
            @Override
            public APIResponse<IdentityUserResponse> get(UUID userId) {
                if (cause instanceof AppException appException) {
                    throw appException;
                }
                log.warn("IdentityService unavailable ({})", cause.getClass().getSimpleName());
                throw new AppException(ErrorCode.IDENTITY_UNAVAILABLE);
            }
        };
    }
}

package mss301.bookingservice.client;

import feign.Request;
import feign.Response;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// HTTP lỗi từ service được gọi → ErrorCode của Booking (404 nghiệp vụ, còn lại 503 — không lộ chi tiết).
class ClientErrorDecoderTests {

    static Response response(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "http://test/internal/x", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(status)
                .request(request)
                .headers(Map.of())
                .build();
    }

    static ErrorCode decode(feign.codec.ErrorDecoder decoder, String methodKey, int status) {
        Exception exception = decoder.decode(methodKey, response(status));
        assertThat(exception).isInstanceOf(AppException.class);
        return ((AppException) exception).getErrorCode();
    }

    @Test
    void providerNotFoundDependsOnMethod() {
        ProviderErrorDecoder decoder = new ProviderErrorDecoder();
        assertThat(decode(decoder, "ProviderClient#getCenter(UUID)", 404)).isEqualTo(ErrorCode.CENTER_NOT_FOUND);
        assertThat(decode(decoder, "ProviderClient#getCaregiver(UUID)", 404)).isEqualTo(ErrorCode.CAREGIVER_NOT_FOUND);
        assertThat(decode(decoder, "ProviderClient#getCenter(UUID)", 401)).isEqualTo(ErrorCode.PROVIDER_UNAVAILABLE);
        assertThat(decode(decoder, "ProviderClient#getCaregiver(UUID)", 500)).isEqualTo(ErrorCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    void careRecipientNotFoundIsBusinessErrorOthersUnavailable() {
        CareRecipientErrorDecoder decoder = new CareRecipientErrorDecoder();
        assertThat(decode(decoder, "CareRecipientClient#getSnapshot(UUID)", 404)).isEqualTo(ErrorCode.RECIPIENT_NOT_FOUND);
        assertThat(decode(decoder, "CareRecipientClient#getSnapshot(UUID)", 403)).isEqualTo(ErrorCode.CARE_RECIPIENT_UNAVAILABLE);
        assertThat(decode(decoder, "CareRecipientClient#checkAccess(UUID,UUID,String)", 503))
                .isEqualTo(ErrorCode.CARE_RECIPIENT_UNAVAILABLE);
    }

    @Test
    void identityErrorsAreAlwaysUnavailable() {
        IdentityErrorDecoder decoder = new IdentityErrorDecoder();
        assertThat(decode(decoder, "IdentityClient#list(IdentityUserBatchRequest)", 404)).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE);
        assertThat(decode(decoder, "IdentityClient#list(IdentityUserBatchRequest)", 401)).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE);
    }
}

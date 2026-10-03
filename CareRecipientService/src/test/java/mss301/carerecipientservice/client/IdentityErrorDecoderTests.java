package mss301.carerecipientservice.client;

import feign.Request;
import feign.Response;
import mss301.carerecipientservice.client.dto.IdentityApiResponse;
import mss301.carerecipientservice.client.dto.IdentityUserBatchRequest;
import mss301.carerecipientservice.client.dto.IdentityUserResponse;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityErrorDecoderTests {

    private final IdentityErrorDecoder identityErrorDecoder = new IdentityErrorDecoder();
    private final IdentityClientFallbackFactory identityClientFallbackFactory = new IdentityClientFallbackFactory();

    @Test
    void notFoundBecomesBusinessError() {
        Exception exception = identityErrorDecoder.decode("IdentityClient#getByEmail(String)", response(404));
        assertThat(exception).isInstanceOf(AppException.class);
        assertThat(((AppException) exception).getErrorCode()).isEqualTo(ErrorCode.IDENTITY_USER_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 500, 502, 503})
    void otherStatusesBecomeUnavailable(int status) {
        Exception exception = identityErrorDecoder.decode("IdentityClient#list(IdentityUserBatchRequest)", response(status));
        assertThat(((AppException) exception).getErrorCode()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE);
        assertThat(((AppException) exception).getErrorCode().getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void batchFallbackReturnsEmptyList() {
        IdentityClient fallback = identityClientFallbackFactory.create(new ConnectException("refused"));
        IdentityApiResponse<List<IdentityUserResponse>> response = fallback.list(
                IdentityUserBatchRequest.builder().userIds(List.of(UUID.randomUUID())).build());
        assertThat(response.getResult()).isEmpty();
    }

    @Test
    void lookupFallbackNeverGuesses() {
        IdentityClient connectionFailure = identityClientFallbackFactory.create(new ConnectException("refused"));
        assertThatThrownBy(() -> connectionFailure.getByEmail("a@example.test"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
        IdentityClient notFound = identityClientFallbackFactory.create(new AppException(ErrorCode.IDENTITY_USER_NOT_FOUND));
        assertThatThrownBy(() -> notFound.get(UUID.randomUUID()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDENTITY_USER_NOT_FOUND));
    }

    private Response response(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "http://identity/internal/users/x", Map.of(),
                null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(Map.of())
                .build();
    }
}

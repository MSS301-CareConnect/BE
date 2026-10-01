package mss301.identityservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthenticationResponse {
    boolean authenticated;

    // Chỉ web: token CSRF mới sau login/refresh để client cập nhật header X-XSRF-TOKEN.
    String csrfToken;

    // Chỉ mobile: token trả trong body; web nhận qua HttpOnly cookie.
    @ToString.Exclude
    String accessToken;

    @ToString.Exclude
    String refreshToken;

    long expiresInSeconds;
}

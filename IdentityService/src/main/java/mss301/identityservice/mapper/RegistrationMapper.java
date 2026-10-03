package mss301.identityservice.mapper;

import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.response.OtpResponse;
import org.mapstruct.Mapper;

import java.util.Map;

@Mapper(config = MapStructConfig.class)
public interface RegistrationMapper {
    // Dữ liệu đăng ký chờ xác thực trên Redis (key = tên property của RegisterRequest) → request.
    RegisterRequest toRequest(Map<String, String> data);

    OtpResponse toOtpResponse(String email, long expiresInSeconds, long resendAfterSeconds);
}

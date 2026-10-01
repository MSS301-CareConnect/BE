package mss301.identityservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // BCrypt chỉ dùng 72 byte đầu — giới hạn tính theo byte UTF-8, không theo số ký tự Java.
    public static final int BCRYPT_MAX_BYTES = 72;

    private static final String STRONG_PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || (!exceedsBcryptLimit(value) && value.matches(STRONG_PASSWORD_PATTERN));
    }

    public static boolean exceedsBcryptLimit(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}

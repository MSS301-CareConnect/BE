package mss301.identityservice.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

// Giá trị claim token_type — là contract với các service khác, không đổi chữ.
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    String value;
}

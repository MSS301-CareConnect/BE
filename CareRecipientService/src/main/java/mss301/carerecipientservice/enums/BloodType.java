package mss301.carerecipientservice.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

// Nhóm máu: API và DB đều dùng ký hiệu "A+"… (BloodTypeConverter) — đồng bộ CHECK care_recipients.blood_type.
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum BloodType {
    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-"),
    UNKNOWN("UNKNOWN");

    @JsonValue
    String value;

    @JsonCreator
    public static BloodType fromValue(String value) {
        for (BloodType bloodType : values()) {
            if (bloodType.value.equals(value)) {
                return bloodType;
            }
        }
        throw new IllegalArgumentException("Unknown blood type");
    }
}

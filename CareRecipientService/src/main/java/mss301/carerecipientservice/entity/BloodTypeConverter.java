package mss301.carerecipientservice.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import mss301.carerecipientservice.enums.BloodType;

// Lưu ký hiệu "A+"… thay cho tên hằng enum — khớp CHECK care_recipients.blood_type.
@Converter(autoApply = true)
public class BloodTypeConverter implements AttributeConverter<BloodType, String> {

    @Override
    public String convertToDatabaseColumn(BloodType bloodType) {
        return bloodType == null ? null : bloodType.getValue();
    }

    @Override
    public BloodType convertToEntityAttribute(String value) {
        return value == null ? null : BloodType.fromValue(value);
    }
}

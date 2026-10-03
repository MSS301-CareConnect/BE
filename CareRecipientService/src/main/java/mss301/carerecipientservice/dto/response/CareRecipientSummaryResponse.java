package mss301.carerecipientservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.Gender;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareRecipientSummaryResponse {
    UUID id;

    String fullName;

    LocalDate dateOfBirth;

    Integer age;

    Gender gender;

    String avatarUrl;

    String district;

    String province;

    RecipientStatus status;

    // Chỉ có ở danh sách của customer: quyền của người gọi + người gọi có phải quản lý chính
    RecipientAccessLevel myAccessLevel;

    Boolean primaryManager;

    Instant createdAt;
}

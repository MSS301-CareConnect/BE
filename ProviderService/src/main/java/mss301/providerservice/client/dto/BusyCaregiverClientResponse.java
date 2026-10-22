package mss301.providerservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Bản sao response của BookingService busy-caregivers (C5.1): caregiver có buổi đang giữ chỗ giao bất kỳ khung nào.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusyCaregiverClientResponse {
    List<UUID> busyCaregiverIds;
}

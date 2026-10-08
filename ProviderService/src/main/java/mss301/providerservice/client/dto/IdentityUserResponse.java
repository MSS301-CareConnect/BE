package mss301.providerservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IdentityUserResponse {
    UUID id;
    String email;
    String fullName;
    String phoneNumber;
    String status;
    Set<String> roles;
}

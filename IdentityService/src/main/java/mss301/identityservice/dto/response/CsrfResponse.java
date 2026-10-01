package mss301.identityservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CsrfResponse {
    // Giá trị gửi lại trong header headerName (X-XSRF-TOKEN) ở mọi request ghi của web.
    String token;
    String headerName;
}

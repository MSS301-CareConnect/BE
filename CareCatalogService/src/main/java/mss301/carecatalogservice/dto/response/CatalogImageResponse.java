package mss301.carecatalogservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Ảnh minh họa vừa tải lên. imageUrl là đường dẫn cố định của Catalog (không hết hạn) để lưu vào CatalogItemRequest.imageUrl.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CatalogImageResponse {
    UUID mediaFileId;
    String imageUrl;
    String contentType;
    Long fileSize;
}

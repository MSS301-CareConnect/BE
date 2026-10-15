package mss301.providerservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.MediaFileType;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaClientResponse {
    UUID mediaFileId;
    String originalFileName;
    String contentType;
    MediaFileType fileType;
    Long fileSize;
}

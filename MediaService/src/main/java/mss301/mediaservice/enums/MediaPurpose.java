package mss301.mediaservice.enums;

import lombok.Getter;

import java.util.Set;

@Getter
public enum MediaPurpose {
    USER_AVATAR(5, Set.of("image/jpeg", "image/png", "image/webp")),
    CARE_CENTER_LOGO(5, Set.of("image/jpeg", "image/png", "image/webp")),
    CAREGIVER_AVATAR(5, Set.of("image/jpeg", "image/png", "image/webp")),
    CAREGIVER_CERTIFICATE(10, Set.of("application/pdf", "image/jpeg", "image/png")),
    CARE_RECIPIENT_AVATAR(5, Set.of("image/jpeg", "image/png", "image/webp")),
    CARE_RECIPIENT_DOCUMENT(10, Set.of("application/pdf", "image/jpeg", "image/png")),
    CARE_REPORT_IMAGE(10, Set.of("image/jpeg", "image/png", "image/webp")),
    CARE_REPORT_VIDEO(100, Set.of("video/mp4", "video/webm", "video/quicktime")),
    INCIDENT_EVIDENCE(10, Set.of("image/jpeg", "image/png", "image/webp")),
    FEEDBACK_ATTACHMENT(10, Set.of("image/jpeg", "image/png", "image/webp")),
    CHAT_ATTACHMENT(20,
            Set.of("application/pdf", "image/jpeg", "image/png", "image/webp", "video/mp4"));

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final long maximumBytes;
    private final Set<String> allowedContentTypes;

    MediaPurpose(long maximumMegabytes, Set<String> allowedContentTypes) {
        this.maximumBytes = maximumMegabytes * BYTES_PER_MEGABYTE;
        this.allowedContentTypes = allowedContentTypes;
    }
}

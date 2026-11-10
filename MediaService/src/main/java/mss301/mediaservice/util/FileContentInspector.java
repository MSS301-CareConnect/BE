package mss301.mediaservice.util;

import mss301.mediaservice.exception.AppException;
import mss301.mediaservice.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class FileContentInspector {

    private static final int SIGNATURE_LENGTH = 16;

    private FileContentInspector() {
    }

    public static void check(MultipartFile file, String contentType) {
        byte[] signature = new byte[SIGNATURE_LENGTH];
        int bytesRead;
        try (InputStream inputStream = file.getInputStream()) {
            bytesRead = inputStream.read(signature);
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (bytesRead < 4 || !matches(contentType, signature, bytesRead)) {
            throw new AppException(ErrorCode.FILE_CONTENT_MISMATCH);
        }
    }

    public static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            default -> throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        };
    }

    private static boolean matches(String contentType, byte[] data, int length) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> unsigned(data[0]) == 0xFF && unsigned(data[1]) == 0xD8
                    && unsigned(data[2]) == 0xFF;
            case "image/png" -> unsigned(data[0]) == 0x89 && data[1] == 0x50
                    && data[2] == 0x4E && data[3] == 0x47;
            case "image/webp" -> length >= 12 && ascii(data, 0, "RIFF") && ascii(data, 8, "WEBP");
            case "application/pdf" -> ascii(data, 0, "%PDF");
            case "video/mp4", "video/quicktime" -> length >= 8 && ascii(data, 4, "ftyp");
            case "video/webm" -> unsigned(data[0]) == 0x1A && unsigned(data[1]) == 0x45
                    && unsigned(data[2]) == 0xDF && unsigned(data[3]) == 0xA3;
            default -> false;
        };
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        if (data.length < offset + expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (data[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}

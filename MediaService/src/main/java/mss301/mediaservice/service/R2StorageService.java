package mss301.mediaservice.service;

import java.io.InputStream;

public interface R2StorageService {

    // Upload nội dung file lên R2, trả về object key.
    String upload(String objectKey, String contentType, long contentLength, InputStream inputStream);

    // Sinh URL tải có thời hạn cho object.
    String createDownloadUrl(String objectKey);

    // Xóa object trên R2.
    void delete(String objectKey);
}

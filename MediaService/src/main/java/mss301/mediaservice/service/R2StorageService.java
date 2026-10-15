package mss301.mediaservice.service;

import java.io.InputStream;

public interface R2StorageService {

    String upload(String objectKey, String contentType, long contentLength, InputStream inputStream);

    String createDownloadUrl(String objectKey);

    void delete(String objectKey);
}

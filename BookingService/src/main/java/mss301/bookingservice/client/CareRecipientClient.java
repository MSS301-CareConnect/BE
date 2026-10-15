package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.RecipientAccessResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * API nội bộ của CareRecipientService (/internal/care-recipients/**): kiểm quyền khách trên hồ sơ và lấy snapshot
 * lúc đặt lịch. Dùng qua CareRecipientDirectoryService, không gọi trực tiếp.
 */
@FeignClient(name = "care-recipient", url = "${booking.care-recipient.url}",
        configuration = CareRecipientClientConfig.class, fallbackFactory = CareRecipientClientFallbackFactory.class)
public interface CareRecipientClient {

    @GetMapping("/internal/care-recipients/{recipientId}/access")
    RemoteApiResponse<RecipientAccessResponse> checkAccess(@PathVariable("recipientId") UUID recipientId,
                                                           @RequestParam("userId") UUID userId,
                                                           @RequestParam("minLevel") String minLevel);

    @GetMapping("/internal/care-recipients/{recipientId}/snapshot")
    RemoteApiResponse<RecipientSnapshot> getSnapshot(@PathVariable("recipientId") UUID recipientId);
}

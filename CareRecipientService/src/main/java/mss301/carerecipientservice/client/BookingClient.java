package mss301.carerecipientservice.client;

import mss301.carerecipientservice.client.dto.RecipientActivityResponse;
import mss301.carerecipientservice.client.dto.BookingApiResponse;
import mss301.carerecipientservice.client.dto.CaregiverAccessResponse;
import mss301.carerecipientservice.client.dto.NotificationCreateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "booking", url = "${care-recipient.booking.url}",
        configuration = BookingClientConfig.class, fallbackFactory = BookingClientFallbackFactory.class)
public interface BookingClient {

    // Hỏi BookingService caregiver có được giao chăm sóc người bệnh này không.
    @GetMapping("/api/v1/bookings/caregiver-access")
    BookingApiResponse<CaregiverAccessResponse> checkCaregiverAccess(@RequestParam("caregiverUserId") UUID caregiverUserId,
                                                                     @RequestParam("recipientId") UUID recipientId);

    // M-07: số booking chưa kết thúc của người bệnh — chặn tạm ngưng / lưu trữ hồ sơ khi còn lịch chạy.
    @GetMapping("/api/v1/bookings/recipient-activity")
    BookingApiResponse<RecipientActivityResponse> getRecipientActivity(@RequestParam("recipientId") UUID recipientId);

    // Thông báo in-app (BookingService giữ hộp thư thông báo) — dùng báo lời mời quản lý hồ sơ (C-04).
    @PostMapping("/api/v1/notifications")
    BookingApiResponse<Object> createNotification(@RequestBody NotificationCreateRequest request);
}

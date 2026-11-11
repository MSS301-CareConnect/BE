package mss301.aiservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.dto.request.StrokeScreeningRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.StrokeScreeningResponse;
import mss301.aiservice.service.StrokeScreeningService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/stroke-screening")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Stroke Screening", description = "API hỗ trợ sàng lọc đặc điểm liên quan đến tiền sử đột quỵ")
public class StrokeScreeningController {
    StrokeScreeningService strokeScreeningService;

    @PostMapping("/predict")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Sàng lọc đặc điểm liên quan đến đột quỵ",
            description = """
                    Chỉ CUSTOMER đã đăng nhập được sử dụng.
                    Kết quả được tạo từ mô hình Logistic Regression kết hợp SMOTE.
                    Đây là công cụ hỗ trợ sàng lọc, không phải chẩn đoán y khoa.
                    """)
    public APIResponse<StrokeScreeningResponse> predict(@Valid @RequestBody StrokeScreeningRequest request) {
        StrokeScreeningResponse response = strokeScreeningService.screen(request);
        return APIResponse.success("Stroke screening completed successfully", response);
    }
}

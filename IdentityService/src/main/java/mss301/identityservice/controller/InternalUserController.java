package mss301.identityservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.request.UserBatchRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.InternalUserResponse;
import mss301.identityservice.service.InternalUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Ngoại lệ của rule #1a (AGENTS.md): API service-to-service, khác cơ chế xác thực (X-Internal-Secret) với AccountController.
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal - Users", description = "Tra cứu tài khoản cho service khác. Chỉ gọi nội bộ với header X-Internal-Secret; gateway chặn /internal/**.")
public class InternalUserController {
    InternalUserService internalUserService;

    @GetMapping("/{userId}")
    public APIResponse<InternalUserResponse> get(@PathVariable UUID userId) {
        return internalUserService.get(userId);
    }

    // required = false: thiếu email do service trả EMAIL_REQUIRED (400) thay vì lỗi binding chung.
    @GetMapping("/by-email")
    public APIResponse<InternalUserResponse> getByEmail(@RequestParam(required = false) String email) {
        return internalUserService.getByEmail(email);
    }

    @PostMapping("/batch")
    public APIResponse<List<InternalUserResponse>> list(@Valid @RequestBody UserBatchRequest request) {
        return internalUserService.list(request);
    }
}

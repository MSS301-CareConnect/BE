package mss301.identityservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.service.JwtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "JWKS", description = "Public key để các service khác (Resource Server) xác minh JWT do IdentityService ký.")
public class JwksController {
    JwtService jwtService;

    // Ngoại lệ có chủ đích của rule "luôn trả APIResponse": giao thức JWKS yêu cầu {"keys":[...]} ở top-level.
    @GetMapping("/.well-known/jwks.json")
    @SecurityRequirements({})
    public Map<String, Object> get() {
        return jwtService.publicKeys();
    }
}

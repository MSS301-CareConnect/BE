package mss301.identityservice.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.entity.ServiceClient;
import mss301.identityservice.mapper.ServiceClientMapper;
import mss301.identityservice.repository.ServiceClientRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Seed idempotent bảng service_clients từ IDENTITY_SERVICE_CLIENTS ("booking:<bcrypt>,payment:<bcrypt>").
 * Client đã có thì cập nhật hash khi đổi secret; client không còn trong env KHÔNG bị xóa/tắt tự động.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ServiceClientInitializer implements ApplicationRunner {

    static Pattern CLIENT_ID_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,49}");
    static String SEEDED_DESCRIPTION = "Seeded from IDENTITY_SERVICE_CLIENTS";
    static Pattern BCRYPT_PATTERN = Pattern.compile("\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    IdentityProperties identityProperties;
    ServiceClientRepository serviceClientRepository;
    ServiceClientMapper serviceClientMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String configured = identityProperties.getServiceClients();
        if (configured == null || configured.isBlank()) {
            return;
        }
        for (String entry : configured.split(",")) {
            if (entry.isBlank()) {
                continue;
            }
            int separator = entry.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("IDENTITY_SERVICE_CLIENTS entries must be <clientId>:<bcrypt>");
            }
            String clientId = entry.substring(0, separator).trim();
            // Compose bắt escape $ thành $$ trong .env; BCrypt không bao giờ chứa "$$" nên chuẩn hóa an toàn.
            String secretHash = entry.substring(separator + 1).trim().replace("$$", "$");
            if (!CLIENT_ID_PATTERN.matcher(clientId).matches() || !BCRYPT_PATTERN.matcher(secretHash).matches()) {
                throw new IllegalArgumentException("Invalid service client entry for " + clientId + " (secret must be a BCrypt hash)");
            }
            seedClient(clientId, secretHash);
        }
    }

    private void seedClient(String clientId, String secretHash) {
        Optional<ServiceClient> existing = serviceClientRepository.findByClientIdAndDeletedAtIsNull(clientId);
        if (existing.isEmpty()) {
            serviceClientRepository.save(serviceClientMapper.toServiceClient(clientId, secretHash, SEEDED_DESCRIPTION, true));
            log.info("Seeded service client {}", clientId);
            return;
        }
        ServiceClient serviceClient = existing.get();
        if (!serviceClient.getSecretHash().equals(secretHash)) {
            serviceClient.setSecretHash(secretHash);
            log.info("Rotated secret of service client {}", clientId);
        }
    }
}

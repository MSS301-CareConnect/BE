import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

// Sinh secret ngẫu nhiên cho một service client + BCrypt hash để đặt vào IDENTITY_SERVICE_CLIENTS.
// Chạy qua scripts/New-ServiceClient.ps1 (cần classpath có spring-security-crypto).
class HashServiceSecret {
    public static void main(String[] args) {
        if (args.length != 1 || !args[0].matches("[a-z][a-z0-9-]{1,49}")) {
            throw new IllegalArgumentException("Usage: HashServiceSecret <clientId>");
        }
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String hash = new BCryptPasswordEncoder().encode(secret);
        System.out.println("Secret (đặt vào <SERVICE>_SERVICE_CLIENT_SECRET của service gọi): " + secret);
        System.out.println("Entry IDENTITY_SERVICE_CLIENTS (chạy trực tiếp):  " + args[0] + ":" + hash);
        System.out.println("Entry IDENTITY_SERVICE_CLIENTS (Docker Compose):  " + args[0] + ":" + hash.replace("$", "$$"));
    }
}

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// Sinh SQL tạo tài khoản CUSTOMER cho e2e (đăng ký thật cần OTP email Brevo — không tự động hóa được).
// Dùng (Run-E2E.ps1 tự gọi): java -cp "<spring-security-crypto.jar>;<commons-logging.jar>" SeedCustomers.java <password> <email:fullName>...
// Chỉ dùng cho DB e2e riêng — KHÔNG chạy vào DB dev/production.
public class SeedCustomers {
    public static void main(String[] args) {
        String hash = new BCryptPasswordEncoder().encode(args[0]);
        StringBuilder sql = new StringBuilder("BEGIN;\n");
        for (int i = 1; i < args.length; i++) {
            String[] parts = args[i].split(":", 2);
            sql.append("INSERT INTO users (user_id, email, password, full_name, phone_number, user_status, authz_version, created_at, updated_at) VALUES (gen_random_uuid(), '")
                    .append(parts[0]).append("', '").append(hash).append("', '").append(parts[1])
                    .append("', '090000000").append(i).append("', 'ACTIVE', 0, now(), now());\n");
            sql.append("INSERT INTO user_roles (user_id, role_id, assigned_at) SELECT u.user_id, r.role_id, now() FROM users u, roles r WHERE u.email = '")
                    .append(parts[0]).append("' AND r.role_code = 'CUSTOMER';\n");
        }
        sql.append("COMMIT;\n");
        System.out.print(sql);
    }
}

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// Sinh SQL tạo tài khoản cho e2e (đăng ký thật cần OTP email Brevo; tài khoản provision cần OTP đặt mật khẩu — không
// tự động hóa được).
// Dùng (Run-E2E.ps1 tự gọi): java -cp "<spring-security-crypto.jar>;<commons-logging.jar>" SeedCustomers.java <password> <account>...
//   account = email:fullName                      → CUSTOMER, user_id ngẫu nhiên
//           = email:fullName:ROLE:userId:centerId → role tùy chọn, user_id cố định ('-' = ngẫu nhiên), center_id ('-' = NULL)
// Chỉ dùng cho DB e2e riêng — KHÔNG chạy vào DB dev/production.
public class SeedCustomers {
    private static final String NONE = "-";

    public static void main(String[] args) {
        String hash = new BCryptPasswordEncoder().encode(args[0]);
        StringBuilder sql = new StringBuilder("BEGIN;\n");
        for (int i = 1; i < args.length; i++) {
            String[] parts = args[i].split(":", 5);
            String role = parts.length > 2 ? parts[2] : "CUSTOMER";
            String userId = parts.length > 3 && !NONE.equals(parts[3]) ? "'" + parts[3] + "'" : "gen_random_uuid()";
            String centerId = parts.length > 4 && !NONE.equals(parts[4]) ? "'" + parts[4] + "'" : "NULL";
            sql.append("INSERT INTO users (user_id, email, password, full_name, phone_number, user_status, center_id, authz_version, created_at, updated_at) VALUES (")
                    .append(userId).append(", '").append(parts[0]).append("', '").append(hash).append("', '").append(parts[1])
                    .append("', '090000000").append(i).append("', 'ACTIVE', ").append(centerId).append(", 0, now(), now());\n");
            sql.append("INSERT INTO user_roles (user_id, role_id, assigned_at) SELECT u.user_id, r.role_id, now() FROM users u, roles r WHERE u.email = '")
                    .append(parts[0]).append("' AND r.role_code = '").append(role).append("';\n");
        }
        sql.append("COMMIT;\n");
        System.out.print(sql);
    }
}

package mss301.bookingservice.util;

import java.text.Normalizer;

public final class VietnamAddressMatcher {

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private VietnamAddressMatcher() {
    }

    // Bỏ dấu tiếng Việt.
    public static String removeDiacritics(String input) {
        if (input == null) {
            return "";
        }
        String str = input.replace("đ", "d").replace("Đ", "d");
        String nfd = Normalizer.normalize(str, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // Chuẩn hóa tên đơn vị hành chính để so khớp (bỏ dấu, tiền tố).
    public static String normalizeUnit(String input) {
        if (input == null) {
            return "";
        }
        String normalized = removeDiacritics(input).toLowerCase();
        normalized = normalized.replaceAll("[-–—_,.]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();

        // Loại bỏ tiền tố đơn vị hành chính
        normalized = normalized.replaceAll("^(thanh pho|tinh|phuong|xa|quan|huyen|thi xa|thi tran|tp|tt|q|h)\\s+", "");
        normalized = normalized.replaceAll("\\s+", " ").trim();

        if (normalized.equals("hcm") || normalized.equals("tphcm") || normalized.equals("sai gon")
                || normalized.equals("ho chi minh city")) {
            return "ho chi minh";
        }
        if (normalized.equals("hn") || normalized.equals("hanoi")) {
            return "ha noi";
        }
        return normalized;
    }

    /**
     * Kiểm tra xem địa chỉ của trung tâm và tỉnh/thành phố chăm sóc có cùng khu vực địa bàn hay không.
     */
    public static boolean isSameServiceArea(String centerAddress, String careProvince) {
        if (centerAddress == null || centerAddress.isBlank() || careProvince == null || careProvince.isBlank()) {
            return true; // Không đủ dữ liệu thì không chặn oan
        }
        String targetProvince = normalizeUnit(careProvince);
        if (targetProvince.isBlank()) {
            return true;
        }

        // 1. Phân tách theo dấu phẩy, kiểm tra từng phân đoạn địa chỉ của trung tâm
        String[] segments = centerAddress.split(",");
        for (String segment : segments) {
            String normalizedSegment = normalizeUnit(segment);
            if (normalizedSegment.equals(targetProvince) || normalizedSegment.endsWith(" " + targetProvince)) {
                return true;
            }
        }

        // 2. Kiểm tra nếu chuỗi centerAddress chứa targetProvince kèm ranh giới khoảng trắng
        String normalizedCenterAddress = " " + removeDiacritics(centerAddress).toLowerCase()
                .replaceAll("[-–—_,.]", " ").replaceAll("\\s+", " ").trim() + " ";
        String targetWord = " " + targetProvince + " ";
        if (normalizedCenterAddress.contains(targetWord)) {
            return true;
        }

        // 3. Xử lý alias cho các thành phố lớn
        if (targetProvince.equals("ho chi minh")) {
            if (normalizedCenterAddress.contains(" hcm ") || normalizedCenterAddress.contains(" tphcm ")
                    || normalizedCenterAddress.contains(" sai gon ")) {
                return true;
            }
        }
        if (targetProvince.equals("ha noi")) {
            if (normalizedCenterAddress.contains(" hn ") || normalizedCenterAddress.contains(" hanoi ")) {
                return true;
            }
        }

        return false;
    }
}

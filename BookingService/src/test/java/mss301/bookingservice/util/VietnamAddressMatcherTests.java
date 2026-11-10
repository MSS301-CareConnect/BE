package mss301.bookingservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VietnamAddressMatcherTests {

    @Test
    @DisplayName("Cùng ở Hà Nội: khớp qua các định dạng khác nhau")
    void sameArea_HaNoi() {
        assertTrue(VietnamAddressMatcher.isSameServiceArea("45 Kim Mã, Ba Đình, Hà Nội", "Thành phố Hà Nội"));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("45 Kim Mã, Ba Đình, TP. Hà Nội", "Hà Nội"));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("Số 10 Tràng Thi, Hoàn Kiếm, TP Hà Nội", "Thành phố Hà Nội"));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("Hà Nội", "Hà Nội"));
    }

    @Test
    @DisplayName("Cùng ở TP. Hồ Chí Minh: khớp qua các alias HCM, TPHCM, Sài Gòn")
    void sameArea_HoChiMinh() {
        assertTrue(VietnamAddressMatcher.isSameServiceArea("1 Lê Lợi, Phường Bến Nghé, Quận 1, TP Hồ Chí Minh", "Thành phố Hồ Chí Minh"));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("123 Nguyễn Thị Minh Khai, Quận 3, TP.HCM", "Hồ Chí Minh"));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("Quận 1, Sài Gòn", "Thành phố Hồ Chí Minh"));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("Thành phố Hồ Chí Minh", "TP. Hồ Chí Minh"));
    }

    @Test
    @DisplayName("Khác tỉnh/thành: Hà Nội vs Hồ Chí Minh, Đà Nẵng vs Cần Thơ")
    void differentArea() {
        assertFalse(VietnamAddressMatcher.isSameServiceArea("45 Kim Mã, Ba Đình, Hà Nội", "Thành phố Hồ Chí Minh"));
        assertFalse(VietnamAddressMatcher.isSameServiceArea("1 Lê Lợi, Quận 1, TP. Hồ Chí Minh", "Thành phố Hà Nội"));
        assertFalse(VietnamAddressMatcher.isSameServiceArea("Hải Châu, Đà Nẵng", "Cần Thơ"));
        assertFalse(VietnamAddressMatcher.isSameServiceArea("Ninh Kiều, Cần Thơ", "Đồng Nai"));
    }

    @Test
    @DisplayName("Địa chỉ trống hoặc null thì fallback an toàn")
    void emptyOrNull() {
        assertTrue(VietnamAddressMatcher.isSameServiceArea(null, "Thành phố Hà Nội"));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("", "Thành phố Hà Nội"));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("45 Kim Mã, Hà Nội", null));
        assertTrue(VietnamAddressMatcher.isSameServiceArea("45 Kim Mã, Hà Nội", ""));
    }
}

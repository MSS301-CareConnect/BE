package mss301.bookingservice.util;

import java.math.BigDecimal;

// Khoảng cách đường chim bay giữa hai tọa độ (haversine) — đủ chính xác cho bán kính check-in vài trăm mét.
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private GeoDistance() {
    }

    // Khoảng cách (mét) giữa hai tọa độ theo công thức haversine.
    public static int meters(BigDecimal fromLatitude, BigDecimal fromLongitude, BigDecimal toLatitude,
                             BigDecimal toLongitude) {
        double fromLat = Math.toRadians(fromLatitude.doubleValue());
        double toLat = Math.toRadians(toLatitude.doubleValue());
        double deltaLat = toLat - fromLat;
        double deltaLng = Math.toRadians(toLongitude.doubleValue() - fromLongitude.doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(fromLat) * Math.cos(toLat) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return (int) Math.round(2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a))));
    }
}

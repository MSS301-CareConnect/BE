"""PaymentService GIẢ cho e2e — PaymentService chưa có trong repo (chủ module khác, port 8087, dải lỗi 6xxx).

Chỉ trả đủ các API BookingService đang gọi qua ApiGateway để luồng booking chạy được:
  GET  /api/v1/payments/me/outstanding     → khách không nợ (C2.6, BK-02 chặn đặt khi nợ)
  GET  /api/v1/payment-policies/effective  → cọc 30% (C2.1, chỉ để hiển thị khi báo giá)
  POST /api/v1/refunds, /api/v1/remaining-invoices → nhận lệnh từ booking_outbox (C2.3), không làm gì
  GET  /actuator/health                    → {"status":"UP"} cho Run-E2E.ps1 chờ khởi động
Không kiểm token, không lưu gì, không phải contract chính thức — khi có PaymentService thật thì Run-E2E.ps1 chạy JAR
thật thay cho stub này. Dùng: python payment_stub.py <port>
"""
import ipaddress
import json
import socket
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

POLICY_ID = "00000000-0000-4000-8000-0000000000e2"
GET_RESULTS = {
    "/actuator/health": None,
    "/api/v1/payments/me/outstanding": {"hasDebt": False},
    "/api/v1/payment-policies/effective": {"policyId": POLICY_ID, "depositPercent": 30},
}
ACCEPTED_POSTS = ("/api/v1/refunds", "/api/v1/remaining-invoices")


class PaymentStubHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/actuator/health":
            self.send_json(200, {"status": "UP"})
        elif path in GET_RESULTS:
            self.send_json(200, {"code": 200, "message": "E2E stub", "result": GET_RESULTS[path]})
        else:
            self.send_json(404, {"code": 404, "message": "E2E stub: không có API này"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
        path = self.path.split("?", 1)[0]
        if path in ACCEPTED_POSTS:
            self.send_json(200, {"code": 200, "message": "E2E stub"})
        else:
            self.send_json(404, {"code": 404, "message": "E2E stub: không có API này"})

    def send_json(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        # Chỉ log method + path (không log header — có token).
        sys.stdout.write("%s %s\n" % (self.command, self.path.split("?", 1)[0]))
        sys.stdout.flush()


class LoopbackDualStackServer(ThreadingHTTPServer):
    """Nghe cả ::1 và 127.0.0.1: "localhost" trên Windows (PowerShell, Java) thử IPv6 trước — chỉ bind IPv4 thì treo."""
    address_family = socket.AF_INET6

    def server_bind(self):
        self.socket.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 0)
        super().server_bind()

    def verify_request(self, request, client_address):
        # Stub không có xác thực thật → chỉ nhận kết nối từ chính máy này.
        host = client_address[0].removeprefix("::ffff:")
        return ipaddress.ip_address(host).is_loopback


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8087
    # "::" + tắt V6ONLY nhận cả IPv4-mapped; verify_request chỉ cho loopback.
    LoopbackDualStackServer(("::", port), PaymentStubHandler).serve_forever()

"""E2E CareConnect qua ApiGateway. Không in token / secret / giá trị cookie.

Thường được gọi bởi Run-E2E.ps1 (dựng hạ tầng riêng, chạy 3 JAR, seed tài khoản). Chạy tay khi stack đã sẵn sàng:
    python scripts/e2e/e2e.py <phase>      phase = a | b | c | d | e | f
  a: luồng mobile (Bearer)  b: luồng web (cookie + CSRF)  c: chặn /internal/**  d: fallback khi IdentityService tắt
  e: luồng booking (customer → trung tâm → đặt cọc → caregiver xem lịch / hồ sơ)  f: booking khi CareRecipientService tắt
Phase c đọc secret nội bộ từ IdentityService/.env và CareRecipientService/.env. Phase d giả định Identity ĐÃ tắt, phase f
giả định CareRecipient ĐÃ tắt. Phase e/f đọc E2E_BOOKING_INTERNAL_SECRET, E2E_CENTER_ID, E2E_CAREGIVER_ID,
E2E_CAREGIVER_USER_ID do Run-E2E.ps1 đặt (seed riêng cho DB e2e).
URL đổi được bằng env E2E_GATEWAY_URL, E2E_IDENTITY_URL, E2E_CARE_RECIPIENT_URL, E2E_BOOKING_URL.
"""
import datetime
import http.cookiejar
import json
import os
import re
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GATEWAY = os.environ.get("E2E_GATEWAY_URL", "http://localhost:8080")
IDENTITY = os.environ.get("E2E_IDENTITY_URL", "http://localhost:8081")
CARE = os.environ.get("E2E_CARE_RECIPIENT_URL", "http://localhost:8082")
BOOKING = os.environ.get("E2E_BOOKING_URL", "http://localhost:8085")
# Tài khoản CUSTOMER do Run-E2E.ps1 seed vào DB e2e riêng (đăng ký thật cần OTP email — không tự động hóa).
PASSWORD = "E2e-Pass#2026"
OWNER = "e2e.owner@careconnect.test"
RELATIVE = "e2e.relative@careconnect.test"
THIRD = "e2e.third@careconnect.test"
MANAGER = "e2e.manager@careconnect.test"
CAREGIVER = "e2e.caregiver@careconnect.test"
CAREGIVER_TWO = "e2e.caregiver2@careconnect.test"
STATE = os.path.join(tempfile.gettempdir(), "careconnect-e2e-state.json")
BOOKING_STATE = os.path.join(tempfile.gettempdir(), "careconnect-e2e-booking-state.json")

results = []


def check(name, condition, detail=""):
    results.append((name, bool(condition)))
    print(("PASS " if condition else "FAIL ") + name + (" -- " + detail if detail and not condition else ""))


def read_env(path):
    values = {}
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value.strip()
    return values


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, url, body=None, headers=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(url, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        for key, value in (headers or {}).items():
            request.add_header(key, value)
        try:
            response = self.opener.open(request, timeout=15)
            status, raw, response_headers = response.status, response.read(), response.headers
        except urllib.error.HTTPError as error:
            status, raw, response_headers = error.code, error.read(), error.headers
        try:
            payload = json.loads(raw) if raw else None
        except ValueError:
            payload = {"raw": raw[:200].decode(errors="replace")}
        return status, payload, response_headers

    def cookie(self, name):
        for cookie in self.jar:
            if cookie.name == name:
                return cookie
        return None


def bearer(token):
    return {"Authorization": "Bearer " + token}


def mobile_login(client, email):
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/auth/mobile/login", {"email": email, "password": PASSWORD})
    check("mobile login qua gateway: " + email, status == 200 and body["result"].get("accessToken"), str(status))
    return body["result"]["accessToken"]


def phase_a():
    client = Client()
    owner_token = mobile_login(client, OWNER)
    relative_token = mobile_login(client, RELATIVE)

    status, body, headers = client.call("POST", GATEWAY + "/api/v1/care-recipients", {
        "fullName": "Nguyễn Văn E2E", "dateOfBirth": "1940-03-15", "gender": "MALE", "bloodType": "A+",
        "addressLine": "1 Nguyễn Huệ", "district": "Quận 1", "province": "TP.HCM",
        "latitude": 10.7769, "longitude": 106.7009, "allergies": "Penicillin",
        "chronicConditions": "Tăng huyết áp", "mobilityLevel": "WHEELCHAIR", "relationshipType": "CHILD"}, bearer(owner_token))
    check("a1 tạo hồ sơ người bệnh", status == 200 and body["result"]["myAccessLevel"] == "OWNER", str(body))
    check("a1 response có X-Request-Id", headers.get("X-Request-Id") is not None)
    recipient_id = body["result"]["profile"]["id"]
    base = GATEWAY + "/api/v1/care-recipients/" + recipient_id

    status, body, _ = client.call("POST", base + "/medications", {
        "medicationName": "Amlodipine", "dosage": "5mg", "frequency": "1 lần/ngày", "timeOfDay": ["08:00"],
        "route": "ORAL", "startDate": "2026-01-01"}, bearer(owner_token))
    check("a2 thêm thuốc", status == 200 and body["result"]["active"] is True, str(body))
    status, body, _ = client.call("POST", base + "/emergency-contacts", {
        "fullName": "Con trai E2E", "relationship": "Con", "phoneNumber": "0912345678", "primary": True}, bearer(owner_token))
    check("a3 thêm liên hệ khẩn cấp chính", status == 200 and body["result"]["primary"] is True, str(body))

    status, body, _ = client.call("POST", base + "/managers",
                                  {"email": RELATIVE.upper(), "relationshipType": "SIBLING", "accessLevel": "EDITOR"},
                                  bearer(owner_token))
    check("a4 thêm người thân bằng email (Feign → Identity)", status == 200 and body["result"]["fullName"] == "E2E Relative",
          str(body))
    relative_manager_id = body["result"]["id"]
    status, body, _ = client.call("POST", base + "/managers",
                                  {"email": THIRD, "relationshipType": "RELATIVE", "accessLevel": "VIEWER"}, bearer(owner_token))
    check("a4b thêm người thân thứ hai (VIEWER)", status == 200, str(body))
    third_manager_id = body["result"]["id"]
    status, body, _ = client.call("POST", base + "/managers",
                                  {"email": "khong-ton-tai@careconnect.test", "relationshipType": "OTHER",
                                   "accessLevel": "VIEWER"}, bearer(owner_token))
    check("a4c email không phải CUSTOMER hoạt động → 400 2401", status == 400 and body["code"] == 2401, str(body))

    status, body, _ = client.call("GET", base + "/managers", headers=bearer(owner_token))
    managers = body["result"] if status == 200 else []
    check("a5 danh sách managers có profile từ Identity",
          len(managers) == 3 and all(m["profileAvailable"] and m["email"] for m in managers), str(body))

    status, body, _ = client.call("GET", base, headers=bearer(relative_token))
    check("a5b người thân (EDITOR) xem được hồ sơ", status == 200 and body["result"]["myAccessLevel"] == "EDITOR", str(status))

    status, body, _ = client.call("PATCH", base + "/managers/" + relative_manager_id, {"accessLevel": "OWNER"}, bearer(owner_token))
    check("a6 đổi quyền người thân → OWNER", status == 200 and body["result"]["accessLevel"] == "OWNER", str(body))
    status, body, _ = client.call("PATCH", base + "/managers/" + relative_manager_id + "/primary", headers=bearer(owner_token))
    check("a7 chuyển quản lý chính", status == 200 and body["result"]["primary"] is True, str(body))
    status, body, _ = client.call("GET", base + "/managers", headers=bearer(owner_token))
    primaries = [m for m in body["result"] if m["primary"]]
    check("a7b đúng một quản lý chính sau khi chuyển", len(primaries) == 1 and primaries[0]["id"] == relative_manager_id)
    status, body, _ = client.call("DELETE", base + "/managers/" + relative_manager_id, headers=bearer(owner_token))
    check("a7c không gỡ được quản lý chính → 409 2405", status == 409 and body["code"] == 2405, str(body))

    status, body, _ = client.call("DELETE", base + "/managers/" + third_manager_id, headers=bearer(owner_token))
    check("a8 gỡ người thân", status == 200, str(body))
    status, body, _ = client.call("GET", base + "/managers", headers=bearer(owner_token))
    check("a8b còn 2 người quản lý", status == 200 and len(body["result"]) == 2)

    status, body, _ = client.call("DELETE", base, headers=bearer(owner_token))
    check("a9 archive hồ sơ (OWNER)", status == 200, str(body))
    status, body, _ = client.call("GET", base, headers=bearer(owner_token))
    check("a9b hồ sơ đã archive → 404 2100", status == 404 and body["code"] == 2100, str(body))

    # Hồ sơ thứ hai dùng cho phase c/d (vẫn ACTIVE).
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/care-recipients",
                                  {"fullName": "Hồ sơ cho fallback", "relationshipType": "SELF"}, bearer(owner_token))
    second_id = body["result"]["profile"]["id"]
    client.call("POST", GATEWAY + "/api/v1/care-recipients/" + second_id + "/managers",
                {"email": RELATIVE, "relationshipType": "CHILD", "accessLevel": "VIEWER"}, bearer(owner_token))
    json.dump({"ownerToken": owner_token, "recipientId": second_id, "archivedId": recipient_id}, open(STATE, "w"))


def phase_b():
    client = Client()
    status, body, headers = client.call("GET", GATEWAY + "/api/v1/auth/csrf")
    csrf = body["result"]["token"] if status == 200 else None
    check("b1 GET /api/v1/auth/csrf qua gateway", status == 200 and csrf and client.cookie("XSRF-TOKEN"), str(status))
    set_cookie = headers.get_all("Set-Cookie") or []
    check("b1b cookie XSRF-TOKEN có Path=/", any(c.startswith("XSRF-TOKEN=") and "Path=/" in c for c in set_cookie), str(set_cookie and "present"))

    status, body, headers = client.call("POST", GATEWAY + "/api/v1/auth/login", {"email": OWNER, "password": PASSWORD})
    check("b2a login web thiếu CSRF → 403 1111", status == 403 and body["code"] == 1111, str(body))
    status, body, headers = client.call("POST", GATEWAY + "/api/v1/auth/login", {"email": OWNER, "password": PASSWORD},
                                        {"X-XSRF-TOKEN": csrf})
    check("b2 login web (cookie + CSRF)", status == 200 and "accessToken" not in (body["result"] or {}), str(status))
    set_cookie = headers.get_all("Set-Cookie") or []
    for name in ("access_token", "refresh_token", "XSRF-TOKEN"):
        check("b2b Set-Cookie " + name + " Path=/ HttpOnly",
              any(c.startswith(name + "=") and "Path=/" in c and "HttpOnly" in c for c in set_cookie))
    csrf = body["result"]["csrfToken"]

    status, body, _ = client.call("GET", GATEWAY + "/api/v1/users/me")
    check("b3 GET /users/me bằng cookie qua gateway", status == 200 and body["result"]["email"] == OWNER, str(status))
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/care-recipients", {"fullName": "Web", "relationshipType": "CHILD"})
    check("b4a ghi CareRecipient bằng cookie thiếu CSRF → 403 2002", status == 403 and body["code"] == 2002, str(body))
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/care-recipients", {"fullName": "Web", "relationshipType": "CHILD"},
                                  {"X-XSRF-TOKEN": csrf})
    check("b4 ghi CareRecipient bằng cookie + CSRF của Identity", status == 200, str(body))

    old_access = client.cookie("access_token").value
    status, body, headers = client.call("POST", GATEWAY + "/api/v1/auth/refresh", headers={"X-XSRF-TOKEN": csrf})
    check("b5 refresh web qua gateway", status == 200 and client.cookie("access_token").value != old_access, str(status))
    csrf = body["result"]["csrfToken"]
    status, body, _ = client.call("GET", GATEWAY + "/api/v1/care-recipients")
    check("b6 gọi API bằng cookie mới sau refresh", status == 200 and body["result"]["totalElements"] >= 1, str(status))

    current_access = client.cookie("access_token").value
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/auth/logout", headers={"X-XSRF-TOKEN": csrf})
    check("b7 logout web qua gateway", status == 200 and client.cookie("access_token") is None, str(status))
    status, body, _ = client.call("GET", GATEWAY + "/api/v1/users/me", headers=bearer(current_access))
    check("b8 access token cũ bị thu hồi tại Identity → 401", status == 401, str(status))


def phase_c():
    client = Client()
    state = json.load(open(STATE))
    identity_env = read_env(ROOT / "IdentityService" / ".env")
    care_env = read_env(ROOT / "CareRecipientService" / ".env")
    for path in ("/internal/users/batch", "/api/v1/internal/users/x", "/api/v1/users/internal/x",
                 "/internal/care-recipients/" + state["recipientId"] + "/snapshot"):
        status, body, _ = client.call("GET", GATEWAY + path, headers=bearer(state["ownerToken"]))
        check("c1 gateway chặn " + path + " → 404", status == 404 and body["code"] == 404, str(status))
    status, body, _ = client.call("GET", IDENTITY + "/internal/users/by-email?email=" + OWNER)
    check("c2 Identity /internal không secret → 401", status == 401 and body["code"] == 401, str(status))
    status, body, _ = client.call("GET", IDENTITY + "/internal/users/by-email?email=" + OWNER,
                                  headers={"X-Internal-Secret": "wrong"})
    check("c2b Identity /internal sai secret → 401", status == 401, str(status))
    status, body, _ = client.call("GET", IDENTITY + "/internal/users/by-email?email=" + OWNER,
                                  headers={"X-Internal-Secret": identity_env["IDENTITY_INTERNAL_SECRET"]})
    check("c2c Identity /internal đúng secret → 200", status == 200 and body["result"]["email"] == OWNER, str(status))
    snapshot = CARE + "/internal/care-recipients/" + state["recipientId"] + "/snapshot"
    status, body, _ = client.call("GET", snapshot)
    check("c3 CareRecipient /internal không secret → 401", status == 401, str(status))
    status, body, _ = client.call("GET", snapshot, headers=bearer(state["ownerToken"]))
    check("c3b CareRecipient /internal với JWT người dùng (không secret) → 401", status == 401, str(status))
    status, body, _ = client.call("GET", snapshot, headers={"X-Internal-Secret": care_env["CARE_RECIPIENT_INTERNAL_SECRET"]})
    check("c3c CareRecipient snapshot đúng secret → 200", status == 200 and body["result"]["recipientId"] == state["recipientId"],
          str(status))


def phase_d():
    client = Client()
    state = json.load(open(STATE))
    url = GATEWAY + "/api/v1/care-recipients/" + state["recipientId"] + "/managers"
    status, body, _ = client.call("GET", url, headers=bearer(state["ownerToken"]))
    managers = body["result"] if status == 200 else []
    check("d1 Identity tắt: danh sách managers vẫn 200", status == 200 and len(managers) == 2, str(body))
    check("d2 Identity tắt: chỉ thiếu profile (profileAvailable=false)",
          managers and all(not m["profileAvailable"] and m.get("fullName") is None and m["accessLevel"] for m in managers))
    status, body, _ = client.call("POST", url, {"email": THIRD, "relationshipType": "OTHER", "accessLevel": "VIEWER"},
                                  bearer(state["ownerToken"]))
    check("d3 Identity tắt: thêm người thân (cần xác minh) → 503 2500", status == 503 and body["code"] == 2500, str(body))
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/auth/mobile/login", {"email": OWNER, "password": PASSWORD})
    check("d4 Identity tắt: route Identity qua gateway → 503 9001", status == 503 and body["code"] == 9001, str(body))


def weekly_booking(recipient_id, monday, start_time="08:00", end_time="10:00", caregiver_id=None, days=(1, 3, 5),
                   weeks=2):
    body = {
        "recipientId": recipient_id, "centerId": os.environ["E2E_CENTER_ID"], "scheduleType": "WEEKLY",
        "startDate": monday.isoformat(), "endDate": (monday + datetime.timedelta(days=7 * weeks - 1)).isoformat(),
        "startTime": start_time, "endTime": end_time, "daysOfWeek": list(days),
        "customerNote": "E2E booking", "services": [{"careServiceId": "11111111-1111-4111-8111-111111111111"}]}
    if caregiver_id:
        body["caregiverId"] = caregiver_id
    return body


def next_monday(min_days_ahead):
    day = datetime.date.today() + datetime.timedelta(days=min_days_ahead)
    return day + datetime.timedelta(days=(7 - day.weekday()) % 7)


def phase_e():
    client = Client()
    owner_token = mobile_login(client, OWNER)
    third_token = mobile_login(client, THIRD)
    manager_token = mobile_login(client, MANAGER)
    caregiver_token = mobile_login(client, CAREGIVER)
    caregiver_two_token = mobile_login(client, CAREGIVER_TWO)
    internal = {"X-Internal-Secret": os.environ["E2E_BOOKING_INTERNAL_SECRET"]}
    caregiver_id = os.environ["E2E_CAREGIVER_ID"]

    status, body, _ = client.call("POST", GATEWAY + "/api/v1/care-recipients", {
        "fullName": "Bà E2E Booking", "dateOfBirth": "1942-07-01", "gender": "FEMALE", "addressLine": "5 Tràng Tiền",
        "district": "Hoàn Kiếm", "province": "Hà Nội", "allergies": "Hải sản", "relationshipType": "CHILD"},
        bearer(owner_token))
    check("e1 tạo hồ sơ người bệnh cho booking", status == 200, str(status))
    recipient_id = body["result"]["profile"]["id"]
    monday = next_monday(14)

    status, body, _ = client.call("POST", GATEWAY + "/api/v1/bookings", weekly_booking(recipient_id, monday),
                                  {**bearer(owner_token), "Idempotency-Key": "e2e-weekly-1"})
    booking = body["result"] if status == 200 else {}
    check("e2 tạo booking WEEKLY qua gateway → PENDING", status == 200 and booking.get("status") == "PENDING", str(body))
    check("e2b mã booking BK-yyyyMMdd-XXXXXX", re.fullmatch(r"BK-\d{8}-[A-Z2-9]{6}", booking.get("bookingCode", "")) is not None)
    check("e2c snapshot người bệnh + địa chỉ lưu trong booking",
          booking.get("recipientSnapshot", {}).get("allergies") == "Hải sản"
          and booking.get("serviceAddress", "").startswith("5 Tràng Tiền"), str(booking.get("serviceAddress")))
    check("e2d giá NULL (chưa nối Catalog)", booking.get("totalAmount") is None and booking["services"][0].get("unitPrice") is None)
    booking_id = booking["id"]

    status, body, _ = client.call("GET", GATEWAY + "/api/v1/bookings/" + booking_id + "/occurrences", headers=bearer(owner_token))
    occurrences = body["result"] if status == 200 else []
    check("e3 6 buổi Thứ 2/4/6 trong 2 tuần", len(occurrences) == 6, str(len(occurrences)))
    check("e3b 08:00 giờ VN lưu 01:00 UTC", occurrences and occurrences[0]["startAt"].endswith("T01:00:00Z"),
          occurrences and occurrences[0]["startAt"])

    status, body, _ = client.call("POST", GATEWAY + "/api/v1/bookings", weekly_booking(recipient_id, monday),
                                  {**bearer(owner_token), "Idempotency-Key": "e2e-weekly-1"})
    check("e4 bấm lại cùng Idempotency-Key → cùng booking", status == 200 and body["result"]["id"] == booking_id, str(status))

    status, body, _ = client.call("POST", GATEWAY + "/api/v1/bookings", weekly_booking(recipient_id, monday), bearer(third_token))
    check("e5 đặt cho hồ sơ không có quyền → 404 5601", status == 404 and body["code"] == 5601, str(body))
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/bookings",
                                  weekly_booking(recipient_id, monday, "09:00", "11:00"), bearer(owner_token))
    check("e6 trùng lịch người bệnh → 409 5216", status == 409 and body["code"] == 5216, str(body))

    status, body, _ = client.call("GET", GATEWAY + "/api/v1/center/bookings", headers=bearer(manager_token))
    items = body["result"]["items"] if status == 200 else []
    check("e7 trung tâm thấy booking kèm tên khách (Identity batch)",
          any(i["id"] == booking_id and i["customer"]["fullName"] == "E2E Owner" for i in items), str(status))

    status, body, _ = client.call("POST", GATEWAY + "/api/v1/center/bookings/" + booking_id + "/accept",
                                  {"caregiverId": caregiver_id}, bearer(manager_token))
    check("e8 trung tâm chấp nhận + phân công caregiver (Provider) → AWAITING_DEPOSIT",
          status == 200 and body["result"]["status"] == "AWAITING_DEPOSIT"
          and body["result"]["caregiverUserId"] == os.environ["E2E_CAREGIVER_USER_ID"], str(body))

    status, body, _ = client.call("GET", GATEWAY + "/api/v1/caregiver/care-recipients/" + recipient_id, headers=bearer(caregiver_token))
    check("e9 chưa đặt cọc: caregiver chưa xem được hồ sơ → 404", status == 404, str(status))

    status, body, _ = client.call("POST", GATEWAY + "/api/v1/internal/bookings/" + booking_id + "/deposit-paid", {}, bearer(owner_token))
    check("e10 gateway chặn /internal/bookings → 404", status == 404, str(status))
    status, body, _ = client.call("POST", BOOKING + "/internal/bookings/" + booking_id + "/deposit-paid", {})
    check("e10b deposit-paid không secret → 401", status == 401, str(status))
    status, body, _ = client.call("POST", BOOKING + "/internal/bookings/" + booking_id + "/deposit-paid",
                                  {"paymentReference": "E2E-PAY-1"}, internal)
    check("e11 deposit-paid (nội bộ) → CONFIRMED", status == 200 and body["result"]["status"] == "CONFIRMED", str(body))

    status, body, _ = client.call("GET", GATEWAY + "/api/v1/bookings/" + booking_id, headers=bearer(owner_token))
    check("e12 khách xem chi tiết: CONFIRMED, buổi đã có caregiver",
          status == 200 and body["result"]["status"] == "CONFIRMED"
          and all(o["caregiverId"] == caregiver_id for o in body["result"]["occurrences"]), str(status))

    params = "?from=" + datetime.date.today().isoformat() + "&to=" + (datetime.date.today() + datetime.timedelta(days=40)).isoformat()
    status, body, _ = client.call("GET", GATEWAY + "/api/v1/caregiver/occurrences" + params, headers=bearer(caregiver_token))
    schedule = body["result"] if status == 200 else []
    check("e13 caregiver thấy 6 buổi được giao", len(schedule) == 6, str(status))
    status, body, _ = client.call("GET", GATEWAY + "/api/v1/caregiver/occurrences/" + schedule[0]["id"], headers=bearer(caregiver_token))
    check("e13b chi tiết buổi kèm snapshot người bệnh",
          status == 200 and body["result"]["recipientSnapshot"]["fullName"] == "Bà E2E Booking", str(status))
    status, body, _ = client.call("GET", GATEWAY + "/api/v1/caregiver/occurrences/" + schedule[0]["id"], headers=bearer(caregiver_two_token))
    check("e13c caregiver khác không thấy buổi → 404", status == 404, str(status))

    status, body, _ = client.call("GET", GATEWAY + "/api/v1/caregiver/care-recipients/" + recipient_id, headers=bearer(caregiver_token))
    check("e14 caregiver được phân công xem hồ sơ qua CareRecipient (hỏi Booking) → 200",
          status == 200 and body["result"]["allergies"] == "Hải sản", str(body))
    status, body, _ = client.call("GET", GATEWAY + "/api/v1/caregiver/care-recipients/" + recipient_id, headers=bearer(caregiver_two_token))
    check("e14b caregiver không được phân công → 404 2100", status == 404 and body["code"] == 2100, str(body))

    status, body, _ = client.call("POST", GATEWAY + "/api/v1/care-recipients",
                                  {"fullName": "Ông E2E Hai", "relationshipType": "CHILD"}, bearer(owner_token))
    second_recipient = body["result"]["profile"]["id"]
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/bookings",
                                  weekly_booking(second_recipient, monday, "09:30", "10:30", caregiver_id), bearer(owner_token))
    check("e15 trùng lịch caregiver → 409 5215", status == 409 and body["code"] == 5215, str(body))

    later = monday + datetime.timedelta(days=21)
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/bookings", weekly_booking(second_recipient, later), bearer(owner_token))
    cancel_id = body["result"]["id"] if status == 200 else None
    check("e16 tạo booking thứ hai (để hủy)", status == 200, str(body))
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/bookings/" + str(cancel_id) + "/cancel",
                                  {"reason": "Đổi kế hoạch"}, bearer(owner_token))
    check("e16b khách hủy → CANCELLED, mọi buổi CANCELLED",
          status == 200 and body["result"]["status"] == "CANCELLED"
          and all(o["status"] == "CANCELLED" for o in body["result"]["occurrences"]), str(status))

    status, body, _ = client.call("GET", GATEWAY + "/api/v1/bookings", headers=bearer(owner_token))
    json.dump({"ownerToken": owner_token, "recipientId": second_recipient,
               "bookingCount": body["result"]["totalElements"] if status == 200 else -1}, open(BOOKING_STATE, "w"))


def phase_f():
    client = Client()
    state = json.load(open(BOOKING_STATE))
    monday = next_monday(70)
    status, body, _ = client.call("POST", GATEWAY + "/api/v1/bookings", weekly_booking(state["recipientId"], monday),
                                  bearer(state["ownerToken"]))
    check("f1 CareRecipient tắt: tạo booking → 503 5600", status == 503 and body["code"] == 5600, str(body))
    status, body, _ = client.call("GET", GATEWAY + "/api/v1/bookings", headers=bearer(state["ownerToken"]))
    check("f2 không tạo booking thiếu snapshot (số booking không đổi)",
          status == 200 and body["result"]["totalElements"] == state["bookingCount"], str(status))


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    {"a": phase_a, "b": phase_b, "c": phase_c, "d": phase_d, "e": phase_e, "f": phase_f}[sys.argv[1]]()
    failed = [name for name, ok in results if not ok]
    print("SUMMARY phase %s: %d passed, %d failed" % (sys.argv[1], len(results) - len(failed), len(failed)))
    sys.exit(1 if failed else 0)

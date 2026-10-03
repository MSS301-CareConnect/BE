"""E2E CareConnect qua ApiGateway. Không in token / secret / giá trị cookie.

Thường được gọi bởi Run-E2E.ps1 (dựng hạ tầng riêng, chạy 3 JAR, seed tài khoản). Chạy tay khi stack đã sẵn sàng:
    python scripts/e2e/e2e.py <phase>      phase = a | b | c | d
  a: luồng mobile (Bearer)  b: luồng web (cookie + CSRF)  c: chặn /internal/**  d: fallback khi IdentityService tắt
Phase c đọc secret nội bộ từ IdentityService/.env và CareRecipientService/.env. Phase d giả định Identity ĐÃ tắt.
URL đổi được bằng env E2E_GATEWAY_URL, E2E_IDENTITY_URL, E2E_CARE_RECIPIENT_URL.
"""
import http.cookiejar
import json
import os
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GATEWAY = os.environ.get("E2E_GATEWAY_URL", "http://localhost:8080")
IDENTITY = os.environ.get("E2E_IDENTITY_URL", "http://localhost:8081")
CARE = os.environ.get("E2E_CARE_RECIPIENT_URL", "http://localhost:8082")
# Tài khoản CUSTOMER do Run-E2E.ps1 seed vào DB e2e riêng (đăng ký thật cần OTP email — không tự động hóa).
PASSWORD = "E2e-Pass#2026"
OWNER = "e2e.owner@careconnect.test"
RELATIVE = "e2e.relative@careconnect.test"
THIRD = "e2e.third@careconnect.test"
STATE = os.path.join(tempfile.gettempdir(), "careconnect-e2e-state.json")

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


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    {"a": phase_a, "b": phase_b, "c": phase_c, "d": phase_d}[sys.argv[1]]()
    failed = [name for name, ok in results if not ok]
    print("SUMMARY phase %s: %d passed, %d failed" % (sys.argv[1], len(results) - len(failed), len(failed)))
    sys.exit(1 if failed else 0)

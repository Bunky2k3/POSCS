#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Chạy thật các test case hộp đen kiểm được qua HTTP, ghi kết quả ra JSON.

Không thay thế người test: chỉ tự động hoá phần kiểm được bằng request/phản hồi
(đăng nhập, kiểm tra dữ liệu đầu vào, phân quyền 403, endpoint JSON, chống path
traversal). Những test case phải nhìn giao diện thì để người chạy tay.

Yêu cầu: ứng dụng đang chạy tại BASE, CSDL là bản dùng-một-lần đã nạp
db/schema.sql cộng tài khoản mẫu (xem README).

Chạy:  python tools/testdoc/run_blackbox.py [BASE_URL]
Kết quả: tools/testdoc/blackbox_results.json  {"<mã test case>": {...}}
"""

import json
import pathlib
import re
import sys
import time

import requests

BASE = "http://localhost:8099/POSCS"
OUT = pathlib.Path(__file__).with_name("blackbox_results.json")

ACCOUNTS = {
    "admin": ("admin", "Admin@123"),
    "sales": ("sale01", "Sales@123"),
    "tech": ("tech01", "Tech@123"),
    "cskh": ("cskh01", "Cskh@123"),
    "tech2": ("tech02", "Tech@123"),
    "locked": ("locked01", "Sales@123"),
}

results = {}

# Hậu tố duy nhất cho mỗi lần chạy, để các ràng buộc UNIQUE (mã số thuế, CCCD,
# số điện thoại) không đụng nhau giữa các lượt chạy trên cùng một CSDL.
RUN = time.strftime("%H%M%S")


def record(case_id, status, actual, note=""):
    results[case_id] = {"status": status, "actual": actual, "note": note}
    mark = {"Đạt": "OK  ", "Trượt": "FAIL", "N/A": "N/A "}[status]
    print("  %s %-22s %s" % (mark, case_id, actual[:96]))


# Token CSRF nằm trong session và được AuthenticationFilter gắn vào mọi request;
# lấy nó từ một trang có render ô ẩn csrfToken. /changePassword.jsp là trang mọi
# vai trò đã đăng nhập đều mở được (dashboard.jsp không có form nên không có ô này).
def utf8(response):
    """requests đoán ISO-8859-1 khi header thiếu charset -> chuỗi tiếng Việt không khớp."""
    response.encoding = "utf-8"
    return response.text


def csrf(session, page="/changePassword.jsp"):
    html = utf8(session.get(BASE + page))
    m = re.search(r'name="csrfToken"\s+value="([^"]+)"', html)
    if not m:
        raise AssertionError("Khong lay duoc csrfToken tu %s" % page)
    return m.group(1)


def login(role, base=BASE):
    """Trả về (session, response cuối) — không tự theo redirect."""
    s = requests.Session()
    token = csrf(s, "/login.jsp")
    user, pwd = ACCOUNTS[role]
    r = s.post(base + "/login", allow_redirects=False,
               data={"username": user, "password": pwd, "csrfToken": token})
    return s, r


def logged_in(session):
    r = session.get(BASE + "/dashboard", allow_redirects=False)
    return r.status_code == 200


def expect(case_id, condition, actual, note=""):
    record(case_id, "Đạt" if condition else "Trượt", actual, note)


# --------------------------------------------------------------------------
# Đăng nhập / đăng xuất
# --------------------------------------------------------------------------
def test_login():
    print("\n[Login]")
    s, r = login("admin")
    expect("TC_LOGIN_001",
           r.status_code == 302 and "dashboard" in (r.headers.get("Location") or "")
           and logged_in(s),
           "HTTP %s -> %s; /dashboard trả 200 sau đăng nhập"
           % (r.status_code, r.headers.get("Location")))

    s = requests.Session()
    token = csrf(s, "/login.jsp")
    r = s.post(BASE + "/login", allow_redirects=False,
               data={"username": "admin@poscs.vn", "password": "Admin@123",
                     "csrfToken": token})
    expect("TC_LOGIN_002",
           r.status_code == 302 and "dashboard" in (r.headers.get("Location") or ""),
           "Đăng nhập bằng email công ty: HTTP %s -> %s"
           % (r.status_code, r.headers.get("Location")))

    for cid, user, pwd, label in [
            ("TC_LOGIN_003", "", "", "bỏ trống cả hai ô"),
            ("TC_LOGIN_004", "admin", "", "bỏ trống mật khẩu")]:
        s = requests.Session()
        token = csrf(s, "/login.jsp")
        r = s.post(BASE + "/login", allow_redirects=False,
                   data={"username": user, "password": pwd, "csrfToken": token})
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "login" in loc and "error" in loc,
               "%s: HTTP %s -> %s" % (label, r.status_code, loc))

    for cid, user, pwd, label in [
            ("TC_LOGIN_005", "khongtontai", "Admin@123", "tài khoản không tồn tại"),
            ("TC_LOGIN_006", "admin", "SaiMatKhau1", "mật khẩu sai")]:
        s = requests.Session()
        token = csrf(s, "/login.jsp")
        r = s.post(BASE + "/login", allow_redirects=False,
                   data={"username": user, "password": pwd, "csrfToken": token})
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "invalid_credentials" in loc,
               "%s: -> %s" % (label, loc))

    # Hai trường hợp trên phải trả về ĐÚNG một thông báo lỗi.
    def err_of(user, pwd):
        s = requests.Session()
        t = csrf(s, "/login.jsp")
        r = s.post(BASE + "/login", allow_redirects=False,
                   data={"username": user, "password": pwd, "csrfToken": t})
        loc = r.headers.get("Location") or ""
        return loc.split("error=")[-1].split("&")[0]

    # Không tách thành mã test case riêng: đây chính là phần "giống hệt trường hợp
    # sai mật khẩu" trong kết quả mong đợi của TC_LOGIN_005, nên ghi đè kết quả
    # của ca đó cho đầy đủ.
    e1, e2 = err_of("khongtontai2", "x"), err_of("admin", "SaiMatKhau2")
    same = e1 == e2
    prev = results["TC_LOGIN_005"]
    record("TC_LOGIN_005", "Đạt" if prev["status"] == "Đạt" and same else "Trượt",
           prev["actual"] + " | lỗi khi sai tài khoản = lỗi khi sai mật khẩu: %r vs %r"
           % (e1, e2))

    record("TC_LOGIN_007", "N/A",
           "Bỏ qua trong lượt tự động: khoá IP 15 phút sẽ chặn các test case sau",
           "Chạy tay cuối buổi")
    record("TC_LOGIN_008", "N/A", "Phụ thuộc TC_LOGIN_007", "Chạy tay")
    record("TC_LOGIN_009", "N/A", "Phụ thuộc TC_LOGIN_007", "Chạy tay")

    s = requests.Session()
    token = csrf(s, "/login.jsp")
    r = s.post(BASE + "/login", allow_redirects=False,
               data={"username": "locked01", "password": "Sales@123",
                     "csrfToken": token})
    loc = r.headers.get("Location") or ""
    expect("TC_LOGIN_010", "account_inactive" in loc,
           "Tài khoản bị khoá: -> %s" % loc)

    s = requests.Session()
    csrf(s, "/login.jsp")
    before = s.cookies.get("JSESSIONID")
    t = csrf(s, "/login.jsp")
    s.post(BASE + "/login", allow_redirects=False,
           data={"username": "admin", "password": "Admin@123", "csrfToken": t})
    after = s.cookies.get("JSESSIONID")
    expect("TC_LOGIN_011", before != after,
           "JSESSIONID trước %s... / sau %s..." % (str(before)[:12], str(after)[:12]))

    s = requests.Session()
    t = csrf(s, "/login.jsp")
    r = s.post(BASE + "/login", allow_redirects=False,
               data={"username": "' OR 1=1 --", "password": "abc", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_LOGIN_012", r.status_code == 302 and "invalid_credentials" in loc,
           "SQL injection: -> %s (không đăng nhập được, không lỗi 500)" % loc)

    s = requests.Session()
    t = csrf(s, "/login.jsp")
    r = s.post(BASE + "/login", allow_redirects=False,
               data={"username": "<script>alert(1)</script>", "password": "abc",
                     "csrfToken": t})
    follow = s.get(BASE + (r.headers.get("Location") or "/login.jsp")
                   .replace(BASE, ""), allow_redirects=True)
    expect("TC_LOGIN_013",
           r.status_code == 302 and "<script>alert(1)</script>" not in utf8(follow),
           "XSS: chuỗi script không xuất hiện nguyên văn trong HTML trả về")

    s = requests.Session()
    r = s.get(BASE + "/customer?action=list", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_LOGIN_014", r.status_code == 302 and "login" in loc,
           "Chưa đăng nhập vào /customer: HTTP %s -> %s" % (r.status_code, loc))


def test_logout():
    print("\n[Logout]")
    s, _ = login("admin")
    r = s.get(BASE + "/login?action=logout", allow_redirects=False)
    still = s.get(BASE + "/dashboard", allow_redirects=False)
    expect("TC_LOGOUT_001",
           r.status_code == 302 and still.status_code == 302,
           "Đăng xuất -> %s; sau đó /dashboard -> %s (bị đẩy về đăng nhập)"
           % (r.status_code, still.status_code))
    expect("TC_LOGOUT_002", still.status_code == 302,
           "Dùng lại session cũ sau khi đăng xuất vẫn bị chặn")
    s2 = requests.Session()
    r2 = s2.get(BASE + "/login?action=logout", allow_redirects=False)
    expect("TC_LOGOUT_003", r2.status_code in (200, 302),
           "Đăng xuất khi chưa đăng nhập: HTTP %s, không lỗi 500" % r2.status_code)


# --------------------------------------------------------------------------
# Phân quyền — phần kiểm được hoàn toàn bằng HTTP
# --------------------------------------------------------------------------
PERM_CASES = [
    # (mã test case, vai trò, method, đường dẫn, dữ liệu, mã HTTP mong đợi, mô tả)
    ("TC_CUSADD_015", "tech", "POST", "/customer", {"action": "create"}, 403,
     "Kỹ thuật gọi customer action=create"),
    ("TC_CUSEDIT_009", "cskh", "POST", "/customer",
     {"action": "update", "customerId": "1"}, 403,
     "CSKH gọi customer action=update"),
    ("TC_CUSDEL_005", "tech", "POST", "/customer", {"action": "delete", "id": "1"}, 403,
     "Kỹ thuật gọi customer action=delete"),
    ("TC_CUSEVAL_007", "cskh", "POST", "/customer",
     {"action": "evaluate", "id": "1", "rating": "Tốt"}, 403,
     "CSKH gọi customer action=evaluate"),
    ("TC_CTRADD_013", "cskh", "POST", "/contract", {"action": "create"}, 403,
     "CSKH gọi contract action=create"),
    ("TC_CTREDIT_009", "tech", "POST", "/contract",
     {"action": "update", "contractId": "1"}, 403,
     "Kỹ thuật gọi contract action=update"),
    ("TC_CTRDEL_005", "cskh", "POST", "/contract", {"action": "delete", "id": "1"}, 403,
     "CSKH gọi contract action=delete"),
    ("TC_CTRADDPRD_009", "tech", "POST", "/contract",
     {"action": "addProduct", "contractId": "1", "productId": "1", "quantity": "1"}, 403,
     "Kỹ thuật gọi contract action=addProduct"),
    ("TC_CTRDELPRD_005", "cskh", "POST", "/contract",
     {"action": "removeProduct", "contractId": "1",
      "contractProductId": "1"}, 403,
     "CSKH gọi contract action=removeProduct"),
    ("TC_CTRIMPORT_008", "tech", "POST", "/contract", {"action": "importPdf"}, 403,
     "Kỹ thuật gọi contract action=importPdf"),
    ("TC_PRDADD_011", "sales", "POST", "/product", {"action": "create"}, 403,
     "Sales gọi product action=create"),
    ("TC_PRDEDIT_009", "cskh", "POST", "/product",
     {"action": "update", "productId": "1"}, 403,
     "CSKH gọi product action=update"),
    ("TC_PRDDEL_005", "sales", "POST", "/product", {"action": "delete", "id": "1"}, 403,
     "Sales gọi product action=delete"),
    ("TC_TKADD_013", "tech", "POST", "/ticket", {"action": "create"}, 403,
     "Kỹ thuật gọi ticket action=create"),
    ("TC_TKEDIT_013", "sales", "POST", "/ticket",
     {"action": "update", "ticketId": "3"}, 403,
     "Sales gọi ticket action=update"),
    ("TC_TKDEL_005", "tech", "POST", "/ticket", {"action": "delete", "id": "1"}, 403,
     "Kỹ thuật gọi ticket action=delete"),
    ("TC_EMPLIST_008", "sales", "GET", "/employee?action=list", None, 403,
     "Sales mở danh sách nhân viên"),
    ("TC_EMPLIST_009", "tech", "GET", "/employee?action=list", None, 403,
     "Kỹ thuật mở danh sách nhân viên"),
    ("TC_EMPLIST_010", "cskh", "GET", "/employee?action=list", None, 403,
     "CSKH mở danh sách nhân viên"),
    ("TC_EMPADD_016", "sales", "POST", "/employee", {"action": "create"}, 403,
     "Sales gọi employee action=create"),
    ("TC_EMPEDIT_008", "cskh", "POST", "/employee",
     {"action": "update", "userId": "16"}, 403,
     "CSKH gọi employee action=update"),
    ("TC_EMPBAN_006", "sales", "POST", "/employee",
     {"action": "toggleStatus", "id": "16"}, 403, "Sales gọi employee toggleStatus"),
    ("TC_EMPSEND_007", "tech", "POST", "/employee",
     {"action": "sendAccount", "id": "16"}, 403, "Kỹ thuật gọi employee sendAccount"),
    ("TC_LOG_012", "sales", "GET", "/systemLog", None, 403,
     "Sales mở nhật ký hệ thống"),
    ("TC_LOG_013", "cskh", "GET", "/systemLog?action=download&file=poscs.log", None,
     403, "CSKH tải file nhật ký"),
]


def test_permissions():
    print("\n[Phân quyền]")
    sessions = {}
    for role in ("admin", "sales", "tech", "cskh"):
        s, _ = login(role)
        if not logged_in(s):
            print("  !! khong dang nhap duoc bang vai tro", role)
        sessions[role] = s

    for cid, role, method, path, data, want, label in PERM_CASES:
        s = sessions[role]
        if method == "POST":
            body = dict(data or {})
            body["csrfToken"] = csrf(s)
            r = s.post(BASE + path, data=body, allow_redirects=False)
        else:
            r = s.get(BASE + path, allow_redirects=False)
        ok = r.status_code == want
        expect(cid, ok, "%s -> HTTP %s (mong đợi %s)" % (label, r.status_code, want))

    # Vai trò chỉ-xem vẫn phải mở được danh sách
    for cid, role, path, label in [
            ("TC_CUSLIST_012", "tech", "/customer?action=list", "Kỹ thuật xem khách hàng"),
            ("TC_CTRLIST_010", "tech", "/contract?action=list", "Kỹ thuật xem hợp đồng"),
            ("TC_PRDLIST_009", "sales", "/product?action=list", "Sales xem sản phẩm"),
            ("TC_TKLIST_010", "sales", "/ticket?action=list", "Sales xem phiếu hỗ trợ")]:
        r = sessions[role].get(BASE + path, allow_redirects=False)
        expect(cid, r.status_code == 200,
               "%s -> HTTP %s (xem được, nút thao tác cần kiểm bằng mắt)"
               % (label, r.status_code))

    # Xuất Excel tính là thao tác đọc
    r = sessions["tech"].get(BASE + "/customer?action=exportExcel", allow_redirects=False)
    expect("TC_CUSEXP_005", r.status_code == 200,
           "Kỹ thuật xuất Excel khách hàng -> HTTP %s, %d byte"
           % (r.status_code, len(r.content)))

    # Ma trận phân quyền tổng hợp
    expect("TC_ACL_003",
           all(results[c]["status"] == "Đạt"
               for c in ("TC_PRDADD_011", "TC_TKEDIT_013")),
           "Sales bị chặn 403 ở cả product/create lẫn ticket/update")
    expect("TC_ACL_005",
           all(results[c]["status"] == "Đạt"
               for c in ("TC_CUSADD_015", "TC_CTREDIT_009")),
           "Kỹ thuật bị chặn 403 ở cả customer/create lẫn contract/update")
    expect("TC_ACL_008",
           all(results[c]["status"] == "Đạt"
               for c in ("TC_CUSEDIT_009", "TC_CTRADD_013", "TC_PRDEDIT_009")),
           "CSKH bị chặn 403 ở customer/contract/product")
    expect("TC_ACL_009",
           all(results[c]["status"] == "Đạt"
               for c in ("TC_EMPLIST_008", "TC_EMPLIST_009", "TC_EMPLIST_010",
                         "TC_LOG_012")),
           "Ba vai trò không phải Admin đều bị 403 ở /employee và /systemLog")
    expect("TC_ACL_010",
           results["TC_PRDADD_011"]["status"] == "Đạt",
           "Gọi thẳng endpoint bỏ qua giao diện vẫn bị máy chủ chặn 403")
    expect("TC_ACL_012", results["TC_CUSEXP_005"]["status"] == "Đạt",
           "Vai trò chỉ-xem vẫn xuất được Excel")
    return sessions


# --------------------------------------------------------------------------
# Kiểm tra dữ liệu đầu vào
# --------------------------------------------------------------------------
# CustomerController/ProductController/ContractController khai báo @MultipartConfig
# và có gọi request.getPart(...), nên form phải gửi dạng multipart đúng như trình
# duyệt gửi. Gửi application/x-www-form-urlencoded thì getPart ném lỗi -> HTTP 500.
MULTIPART_PATHS = ("/customer", "/product", "/contract")


def post(session, path, data, page="/changePassword.jsp"):
    body = dict(data)
    body["csrfToken"] = csrf(session, page)
    if path in MULTIPART_PATHS:
        fields = {k: (None, "" if v is None else str(v)) for k, v in body.items()}
        return session.post(BASE + path, files=fields, allow_redirects=False)
    return session.post(BASE + path, data=body, allow_redirects=False)


def test_customer_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Khách hàng]")
    s = sessions["admin"]
    base_ok = {"action": "create", "customerName": "Cong ty Kiem Thu " + RUN,
               "customerType": "Doanh nghiệp", "customerGroup": "Khách hàng mới",
               "taxCode": "99" + RUN + "01", "phone": "09" + RUN + "01",
               "email": "kiemthu%s@example.vn" % RUN, "accountOwnerId": "15",
               "districtId": "1", "addressDetail": "So 1 duong Kiem Thu"}
    r = post(s, "/customer", base_ok)
    loc = r.headers.get("Location") or ""
    expect("TC_CUSADD_001", r.status_code == 302 and "error" not in loc,
           "tạo khách hàng đủ thông tin hợp lệ -> %s" % (loc or "HTTP %s" % r.status_code),
           "Ca đối chứng: hỏng ca này thì các ca lỗi bên dưới không đáng tin")

    cases = [
        ("TC_CUSADD_002", {"customerName": ""}, "invalid", "bỏ trống tên doanh nghiệp"),
        ("TC_CUSADD_003", {"taxCode": ""}, "invalid", "bỏ trống mã số thuế"),
        ("TC_CUSADD_004", {"accountOwnerId": ""}, "invalid", "chưa chọn người phụ trách"),
        ("TC_CUSADD_005", {"phone": "12345"}, "invalid", "số điện thoại sai định dạng"),
        ("TC_CUSADD_008", {"email": ""}, "invalid", "bỏ trống email"),
        ("TC_CUSADD_009", {"email": "lienhe@"}, "invalid", "email sai định dạng"),
        ("TC_CUSADD_010", {"joinDate": "2099-01-01"}, "invalid", "ngày tham gia tương lai"),
    ]
    for cid, override, want_err, label in cases:
        data = dict(base_ok)
        data.update(override)
        r = post(s, "/customer", data)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and want_err in loc,
               "%s -> %s" % (label, loc or "HTTP %s" % r.status_code))

    for cid, phone, label in [("TC_CUSADD_006", "09 " + RUN + " 02", "SĐT có dấu cách"),
                              ("TC_CUSADD_007", "+849" + RUN + "03", "SĐT dạng +84")]:
        data = dict(base_ok)
        data["phone"] = phone
        data["taxCode"] = "98" + RUN + phone[-2:]
        data["email"] = "kt%s%s@example.vn" % (RUN, phone[-2:])
        data["customerName"] = "Cong ty %s %s" % (RUN, phone[-2:])
        r = post(s, "/customer", data)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "error" not in loc,
               "%s -> %s" % (label, loc))

    data = dict(base_ok)
    data["joinDate"] = time.strftime("%Y-%m-%d")
    data["taxCode"] = "97" + RUN + "04"
    data["email"] = "hnay%s@example.vn" % RUN
    data["phone"] = "09" + RUN + "04"
    data["customerName"] = "Cong ty hom nay " + RUN
    r = post(s, "/customer", data)
    loc = r.headers.get("Location") or ""
    expect("TC_CUSADD_011", r.status_code == 302 and "error" not in loc,
           "ngày tham gia đúng hôm nay (giá trị biên) -> %s" % loc)

    r = post(s, "/customer", base_ok)          # lần 2 với đúng mã số thuế đã tạo
    loc = r.headers.get("Location") or ""
    expect("TC_CUSADD_012", "create_failed" in loc or "error" in loc,
           "trùng mã số thuế -> %s" % loc)

    r = post(s, "/customer", {"action": "update", "customerId": "999999",
                              "customerName": "X"})
    loc = r.headers.get("Location") or ""
    expect("TC_CUSEDIT_008", r.status_code == 302 and "notfound" in loc,
           "sửa khách hàng id=999999 -> %s" % loc)

    r = post(s, "/customer", {"action": "delete", "id": "999999"})
    loc = r.headers.get("Location") or ""
    expect("TC_CUSDEL_004", r.status_code == 302 and "notfound" in loc,
           "xoá khách hàng id=999999 -> %s" % loc)

    r = post(s, "/customer", {"action": "evaluate", "id": "1", "rating": "HackedValue"})
    loc = r.headers.get("Location") or ""
    expect("TC_CUSEVAL_005", "invalid_rating" in loc,
           "mức đánh giá không hợp lệ -> %s" % loc)

    r = post(s, "/customer", {"action": "evaluate", "id": "999999", "rating": "Tốt"})
    loc = r.headers.get("Location") or ""
    expect("TC_CUSEVAL_006", "notfound" in loc,
           "đánh giá khách hàng id=999999 -> %s" % loc)

    for cid, url, label in [
            ("TC_CUSVIEW_006", "/customer?action=view&id=999999", "id không tồn tại"),
            ("TC_CUSVIEW_007", "/customer?action=view&id=abc", "id không phải số")]:
        r = s.get(BASE + url, allow_redirects=False)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "notfound" in loc,
               "%s -> %s" % (label, loc))

    r = s.get(BASE + "/customer?action=list&page=abc", allow_redirects=False)
    expect("TC_CUSLIST_011", r.status_code == 200,
           "page=abc -> HTTP %s (không lỗi 500)" % r.status_code)


def test_contract_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Hợp đồng]")
    s = sessions["admin"]
    ok = {"action": "create", "title": "Hop dong kiem thu",
          "contractType": "Bảo trì", "enterpriseId": "1", "ownerId": "15",
          "signDate": "2026-01-01", "effectiveDate": "2026-01-05",
          "endDate": "2026-12-31"}
    r = post(s, "/contract", ok)
    loc = r.headers.get("Location") or ""
    expect("TC_CTRADD_001", r.status_code == 302 and "error" not in loc,
           "tạo hợp đồng đủ thông tin hợp lệ -> %s" % (loc or "HTTP %s" % r.status_code),
           "Ca đối chứng")

    for cid, override, label in [
            ("TC_CTRADD_002", {"title": ""}, "bỏ trống tiêu đề"),
            ("TC_CTRADD_003", {"enterpriseId": ""}, "chưa chọn khách hàng"),
            ("TC_CTRADD_004", {"ownerId": ""}, "chưa chọn người phụ trách"),
            ("TC_CTRADD_005", {"endDate": ""}, "thiếu ngày kết thúc"),
            ("TC_CTRADD_006", {"signDate": "2026-01-10"}, "ngày ký sau ngày hiệu lực"),
            ("TC_CTRADD_007", {"effectiveDate": "2026-12-31", "endDate": "2026-01-01"},
             "ngày hiệu lực sau ngày kết thúc")]:
        data = dict(ok)
        data.update(override)
        r = post(s, "/contract", data)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "invalid" in loc,
               "%s -> %s" % (label, loc))

    for cid, override, label in [
            ("TC_CTRADD_008", {"signDate": "2026-01-05"}, "ngày ký = ngày hiệu lực"),
            ("TC_CTRADD_009", {"effectiveDate": "2026-12-31"}, "hiệu lực = kết thúc")]:
        data = dict(ok)
        data.update(override)
        r = post(s, "/contract", data)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "invalid" not in loc,
               "%s (giá trị biên) -> %s" % (label, loc))

    data = dict(ok)
    data["attachmentUrl"] = "javascript:alert(1)"
    r = post(s, "/contract", data)
    loc = r.headers.get("Location") or ""
    expect("TC_CTRADD_010", "invalid_drive_link" in loc,
           "link đính kèm javascript: -> %s" % loc)

    data = dict(ok)
    data["attachmentUrl"] = "https://drive.google.com/file/d/1AbCdEf/view"
    r = post(s, "/contract", data)
    loc = r.headers.get("Location") or ""
    expect("TC_CTRADD_011", "error" not in loc, "link Google Drive hợp lệ -> %s" % loc)

    r = post(s, "/contract", {"action": "update", "contractId": "999999", "title": "X"})
    loc = r.headers.get("Location") or ""
    expect("TC_CTREDIT_008", "notfound" in loc, "sửa hợp đồng id=999999 -> %s" % loc)

    r = post(s, "/contract", {"action": "delete", "id": "999999"})
    loc = r.headers.get("Location") or ""
    expect("TC_CTRDEL_004", "notfound" in loc, "xoá hợp đồng id=999999 -> %s" % loc)

    for cid, qty, label in [("TC_CTRADDPRD_003", "0", "số lượng 0"),
                            ("TC_CTRADDPRD_004", "-5", "số lượng âm"),
                            ("TC_CTRADDPRD_006", "2.5", "số lượng thập phân")]:
        r = post(s, "/contract", {"action": "addProduct", "contractId": "1",
                                  "productId": "1", "quantity": qty})
        loc = r.headers.get("Location") or ""
        expect(cid, "add_product_invalid" in loc, "%s -> %s" % (label, loc))

    r = post(s, "/contract", {"action": "addProduct", "contractId": "1",
                              "productId": "", "quantity": "2"})
    loc = r.headers.get("Location") or ""
    expect("TC_CTRADDPRD_002", "add_product_invalid" in loc,
           "chưa chọn sản phẩm -> %s" % loc)

    r = post(s, "/contract", {"action": "removeProduct", "contractId": "1",
                              "contractProductId": "999999"})
    loc = r.headers.get("Location") or ""
    expect("TC_CTRDELPRD_003", "remove_product_failed" in loc or "error" in loc,
           "gỡ dòng sản phẩm không tồn tại -> %s" % loc)

    r = s.get(BASE + "/contract?action=view&id=999999", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_CTRVIEW_007", "notfound" in loc, "xem hợp đồng id=999999 -> %s" % loc)

    r = s.get(BASE + "/contract?action=list&status=hacked", allow_redirects=False)
    expect("TC_CTRLIST_008", r.status_code == 200,
           "status=hacked -> HTTP %s (không lỗi 500)" % r.status_code)

    r = s.get(BASE + "/contract?action=exportPdf&id=999999", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_CTREXP_005", r.status_code == 302 and "notfound" in loc,
           "xuất PDF hợp đồng id=999999 -> %s" % loc)


def test_ticket_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Phiếu hỗ trợ]")
    s = sessions["cskh"]
    ok = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
          "priority": "Cao", "receptionChannel": "Điện thoại",
          "assignedTechnicianId": "16", "description": "Mo ta kiem thu"}
    r = post(s, "/ticket", ok)
    loc = r.headers.get("Location") or ""
    expect("TC_TKADD_001", r.status_code == 302 and "error" not in loc,
           "tạo phiếu đủ thông tin hợp lệ -> %s" % (loc or "HTTP %s" % r.status_code),
           "Ca đối chứng")

    for cid, override, label in [
            ("TC_TKADD_002", {"enterpriseId": ""}, "chưa chọn khách hàng"),
            ("TC_TKADD_003", {"assignedTechnicianId": ""}, "chưa chọn kỹ thuật viên"),
            ("TC_TKADD_004", {"priority": ""}, "chưa chọn mức ưu tiên"),
            ("TC_TKADD_005", {"receptionChannel": ""}, "chưa chọn kênh tiếp nhận"),
            ("TC_TKADD_006", {"description": ""}, "bỏ trống mô tả")]:
        data = dict(ok)
        data.update(override)
        r = post(s, "/ticket", data)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "invalid" in loc,
               "%s -> %s" % (label, loc))

    data = dict(ok)
    data["contractId"] = "999999"
    r = post(s, "/ticket", data)
    loc = r.headers.get("Location") or ""
    expect("TC_TKADD_008", "contract_mismatch" in loc or "invalid" in loc,
           "hợp đồng không thuộc khách hàng -> %s" % loc)

    data = dict(ok)
    r = post(s, "/ticket", data)
    loc = r.headers.get("Location") or ""
    expect("TC_TKADD_009", r.status_code == 302 and "error" not in loc,
           "không gắn hợp đồng -> %s (hợp đồng là tuỳ chọn)" % loc)

    r = post(s, "/ticket", {"action": "update", "ticketId": "999999",
                            "description": "X"})
    loc = r.headers.get("Location") or ""
    expect("TC_TKEDIT_012", "notfound" in loc, "sửa phiếu id=999999 -> %s" % loc)

    r = s.get(BASE + "/ticket?action=view&id=999999", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_TKVIEW_005", "notfound" in loc, "xem phiếu id=999999 -> %s" % loc)

    r = s.get(BASE + "/ticket?action=list&status=hacked", allow_redirects=False)
    expect("TC_TKLIST_005", r.status_code == 200,
           "status=hacked -> HTTP %s" % r.status_code)

    # Kỹ thuật viên cập nhật phiếu KHÔNG giao cho mình
    st, _ = login("tech2")
    r = post(st, "/ticket", {"action": "update", "ticketId": "3", "status": "Đang xử lý",
                             "description": "Thu sua phieu cua nguoi khac"})
    expect("TC_TKEDIT_008", r.status_code == 403
           or "error" in (r.headers.get("Location") or ""),
           "tech02 sửa phiếu của người khác -> HTTP %s %s"
           % (r.status_code, r.headers.get("Location") or ""))


def test_employee_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Nhân viên]")
    s = sessions["admin"]
    ok = {"action": "create", "lastName": "Nguyen", "firstName": "An" + RUN,
          "citizenId": "0012" + RUN + "01", "gender": "Nam",
          "dateOfBirth": "1995-01-01",
          "hireDate": "2024-01-01", "roleId": "2", "departmentId": "2",
          "districtId": "1", "addressDetail": "So 1 duong Kiem Thu",
          "personalEmail": "an.kiemthu%s@gmail.com" % RUN,
          "phone": "091" + RUN + "1"}
    r = post(s, "/employee", ok)
    loc = r.headers.get("Location") or ""
    expect("TC_EMPADD_001", r.status_code == 302 and "error" not in loc,
           "tạo nhân viên đủ thông tin hợp lệ -> %s" % (loc or "HTTP %s" % r.status_code),
           "Ca đối chứng")

    for cid, override, label in [
            ("TC_EMPADD_004", {"lastName": ""}, "bỏ trống họ"),
            ("TC_EMPADD_005", {"citizenId": ""}, "bỏ trống CCCD"),
            ("TC_EMPADD_006", {"firstName": "<script>alert(1)</script>"},
             "họ tên chứa thẻ HTML"),
            ("TC_EMPADD_007", {"dateOfBirth": "2099-01-01"}, "ngày sinh tương lai"),
            ("TC_EMPADD_008", {"dateOfBirth": time.strftime("%Y-%m-%d")}, "ngày sinh hôm nay"),
            ("TC_EMPADD_009", {"departmentId": ""}, "chưa chọn phòng ban"),
            ("TC_EMPADD_010", {"roleId": ""}, "chưa chọn vai trò"),
            ("TC_EMPADD_011", {"gender": "Hacked"}, "giới tính giá trị lạ"),
            ("TC_EMPADD_012", {"personalEmail": "an.nguyen@"}, "email cá nhân sai"),
            ("TC_EMPADD_013", {"personalEmail": ""}, "bỏ trống email cá nhân")]:
        data = dict(ok)
        data.update(override)
        r = post(s, "/employee", data)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "invalid" in loc,
               "%s -> %s" % (label, loc))

    data = dict(ok)
    data["citizenId"] = "001090000001"          # trùng CCCD của admin
    data["phone"] = "091" + RUN + "9"           # SĐT phải khác, không thì báo trùng SĐT trước
    data["personalEmail"] = "cccd%s@gmail.com" % RUN
    data["firstName"] = "Cuong" + RUN
    r = post(s, "/employee", data)
    loc = r.headers.get("Location") or ""
    expect("TC_EMPADD_014", "duplicate_citizen" in loc, "trùng CCCD -> %s" % loc)

    data = dict(ok)
    data["citizenId"] = "0012" + RUN + "02"
    data["personalEmail"] = "khac%s@gmail.com" % RUN
    data["firstName"] = "Binh" + RUN
    data["phone"] = "0900000001"                # trùng SĐT của admin
    r = post(s, "/employee", data)
    loc = r.headers.get("Location") or ""
    expect("TC_EMPADD_015", "duplicate_phone" in loc, "trùng SĐT -> %s" % loc)

    r = post(s, "/employee", {"action": "update", "userId": "999999", "lastName": "X"})
    loc = r.headers.get("Location") or ""
    expect("TC_EMPEDIT_007", "notfound" in loc, "sửa nhân viên id=999999 -> %s" % loc)

    r = post(s, "/employee", {"action": "toggleStatus", "id": "1"})
    loc = r.headers.get("Location") or ""
    expect("TC_EMPBAN_003", "cannot_self_ban" in loc,
           "Admin tự khoá tài khoản của chính mình -> %s" % loc)

    r = post(s, "/employee", {"action": "toggleStatus", "id": "999999"})
    loc = r.headers.get("Location") or ""
    expect("TC_EMPBAN_005", "notfound" in loc, "khoá nhân viên id=999999 -> %s" % loc)

    r = post(s, "/employee", {"action": "sendAccount", "id": "999999"})
    loc = r.headers.get("Location") or ""
    expect("TC_EMPSEND_006", "notfound" in loc,
           "gửi tài khoản cho id=999999 -> %s" % loc)

    r = s.get(BASE + "/employee?action=view&id=999999", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_EMPVIEW_004", "notfound" in loc, "xem nhân viên id=999999 -> %s" % loc)


def test_product_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Sản phẩm]")
    s = sessions["tech"]
    r = post(s, "/product", {"action": "create",
                             "productName": "SP kiem thu " + RUN, "categoryId": "1",
                             "unitPrice": "1000000", "description": "Mo ta"})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_001", r.status_code == 302 and "error" not in loc,
           "tạo sản phẩm đủ thông tin hợp lệ -> %s" % (loc or "HTTP %s" % r.status_code),
           "Ca đối chứng")

    for cid, data, label in [
            ("TC_PRDADD_002", {"action": "create", "productName": "", "categoryId": "1"},
             "bỏ trống tên sản phẩm"),
            ("TC_PRDADD_003", {"action": "create", "productName": "SP kiem thu",
                               "categoryId": ""}, "chưa chọn danh mục")]:
        r = post(s, "/product", data)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "invalid" in loc,
               "%s -> %s" % (label, loc))

    r = post(s, "/product", {"action": "update", "productId": "999999", "productName": "X"})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDEDIT_008", "notfound" in loc, "sửa sản phẩm id=999999 -> %s" % loc)

    r = post(s, "/product", {"action": "delete", "id": "999999"})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDDEL_004", "notfound" in loc, "xoá sản phẩm id=999999 -> %s" % loc)

    r = s.get(BASE + "/product?action=view&id=999999", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_PRDVIEW_006", "notfound" in loc, "xem sản phẩm id=999999 -> %s" % loc)


# --------------------------------------------------------------------------
# Endpoint JSON, phục vụ file, nhật ký hệ thống
# --------------------------------------------------------------------------
def test_ajax(sessions):
    print("\n[Endpoint JSON]")
    s = sessions["admin"]
    r = s.get(BASE + "/address/wards?provinceId=1")
    try:
        data = r.json()
        ok = isinstance(data, list) and len(data) > 0
    except ValueError:
        data, ok = None, False
    expect("TC_AJAX_001", r.status_code == 200 and ok,
           "provinceId=1 -> HTTP %s, %d phần tử JSON"
           % (r.status_code, len(data) if isinstance(data, list) else -1))

    for cid, url, label in [
            ("TC_AJAX_002", "/address/wards", "không kèm mã tỉnh"),
            ("TC_AJAX_003", "/address/wards?provinceId=abc", "mã tỉnh là chữ")]:
        r = s.get(BASE + url)
        body = r.text.strip()
        expect(cid, r.status_code == 400 and body in ("[]", "[ ]"),
               "%s -> HTTP %s, body %s" % (label, r.status_code, body[:40]))

    r = s.get(BASE + "/address/wards?provinceId=999999")
    expect("TC_AJAX_004", r.status_code == 200 and r.text.strip() == "[]",
           "mã tỉnh không tồn tại -> HTTP %s, body %s" % (r.status_code, r.text.strip()[:40]))

    t0 = time.time()
    s.get(BASE + "/address/wards?provinceId=1")
    t1 = time.time()
    s.get(BASE + "/address/wards?provinceId=1")
    t2 = time.time()
    expect("TC_AJAX_005", (t2 - t1) <= (t1 - t0) + 0.05,
           "gọi lần 2 cùng tỉnh: %.0f ms so với %.0f ms lần 1"
           % ((t2 - t1) * 1000, (t1 - t0) * 1000))

    r = s.get(BASE + "/contract/byEnterprise?enterpriseId=1")
    try:
        data = r.json()
        ok = isinstance(data, list)
    except ValueError:
        data, ok = None, False
    expect("TC_AJAX_006", r.status_code == 200 and ok,
           "enterpriseId=1 -> HTTP %s, %d phần tử"
           % (r.status_code, len(data) if isinstance(data, list) else -1))

    r = s.get(BASE + "/contract/byEnterprise")
    expect("TC_AJAX_008", r.status_code == 400,
           "không kèm mã khách hàng -> HTTP %s, body %s"
           % (r.status_code, r.text.strip()[:30]))


def test_uploads(sessions):
    print("\n[Phục vụ file tải lên]")
    s = sessions["admin"]
    r = s.get(BASE + "/uploads/khongcothat.jpg", allow_redirects=False)
    expect("TC_UPLOAD_002", r.status_code == 404,
           "file không tồn tại -> HTTP %s" % r.status_code)

    r = s.get(BASE + "/uploads/", allow_redirects=False)
    expect("TC_UPLOAD_003", r.status_code == 404,
           "/uploads/ không kèm tên file -> HTTP %s (không liệt kê thư mục)"
           % r.status_code)

    r = requests.get(BASE + "/uploads/../WEB-INF/web.xml", allow_redirects=False)
    leaked = "<web-app" in r.text
    expect("TC_UPLOAD_004", not leaked,
           "path traversal -> HTTP %s, %s"
           % (r.status_code, "LỘ nội dung web.xml" if leaked else "không lộ nội dung"))


def test_systemlog(sessions):
    print("\n[Nhật ký hệ thống]")
    s = sessions["admin"]
    r = s.get(BASE + "/systemLog", allow_redirects=False)
    expect("TC_LOG_001", r.status_code == 200,
           "Admin mở nhật ký -> HTTP %s" % r.status_code)

    r = s.get(BASE + "/systemLog?lines=abc", allow_redirects=False)
    expect("TC_LOG_005", r.status_code == 200,
           "lines=abc -> HTTP %s (không lỗi 500)" % r.status_code)

    r = s.get(BASE + "/systemLog?lines=999999999", allow_redirects=False)
    expect("TC_LOG_006", r.status_code == 200,
           "lines=999999999 -> HTTP %s" % r.status_code)

    # Chọn file lạ thì ứng dụng chuyển hướng kèm error=notfound; đi theo redirect
    # để xem trang đích có thật sự hiện thông báo hay không.
    r = s.get(BASE + "/systemLog?file=khongcothat.log", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    final = s.get(BASE + "/systemLog?error=notfound") if r.status_code == 302 else r
    expect("TC_LOG_008",
           r.status_code == 302 and "error=notfound" in loc
           and "Không tìm thấy file log" in utf8(final),
           "file lạ -> HTTP %s %s, trang đích có thông báo không tìm thấy file log"
           % (r.status_code, loc))

    r = s.get(BASE + "/systemLog?file=../../conf/server.xml", allow_redirects=False)
    leaked = "<Server" in r.text or "Connector" in r.text
    expect("TC_LOG_009", not leaked,
           "path traversal ra ngoài thư mục log -> %s"
           % ("LỘ nội dung server.xml" if leaked else "không lộ nội dung"))


def test_misc(sessions):
    print("\n[Dashboard & Thông báo]")
    for role in ("admin", "sales", "tech", "cskh"):
        s = sessions[role] if role in sessions else login(role)[0]
        r = s.get(BASE + "/dashboard", allow_redirects=False)
        if r.status_code != 200:
            expect("TC_DASH_009", False, "vai trò %s mở Dashboard -> HTTP %s"
                   % (role, r.status_code))
            break
    else:
        expect("TC_DASH_009", True, "cả bốn vai trò đều mở được Dashboard (HTTP 200)")

    s = sessions["admin"]
    r = s.get(BASE + "/dashboard", allow_redirects=False)
    expect("TC_DASH_001", r.status_code == 200 and "dashboard" in r.text.lower(),
           "Dashboard trả HTTP %s, %d byte HTML" % (r.status_code, len(r.content)))

    r = s.get(BASE + "/notifications", allow_redirects=False)
    expect("TC_NOTI_002", r.status_code == 200,
           "trang thông báo -> HTTP %s" % r.status_code)

    r = s.get(BASE + "/notifications?action=read", allow_redirects=False)
    expect("TC_NOTI_007", r.status_code in (200, 302),
           "action=read không kèm id -> HTTP %s (không lỗi 500)" % r.status_code)

    s2 = requests.Session()
    r = s2.get(BASE + "/notifications", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_NOTI_006", r.status_code == 302 and "login" in loc,
           "chưa đăng nhập -> HTTP %s %s" % (r.status_code, loc))


def main():
    global BASE
    if len(sys.argv) > 1:
        BASE = sys.argv[1].rstrip("/")
    print("Muc tieu:", BASE)
    try:
        requests.get(BASE + "/login.jsp", timeout=5)
    except requests.RequestException as ex:
        sys.exit("Khong ket noi duoc toi %s: %s" % (BASE, ex))

    test_login()
    test_logout()
    sessions = test_permissions()
    test_customer_validation(sessions)
    test_contract_validation(sessions)
    test_ticket_validation(sessions)
    test_employee_validation(sessions)
    test_product_validation(sessions)
    test_ajax(sessions)
    test_uploads(sessions)
    test_systemlog(sessions)
    test_misc(sessions)

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2),
                   encoding="utf-8")
    counts = {}
    for value in results.values():
        counts[value["status"]] = counts.get(value["status"], 0) + 1
    print("\nDa ghi: %s" % OUT)
    print("Tong: %d test case | %s" % (len(results), counts))


if __name__ == "__main__":
    main()

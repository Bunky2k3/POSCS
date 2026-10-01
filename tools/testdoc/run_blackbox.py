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

import testdb as T

BASE = "http://localhost:8099/POSCS"
OUT = pathlib.Path(__file__).with_name("blackbox_results.json")

# Vai CSKH đã gộp vào Sales (V37) nên không còn tài khoản cskh01. Các tài khoản
# sales2..5 / cskh2 do db/schema.sql gieo sẵn (mật khẩu chung Poscs@123) và
# mang sẵn địa bàn -- cần cho các ca "phạm vi của Sales" (PR #149/#155):
#   sales4 cầm Hà Nội, Quảng Ninh, Phú Thọ, Lào Cai, Bắc Ninh
#   sales2 cầm Hải Phòng, Thái Nguyên, Cao Bằng
#   sales3 cầm Lạng Sơn, Tuyên Quang, Hà Tĩnh (và đứng tên KH-0003 ở Bắc Ninh)
#   cskh2  là Sales CHƯA có tỉnh -> trang Thêm khách hàng chạy như Admin
# Đăng nhập một tài khoản không tồn tại cũng bị tính là một lần sai vào hạn
# mức 5 lần / IP -- đừng thêm tài khoản chưa có trong fixtures.sql vào đây.
ACCOUNTS = {
    "admin": ("admin", "Admin@123"),
    "sales": ("sale01", "Sales@123"),
    "tech": ("tech01", "Tech@123"),
    "tech2": ("tech02", "Tech@123"),
    "locked": ("locked01", "Sales@123"),
    "sales2": ("sales2", "Poscs@123"),
    "sales3": ("sales3", "Poscs@123"),
    "sales4": ("sales4", "Poscs@123"),
    "cskh2": ("cskh2", "Poscs@123"),
    "doimk": ("doimk01", "Sales@123"),
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
    # JSTL <fmt:formatDate> chỉ áp dụng pattern khi xác định được locale của
    # request; thiếu header này thì nó in thẳng Date.toString() và mọi phép
    # kiểm ngày tháng đọc ra kết quả khác hẳn thứ người dùng thật nhìn thấy.
    s.headers["Accept-Language"] = "vi-VN,vi;q=0.9,en;q=0.8"
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

    # Từ V36 không còn "email công ty": ca đăng nhập bằng email đã bỏ khỏi tài
    # liệu, các ca phía sau dồn lên một số (TC_LOGIN_002 = bỏ trống cả hai ô).
    for cid, user, pwd, label in [
            ("TC_LOGIN_002", "", "", "bỏ trống cả hai ô"),
            ("TC_LOGIN_003", "admin", "", "bỏ trống mật khẩu")]:
        s = requests.Session()
        token = csrf(s, "/login.jsp")
        r = s.post(BASE + "/login", allow_redirects=False,
                   data={"username": user, "password": pwd, "csrfToken": token})
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "login" in loc and "error" in loc,
               "%s: HTTP %s -> %s" % (label, r.status_code, loc))

    for cid, user, pwd, label in [
            ("TC_LOGIN_004", "khongtontai", "Admin@123", "tài khoản không tồn tại"),
            ("TC_LOGIN_005", "admin", "SaiMatKhau1", "mật khẩu sai")]:
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
    # sai mật khẩu" trong kết quả mong đợi của TC_LOGIN_004, nên ghi đè kết quả
    # của ca đó cho đầy đủ.
    e1, e2 = err_of("khongtontai2", "x"), err_of("admin", "SaiMatKhau2")
    same = e1 == e2
    prev = results["TC_LOGIN_004"]
    record("TC_LOGIN_004", "Đạt" if prev["status"] == "Đạt" and same else "Trượt",
           prev["actual"] + " | lỗi khi sai tài khoản = lỗi khi sai mật khẩu: %r vs %r"
           % (e1, e2))

    # Tới đây IP đã sai 4 lần -- thêm một lần nữa là bị khoá 15 phút và mọi ca
    # phía sau trượt oan. Một lần đăng nhập đúng xoá bộ đếm (chính là hành vi
    # của TC_LOGIN_008, kiểm đầy đủ ở run_blackbox_rest.py).
    login("admin")

    # Ba ca khoá IP chạy ở run_blackbox_rest.py (phải chờ theo đồng hồ thật).
    # Không ghi gì ở đây: lượt này GHI ĐÈ file kết quả, ghi "N/A" thì lượt rest
    # vẫn đè lại được, nhưng chạy riêng lượt này sẽ xoá mất kết quả thật.

    s = requests.Session()
    token = csrf(s, "/login.jsp")
    r = s.post(BASE + "/login", allow_redirects=False,
               data={"username": "locked01", "password": "Sales@123",
                     "csrfToken": token})
    loc = r.headers.get("Location") or ""
    expect("TC_LOGIN_009", "account_inactive" in loc,
           "Tài khoản bị khoá: -> %s" % loc)

    s = requests.Session()
    csrf(s, "/login.jsp")
    before = s.cookies.get("JSESSIONID")
    t = csrf(s, "/login.jsp")
    s.post(BASE + "/login", allow_redirects=False,
           data={"username": "admin", "password": "Admin@123", "csrfToken": t})
    after = s.cookies.get("JSESSIONID")
    expect("TC_LOGIN_010", before != after,
           "JSESSIONID trước %s... / sau %s..." % (str(before)[:12], str(after)[:12]))

    s = requests.Session()
    t = csrf(s, "/login.jsp")
    r = s.post(BASE + "/login", allow_redirects=False,
               data={"username": "' OR 1=1 --", "password": "abc", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_LOGIN_011", r.status_code == 302 and "invalid_credentials" in loc,
           "SQL injection: -> %s (không đăng nhập được, không lỗi 500)" % loc)

    s = requests.Session()
    t = csrf(s, "/login.jsp")
    r = s.post(BASE + "/login", allow_redirects=False,
               data={"username": "<script>alert(1)</script>", "password": "abc",
                     "csrfToken": t})
    follow = s.get(BASE + (r.headers.get("Location") or "/login.jsp")
                   .replace(BASE, ""), allow_redirects=True)
    expect("TC_LOGIN_012",
           r.status_code == 302 and "<script>alert(1)</script>" not in utf8(follow),
           "XSS: chuỗi script không xuất hiện nguyên văn trong HTML trả về")

    s = requests.Session()
    r = s.get(BASE + "/customer?action=list", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_LOGIN_013", r.status_code == 302 and "login" in loc,
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
    # Vai CSKH đã gộp vào Sales (V37): các ca chặn của vai đó chuyển sang vai
    # thật sự còn bị chặn theo tài liệu (Kỹ thuật với khách hàng / hợp đồng,
    # Sales với sản phẩm / nhân viên / nhật ký).
    ("TC_CUSADD_015", "tech", "POST", "/customer", {"action": "create"}, 403,
     "Kỹ thuật gọi customer action=create"),
    ("TC_CUSEDIT_009", "tech", "POST", "/customer",
     {"action": "update", "customerId": "1"}, 403,
     "Kỹ thuật gọi customer action=update"),
    ("TC_CUSDEL_005", "tech", "POST", "/customer", {"action": "delete", "id": "1"}, 403,
     "Kỹ thuật gọi customer action=delete"),
    ("TC_CUSEVAL_007", "tech", "POST", "/customer",
     {"action": "evaluate", "id": "1", "rating": "GOOD"}, 403,
     "Kỹ thuật gọi customer action=evaluate"),
    ("TC_CTRADD_013", "tech", "POST", "/contract", {"action": "create"}, 403,
     "Kỹ thuật gọi contract action=create"),
    ("TC_CTREDIT_009", "tech", "POST", "/contract",
     {"action": "update", "contractId": "1"}, 403,
     "Kỹ thuật gọi contract action=update"),
    # Hợp đồng 1 của bộ demo ĐÃ KÝ: huỷ bản ghi hợp đồng đã ký chỉ Admin làm
    # được (requireAdmin), Sales có Full access trên hợp đồng vẫn bị 403.
    ("TC_CTRDEL_005", "sales", "POST", "/contract",
     {"action": "delete", "id": "1", "voidReason": "thu"}, 403,
     "Sales gọi contract action=delete trên hợp đồng đã ký"),
    ("TC_CTRADDPRD_009", "tech", "POST", "/contract",
     {"action": "addProduct", "contractId": "1", "productId": "1", "quantity": "1"}, 403,
     "Kỹ thuật gọi contract action=addProduct"),
    ("TC_CTRDELPRD_005", "tech", "POST", "/contract",
     {"action": "removeProduct", "contractId": "1",
      "contractProductId": "1"}, 403,
     "Kỹ thuật gọi contract action=removeProduct"),
    ("TC_PRDADD_011", "sales", "POST", "/product", {"action": "create"}, 403,
     "Sales gọi product action=create"),
    ("TC_PRDEDIT_009", "sales", "POST", "/product",
     {"action": "update", "productId": "1"}, 403,
     "Sales gọi product action=update"),
    ("TC_PRDDEL_005", "sales", "POST", "/product", {"action": "delete", "id": "1"}, 403,
     "Sales gọi product action=delete"),
    ("TC_TKADD_013", "tech", "POST", "/ticket", {"action": "create"}, 403,
     "Kỹ thuật gọi ticket action=create"),
    ("TC_TKDEL_005", "tech", "POST", "/ticket", {"action": "delete", "id": "1"}, 403,
     "Kỹ thuật gọi ticket action=delete"),
    ("TC_EMPLIST_008", "sales", "GET", "/employee?action=list", None, 403,
     "Sales mở danh sách nhân viên"),
    ("TC_EMPLIST_009", "tech", "GET", "/employee?action=list", None, 403,
     "Kỹ thuật mở danh sách nhân viên"),
    ("TC_EMPADD_016", "sales", "POST", "/employee", {"action": "create"}, 403,
     "Sales gọi employee action=create"),
    ("TC_EMPEDIT_008", "tech", "POST", "/employee",
     {"action": "update", "userId": "1"}, 403,
     "Kỹ thuật gọi employee action=update"),
    ("TC_EMPBAN_006", "sales", "POST", "/employee",
     {"action": "toggleStatus", "id": "1"}, 403, "Sales gọi employee toggleStatus"),
    ("TC_EMPSEND_007", "tech", "POST", "/employee",
     {"action": "sendAccount", "id": "1"}, 403, "Kỹ thuật gọi employee sendAccount"),
    ("TC_LOG_012", "sales", "GET", "/systemLog", None, 403,
     "Sales mở nhật ký hệ thống"),
    ("TC_LOG_013", "sales", "GET", "/systemLog?action=download&file=poscs.log", None,
     403, "Sales tải file nhật ký"),
]


def test_permissions():
    print("\n[Phân quyền]")
    sessions = {}
    for role in ("admin", "sales", "tech"):
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
            ("TC_PRDLIST_009", "sales", "/product?action=list", "Sales xem sản phẩm")]:
        r = sessions[role].get(BASE + path, allow_redirects=False)
        expect(cid, r.status_code == 200,
               "%s -> HTTP %s (xem được, nút thao tác cần kiểm bằng mắt)"
               % (label, r.status_code))

    # Từ V37 Sales có Full trên phiếu; vai chỉ-xem của Phiếu giờ là Kỹ thuật.
    r = sessions["tech"].get(BASE + "/ticket?action=list", allow_redirects=False)
    page = utf8(r)
    expect("TC_TKLIST_010",
           r.status_code == 200 and "action=new" not in page and 'class="act-edit"' not in page,
           "Kỹ thuật xem phiếu hỗ trợ -> HTTP %s; nút Tạo phiếu: %s; nút Sửa ở dòng: %s"
           % (r.status_code, "có" if "action=new" in page else "không",
              "có" if 'class="act-edit"' in page else "không"))

    # Xuất Excel tính là thao tác đọc
    r = sessions["tech"].get(BASE + "/customer?action=exportExcel", allow_redirects=False)
    expect("TC_CUSEXP_005", r.status_code == 200,
           "Kỹ thuật xuất Excel khách hàng -> HTTP %s, %d byte"
           % (r.status_code, len(r.content)))

    # Ma trận phân quyền tổng hợp
    expect("TC_ACL_003",
           all(results[c]["status"] == "Đạt"
               for c in ("TC_PRDADD_011", "TC_PRDEDIT_009", "TC_PRDDEL_005")),
           "Sales bị chặn 403 ở product create / update / delete")
    expect("TC_ACL_005",
           all(results[c]["status"] == "Đạt"
               for c in ("TC_CUSADD_015", "TC_CTREDIT_009")),
           "Kỹ thuật bị chặn 403 ở cả customer/create lẫn contract/update")
    expect("TC_ACL_008",
           all(results[c]["status"] == "Đạt"
               for c in ("TC_TKADD_013", "TC_TKDEL_005")),
           "Kỹ thuật bị chặn 403 khi tạo và khi xoá phiếu hỗ trợ")
    expect("TC_ACL_009",
           all(results[c]["status"] == "Đạt"
               for c in ("TC_EMPLIST_008", "TC_EMPLIST_009", "TC_LOG_012")),
           "Sales và Kỹ thuật đều bị 403 ở /employee; Sales bị 403 ở /systemLog")
    expect("TC_ACL_010",
           results["TC_PRDADD_011"]["status"] == "Đạt",
           "Gọi thẳng endpoint bỏ qua giao diện vẫn bị máy chủ chặn 403")
    expect("TC_ACL_011", results["TC_CUSEXP_005"]["status"] == "Đạt",
           "Vai trò chỉ-xem vẫn xuất được Excel")
    return sessions


# --------------------------------------------------------------------------
# Kiểm tra dữ liệu đầu vào
# --------------------------------------------------------------------------
# CustomerController/ProductController/ContractController khai báo @MultipartConfig
# và có gọi request.getPart(...), nên form phải gửi dạng multipart đúng như trình
# duyệt gửi. Gửi application/x-www-form-urlencoded thì getPart ném lỗi -> HTTP 500.
# /contract không còn @MultipartConfig từ V34 (bỏ ô tải file hợp đồng) -- gửi
# multipart tới đó thì getParameter không đọc được csrfToken và nhận 403.
MULTIPART_PATHS = ("/customer", "/product")


def post(session, path, data, page="/changePassword.jsp"):
    body = dict(data)
    body["csrfToken"] = csrf(session, page)
    if path in MULTIPART_PATHS:
        fields = {k: (None, "" if v is None else str(v)) for k, v in body.items()}
        return session.post(BASE + path, files=fields, allow_redirects=False)
    return session.post(BASE + path, data=body, allow_redirects=False)


# Phường/xã dùng trong các ca khách hàng (id trong bảng districts của schema.sql).
WARD_HA_NOI = "1"        # Hà Nội -- sales4 cầm
WARD_HAI_PHONG = "1022"  # Hải Phòng -- sales2 cầm
WARD_LAI_CHAU = "352"    # Lai Châu -- tỉnh địa bàn, CHƯA ai cầm trong CSDL test
WARD_HCM = "2563"        # TP Hồ Chí Minh -- ngoài 18 tỉnh địa bàn (chỉ nhà cung cấp chọn được)


def test_customer_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Khách hàng]")
    s = sessions["admin"]
    # Admin tạo ở Hà Nội: người phụ trách tự suy theo người cầm tỉnh (sales4),
    # ô accountOwnerId gửi lên bị bỏ qua -- không cần ghi cứng id người nào.
    base_ok = {"action": "create", "customerName": "Cong ty Kiem Thu " + RUN,
               "customerType": "Nhà mạng viễn thông", "customerGroup": "Tiềm năng",
               "taxCode": "99" + RUN + "01", "phone": "09" + RUN + "01",
               "email": "kiemthu%s@example.vn" % RUN,
               "districtId": WARD_HA_NOI, "addressDetail": "So 1 duong Kiem Thu"}

    # Ca đối chứng theo tài liệu: Sales ĐÃ có tỉnh tạo khách trong tỉnh mình,
    # không có bước chọn người phụ trách -- khách đứng tên chính người tạo.
    s4, _ = login("sales4")
    data = dict(base_ok)
    data.update({"customerName": "Cong ty Sales4 " + RUN, "taxCode": "96" + RUN + "01",
                 "phone": "09" + RUN + "91", "email": "sales4kt%s@example.vn" % RUN})
    r = post(s4, "/customer", data)
    loc = r.headers.get("Location") or ""
    row = T.run("SELECT e.enterprise_code, u.username FROM enterprises e JOIN users u "
                "ON u.user_id = e.account_owner_id WHERE e.tax_code = '%s'" % data["taxCode"])
    code, owner = row[0] if row else ("", "")
    expect("TC_CUSADD_001",
           r.status_code == 302 and "error" not in loc and code.startswith("KH-") and owner == "sales4",
           "sales4 tạo khách ở Hà Nội -> %s; mã %s, người phụ trách %s"
           % (loc or "HTTP %s" % r.status_code, code or "?", owner or "?"),
           "Ca đối chứng: hỏng ca này thì các ca lỗi bên dưới không đáng tin")

    cases = [
        ("TC_CUSADD_002", {"customerName": ""}, "invalid", "bỏ trống tên doanh nghiệp"),
        ("TC_CUSADD_003", {"taxCode": ""}, "invalid", "bỏ trống mã số thuế"),
        # Chỉ tỉnh CHƯA ai cầm mới mở ô người phụ trách cho chọn tay.
        ("TC_CUSADD_004", {"accountOwnerId": "", "districtId": WARD_LAI_CHAU}, "invalid",
         "tỉnh chưa ai cầm, chưa chọn người phụ trách"),
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

    r = post(s, "/customer", base_ok)          # tạo lần đầu
    data = dict(base_ok)                       # lần 2: chỉ trùng mã số thuế
    data.update({"phone": "09" + RUN + "05", "email": "trung%s@example.vn" % RUN})
    r = post(s, "/customer", data)
    loc = r.headers.get("Location") or ""
    expect("TC_CUSADD_012", "error=duplicate_tax_code" in loc,
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

    r = post(s, "/customer", {"action": "evaluate", "id": "999999", "rating": "GOOD"})
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


def contract_row(contract_id):
    """(progress_status, status, signing_date, effective_date, end_date) đọc thẳng CSDL."""
    rows = T.run("SELECT progress_status, status, IFNULL(signing_date,''), "
                 "IFNULL(effective_date,''), IFNULL(end_date,'') FROM contracts "
                 "WHERE contract_id = %d" % int(contract_id))
    return rows[0] if rows else None


def created_id(loc, kind="contract"):
    m = re.search(r"/%s\?action=view&id=(\d+)" % kind, loc or "")
    return m.group(1) if m else None


def follow(session, loc):
    """Mở trang đích của một redirect -- kiểm câu thông báo phải nhìn trang đích."""
    if not loc:
        return ""
    path = loc.split("/POSCS", 1)[-1] if "/POSCS" in loc else loc
    return utf8(session.get(BASE + path))


def test_contract_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Hợp đồng]")
    s = sessions["admin"]
    owner = T.user_id("sale01")
    # Form tạo KHÔNG còn ô ngày (PR #164): thời hạn nhập ở trang Quản lý hợp
    # đồng, ngày ký đóng dấu lúc bấm Ký. Mã hợp đồng do người dùng nhập (V28).
    ok = {"action": "create", "kind": "sell", "contractCode": "KT/%s/HĐKT-TEST" % RUN,
          "title": "Hop dong kiem thu", "contractType": "Cung cấp thiết bị",
          "enterpriseId": "1", "ownerId": owner}
    r = post(s, "/contract", ok)
    loc = r.headers.get("Location") or ""
    draft_id = created_id(loc)
    row = contract_row(draft_id) if draft_id else None
    expect("TC_CTRADD_001",
           r.status_code == 302 and "error" not in loc and row is not None
           and row[0] == "Nháp" and row[2:] == ["", "", ""],
           "tạo hợp đồng không kèm ngày nào -> %s; tiến độ %s, ngày ký/hiệu lực/kết thúc %s"
           % (loc or "HTTP %s" % r.status_code, row[0] if row else "?",
              "trống cả ba" if row and row[2:] == ["", "", ""] else (row[2:] if row else "?")),
           "Ca đối chứng")

    counter = [0]

    def create(**override):
        counter[0] += 1
        data = dict(ok)
        data["contractCode"] = "KT/%s/%02d" % (RUN, counter[0])
        data.update(override)
        r = post(s, "/contract", data)
        return r.headers.get("Location") or ""

    for cid, override, label in [
            ("TC_CTRADD_002", {"title": ""}, "bỏ trống tiêu đề"),
            ("TC_CTRADD_003", {"enterpriseId": ""}, "chưa chọn khách hàng"),
            ("TC_CTRADD_004", {"ownerId": ""}, "chưa chọn người phụ trách"),
            ("TC_CTRADD_005", {"contractCode": ""}, "bỏ trống mã hợp đồng"),
            ("TC_CTRADD_007", {"effectiveDate": "2026-12-31", "endDate": "2026-01-01"},
             "POST thẳng thời hạn ngược")]:
        loc = create(**override)
        expect(cid, "error=invalid" in loc and "kind=sell" in loc, "%s -> %s" % (label, loc))

    loc = create(contractCode="01/2026/HĐKT-POSTEF")
    expect("TC_CTRADD_006", "error=duplicate_code" in loc,
           "trùng mã với hợp đồng demo 01/2026/HĐKT-POSTEF -> %s" % loc)

    loc = create(contractValue="")
    expect("TC_CTRADD_008", "error" not in loc and bool(created_id(loc)),
           "để trống giá trị hợp đồng -> %s" % loc)

    code = "123/%s/HĐKT-POSTEF" % RUN
    loc = create(contractCode=code)
    saved = T.scalar("SELECT contract_code FROM contracts WHERE contract_id = %s"
                     % (created_id(loc) or 0))
    expect("TC_CTRADD_014", saved == code,
           "mã nhập tay %s -> lưu thành %s" % (code, saved))

    # Form tạo: không ô ngày, không ô link PDF, nhãn đối tác theo chiều, khung nhắc.
    form_sell = utf8(s.get(BASE + "/contract?action=new&kind=sell"))
    date_fields = [f for f in ("signDate", "effectiveDate", "endDate")
                   if 'name="%s"' % f in form_sell]
    expect("TC_CTRADD_010", 'name="attachmentUrl"' not in form_sell,
           "form tạo: ô link file PDF %s"
           % ("CÒN" if 'name="attachmentUrl"' in form_sell else "không có"))
    expect("TC_CTRADD_015",
           "-- Chọn khách hàng --" in form_sell and "-- Chọn nhà cung cấp --" not in form_sell,
           "form Hợp đồng bán: lựa chọn đầu ô đối tác %s"
           % ("là \"-- Chọn khách hàng --\"" if "-- Chọn khách hàng --" in form_sell else "KHÔNG đúng"))
    note_ok = ("màn hình sửa" in form_sell and "Ký hợp đồng" in form_sell and not date_fields)
    expect("TC_CTRADD_016", note_ok,
           "form tạo có khung nhắc thời hạn điền ở màn hình sửa: %s; ô ngày còn trên form: %s"
           % ("có" if "màn hình sửa" in form_sell else "KHÔNG", ", ".join(date_fields) or "không"))
    form_buy = utf8(s.get(BASE + "/contract?action=new&kind=buy"))
    loc = create(kind="buy", enterpriseId="")
    expect("TC_CTRADD_018",
           "-- Chọn nhà cung cấp --" in form_buy and "Vui lòng chọn nhà cung cấp." in form_buy
           and "kind=buy" in loc and "error=invalid" in loc,
           "form Hợp đồng mua: nhãn Nhà cung cấp %s; POST thiếu đối tác -> %s"
           % ("có" if "-- Chọn nhà cung cấp --" in form_buy else "KHÔNG", loc))

    # ---- Trang Quản lý hợp đồng (action=update) trên bản nháp vừa tạo ----
    def update(cid_, **override):
        data = dict(ok)
        data.update({"action": "update", "contractId": cid_})
        data.update(override)
        r = post(s, "/contract", data)
        return r.headers.get("Location") or ""

    if draft_id:
        loc = update(draft_id, signDate="2026-01-01")
        row = contract_row(draft_id)
        expect("TC_CTREDIT_004", "error" not in loc and row[2] == "",
               "POST update kèm signDate -> %s; ngày ký sau khi lưu: %s"
               % (loc, row[2] or "vẫn trống"))

        loc = update(draft_id, effectiveDate="2026-12-31", endDate="2026-12-31")
        row = contract_row(draft_id)
        expect("TC_CTRADD_009", "error" not in loc and row[3] == row[4] == "2026-12-31",
               "thời hạn một ngày 31/12/2026 -> %s; lưu %s .. %s" % (loc, row[3], row[4]))

        loc = update(draft_id, effectiveDate="2026-12-31", endDate="2026-01-01")
        row2 = contract_row(draft_id)
        expect("TC_CTREDIT_015", "error=invalid" in loc and row2[3:] == row[3:],
               "POST hiệu lực sau kết thúc -> %s; thời hạn giữ %s .. %s" % (loc, row2[3], row2[4]))

        today = time.strftime("%Y-%m-%d")
        plus10 = time.strftime("%Y-%m-%d", time.localtime(time.time() + 10 * 86400))
        plus90 = time.strftime("%Y-%m-%d", time.localtime(time.time() + 90 * 86400))
        plus365 = time.strftime("%Y-%m-%d", time.localtime(time.time() + 365 * 86400))
        update(draft_id, effectiveDate=plus10, endDate=plus365)
        row = contract_row(draft_id)
        expect("TC_CTRADD_012", row[1] == "Chưa hiệu lực",
               "hiệu lực %s, kết thúc %s -> trạng thái %s" % (plus10, plus365, row[1]))
        loc = update(draft_id, effectiveDate=today, endDate=plus90)
        row = contract_row(draft_id)
        expect("TC_CTREDIT_014", "error" not in loc and row[1] == "Đang hiệu lực",
               "hiệu lực hôm nay, kết thúc +90 ngày -> %s; trạng thái %s" % (loc, row[1]))

        # Tài liệu kèm theo (V34): chỉ lưu link, huỷ phải có lý do.
        r = post(s, "/contract", {"action": "addDocument", "contractId": draft_id,
                                  "docType": "Hợp đồng đã ký", "docTitle": "Ban ky " + RUN,
                                  "fileUrl": "https://drive.google.com/file/d/1AbCdEf/view"})
        loc = r.headers.get("Location") or ""
        doc_id = T.scalar("SELECT document_id FROM contract_documents WHERE contract_id = %s "
                          "AND title = 'Ban ky %s' AND is_deleted = 0" % (draft_id, RUN))
        detail = utf8(s.get(BASE + "/contract?action=view&id=" + draft_id))
        expect("TC_CTRADD_011",
               "error" not in loc and bool(doc_id) and "drive.google.com/file/d/1AbCdEf" in detail,
               "treo link Drive vào bản nháp -> %s; tab Tài liệu %s link"
               % (loc, "có" if "drive.google.com/file/d/1AbCdEf" in detail else "KHÔNG có"))
        r = post(s, "/contract", {"action": "addDocument", "contractId": draft_id,
                                  "docType": "Hợp đồng đã ký", "fileUrl": "ftp://abc"})
        loc = r.headers.get("Location") or ""
        expect("TC_CTREDIT_006", "error=document_invalid" in loc, "link ftp://abc -> %s" % loc)
        r = post(s, "/contract", {"action": "voidDocument", "contractId": draft_id,
                                  "documentId": doc_id or "0", "reason": ""})
        loc = r.headers.get("Location") or ""
        still = T.scalar("SELECT is_deleted FROM contract_documents WHERE document_id = %s"
                         % (doc_id or 0))
        expect("TC_CTREDIT_007", "error=document_reason_required" in loc and still == "0",
               "huỷ tài liệu không lý do -> %s; tài liệu %s"
               % (loc, "vẫn còn" if still == "0" else "đã bị huỷ"))

    # Bản nháp chưa có thời hạn thì chưa ký được.
    nid = created_id(create())
    r = post(s, "/contract", {"action": "changeProgress", "contractId": nid or "0",
                              "toStatus": "Đã ký"})
    loc = r.headers.get("Location") or ""
    row = contract_row(nid) if nid else None
    expect("TC_CTREDIT_017", "error=missing_term" in loc and row is not None and row[0] == "Nháp",
           "ký bản nháp chưa có thời hạn -> %s; tiến độ %s" % (loc, row[0] if row else "?"))

    r = post(s, "/contract", {"action": "update", "contractId": "999999", "title": "X"})
    loc = r.headers.get("Location") or ""
    expect("TC_CTREDIT_008", "notfound" in loc, "sửa hợp đồng id=999999 -> %s" % loc)

    r = post(s, "/contract", {"action": "delete", "id": "999999", "voidReason": "thu"})
    loc = r.headers.get("Location") or ""
    expect("TC_CTRDEL_007", "notfound" in loc, "huỷ bản ghi hợp đồng id=999999 -> %s" % loc)

    # Hàng hoá: kiểm số lượng xảy ra TRƯỚC kiểm tiến độ, nên dùng hợp đồng 1 (đã ký) được.
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
    page = follow(s, loc)
    expect("TC_CTRADDPRD_002", "add_product_invalid" in loc
           and "chọn một sản phẩm và nhập số lượng là số nguyên lớn hơn 0" in page,
           "chưa chọn sản phẩm -> %s; trang hiện đúng câu thông báo: %s"
           % (loc, "có" if "chọn một sản phẩm và nhập số lượng" in page else "KHÔNG"))

    r = post(s, "/contract", {"action": "addProduct", "contractId": "1",
                              "productId": "1", "quantity": "1"})
    loc = r.headers.get("Location") or ""
    expect("TC_CTRADDPRD_010", "error=add_product_failed" in loc,
           "thêm hàng hoá vào hợp đồng Đã ký -> %s" % loc)

    r = post(s, "/contract", {"action": "removeProduct", "contractId": "1",
                              "contractProductId": "999999"})
    loc = r.headers.get("Location") or ""
    page = follow(s, loc)
    expect("TC_CTRDELPRD_003", "error=remove_product_failed" in loc
           and "Không gỡ được hàng hoá này" in page,
           "gỡ dòng hàng hoá không tồn tại -> %s" % loc)

    r = s.get(BASE + "/contract?action=view&id=999999", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_CTRVIEW_007", "notfound" in loc, "xem hợp đồng id=999999 -> %s" % loc)

    r = s.get(BASE + "/contract?action=list&status=hacked", allow_redirects=False)
    expect("TC_CTRLIST_008", r.status_code == 200,
           "status=hacked -> HTTP %s (không lỗi 500)" % r.status_code)


def test_ticket_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Phiếu hỗ trợ]")
    # Vai CSKH đã gộp vào Sales (V37): Sales là vai tiếp nhận phiếu.
    s = sessions["sales"]
    ok = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
          "priority": "Cao", "receptionChannel": "Điện thoại",
          "assignedTechnicianId": T.user_id("tech01"), "description": "Mo ta kiem thu"}
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
               "%s -> %s" % (label, loc or "HTTP %s" % r.status_code))

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

    # Sales (gồm vai CSKH cũ) có toàn quyền với phiếu từ V37 -- ca này trước
    # đây kiểm Sales bị 403.
    row = T.run("SELECT enterprise_id, IFNULL(contract_id,''), ticket_type, reception_channel, "
                "assigned_technician_id, description, status FROM technicalrequests WHERE ticket_id = 3")[0]
    r = post(s, "/ticket", {"action": "update", "ticketId": "3", "enterpriseId": row[0],
                            "contractId": row[1], "contractLoaded": "1", "ticketType": row[2],
                            "priority": "Cao", "receptionChannel": row[3],
                            "assignedTechnicianId": row[4], "description": row[5],
                            "status": row[6]})
    loc = r.headers.get("Location") or ""
    pr = T.scalar("SELECT priority FROM technicalrequests WHERE ticket_id = 3")
    expect("TC_TKEDIT_013", r.status_code == 302 and "error" not in loc and pr == "Cao",
           "Sales sửa mức ưu tiên phiếu 3 -> %s; ưu tiên sau khi lưu: %s"
           % (loc or "HTTP %s" % r.status_code, pr))

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

    # V35: CCCD không còn bắt buộc lúc Admin tạo -- bỏ trống vẫn tạo được.
    data = dict(ok)
    data.update({"citizenId": "", "firstName": "Khong" + RUN, "phone": "091" + RUN + "5",
                 "personalEmail": "khongcccd%s@gmail.com" % RUN})
    r = post(s, "/employee", data)
    loc = r.headers.get("Location") or ""
    expect("TC_EMPADD_005", r.status_code == 302 and "error" not in loc,
           "bỏ trống CCCD -> %s" % loc)

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

    r = post(s, "/employee", {"action": "toggleStatus", "id": T.user_id("admin")})
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

    # Tìm kiếm (PR #154): họ tên ghép liền, và cả số điện thoại.
    # sales4 = Phạm Thị Ngọc Anh: "Ngọc Anh" vắt qua cuối tên đệm + tên.
    for cid, kw, want, label in [
            ("TC_EMPLIST_010", "Ngọc Anh", "sales4", "họ tên ghép qua tên đệm và tên"),
            ("TC_EMPLIST_011", "340004", "sales4", "một phần số điện thoại 0912340004")]:
        page = utf8(s.get(BASE + "/employee", params={"action": "list", "keyword": kw}))
        expect(cid, want in page and "sales2" not in page,
               "keyword=%r (%s) -> %s sales4, %s sales2"
               % (kw, label, "có" if want in page else "KHÔNG có",
                  "lẫn" if "sales2" in page else "không lẫn"))
    page = utf8(s.get(BASE + "/employee", params={"action": "list", "keyword": "a&b #1"}))
    links = re.findall(r'href="[^"]*keyword=([^"&]*)', page)
    expect("TC_EMPLIST_012", bool(links) and all(l == "a%26b+%231" for l in links),
           "keyword='a&b #1' -> %d link mang keyword, giá trị %s"
           % (len(links), sorted(set(links)) or "-"),
           "Kiểm cách mã hoá từ khoá trong các link phân trang / lọc của trang (PR #166)")


def test_product_validation(sessions):
    print("\n[Kiểm tra dữ liệu — Sản phẩm]")
    s = sessions["tech"]
    # Danh mục 7 (Ắc quy) là danh mục LÁ; danh mục cha bị invalid_category.
    r = post(s, "/product", {"action": "create",
                             "productName": "SP kiem thu " + RUN, "categoryId": "7",
                             "description": "Mo ta"})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_001", r.status_code == 302 and "error" not in loc,
           "tạo sản phẩm đủ thông tin hợp lệ -> %s" % (loc or "HTTP %s" % r.status_code),
           "Ca đối chứng")

    for cid, data, label in [
            ("TC_PRDADD_002", {"action": "create", "productName": "", "categoryId": "7"},
             "bỏ trống tên sản phẩm"),
            ("TC_PRDADD_003", {"action": "create", "productName": "SP kiem thu",
                               "categoryId": ""}, "chưa chọn danh mục")]:
        r = post(s, "/product", data)
        loc = r.headers.get("Location") or ""
        expect(cid, r.status_code == 302 and "invalid" in loc,
               "%s -> %s" % (label, loc))

    r = post(s, "/product", {"action": "create", "productName": "SP cha " + RUN,
                             "categoryId": "1"})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_010", "error=invalid_category" in loc,
           "chọn danh mục cha (id 1, có 3 danh mục con) -> %s" % loc)

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
    for role in ("admin", "sales", "tech"):
        s = sessions[role] if role in sessions else login(role)[0]
        r = s.get(BASE + "/dashboard", allow_redirects=False)
        if r.status_code != 200:
            expect("TC_DASH_009", False, "vai trò %s mở Dashboard -> HTTP %s"
                   % (role, r.status_code))
            break
    else:
        expect("TC_DASH_009", True, "cả ba vai trò đều mở được Dashboard (HTTP 200)")

    s = sessions["admin"]
    r = s.get(BASE + "/dashboard", allow_redirects=False)
    page = utf8(r)
    labels = [x for x in ("Tổng khách hàng", "Hợp đồng đang hiệu lực", "Doanh thu hợp đồng")
              if x in page]
    expect("TC_DASH_001", r.status_code == 200 and len(labels) == 3,
           "Dashboard trả HTTP %s; các ô số có mặt: %s" % (r.status_code, ", ".join(labels) or "không ô nào"))

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

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lượt 3: những test case còn lại — các ca biên về ngày, và nhánh OTP cần
chờ theo thời gian thật (đếm ngược 30 giây, hết hạn 5 phút).

Tách riêng vì lượt này chạy chậm (có chỗ phải chờ đủ 5 phút).

Chạy:  python tools/testdoc/run_blackbox_rest.py [BASE_URL] [--log <tomcat.out>]
       [--skip-slow]  bỏ qua các ca phải chờ hết hạn OTP
"""

import json
import os
import pathlib
import re
import sys
import time
from urllib.parse import urljoin

import requests

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import run_blackbox as R            # noqa: E402
import run_blackbox_ui as U         # noqa: E402
import testdb as T                  # noqa: E402

SKIP_SLOW = False


def test_boundaries(s):
    print("\n[Các ca biên & còn lại]")
    # Form tạo không còn ô ngày (PR #164): tạo bản nháp rồi nhập thời hạn ở
    # trang Quản lý hợp đồng (action=update). /contract không còn multipart.
    ok = {"action": "create", "kind": "sell", "title": "HD bien " + R.RUN,
          "contractType": "Cung cấp thiết bị", "enterpriseId": "1",
          "ownerId": T.user_id("sale01")}
    today = time.strftime("%Y-%m-%d")

    def status_of(days):
        code = "BIEN/%s/%d" % (R.RUN, days)
        r = R.post(s, "/contract", dict(ok, contractCode=code))
        m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
        if not m:
            return None
        end = time.strftime("%Y-%m-%d", time.localtime(time.time() + days * 86400))
        R.post(s, "/contract", dict(ok, action="update", contractId=m.group(1), contractCode=code,
                                    effectiveDate=today, endDate=end))
        page = U.html(s.get(R.BASE + "/contract?action=view&id=" + m.group(1)))
        shown = [x for x in ("Sắp hết hạn", "Đang hiệu lực", "Đã hết hạn", "Chưa hiệu lực")
                 if x in U.page_text(page)]
        return T.scalar("SELECT status FROM contracts WHERE contract_id = %s" % m.group(1)), shown

    st30, shown30 = status_of(30) or (None, [])
    U.expect("TC_CTRLIST_003", st30 == "Sắp hết hạn" and "Sắp hết hạn" in shown30,
             "Hợp đồng còn đúng 30 ngày -> trạng thái %r" % st30)
    st31, shown31 = status_of(31) or (None, [])
    U.expect("TC_CTRLIST_004", st31 == "Đang hiệu lực" and "Đang hiệu lực" in shown31,
             "Hợp đồng còn 31 ngày -> trạng thái %r" % st31)

    # Tìm theo số điện thoại: ô tìm kiếm dò mã KH, tên, số điện thoại (không dò
    # mã số thuế) -- lấy SĐT từ CSDL.
    phone = T.scalar("SELECT phone FROM enterprises WHERE enterprise_code = 'KH-0001'")
    page = U.html(s.get(R.BASE + "/customer?action=list&keyword=" + phone))
    names = U.column(page, "Khách hàng") or []
    U.expect("TC_CUSLIST_003", len(names) == 1 and "Sông Hồng" in names[0],
             "Tìm theo số điện thoại %s -> %d dòng: %s" % (phone, len(names), names))

    # cây danh mục mặc định thu gọn
    page = U.html(s.get(R.BASE + "/product?action=list"))
    raw = str(page)
    expanded = len(re.findall(r'aria-expanded="true"', raw))
    U.expect("TC_PRDLIST_003", expanded == 0,
             "Cây danh mục: %d nhánh đang mở sẵn" % expanded)

    # mã sản phẩm tăng liên tiếp (danh mục 7 là danh mục lá; không còn ô đơn giá)
    st, _ = R.login("tech")
    codes = []
    for i in range(2):
        r = U.upload(st, "/product",
                     {"action": "create", "productName": "SP ma %s %d" % (R.RUN, i),
                      "categoryId": "7"}, {})
        m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
        if m:
            code = T.scalar("SELECT product_code FROM products WHERE product_id = %s" % m.group(1))
            if code:
                codes.append(code.split("-")[-1])
    ok_seq = (len(codes) == 2 and int(codes[1]) == int(codes[0]) + 1
              and len(codes[1]) == len(codes[0]))
    U.expect("TC_PRDADD_004", ok_seq,
             "Hai sản phẩm tạo liên tiếp có mã SP-%s -> SP-%s"
             % (codes[0] if codes else "?", codes[1] if len(codes) > 1 else "?"))

    # phiếu quá hạn SLA được đánh dấu (Sales tiếp nhận -- vai CSKH đã gộp vào Sales)
    sc, _ = R.login("sales")
    past = time.strftime("%Y-%m-%dT%H:%M", time.localtime(time.time() - 3 * 86400))
    R.post(sc, "/ticket", {"action": "create", "enterpriseId": "1",
                           "ticketType": "Lỗi phần mềm", "priority": "Cao",
                           "receptionChannel": "Điện thoại",
                           "assignedTechnicianId": T.user_id("tech01"),
                           "slaDeadline": past,
                           "description": "Qua han SLA " + R.RUN})
    page = U.html(sc.get(R.BASE + "/ticket?action=list&assignee=all&keyword=" + R.RUN))
    raw = str(page)
    marked = bool(re.search(r"(overdue|qu[áa] h[ạa]n|text-danger|bg-danger|sla-late)",
                            raw, re.I))
    U.expect("TC_TKLIST_008", marked,
             "Phiếu quá hạn SLA %s được đánh dấu nổi bật trên danh sách"
             % ("CÓ" if marked else "KHÔNG"))

    # AJAX: khách hàng chưa có hợp đồng + escape ký tự đặc biệt
    r = U.upload(s, "/customer",
                 {"action": "create", "customerName": "Cty chua HD " + R.RUN,
                  "customerType": "Nhà mạng viễn thông", "customerGroup": "Tiềm năng",
                  "taxCode": "74" + R.RUN, "phone": "0974" + R.RUN,
                  "email": "nc%s@example.vn" % R.RUN,
                  "districtId": "1", "addressDetail": "So 1"}, {})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if m:
        rr = s.get(R.BASE + "/contract/byEnterprise?enterpriseId=" + m.group(1))
        try:
            data = rr.json()
        except ValueError:
            data = None
        U.expect("TC_AJAX_007", data == [],
                 "Khách hàng chưa có hợp đồng -> HTTP %s, body %r"
                 % (rr.status_code, rr.text.strip()[:40]))

        R.post(s, "/contract", dict(ok, contractCode="AJAX/%s" % R.RUN,
                                    title='Hop dong "ABC" ' + R.RUN, enterpriseId=m.group(1)))
        rr = s.get(R.BASE + "/contract/byEnterprise?enterpriseId=" + m.group(1))
        try:
            data = rr.json()
            parsed = True
        except ValueError:
            data, parsed = None, False
        U.expect("TC_AJAX_009", parsed and any('"ABC"' in str(x) for x in (data or [])),
                 "Tiêu đề chứa dấu nháy kép: JSON %s phân tích được, %d phần tử"
                 % ("" if parsed else "KHÔNG", len(data or [])))
    else:
        U.record("TC_AJAX_007", "N/A", "Không tạo được khách hàng mới", "")
        U.record("TC_AJAX_009", "N/A", "Phụ thuộc TC_AJAX_007", "")


def test_otp_timing():
    print("\n[OTP — các ca phụ thuộc thời gian]")
    # Quên mật khẩu tra theo USERNAME (V36); mã gửi tới personal_email trong hồ sơ.
    username = "sale01"
    email = T.scalar("SELECT personal_email FROM users WHERE username = '%s'" % username)

    s = requests.Session()
    t = R.csrf(s, "/forgotPassword.jsp")
    before = U.count_log(r"Ma OTP: \d{6}")
    s.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
           data={"username": username, "csrfToken": t})
    time.sleep(0.5)
    if U.count_log(r"Ma OTP: \d{6}") == before:
        for cid in ("TC_OTP_003", "TC_OTP_004", "TC_RESEND_001", "TC_RESEND_003"):
            U.record(cid, "N/A",
                     "Hạn mức 10 yêu cầu OTP/IP trong 15 phút đã cạn",
                     "Khởi động lại Tomcat rồi chạy lại lượt này trước")
        return
    otp1 = U.otp_from_log(email)

    # chờ hết đếm ngược 30 giây rồi gửi lại
    print("  ... chờ 32 giây cho hết đếm ngược gửi lại")
    time.sleep(32)
    before = U.count_log(r"Ma OTP: \d{6}")
    t = R.csrf(s, "/verifyOtp.jsp")
    r = s.post(R.BASE + "/ResendOtpServlet", allow_redirects=False,
               data={"csrfToken": t})
    time.sleep(0.5)
    otp2 = U.otp_from_log(email)
    U.expect("TC_RESEND_001",
             U.count_log(r"Ma OTP: \d{6}") == before + 1 and otp2 != otp1,
             "Gửi lại sau 30 giây -> %s, mã mới %s khác mã cũ %s"
             % (r.headers.get("Location"), otp2, otp1))

    t = R.csrf(s, "/verifyOtp.jsp")
    r = s.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
               data={"otpCode": otp1, "csrfToken": t})
    loc = r.headers.get("Location") or ""
    U.expect("TC_RESEND_003", "invalid_otp" in loc,
             "Nhập mã CŨ sau khi đã gửi lại -> %s" % loc)

    for i in range(5):
        t = R.csrf(s, "/verifyOtp.jsp")
        r = s.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
                   data={"otpCode": "000000", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    # Mở cả trang đích, không chỉ nhìn URL: từng có lúc URL đúng mà trang đó
    # đá tiếp về bước 1, thông báo không bao giờ hiện, ca này vẫn "Đạt".
    shown = U.page_text(U.html(s.get(urljoin(R.BASE + "/", loc)))) if loc else ""
    msg = re.search(r"Bạn đã nhập sai mã OTP quá nhiều lần[^.]*\.", shown)
    U.expect("TC_OTP_003", "too_many_attempts" in loc and msg is not None,
             "Nhập sai 5 lần liên tiếp -> %s, trang báo: %s"
             % (loc, msg.group(0) if msg else "(không thấy thông báo)"))

    if SKIP_SLOW:
        U.record("TC_OTP_004", "N/A", "Bỏ qua do chạy với --skip-slow",
                 "Chạy lại không kèm cờ này để kiểm ca hết hạn 5 phút")
        return

    s2 = requests.Session()
    t = R.csrf(s2, "/forgotPassword.jsp")
    before = U.count_log(r"Ma OTP: \d{6}")
    s2.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
            data={"username": username, "csrfToken": t})
    time.sleep(0.5)
    if U.count_log(r"Ma OTP: \d{6}") == before:
        U.record("TC_OTP_004", "N/A", "Hết hạn mức OTP trước khi kiểm được", "")
        return
    otp3 = U.otp_from_log(email)
    print("  ... chờ 5 phút 5 giây cho mã OTP hết hạn")
    time.sleep(305)
    t = R.csrf(s2, "/verifyOtp.jsp")
    r = s2.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
                data={"otpCode": otp3, "csrfToken": t})
    loc = r.headers.get("Location") or ""
    U.expect("TC_OTP_004", "expired" in loc,
             "Nhập đúng mã %s sau 5 phút -> %s" % (otp3, loc))




def reseed():
    """Nạp lại tài khoản mẫu trước khi chạy lượt này."""
    sql = pathlib.Path(__file__).with_name("blackbox") / "fixtures.sql"
    try:
        T.run(sql.read_text(encoding="utf-8"))
        return True
    except Exception as ex:          # noqa: BLE001 -- chỉ để báo, không dừng lượt chạy
        print("  !! nap fixtures loi:", ex)
        return False


def test_special_fixtures(s):
    """Các ca cần dữ liệu nền không dựng được qua giao diện."""
    print("\n[Dữ liệu nền đặc biệt]")

    # --- file log rỗng
    log_dir = None
    page = U.html(s.get(R.BASE + "/systemLog"))
    m = re.search(r"([A-Za-z]:\\[^<>\"']+?logs?)\\?\\?", str(page))
    if not m:
        m = re.search(r"([A-Za-z]:\\[^<>\"'\s]+logs)", str(page))
    if m:
        log_dir = pathlib.Path(m.group(1))
    if log_dir and log_dir.is_dir():
        empty = log_dir / "poscs-rong.log"
        empty.write_text("", encoding="utf-8")
        r = s.get(R.BASE + "/systemLog?file=" + empty.name)
        U.expect("TC_LOG_011", r.status_code == 200,
                 "Chọn file log rỗng -> HTTP %s, khung nội dung để trống, không lỗi"
                 % r.status_code)
        empty.unlink(missing_ok=True)
    else:
        U.record("TC_LOG_011", "N/A",
                 "Không đọc được đường dẫn thư mục log trên trang", "")

    # --- file .svg và file không phần mở rộng đặt thẳng vào thư mục uploads
    upload_dir = None
    if U.TOMCAT_LOG is not None:
        guess = U.TOMCAT_LOG.parent / "uploads"
        if guess.is_dir():
            upload_dir = guess
    if os.environ.get("UPLOAD_DIR"):
        guess = pathlib.Path(os.environ["UPLOAD_DIR"])
        upload_dir = guess if guess.is_dir() else None
    if upload_dir:
        svg = upload_dir / "kiemthu.svg"
        svg.write_bytes(U.SVG)
        r = s.get(R.BASE + "/uploads/" + svg.name)
        ctype = (r.headers.get("Content-Type") or "").lower()
        disp = (r.headers.get("Content-Disposition") or "").lower()
        U.expect("TC_UPLOAD_005",
                 r.status_code != 200 or "svg" not in ctype or "attachment" in disp,
                 "Mở file .svg trong uploads: HTTP %s, type %r, %s"
                 % (r.status_code, ctype, disp or "không có Content-Disposition"))
        svg.unlink(missing_ok=True)

        noext = upload_dir / "khongduoi"
        noext.write_bytes(b"noi dung bat ky")
        r = s.get(R.BASE + "/uploads/" + noext.name)
        ctype = (r.headers.get("Content-Type") or "").lower()
        U.expect("TC_UPLOAD_006",
                 r.status_code != 200 or "html" not in ctype,
                 "Mở file không phần mở rộng: HTTP %s, type %r"
                 % (r.status_code, ctype))
        noext.unlink(missing_ok=True)
    else:
        U.record("TC_UPLOAD_005", "N/A", "Không xác định được thư mục uploads", "")
        U.record("TC_UPLOAD_006", "N/A", "Không xác định được thư mục uploads", "")


def test_otp_quota():
    """ĐỂ SAU test_otp_timing: ca này làm cạn hạn mức 10 yêu cầu/IP trong 15 phút."""
    print("\n[Hạn mức yêu cầu OTP]")
    quota_user = "tech01"
    burned = 0
    for _ in range(12):
        sx = requests.Session()
        tx = R.csrf(sx, "/forgotPassword.jsp")
        before = U.count_log(r"Ma OTP: \d{6}")
        sx.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                data={"username": quota_user, "csrfToken": tx})
        time.sleep(0.2)
        if U.count_log(r"Ma OTP: \d{6}") > before:
            burned += 1
    last = requests.Session()
    tl = R.csrf(last, "/forgotPassword.jsp")
    before = U.count_log(r"Ma OTP: \d{6}")
    rl = last.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                   data={"username": quota_user, "csrfToken": tl})
    time.sleep(0.4)
    U.expect("TC_FORGOT_004",
             burned <= 10 and U.count_log(r"Ma OTP: \d{6}") == before,
             "Gửi 13 yêu cầu: chỉ %d lần thực sự sinh mã; yêu cầu sau hạn mức vẫn "
             "chuyển hướng bình thường (%s) nhưng không gửi thêm mã"
             % (burned, rl.headers.get("Location")))


def test_login_lockout():
    """ĐỂ CUỐI CÙNG: khoá IP 15 phút, chạy sớm thì chặn mọi ca đăng nhập sau."""
    print("\n[Khoá IP sau 5 lần đăng nhập sai]")
    user, pwd = R.ACCOUNTS["sales"]
    for i in range(5):
        sx = requests.Session()
        t = R.csrf(sx, "/login.jsp")
        sx.post(R.BASE + "/login", allow_redirects=False,
                data={"username": user, "password": "SaiMatKhau%d" % i,
                      "csrfToken": t})
    sx = requests.Session()
    t = R.csrf(sx, "/login.jsp")
    r = sx.post(R.BASE + "/login", allow_redirects=False,
                data={"username": user, "password": pwd, "csrfToken": t})
    loc = r.headers.get("Location") or ""
    U.expect("TC_LOGIN_006", "dashboard" not in loc,
             "Sai 5 lần rồi nhập ĐÚNG mật khẩu -> %s (vẫn bị từ chối)" % loc)

    if SKIP_SLOW:
        U.record("TC_LOGIN_007", "N/A", "Bỏ qua do chạy với --skip-slow",
                 "Ca này phải chờ hết 15 phút khoá")
        U.record("TC_LOGIN_008", "N/A", "Phụ thuộc TC_LOGIN_007", "")
        return

    print("  ... chờ 15 phút 15 giây cho hết thời gian khoá IP")
    time.sleep(915)
    sx = requests.Session()
    t = R.csrf(sx, "/login.jsp")
    r = sx.post(R.BASE + "/login", allow_redirects=False,
                data={"username": user, "password": pwd, "csrfToken": t})
    loc = r.headers.get("Location") or ""
    U.expect("TC_LOGIN_007", "dashboard" in loc,
             "Sau 15 phút, đăng nhập đúng -> %s" % loc)

    for i in range(4):
        sx = requests.Session()
        t = R.csrf(sx, "/login.jsp")
        sx.post(R.BASE + "/login", allow_redirects=False,
                data={"username": user, "password": "LaiSai%d" % i,
                      "csrfToken": t})
    sx = requests.Session()
    t = R.csrf(sx, "/login.jsp")
    r = sx.post(R.BASE + "/login", allow_redirects=False,
                data={"username": user, "password": pwd, "csrfToken": t})
    loc = r.headers.get("Location") or ""
    U.expect("TC_LOGIN_008", "dashboard" in loc,
             "Sai 4 lần sau một lần đúng -> vẫn vào được: %s" % loc)


def main():
    global SKIP_SLOW
    args = sys.argv[1:]
    if "--skip-slow" in args:
        SKIP_SLOW = True
        args.remove("--skip-slow")
    if "--log" in args:
        i = args.index("--log")
        U.TOMCAT_LOG = pathlib.Path(args[i + 1])
        del args[i:i + 2]
    if args:
        R.BASE = args[0].rstrip("/")
    print("Muc tieu:", R.BASE, "| log:", U.TOMCAT_LOG)

    if R.OUT.is_file():
        R.results.update(json.loads(R.OUT.read_text(encoding="utf-8")))
        print("Nap %d ket qua cua cac luot truoc" % len(R.results))

    # Lượt 2 có ca đổi mật khẩu THẬT (TC_CHGPWD_001) và khoá tài khoản thật, nên
    # tới lượt này mật khẩu trong CSDL đã khác hằng số trong ACCOUNTS. Không nạp
    # lại thì login() im lặng thất bại và mọi ca phía sau trượt oan.
    if reseed():
        print("Da nap lai blackbox/fixtures.sql")
    else:
        print("CANH BAO: khong nap lai duoc fixtures.sql, ket qua co the sai")

    s, _ = R.login("admin")
    if s.get(R.BASE + "/dashboard", allow_redirects=False).status_code != 200:
        sys.exit("Khong dang nhap duoc bang tai khoan admin -- kiem tra fixtures")
    test_boundaries(s)
    test_special_fixtures(s)
    test_otp_timing()
    test_otp_quota()       # làm cạn hạn mức -> phải chạy sau test_otp_timing
    test_login_lockout()   # khoá IP 15 phút -> phải là phần cuối cùng

    R.OUT.write_text(json.dumps(R.results, ensure_ascii=False, indent=2),
                     encoding="utf-8")
    counts = {}
    for v in R.results.values():
        counts[v["status"]] = counts.get(v["status"], 0) + 1
    print("\nDa ghi: %s" % R.OUT)
    print("Tong cong: %d test case | %s" % (len(R.results), counts))


if __name__ == "__main__":
    main()

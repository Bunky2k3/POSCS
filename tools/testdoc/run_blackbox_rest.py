#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lượt 3: những test case còn lại — nhập hợp đồng từ PDF, các ca biên về ngày,
và nhánh OTP cần chờ theo thời gian thật (đếm ngược 30 giây, hết hạn 5 phút).

Tách riêng vì lượt này chạy chậm (có chỗ phải chờ đủ 5 phút) và vì phần nhập
PDF cần điền vào đúng mẫu do ứng dụng phát ra.

Chạy:  python tools/testdoc/run_blackbox_rest.py [BASE_URL] [--log <tomcat.out>]
       [--skip-slow]  bỏ qua các ca phải chờ hết hạn OTP
"""

import io
import json
import os
import pathlib
import re
import sys
import time

import requests
from pypdf import PdfReader, PdfWriter

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import run_blackbox as R            # noqa: E402
import run_blackbox_ui as U         # noqa: E402

SKIP_SLOW = False


def fill_template(pdf_bytes, values):
    """Điền vào đúng mẫu PDF do /contract?action=downloadImportTemplate phát ra."""
    reader = PdfReader(io.BytesIO(pdf_bytes))
    writer = PdfWriter()
    writer.append(reader)
    for page in writer.pages:
        try:
            writer.update_page_form_field_values(page, values)
        except Exception:
            pass
    writer.set_need_appearances_writer(True)
    out = io.BytesIO()
    writer.write(out)
    return out.getvalue()


def import_result(r):
    """(thành công?, thông báo hiện trên trang).

    Handler KHÔNG redirect: cả khi thành công lẫn khi lỗi đều forward lại
    importcontract.jsp, chỉ khác nội dung thông báo. Nên phải đọc chữ trên
    trang chứ không nhìn mã HTTP.
    """
    if r.status_code == 302:
        loc = r.headers.get("Location") or ""
        return "error" not in loc, "HTTP 302 -> %s" % loc
    r.encoding = "utf-8"
    text = re.sub(r"\s+", " ", U.page_text(U.html(r)))
    ok = "Đã tạo hợp đồng" in text
    marker = re.search(r"(Đã tạo hợp đồng[^.]{0,60}|Không nhập được[^.]{0,140}|"
                       r"Vui lòng chọn file[^.]{0,40}|File không đúng mẫu[^.]{0,80})",
                       text)
    return ok, "HTTP %s, trang báo: %r" % (
        r.status_code, marker.group(1).strip()[:110] if marker else "(không rõ)")


def test_import_pdf(s):
    print("\n[Nhập hợp đồng từ PDF]")
    r = s.get(R.BASE + "/contract?action=downloadImportTemplate")
    tmpl = r.content
    U.expect("TC_CTRIMPORT_001",
             r.status_code == 200 and tmpl[:5] == b"%PDF-",
             "Tải mẫu PDF: HTTP %s, %s, %d byte"
             % (r.status_code, r.headers.get("Content-Type"), len(tmpl)))

    # Các ô /Ch chỉ nhận đúng giá trị trong danh sách của mẫu (xem /Opt):
    # contractType, buyerType, buyerGroup, buyerProvince.
    good = {"title": "HD nhap PDF " + R.RUN,
            "contractType": "Bảo trì bảo dưỡng",
            "signDate": "01/01/2026", "effectiveDate": "05/01/2026",
            "endDate": "31/12/2026", "ownerUsername": "sale01",
            "buyerTax": "77" + R.RUN, "buyerName": "Cty nhap PDF " + R.RUN,
            "buyerType": "Nhà mạng viễn thông", "buyerGroup": "Tiềm năng",
            "buyerProvince": "Thành phố Hà Nội",
            # Khách hàng chưa có trong hệ thống sẽ được tạo mới, nên bắt buộc
            # điền đủ Tỉnh/Thành + Xã/Phường + Địa chỉ chi tiết.
            "buyerWard": "Phường Ba Đình",
            "buyerAddressDetail": "So 1 duong Kiem Thu",
            "buyerEmail": "pdf%s@example.vn" % R.RUN,
            "buyerPhone": "0977" + R.RUN}

    r = U.upload(s, "/contract", {"action": "importPdf"},
                 {"file": ("hopdong.pdf", fill_template(tmpl, good),
                           "application/pdf")})
    ok, detail = import_result(r)
    U.expect("TC_CTRIMPORT_002", ok, "Nhập từ file điền đúng mẫu: %s" % detail)

    r = U.upload(s, "/contract", {"action": "importPdf"},
                 {"file": ("batky.pdf", U.PDF_MIN, "application/pdf")})
    ok, detail = import_result(r)
    U.expect("TC_CTRIMPORT_003", not ok, "File PDF không theo mẫu: %s" % detail)

    r = U.upload(s, "/contract", {"action": "importPdf"},
                 {"file": ("hopdong.docx", b"PK\x03\x04docx",
                           "application/msword")})
    ok, detail = import_result(r)
    U.expect("TC_CTRIMPORT_004", not ok, "File không phải PDF: %s" % detail)

    r = U.upload(s, "/contract", {"action": "importPdf"}, {})
    ok, detail = import_result(r)
    U.expect("TC_CTRIMPORT_005", not ok, "Không chọn file: %s" % detail)

    missing = dict(good)
    missing["title"] = ""
    missing["buyerName"] = "Cty thieu tieu de " + R.RUN
    r = U.upload(s, "/contract", {"action": "importPdf"},
                 {"file": ("thieu.pdf", fill_template(tmpl, missing),
                           "application/pdf")})
    ok, detail = import_result(r)
    U.expect("TC_CTRIMPORT_006", not ok, "File thiếu ô Tiêu đề: %s" % detail)

    bad_date = dict(good)
    bad_date["effectiveDate"] = "31/12/2026"
    bad_date["endDate"] = "01/01/2026"
    bad_date["buyerTax"] = "76" + R.RUN
    bad_date["buyerName"] = "Cty sai ngay " + R.RUN
    r = U.upload(s, "/contract", {"action": "importPdf"},
                 {"file": ("saingay.pdf", fill_template(tmpl, bad_date),
                           "application/pdf")})
    ok, detail = import_result(r)
    U.expect("TC_CTRIMPORT_007", not ok, "File có ngày hiệu lực sau ngày kết thúc: %s" % detail)


def test_boundaries(s):
    print("\n[Các ca biên & còn lại]")
    ok = {"action": "create", "title": "HD bien " + R.RUN,
          "contractType": "Bảo trì", "enterpriseId": "1", "ownerId": "15",
          "signDate": time.strftime("%Y-%m-%d"),
          "effectiveDate": time.strftime("%Y-%m-%d")}

    def status_of(days):
        d = dict(ok)
        d["title"] = "HD %s ngay %s" % (days, R.RUN)
        d["endDate"] = time.strftime("%Y-%m-%d",
                                     time.localtime(time.time() + days * 86400))
        r = U.upload(s, "/contract", d, {})
        m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
        if not m:
            return None, r.headers.get("Location")
        txt = U.page_text(U.html(s.get(R.BASE + "/contract?action=view&id=" + m.group(1))))
        for label in ("Sắp hết hạn", "Đang hiệu lực", "Đã hết hạn", "Chưa hiệu lực"):
            if label in txt:
                return label, m.group(1)
        return None, m.group(1)

    st30, _ = status_of(30)
    U.expect("TC_CTRLIST_003", st30 == "Sắp hết hạn",
             "Hợp đồng còn đúng 30 ngày -> trạng thái %r" % st30)
    st31, _ = status_of(31)
    U.expect("TC_CTRLIST_004", st31 == "Đang hiệu lực",
             "Hợp đồng còn 31 ngày -> trạng thái %r" % st31)

    # hợp đồng có link http thường (không phải Drive)
    d = dict(ok)
    d["title"] = "HD link thuong " + R.RUN
    d["endDate"] = "2027-12-31"
    d["attachmentUrl"] = "https://example.com/hopdong.pdf"
    r = U.upload(s, "/contract", d, {})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if m:
        page = U.html(s.get(R.BASE + "/contract?action=view&id=" + m.group(1)))
        iframe = page.find("iframe")
        link = [a.get("href") for a in page.find_all("a")
                if "example.com" in (a.get("href") or "")]
        U.expect("TC_CTRVIEW_005", bool(link) and iframe is None,
                 "Link http thường: %s link bấm được, %s khung xem trước"
                 % ("có" if link else "không có",
                    "không dựng" if iframe is None else "VẪN dựng"))
    else:
        U.record("TC_CTRVIEW_005", "N/A", "Không tạo được hợp đồng có link", "")

    # tìm khách hàng theo mã số thuế
    page = U.html(s.get(R.BASE + "/customer?action=list"))
    codes = re.findall(r"\b\d{10,13}\b", U.listing_text(page))
    if codes:
        page = U.html(s.get(R.BASE + "/customer?action=list&keyword=" + codes[0]))
        U.expect("TC_CUSLIST_003", U.body_rows(page) >= 1,
                 "Tìm theo mã số thuế %s -> %d dòng" % (codes[0], U.body_rows(page)))
    else:
        U.record("TC_CUSLIST_003", "N/A", "Không đọc được mã số thuế trên danh sách", "")

    # xem khách hàng đã xoá mềm
    cus = {"action": "create", "customerName": "Cty xoa mem " + R.RUN,
           "customerType": "Doanh nghiệp", "customerGroup": "Khách hàng mới",
           "taxCode": "75" + R.RUN, "phone": "0975" + R.RUN,
           "email": "xm%s@example.vn" % R.RUN, "accountOwnerId": "15",
           "districtId": "1", "addressDetail": "So 1"}
    r = U.upload(s, "/customer", cus, {})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if m:
        R.post(s, "/customer", {"action": "delete", "id": m.group(1)})
        after = s.get(R.BASE + "/customer?action=view&id=" + m.group(1),
                      allow_redirects=False)
        loc = after.headers.get("Location") or ""
        U.expect("TC_CUSVIEW_008",
                 after.status_code == 302 and "notfound" in loc,
                 "Mở chi tiết khách hàng đã xoá mềm -> HTTP %s %s"
                 % (after.status_code, loc))
    else:
        U.record("TC_CUSVIEW_008", "N/A", "Không tạo được khách hàng để xoá", "")

    # cây danh mục mặc định thu gọn
    page = U.html(s.get(R.BASE + "/product?action=list"))
    raw = str(page)
    expanded = len(re.findall(r'aria-expanded="true"', raw))
    U.expect("TC_PRDLIST_003", expanded == 0,
             "Cây danh mục: %d nhánh đang mở sẵn" % expanded)

    # mã sản phẩm tăng liên tiếp
    st, _ = R.login("tech")
    codes = []
    for i in range(2):
        r = U.upload(st, "/product",
                     {"action": "create", "productName": "SP ma %s %d" % (R.RUN, i),
                      "categoryId": "1", "unitPrice": "1000"}, {})
        m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
        if m:
            txt = U.page_text(U.html(st.get(R.BASE + "/product?action=view&id=" + m.group(1))))
            found = re.search(r"SP-(\d+)", txt)
            if found:
                codes.append(found.group(1))
    ok_seq = (len(codes) == 2 and int(codes[1]) == int(codes[0]) + 1
              and len(codes[1]) == len(codes[0]))
    U.expect("TC_PRDADD_004", ok_seq,
             "Hai sản phẩm tạo liên tiếp có mã %s -> %s"
             % (codes[0] if codes else "?", codes[1] if len(codes) > 1 else "?"))

    # phiếu quá hạn SLA được đánh dấu
    sc, _ = R.login("cskh")
    past = time.strftime("%Y-%m-%dT%H:%M",
                         time.localtime(time.time() - 3 * 86400))
    r = R.post(sc, "/ticket", {"action": "create", "enterpriseId": "1",
                               "ticketType": "Lỗi phần mềm", "priority": "Cao",
                               "receptionChannel": "Điện thoại",
                               "assignedTechnicianId": "16",
                               "slaDeadline": past,
                               "description": "Qua han SLA " + R.RUN})
    page = U.html(sc.get(R.BASE + "/ticket?action=list&keyword=" + R.RUN))
    raw = str(page)
    marked = bool(re.search(r"(overdue|qu[áa] h[ạa]n|text-danger|bg-danger|sla-late)",
                            raw, re.I))
    U.expect("TC_TKLIST_008", marked,
             "Phiếu quá hạn SLA %s được đánh dấu nổi bật trên danh sách"
             % ("CÓ" if marked else "KHÔNG"))

    # AJAX: khách hàng chưa có hợp đồng + escape ký tự đặc biệt
    r = U.upload(s, "/customer",
                 {"action": "create", "customerName": "Cty chua HD " + R.RUN,
                  "customerType": "Doanh nghiệp", "customerGroup": "Khách hàng mới",
                  "taxCode": "74" + R.RUN, "phone": "0974" + R.RUN,
                  "email": "nc%s@example.vn" % R.RUN, "accountOwnerId": "15",
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

        d = dict(ok)
        d["title"] = 'Hop dong "ABC" ' + R.RUN
        d["enterpriseId"] = m.group(1)
        d["endDate"] = "2027-12-31"
        U.upload(s, "/contract", d, {})
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
    email = "sale01@poscs.vn"

    s = requests.Session()
    t = R.csrf(s, "/forgotPassword.jsp")
    before = U.count_log(r"Ma OTP: \d{6}")
    s.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
           data={"email": email, "csrfToken": t})
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
    U.expect("TC_OTP_003", "too_many_attempts" in loc,
             "Nhập sai 5 lần liên tiếp -> %s" % loc)

    if SKIP_SLOW:
        U.record("TC_OTP_004", "N/A", "Bỏ qua do chạy với --skip-slow",
                 "Chạy lại không kèm cờ này để kiểm ca hết hạn 5 phút")
        return

    s2 = requests.Session()
    t = R.csrf(s2, "/forgotPassword.jsp")
    before = U.count_log(r"Ma OTP: \d{6}")
    s2.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
            data={"email": email, "csrfToken": t})
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




def mysql(sql):
    """Chạy một câu lệnh trên CSDL kiểm thử để dựng dữ liệu nền đặc biệt."""
    import subprocess
    exe = r"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe"
    if not pathlib.Path(exe).is_file():
        return False
    try:
        subprocess.run([exe, "-h127.0.0.1", "-uroot", "-p1234",
                        "--default-character-set=utf8mb4", "poscs_bbtest",
                        "-e", sql], capture_output=True, timeout=30)
        return True
    except Exception:
        return False


def test_special_fixtures(s):
    """Các ca cần dữ liệu nền không dựng được qua giao diện."""
    print("\n[Dữ liệu nền đặc biệt]")

    # --- nhân viên không có email cá nhân (form bắt buộc ô này nên phải sửa DB)
    emp = {"action": "create", "lastName": "Nguyen", "firstName": "NoMail" + R.RUN,
           "citizenId": "0055" + R.RUN, "gender": "Nam",
           "dateOfBirth": "1995-01-01", "hireDate": "2024-01-01",
           "roleId": "2", "departmentId": "2", "districtId": "1",
           "addressDetail": "So 1", "personalEmail": "nm%s@gmail.com" % R.RUN,
           "phone": "0955" + R.RUN}
    r = R.post(s, "/employee", emp)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if m and mysql("UPDATE users SET personal_email = NULL WHERE user_id = %s"
                   % m.group(1)):
        r = R.post(s, "/employee", {"action": "sendAccount", "id": m.group(1)})
        loc = r.headers.get("Location") or ""
        U.expect("TC_EMPSEND_002", "no_personal_email" in loc,
                 "Nhân viên không có email cá nhân -> %s (mật khẩu giữ nguyên)"
                 % loc)
    else:
        U.record("TC_EMPSEND_002", "N/A",
                 "Không dựng được nhân viên thiếu email cá nhân", "")

    # --- tài khoản bị khoá trong lúc form đổi mật khẩu đang mở
    emp2 = dict(emp)
    emp2["firstName"] = "Locked" + R.RUN
    emp2["citizenId"] = "0056" + R.RUN
    emp2["phone"] = "0956" + R.RUN
    emp2["personalEmail"] = "lk%s@gmail.com" % R.RUN
    r = R.post(s, "/employee", emp2)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    done = False
    if m:
        uid = m.group(1)
        ADMIN_HASH = "$2a$10$nCKNnrIKggQ57ZBBItt.1.iPzP0QDo2eQQzpzpx/DO.wfZQ8VWJZO"
        mysql("UPDATE users SET password_hash = %s WHERE user_id = %s"
              % (repr(ADMIN_HASH).replace('"', "'"), uid))
        page = U.html(s.get(R.BASE + "/employee?action=view&id=" + uid))
        uname = re.search(r"(?:Tên đăng nhập|Username)[: ]+(\S+)",
                          U.page_text(page))
        if uname:
            victim = requests.Session()
            t = R.csrf(victim, "/login.jsp")
            rr = victim.post(R.BASE + "/login", allow_redirects=False,
                             data={"username": uname.group(1),
                                   "password": "Admin@123", "csrfToken": t})
            if "dashboard" in (rr.headers.get("Location") or ""):
                token = R.csrf(victim)          # mở sẵn form đổi mật khẩu
                R.post(s, "/employee", {"action": "toggleStatus", "id": uid})
                rr = victim.post(R.BASE + "/changePassword", allow_redirects=False,
                                 data={"oldPassword": "Admin@123",
                                       "newPassword": "MatKhauKhac@1",
                                       "confirmPassword": "MatKhauKhac@1",
                                       "csrfToken": token})
                loc = rr.headers.get("Location") or ""
                U.expect("TC_CHGPWD_007",
                         rr.status_code == 302 and "login" in loc,
                         "Bị khoá giữa lúc form đang mở, bấm Xác nhận -> HTTP %s %s"
                         % (rr.status_code, loc))
                done = True
    if not done:
        U.record("TC_CHGPWD_007", "N/A",
                 "Không dựng được tài khoản để khoá giữa chừng", "")

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
    if upload_dir is None and os.environ.get("UPLOAD_DIR"):
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
    quota_email = "tech01@poscs.vn"
    burned = 0
    for _ in range(12):
        sx = requests.Session()
        tx = R.csrf(sx, "/forgotPassword.jsp")
        before = U.count_log(r"Ma OTP: \d{6}")
        sx.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                data={"email": quota_email, "csrfToken": tx})
        time.sleep(0.2)
        if U.count_log(r"Ma OTP: \d{6}") > before:
            burned += 1
    last = requests.Session()
    tl = R.csrf(last, "/forgotPassword.jsp")
    before = U.count_log(r"Ma OTP: \d{6}")
    rl = last.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                   data={"email": quota_email, "csrfToken": tl})
    time.sleep(0.4)
    U.expect("TC_FORGOT_005",
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
    U.expect("TC_LOGIN_007", "dashboard" not in loc,
             "Sai 5 lần rồi nhập ĐÚNG mật khẩu -> %s (vẫn bị từ chối)" % loc)

    if SKIP_SLOW:
        U.record("TC_LOGIN_008", "N/A", "Bỏ qua do chạy với --skip-slow",
                 "Ca này phải chờ hết 15 phút khoá")
        U.record("TC_LOGIN_009", "N/A", "Phụ thuộc TC_LOGIN_008", "")
        return

    print("  ... chờ 15 phút 15 giây cho hết thời gian khoá IP")
    time.sleep(915)
    sx = requests.Session()
    t = R.csrf(sx, "/login.jsp")
    r = sx.post(R.BASE + "/login", allow_redirects=False,
                data={"username": user, "password": pwd, "csrfToken": t})
    loc = r.headers.get("Location") or ""
    U.expect("TC_LOGIN_008", "dashboard" in loc,
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
    U.expect("TC_LOGIN_009", "dashboard" in loc,
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

    s, _ = R.login("admin")
    test_import_pdf(s)
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

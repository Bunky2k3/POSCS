#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Chạy thật các luồng nghiệp vụ của Report 5.3 trên bản triển khai.

Khác run_blackbox*: ở đây các bước PHỤ THUỘC NHAU theo thứ tự — tài khoản tạo ở
bước 1 được dùng để đăng nhập ở bước 4, hợp đồng tạo ở luồng khách hàng được
dùng lại ở luồng phiếu hỗ trợ. Hỏng một bước thì các bước sau của cùng luồng
mất chỗ dựa, nên mỗi luồng tự dừng và ghi N/A cho phần còn lại thay vì báo
trượt hàng loạt.

Chạy:  python tools/testdoc/run_systemtest.py [BASE_URL] --log <tomcat.out>
       [--part scheduler]   chỉ chạy 2 ca cần khởi động lại máy chủ

Kết quả: tools/testdoc/systemtest_results.json
"""

import json
import pathlib
import re
import sys
import time

import requests

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import run_blackbox as R          # noqa: E402
import run_blackbox_ui as U       # noqa: E402
import run_blackbox_rest as X     # noqa: E402

OUT = pathlib.Path(__file__).with_name("systemtest_results.json")
results = {}


def record(case_id, status, actual, note=""):
    results[case_id] = {"status": status, "actual": actual, "note": note}
    mark = {"Đạt": "OK  ", "Trượt": "FAIL", "N/A": "N/A "}[status]
    print("  %s %-14s %s" % (mark, case_id, actual[:100]))


def expect(case_id, condition, actual, note=""):
    record(case_id, "Đạt" if condition else "Trượt", actual, note)
    return condition


def skip_rest(prefix, first, last, why):
    for i in range(first, last + 1):
        cid = "%s_%03d" % (prefix, i)
        if cid not in results:
            record(cid, "N/A", why, "Bước trước của luồng không hoàn tất")


def fresh(role_key, username, password):
    """Session mới cho một tài khoản bất kỳ (không có trong ACCOUNTS)."""
    s = requests.Session()
    s.headers["Accept-Language"] = "vi-VN,vi;q=0.9,en;q=0.8"
    token = R.csrf(s, "/login.jsp")
    r = s.post(R.BASE + "/login", allow_redirects=False,
               data={"username": username, "password": password,
                     "csrfToken": token})
    return s, "dashboard" in (r.headers.get("Location") or "")


def logged_in(session):
    return session.get(R.BASE + "/dashboard",
                       allow_redirects=False).status_code == 200


# ==========================================================================
# Luồng 1 — Vòng đời tài khoản nhân viên
# ==========================================================================
def flow_auth():
    print("\n[Luồng 1] Vòng đời tài khoản nhân viên")
    admin, _ = R.login("admin")
    run = R.RUN

    emp = {"action": "create", "lastName": "Nguyen", "firstName": "Vong" + run,
           "citizenId": "0077" + run, "gender": "Nam",
           "dateOfBirth": "1996-03-03", "hireDate": "2025-01-01",
           "roleId": "2", "departmentId": "2", "districtId": "1",
           "addressDetail": "So 7 duong Kiem Thu",
           "personalEmail": "vongdoi%s@gmail.com" % run,
           "phone": "0977" + run + "1"}
    r = R.post(admin, "/employee", emp)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if not expect("ST_AUTH_001", bool(m) and "error" not in (r.headers.get("Location") or ""),
                  "Tạo hồ sơ nhân viên -> %s" % r.headers.get("Location")):
        skip_rest("ST_AUTH", 2, 17, "Không tạo được hồ sơ nhân viên ở bước 1")
        return
    uid = m.group(1)

    page = U.html(admin.get(R.BASE + "/employee?action=view&id=" + uid))
    txt = U.page_text(page)
    uname_m = re.search(r"(?:Tên đăng nhập|Username)[:\s]+(\S+)", txt)
    uname = uname_m.group(1) if uname_m else None
    if uname is None:
        skip_rest("ST_AUTH", 2, 17, "Không đọc được tên đăng nhập vừa sinh")
        return
    print("       tài khoản vừa tạo: %s (id=%s)" % (uname, uid))

    _, ok = fresh("new", uname, "MatKhauBatKy@1")
    expect("ST_AUTH_002", not ok,
           "Chưa cấp mật khẩu: đăng nhập bằng %s bị từ chối" % uname)

    before = U.count_log(r"Mat khau tam: \S+")
    r = R.post(admin, "/employee", {"action": "sendAccount", "id": uid})
    time.sleep(0.4)
    loc = r.headers.get("Location") or ""
    temp_pw = U.temp_password_from_log()
    sent = U.count_log(r"Mat khau tam: \S+") == before + 1
    expect("ST_AUTH_003", sent and "error" not in loc,
           "Gửi thông tin tài khoản -> %s; log ghi mật khẩu tạm %s"
           % (loc, "(có)" if temp_pw else "(không đọc được)"))
    if not (sent and temp_pw):
        skip_rest("ST_AUTH", 4, 17, "Không lấy được mật khẩu tạm")
        return

    emp_sess, ok = fresh("new", uname, temp_pw)
    expect("ST_AUTH_004", ok,
           "Đăng nhập lần đầu bằng mật khẩu tạm: %s" % ("được" if ok else "KHÔNG được"))
    if not ok:
        skip_rest("ST_AUTH", 5, 17, "Không đăng nhập được bằng mật khẩu tạm")
        return

    r = emp_sess.post(R.BASE + "/changePassword", allow_redirects=False,
                      data={"oldPassword": "SaiMatKhau", "newPassword": "MatKhauMoi@1",
                            "confirmPassword": "MatKhauMoi@1",
                            "csrfToken": R.csrf(emp_sess)})
    expect("ST_AUTH_005", "wrong_old_password" in (r.headers.get("Location") or ""),
           "Sai mật khẩu hiện tại -> %s" % r.headers.get("Location"))

    new_pw = "MatKhauMoi@%s" % run
    r = emp_sess.post(R.BASE + "/changePassword", allow_redirects=False,
                      data={"oldPassword": temp_pw, "newPassword": new_pw,
                            "confirmPassword": new_pw,
                            "csrfToken": R.csrf(emp_sess)})
    loc = r.headers.get("Location") or ""
    killed = not logged_in(emp_sess)
    expect("ST_AUTH_006", "error" not in loc and killed,
           "Đổi mật khẩu -> %s; phiên hiện tại %s"
           % (loc, "bị huỷ" if killed else "CÒN SỐNG"))

    _, ok = fresh("new", uname, temp_pw)
    expect("ST_AUTH_007", not ok, "Mật khẩu tạm sau khi đã đổi: bị từ chối")

    emp_sess, ok = fresh("new", uname, new_pw)
    expect("ST_AUTH_008", ok, "Đăng nhập bằng mật khẩu mới: %s"
           % ("được" if ok else "KHÔNG được"))

    # --- quên mật khẩu bằng OTP (email công ty)
    page = U.html(admin.get(R.BASE + "/employee?action=view&id=" + uid))
    mail_m = re.search(r"[\w.+-]+@[\w.-]+\.\w+", U.page_text(page))
    company_mail = None
    for cand in re.findall(r"[\w.+-]+@[\w.-]+\.\w+", U.page_text(page)):
        if "gmail" not in cand:
            company_mail = cand
            break
    if company_mail is None:
        skip_rest("ST_AUTH", 9, 13, "Không đọc được email công ty của tài khoản")
    else:
        otp_sess = requests.Session()
        otp_sess.headers["Accept-Language"] = "vi-VN,vi;q=0.9"
        before = U.count_log(r"Ma OTP: \d{6}")
        t = R.csrf(otp_sess, "/forgotPassword.jsp")
        r = otp_sess.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                          data={"email": company_mail, "csrfToken": t})
        time.sleep(0.5)
        issued = U.count_log(r"Ma OTP: \d{6}") == before + 1
        otp = U.otp_from_log(company_mail)
        expect("ST_AUTH_009", issued and otp is not None,
               "Gửi OTP tới %s -> %s, mã %s" % (company_mail, r.headers.get("Location"), otp))
        if not issued:
            skip_rest("ST_AUTH", 10, 13,
                      "Hạn mức 10 yêu cầu OTP/IP trong 15 phút đã cạn")
        else:
            t = R.csrf(otp_sess, "/verifyOtp.jsp")
            r = otp_sess.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
                              data={"otpCode": "000000", "csrfToken": t})
            expect("ST_AUTH_010", "invalid_otp" in (r.headers.get("Location") or ""),
                   "Nhập sai OTP -> %s" % r.headers.get("Location"))

            t = R.csrf(otp_sess, "/verifyOtp.jsp")
            r = otp_sess.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
                              data={"otpCode": otp, "csrfToken": t})
            loc = r.headers.get("Location") or ""
            expect("ST_AUTH_011", "resetPassword" in loc,
                   "Nhập đúng OTP -> %s" % loc)

            otp_pw = "MatKhauOtp@%s" % run
            t = R.csrf(otp_sess, "/resetPassword.jsp")
            r = otp_sess.post(R.BASE + "/ResetPasswordServlet", allow_redirects=False,
                              data={"newPassword": otp_pw, "confirmPassword": otp_pw,
                                    "csrfToken": t})
            loc = r.headers.get("Location") or ""
            emp_sess, ok = fresh("new", uname, otp_pw)
            expect("ST_AUTH_012", "error" not in loc and ok,
                   "Đặt mật khẩu mới -> %s; đăng nhập lại: %s"
                   % (loc, "được" if ok else "KHÔNG được"))
            if ok:
                new_pw = otp_pw

            t = R.csrf(otp_sess, "/verifyOtp.jsp")
            r = otp_sess.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
                              data={"otpCode": otp, "csrfToken": t})
            loc = r.headers.get("Location") or ""
            expect("ST_AUTH_013", "resetPassword" not in loc,
                   "Dùng lại mã OTP cũ -> %s (bị từ chối)" % loc)

    # --- Admin khoá tài khoản
    emp_sess, ok = fresh("new", uname, new_pw)
    alive_before = logged_in(emp_sess) if ok else False
    r = R.post(admin, "/employee", {"action": "toggleStatus", "id": uid})
    loc = r.headers.get("Location") or ""
    expect("ST_AUTH_014", "error" not in loc, "Admin khoá tài khoản -> %s" % loc)

    alive_after = logged_in(emp_sess)
    expect("ST_AUTH_015", alive_before and not alive_after,
           "Phiên đang mở của nhân viên: trước khi khoá %s, sau khi khoá %s"
           % ("sống" if alive_before else "không có",
              "BỊ CẮT" if not alive_after else "VẪN SỐNG"))

    _, ok = fresh("new", uname, new_pw)
    expect("ST_AUTH_016", not ok, "Tài khoản bị khoá đăng nhập lại: bị từ chối")

    R.post(admin, "/employee", {"action": "toggleStatus", "id": uid})
    _, ok = fresh("new", uname, new_pw)
    expect("ST_AUTH_017", ok,
           "Sau khi mở khoá, đăng nhập bằng mật khẩu cũ: %s"
           % ("được" if ok else "KHÔNG được"))


# ==========================================================================
# Luồng 2 — Khách hàng tới hợp đồng
# ==========================================================================
STATE = {}


def flow_customer():
    print("\n[Luồng 2] Từ khách hàng mới tới hợp đồng hết hạn")
    sales, _ = R.login("sales")
    admin, _ = R.login("admin")
    tech, _ = R.login("tech")
    run = R.RUN

    def dashboard_customers(session):
        """Ô tổng khách hàng nằm ngay trước dòng '... khách hàng mới tháng này'.

        Bắt số theo chữ 'khách hàng' trong toàn trang sẽ dính menu bên trái.
        """
        page = U.html(session.get(R.BASE + "/dashboard"))
        for block in page.find_all(class_="kpi-top"):
            label = block.find(class_="kpi-label")
            value = block.find(class_="kpi-value")
            if label and value and "Tổng khách hàng" in label.get_text(strip=True):
                return value.get_text(strip=True)
        return None

    before_total = dashboard_customers(admin)

    tax = "66" + run
    cus = {"action": "create", "customerName": "Cty Vong Doi " + run,
           "customerType": "Doanh nghiệp", "customerGroup": "Khách hàng mới",
           "taxCode": tax, "phone": "0966" + run + "1",
           "email": "vd%s@example.vn" % run, "accountOwnerId": "15",
           "districtId": "1", "addressDetail": "So 6 duong Kiem Thu"}
    r = U.upload(sales, "/customer", cus, {})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if not expect("ST_CUS_001", bool(m), "Tạo khách hàng -> %s" % r.headers.get("Location")):
        skip_rest("ST_CUS", 2, 20, "Không tạo được khách hàng ở bước 1")
        return
    cus_id = m.group(1)
    STATE["customer"] = cus_id

    page = U.html(sales.get(R.BASE + "/customer?action=list&keyword=" + tax))
    expect("ST_CUS_002", U.body_rows(page) == 1,
           "Tìm theo mã số thuế %s -> %d dòng" % (tax, U.body_rows(page)))

    after_total = dashboard_customers(admin)
    expect("ST_CUS_003", before_total != after_total or before_total is None,
           "Tổng khách hàng trên Dashboard: %s -> %s" % (before_total, after_total))

    r = R.post(sales, "/customer", {"action": "evaluate", "id": cus_id,
                                    "rating": "GOOD",
                                    "description": "Khach hang tiem nang " + run})
    page = U.html(sales.get(R.BASE + "/customer?action=view&id=" + cus_id))
    rows = [tr for tr in page.find_all("tr") if tr.find_all("td")]
    has_label = "Tốt" in U.page_text(page)
    expect("ST_CUS_004", "error" not in (r.headers.get("Location") or "") and has_label,
           "Đánh giá mức Tốt -> %s; trang chi tiết hiện nhãn tiếng Việt: %s"
           % (r.headers.get("Location"), "có" if has_label else "KHÔNG"))

    future = time.strftime("%Y-%m-%d", time.localtime(time.time() + 15 * 86400))
    later = time.strftime("%Y-%m-%d", time.localtime(time.time() + 400 * 86400))
    ctr = {"action": "create", "title": "HD vong doi " + run,
           "contractType": "Bảo trì", "enterpriseId": cus_id, "ownerId": "15",
           "signDate": time.strftime("%Y-%m-%d"), "effectiveDate": future,
           "endDate": later}
    r = U.upload(sales, "/contract", ctr, {})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if not m:
        expect("ST_CUS_005", False, "Tạo hợp đồng -> %s" % r.headers.get("Location"))
        skip_rest("ST_CUS", 6, 20, "Không tạo được hợp đồng ở bước 2")
        return
    ctr_id = m.group(1)
    STATE["contract"] = ctr_id
    status = U.page_text(U.html(sales.get(R.BASE + "/contract?action=view&id=" + ctr_id)))
    expect("ST_CUS_005", "Chưa hiệu lực" in status,
           "Tạo hợp đồng hiệu lực từ %s -> trạng thái Chưa hiệu lực" % future)

    page = U.html(sales.get(R.BASE + "/customer?action=view&id=" + cus_id))
    expect("ST_CUS_006", run in U.page_text(page),
           "Hợp đồng vừa tạo xuất hiện trong khối hợp đồng của khách hàng")

    for pid, qty in (("1", "3"), ("2", "1")):
        R.post(sales, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                    "productId": pid, "quantity": qty})
    lines = re.findall(r"confirmRemoveProduct\((\d+)\)",
                       str(U.html(sales.get(R.BASE + "/contract?action=view&id=" + ctr_id))))
    expect("ST_CUS_007", len(lines) == 2,
           "Bảng sản phẩm của hợp đồng có %d dòng" % len(lines))

    r = R.post(sales, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                    "productId": "1", "quantity": "0"})
    after = re.findall(r"confirmRemoveProduct\((\d+)\)",
                       str(U.html(sales.get(R.BASE + "/contract?action=view&id=" + ctr_id))))
    expect("ST_CUS_008",
           "add_product_invalid" in (r.headers.get("Location") or "")
           and len(after) == len(lines),
           "Số lượng 0 -> %s; bảng sản phẩm vẫn %d dòng"
           % (r.headers.get("Location"), len(after)))

    r = R.post(tech, "/product", {"action": "delete", "id": "1"})
    expect("ST_CUS_009", "has_active_contracts" in (r.headers.get("Location") or ""),
           "Xoá sản phẩm đang nằm trong hợp đồng -> %s" % r.headers.get("Location"))

    def set_dates(effective, end, sign=None):
        # isValid() đòi ngày ký <= ngày hiệu lực <= ngày kết thúc. Lùi ngày hiệu
        # lực về quá khứ mà giữ nguyên ngày ký hôm nay thì bản cập nhật bị từ
        # chối và trạng thái không đổi -- dễ tưởng nhầm là lỗi tính trạng thái.
        d = dict(ctr)
        d["action"] = "update"
        d["contractId"] = ctr_id
        d["signDate"] = sign or min(effective, d["signDate"])
        d["effectiveDate"] = effective
        d["endDate"] = end
        U.upload(sales, "/contract", d, {})
        return U.page_text(U.html(sales.get(R.BASE + "/contract?action=view&id=" + ctr_id)))

    today = time.strftime("%Y-%m-%d")
    txt = set_dates(today, time.strftime("%Y-%m-%d", time.localtime(time.time() + 90 * 86400)))
    expect("ST_CUS_010", "Đang hiệu lực" in txt,
           "Tới ngày hiệu lực -> trạng thái Đang hiệu lực")

    d30 = time.strftime("%Y-%m-%d", time.localtime(time.time() + 30 * 86400))
    txt = set_dates(today, d30)
    expect("ST_CUS_011", "Sắp hết hạn" in txt,
           "Còn đúng 30 ngày -> trạng thái Sắp hết hạn (giá trị biên)")

    page = U.html(sales.get(R.BASE + "/contract?action=list&status=Sắp hết hạn&keyword=" + run))
    expect("ST_CUS_012", U.body_rows(page) >= 1,
           "Lọc trạng thái Sắp hết hạn -> thấy hợp đồng này (%d dòng)"
           % U.body_rows(page))

    past = time.strftime("%Y-%m-%d", time.localtime(time.time() - 5 * 86400))
    long_ago = time.strftime("%Y-%m-%d", time.localtime(time.time() - 60 * 86400))
    txt = set_dates(time.strftime("%Y-%m-%d", time.localtime(time.time() - 30 * 86400)),
                    past, sign=long_ago)
    expect("ST_CUS_013", "Đã hết hạn" in txt,
           "Quá ngày kết thúc -> trạng thái Đã hết hạn")

    r = R.post(sales, "/contract", {"action": "delete", "id": ctr_id})
    expect("ST_CUS_014", "cannot_delete" in (r.headers.get("Location") or ""),
           "Xoá hợp đồng đã có hiệu lực -> %s" % r.headers.get("Location"))

    # đưa về Sắp hết hạn để bước cảnh báo dùng
    set_dates(today, d30)
    record("ST_CUS_015", "N/A",
           "Cần khởi động lại máy chủ để tác vụ nền chạy",
           "Chạy lại với --part scheduler")
    record("ST_CUS_016", "N/A",
           "Cần khởi động lại máy chủ lần hai để kiểm không sinh trùng",
           "Chạy lại với --part scheduler")

    txt = U.page_text(U.html(admin.get(R.BASE + "/dashboard")))
    expect("ST_CUS_017", "hết hạn" in txt.lower(),
           "Dashboard có khối hợp đồng sắp hết hạn")

    r = sales.get(R.BASE + "/contract?action=exportExcel&status=Sắp hết hạn")
    try:
        n, cells = U.xls_rows(r.content) if hasattr(U, "xls_rows") else (0, [])
    except Exception:
        n, cells = 0, []
    if not n:
        import xlrd
        book = xlrd.open_workbook(file_contents=r.content)
        sheet = book.sheet_by_index(0)
        n = sheet.nrows
        cells = [sheet.cell_value(i, j) for i in range(sheet.nrows)
                 for j in range(sheet.ncols)]
    expect("ST_CUS_018", n > 1 and any(run in str(c) for c in cells),
           "Excel hợp đồng lọc Sắp hết hạn: %d dòng, có chứa hợp đồng vừa tạo" % n)

    from pypdf import PdfReader
    import io
    r = sales.get(R.BASE + "/contract?action=exportPdf&id=" + ctr_id)
    ok_pdf = r.content[:5] == b"%PDF-"
    pages = len(PdfReader(io.BytesIO(r.content)).pages) if ok_pdf else 0
    expect("ST_CUS_019", ok_pdf and pages >= 1,
           "PDF hợp đồng: %d trang, %d byte" % (pages, len(r.content)))

    set_dates(today, time.strftime("%Y-%m-%d", time.localtime(time.time() + 90 * 86400)))
    r = R.post(sales, "/customer", {"action": "delete", "id": cus_id})
    expect("ST_CUS_020", "has_active_contracts" in (r.headers.get("Location") or ""),
           "Xoá khách hàng còn hợp đồng hiệu lực -> %s" % r.headers.get("Location"))


def flow_scheduler():
    """Hai ca cần khởi động lại máy chủ — chạy riêng bằng --part scheduler."""
    print("\n[Luồng 2 — phần tác vụ nền]")
    admin, _ = R.login("admin")
    import subprocess
    exe = r"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe"

    def count_notif():
        out = subprocess.run([exe, "-h127.0.0.1", "-uroot", "-p1234", "-N", "-B",
                              "poscs_bbtest", "-e",
                              "SELECT COUNT(*) FROM notifications "
                              "WHERE ref_type='contract_expiring'"],
                             capture_output=True, text=True)
        try:
            return int(out.stdout.strip().splitlines()[-1])
        except Exception:
            return -1

    n = count_notif()
    page = U.html(R.login("sales")[0].get(R.BASE + "/notifications"))
    seen = U.page_text(page).count("sắp hết hạn")
    expect("ST_CUS_015", n > 0 and seen > 0,
           "Sau khi khởi động lại, tác vụ nền sinh %d thông báo hợp đồng sắp hết "
           "hạn; trang thông báo của người phụ trách hiện %d mục" % (n, seen))
    STATE["notif_count"] = n


def flow_scheduler_second(first_count):
    print("\n[Luồng 2 — chạy lại tác vụ nền]")
    import subprocess
    exe = r"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe"
    out = subprocess.run([exe, "-h127.0.0.1", "-uroot", "-p1234", "-N", "-B",
                          "poscs_bbtest", "-e",
                          "SELECT COUNT(*) FROM notifications "
                          "WHERE ref_type='contract_expiring'; "
                          "SELECT COUNT(*) FROM (SELECT ref_id FROM notifications "
                          "WHERE ref_type='contract_expiring' GROUP BY ref_id, user_id "
                          "HAVING COUNT(*)>1) t"],
                         capture_output=True, text=True)
    nums = [int(x) for x in re.findall(r"\d+", out.stdout)]
    total, dup = (nums + [0, 0])[:2]
    expect("ST_CUS_016", total == first_count and dup == 0,
           "Chạy lại tác vụ nền: %d thông báo (lần trước %d), %d ref_id bị trùng"
           % (total, first_count, dup))


# ==========================================================================
# Luồng 3 — Phiếu hỗ trợ
# ==========================================================================
def flow_support():
    print("\n[Luồng 3] Xử lý phiếu hỗ trợ kỹ thuật")
    cskh, _ = R.login("cskh")
    tech, _ = R.login("tech")
    tech2, _ = R.login("tech2")
    sales, _ = R.login("sales")
    admin, _ = R.login("admin")
    run = R.RUN

    cus_id = STATE.get("customer", "1")
    ctr_id = STATE.get("contract")
    if ctr_id is None:
        r = cskh.get(R.BASE + "/contract/byEnterprise?enterpriseId=" + cus_id)
        try:
            ctr_id = str(r.json()[0].get("contractId") or r.json()[0].get("id"))
        except Exception:
            ctr_id = None

    tk = {"action": "create", "enterpriseId": cus_id, "ticketType": "Lỗi phần mềm",
          "priority": "Cao", "receptionChannel": "Điện thoại",
          "assignedTechnicianId": "16", "description": "Phieu he thong " + run}
    if ctr_id:
        tk["contractId"] = ctr_id
    r = R.post(cskh, "/ticket", tk)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if not expect("ST_TK_001", bool(m) and "error" not in (r.headers.get("Location") or ""),
                  "Tạo phiếu gắn hợp đồng %s -> %s" % (ctr_id, r.headers.get("Location"))):
        skip_rest("ST_TK", 2, 21, "Không tạo được phiếu ở bước 1")
        return
    tid = m.group(1)

    a = cskh.get(R.BASE + "/contract/byEnterprise?enterpriseId=" + cus_id).json()
    b = cskh.get(R.BASE + "/contract/byEnterprise?enterpriseId=1").json()
    expect("ST_TK_002", isinstance(a, list) and isinstance(b, list) and a != b,
           "Dropdown hợp đồng nạp theo khách hàng: khách %s có %d hợp đồng, "
           "khách 1 có %d — hai danh sách khác nhau" % (cus_id, len(a), len(b)))

    d = dict(tk)
    d["contractId"] = "1"          # hợp đồng của khách hàng khác
    d["enterpriseId"] = cus_id
    d["description"] = "Phieu sai hop dong " + run
    r = R.post(cskh, "/ticket", d)
    loc = r.headers.get("Location") or ""
    expect("ST_TK_003", "contract_mismatch" in loc,
           "Gắn hợp đồng của khách hàng khác -> %s" % loc)

    page = U.html(cskh.get(R.BASE + "/ticket?action=list&status=Mới tiếp nhận&keyword=" + run))
    expect("ST_TK_004", U.body_rows(page) >= 1,
           "Lọc trạng thái Mới tiếp nhận -> thấy phiếu vừa tạo (%d dòng)"
           % U.body_rows(page))

    def history_rows():
        """Lịch sử đổi trạng thái render bằng <ul class="history-list">."""
        raw = str(U.html(cskh.get(R.BASE + "/ticket?action=view&id=" + tid)))
        return len(re.findall(r'class="[^"]*history-item', raw))

    upd = {"action": "update", "ticketId": tid, "status": "Đang xử lý",
           "resolutionSummary": "Dang kiem tra thiet bi"}
    r = R.post(tech, "/ticket", upd)
    txt = U.page_text(U.html(cskh.get(R.BASE + "/ticket?action=view&id=" + tid)))
    expect("ST_TK_005", r.status_code == 302 and "Đang xử lý" in txt,
           "Kỹ thuật viên được giao cập nhật -> HTTP %s, trạng thái Đang xử lý"
           % r.status_code)

    h1 = history_rows()
    expect("ST_TK_006", h1 >= 1, "Lịch sử ghi %d dòng sau lần đổi trạng thái" % h1)

    R.post(tech, "/ticket", {"action": "update", "ticketId": tid,
                             "status": "Đang xử lý",
                             "resolutionSummary": "Cap nhat tien do " + run})
    h2 = history_rows()
    expect("ST_TK_007", h2 == h1,
           "Lưu không đổi trạng thái: lịch sử vẫn %d dòng (trước đó %d)" % (h2, h1))

    r = R.post(tech2, "/ticket", {"action": "update", "ticketId": tid,
                                  "status": "Đã đóng"})
    expect("ST_TK_008", r.status_code == 403,
           "Kỹ thuật viên khác sửa phiếu này -> HTTP %s" % r.status_code)

    r1 = R.post(tech, "/ticket", dict(tk, description="Tech tao phieu " + run))
    r2 = R.post(tech, "/ticket", {"action": "delete", "id": tid})
    expect("ST_TK_009", r1.status_code == 403 and r2.status_code == 403,
           "Kỹ thuật tạo phiếu -> HTTP %s; xoá phiếu -> HTTP %s"
           % (r1.status_code, r2.status_code))

    view = sales.get(R.BASE + "/ticket?action=list", allow_redirects=False)
    r = R.post(sales, "/ticket", {"action": "update", "ticketId": tid,
                                  "status": "Đã đóng"})
    expect("ST_TK_010", view.status_code == 200 and r.status_code == 403,
           "Sales xem danh sách -> HTTP %s; cập nhật phiếu -> HTTP %s"
           % (view.status_code, r.status_code))

    r = R.post(tech, "/ticket", {"action": "update", "ticketId": tid,
                                 "status": "Đã đóng",
                                 "resolutionSummary": "Da xu ly xong " + run})
    txt = U.page_text(U.html(cskh.get(R.BASE + "/ticket?action=view&id=" + tid)))
    expect("ST_TK_011", "Đã đóng" in txt,
           "Đóng phiếu kèm kết quả xử lý -> trạng thái Đã đóng")

    import subprocess
    exe = r"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe"

    def resolved_at():
        out = subprocess.run([exe, "-h127.0.0.1", "-uroot", "-p1234", "-N", "-B",
                              "poscs_bbtest", "-e",
                              "SELECT resolved_at FROM technicalrequests WHERE ticket_id=%s" % tid],
                             capture_output=True, text=True)
        return out.stdout.strip().splitlines()[-1] if out.stdout.strip() else ""

    first_resolved = resolved_at()
    time.sleep(1.2)
    R.post(tech, "/ticket", {"action": "update", "ticketId": tid,
                             "status": "Đã đóng",
                             "resolutionSummary": "Da xu ly xong " + run})
    second_resolved = resolved_at()
    expect("ST_TK_012", first_resolved and first_resolved == second_resolved,
           "Mốc đóng phiếu: lần đầu %s, sau khi lưu lại %s"
           % (first_resolved, second_resolved))

    R.post(tech, "/ticket", {"action": "update", "ticketId": tid,
                             "status": "Đang xử lý",
                             "resolutionSummary": "Khach bao chua xong"})
    txt = U.page_text(U.html(cskh.get(R.BASE + "/ticket?action=view&id=" + tid)))
    expect("ST_TK_013", "Đang xử lý" in txt,
           "Mở lại phiếu đã đóng -> trạng thái Đang xử lý")

    past = time.strftime("%Y-%m-%dT%H:%M", time.localtime(time.time() - 3 * 86400))
    soon = time.strftime("%Y-%m-%dT%H:%M", time.localtime(time.time() + 3 * 3600))
    ids = {}
    for label, sla in (("qua", past), ("soon", soon)):
        d = dict(tk)
        d["slaDeadline"] = sla
        d["description"] = "SLA%s%s" % (label, run)
        rr = R.post(cskh, "/ticket", d)
        mm = re.search(r"id=(\d+)", rr.headers.get("Location") or "")
        ids[label] = mm.group(1) if mm else None
    raw = str(U.html(cskh.get(R.BASE + "/ticket?action=list&keyword=SLA")))
    expect("ST_TK_014", "Quá hạn SLA" in raw,
           "Phiếu quá hạn SLA hiện nhãn cảnh báo trên danh sách")
    expect("ST_TK_015", "Sắp tới hạn" in raw,
           "Phiếu còn dưới 24 giờ hiện nhãn sắp tới hạn")

    if ids.get("qua"):
        R.post(cskh, "/ticket", {"action": "update", "ticketId": ids["qua"],
                                 "enterpriseId": cus_id, "ticketType": "Lỗi phần mềm",
                                 "priority": "Cao", "receptionChannel": "Điện thoại",
                                 "assignedTechnicianId": "16",
                                 "description": "SLAqua" + run,
                                 "status": "Đã đóng",
                                 "resolutionSummary": "Xong"})
        raw = str(U.html(cskh.get(R.BASE + "/ticket?action=list&keyword=SLAqua" + run)))
        expect("ST_TK_016", "Quá hạn SLA" not in raw,
               "Phiếu quá hạn sau khi đóng: không còn nhãn cảnh báo")
    else:
        record("ST_TK_016", "N/A", "Không tạo được phiếu quá hạn để đóng", "")

    # Gửi chuỗi tiếng Việt qua tham số -e hay bị hỏng mã hoá; so sánh bằng mã
    # hex của "Đã đóng" cho chắc.
    # Không so sánh chuỗi tiếng Việt qua dòng lệnh (vướng mã hoá lẫn collation);
    # phiếu đã đóng là phiếu có resolved_at, dùng luôn cột đó.
    out = subprocess.run([exe, "-h127.0.0.1", "-uroot", "-p1234", "-N", "-B",
                          "--default-character-set=utf8mb4", "poscs_bbtest", "-e",
                          "SELECT COUNT(*) FROM technicalrequests WHERE is_deleted=0 "
                          "AND resolved_at IS NULL AND sla_deadline IS NOT NULL "
                          "AND sla_deadline <= DATE_ADD(NOW(), INTERVAL 24 HOUR)"],
                         capture_output=True, text=True)
    try:
        db_count = int(out.stdout.strip().splitlines()[-1])
    except Exception:
        db_count = -1
    txt = U.page_text(U.html(admin.get(R.BASE + "/dashboard")))
    expect("ST_TK_017", db_count >= 0 and str(db_count) in txt,
           "Quy tắc cảnh báo: CSDL đếm %d phiếu cần chú ý; con số này có xuất "
           "hiện trên Dashboard: %s" % (db_count, "có" if str(db_count) in txt else "không"))

    import xlrd
    r = cskh.get(R.BASE + "/ticket?action=exportExcel&status=Đã đóng")
    book = xlrd.open_workbook(file_contents=r.content)
    sheet = book.sheet_by_index(0)
    values = [sheet.cell_value(i, j) for i in range(sheet.nrows)
              for j in range(sheet.ncols)]
    expect("ST_TK_018", sheet.nrows > 1 and not any("Mới tiếp nhận" in str(v) for v in values),
           "Excel lọc trạng thái Đã đóng: %d dòng, không lẫn phiếu Mới tiếp nhận"
           % sheet.nrows)

    from pypdf import PdfReader
    import io
    r = cskh.get(R.BASE + "/ticket?action=exportPdf&id=" + tid)
    ok_pdf = r.content[:5] == b"%PDF-"
    expect("ST_TK_019", ok_pdf,
           "PDF phiếu: %d byte, %s"
           % (len(r.content), "đọc được" if ok_pdf else "KHÔNG phải PDF"))

    victim = ids.get("soon") or tid
    r = R.post(cskh, "/ticket", {"action": "delete", "id": victim})
    loc = r.headers.get("Location") or ""
    after = cskh.get(R.BASE + "/ticket?action=view&id=" + victim, allow_redirects=False)
    expect("ST_TK_020", "cannot_delete" not in loc,
           "CSKH xoá phiếu -> %s" % loc)
    expect("ST_TK_021", after.status_code == 302 and "notfound" in (after.headers.get("Location") or ""),
           "Mở lại phiếu đã xoá -> HTTP %s %s"
           % (after.status_code, after.headers.get("Location")))


# ==========================================================================
# Luồng 4 — Danh mục sản phẩm
# ==========================================================================
def flow_product():
    print("\n[Luồng 4] Quản lý danh mục sản phẩm")
    tech, _ = R.login("tech")
    sales, _ = R.login("sales")
    run = R.RUN

    prd = {"action": "create", "productName": "SP he thong " + run,
           "categoryId": "1", "unitPrice": "5000000", "description": "Mo ta " + run}
    r = U.upload(tech, "/product", prd,
                 {"images": ("a.jpg", U.JPG, "image/jpeg"),
                  "catalogues": ("c.pdf", U.PDF_MIN, "application/pdf")})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if not expect("ST_PRD_001", bool(m), "Tạo sản phẩm kèm ảnh và catalogue -> %s"
                  % r.headers.get("Location")):
        skip_rest("ST_PRD", 2, 19, "Không tạo được sản phẩm ở bước 1")
        return
    pid = m.group(1)

    def code_of(product_id):
        txt = U.page_text(U.html(tech.get(R.BASE + "/product?action=view&id=" + product_id)))
        mm = re.search(r"SP-(\d+)", txt)
        return mm.group(1) if mm else None

    d = dict(prd)
    d["productName"] = "SP he thong 2 " + run
    r2 = U.upload(tech, "/product", d, {})
    m2 = re.search(r"id=(\d+)", r2.headers.get("Location") or "")
    c1, c2 = code_of(pid), (code_of(m2.group(1)) if m2 else None)
    expect("ST_PRD_002",
           c1 and c2 and int(c2) == int(c1) + 1 and len(c1) == len(c2),
           "Mã sinh liên tiếp: SP-%s -> SP-%s" % (c1, c2))

    d = dict(prd)
    d["productName"] = "SP svg " + run
    r = U.upload(tech, "/product", d, {"images": ("a.svg", U.SVG, "image/svg+xml")})
    expect("ST_PRD_003", "invalid_image_type" in (r.headers.get("Location") or ""),
           "Ảnh .svg -> %s" % r.headers.get("Location"))

    d["productName"] = "SP docx " + run
    r = U.upload(tech, "/product", d,
                 {"catalogues": ("c.docx", b"PK\x03\x04docx", "application/msword")})
    expect("ST_PRD_004", "invalid_catalogue_type" in (r.headers.get("Location") or ""),
           "Catalogue .docx -> %s" % r.headers.get("Location"))

    page = U.html(tech.get(R.BASE + "/product?action=view&id=" + pid))
    src = next((i.get("src") for i in page.find_all("img")
                if "uploads" in (i.get("src") or "")), None)
    if src:
        resp = tech.get(R.BASE + src.split("/POSCS")[-1])
        expect("ST_PRD_005",
               resp.status_code == 200
               and resp.headers.get("X-Content-Type-Options") == "nosniff",
               "Mở ảnh đã tải lên: HTTP %s, X-Content-Type-Options=%s"
               % (resp.status_code, resp.headers.get("X-Content-Type-Options")))
    else:
        record("ST_PRD_005", "N/A", "Không tìm thấy ảnh trên trang chi tiết", "")

    page = U.html(tech.get(R.BASE + "/product?action=edit&id=" + pid))
    name_input = page.find("input", {"name": "productName"})
    expect("ST_PRD_006", name_input is not None and run in (name_input.get("value") or ""),
           "Form sửa điền sẵn tên: %r" % (name_input.get("value") if name_input else None))

    upd = dict(prd)
    upd["action"] = "update"
    upd["productId"] = pid
    upd["productName"] = "SP he thong sua " + run
    upd["unitPrice"] = "7500000"
    U.upload(tech, "/product", upd, {})
    txt = U.page_text(U.html(tech.get(R.BASE + "/product?action=view&id=" + pid)))
    expect("ST_PRD_007", "sua" in txt, "Sửa tên và đơn giá -> trang chi tiết hiện giá trị mới")

    raw = str(U.html(tech.get(R.BASE + "/product?action=edit&id=" + pid)))
    img_ids = re.findall(r'name="removedImageIds"[^>]*value="(\d+)"', raw)
    if not img_ids:
        img_ids = re.findall(r"removeImage\((\d+)\)", raw)
    d = dict(upd)
    if img_ids:
        d["removedImageIds"] = img_ids[0]
    r = U.upload(tech, "/product", d, {"images": ("b.png", U.PNG, "image/png")})
    page = U.html(tech.get(R.BASE + "/product?action=view&id=" + pid))
    imgs = [i for i in page.find_all("img") if "uploads" in (i.get("src") or "")]
    expect("ST_PRD_008", "error" not in (r.headers.get("Location") or "") and imgs,
           "Gỡ một ảnh và thêm ảnh mới trong cùng lần lưu: còn %d ảnh" % len(imgs))

    before = len(imgs)
    U.upload(tech, "/product", upd, {})
    page = U.html(tech.get(R.BASE + "/product?action=view&id=" + pid))
    after = len([i for i in page.find_all("img") if "uploads" in (i.get("src") or "")])
    expect("ST_PRD_009", after == before,
           "Lưu không chọn file: số ảnh %d -> %d (giữ nguyên)" % (before, after))

    if m2:
        other = m2.group(1)
        raw_other = str(U.html(tech.get(R.BASE + "/product?action=view&id=" + other)))
        d = dict(upd)
        d["removedImageIds"] = "999999"
        U.upload(tech, "/product", d, {})
        still = len([i for i in U.html(tech.get(R.BASE + "/product?action=view&id=" + pid))
                     .find_all("img") if "uploads" in (i.get("src") or "")])
        expect("ST_PRD_010", still == after,
               "Gỡ ảnh bằng id không thuộc sản phẩm: số ảnh không đổi (%d)" % still)
    else:
        record("ST_PRD_010", "N/A", "Không có sản phẩm thứ hai để đối chiếu", "")

    page = U.html(sales.get(R.BASE + "/product?action=list&keyword=" + run))
    expect("ST_PRD_011", U.body_rows(page) >= 1,
           "Sales tìm thấy sản phẩm khi lập hợp đồng (%d dòng)" % U.body_rows(page))

    r1 = R.post(sales, "/product", {"action": "create", "productName": "X" + run,
                                    "categoryId": "1"})
    r2 = R.post(sales, "/product", {"action": "delete", "id": pid})
    expect("ST_PRD_012", r1.status_code == 403 and r2.status_code == 403,
           "Sales tạo sản phẩm -> HTTP %s; xoá sản phẩm -> HTTP %s"
           % (r1.status_code, r2.status_code))

    ctr_id = STATE.get("contract")
    if not ctr_id:
        record("ST_PRD_013", "N/A", "Không có hợp đồng từ luồng 2 để gắn", "")
        skip_rest("ST_PRD", 14, 19, "Không gắn được sản phẩm vào hợp đồng")
        return

    r = R.post(sales, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                    "productId": pid, "quantity": "2"})
    expect("ST_PRD_013", "error" not in (r.headers.get("Location") or ""),
           "Gắn sản phẩm vào hợp đồng -> %s" % r.headers.get("Location"))

    txt = U.page_text(U.html(tech.get(R.BASE + "/product?action=view&id=" + pid)))
    expect("ST_PRD_014", "hợp đồng" in txt.lower(),
           "Trang chi tiết sản phẩm có khối hợp đồng đang dùng nó")

    r = R.post(tech, "/product", {"action": "delete", "id": pid})
    expect("ST_PRD_015", "has_active_contracts" in (r.headers.get("Location") or ""),
           "Xoá sản phẩm đang trong hợp đồng -> %s" % r.headers.get("Location"))

    raw = str(U.html(sales.get(R.BASE + "/contract?action=view&id=" + ctr_id)))
    lines = re.findall(r"confirmRemoveProduct\((\d+)\)", raw)
    removed = False
    if lines:
        r = R.post(sales, "/contract", {"action": "removeProduct",
                                        "contractId": ctr_id,
                                        "contractProductId": lines[-1]})
        removed = "error" not in (r.headers.get("Location") or "")
    expect("ST_PRD_016", removed, "Gỡ dòng sản phẩm khỏi hợp đồng: %s"
           % ("thành công" if removed else "không tìm được dòng"))

    r = R.post(tech, "/product", {"action": "delete", "id": pid})
    loc = r.headers.get("Location") or ""
    expect("ST_PRD_017", "error" not in loc, "Sau khi gỡ thì xoá sản phẩm -> %s" % loc)

    after = tech.get(R.BASE + "/product?action=view&id=" + pid, allow_redirects=False)
    expect("ST_PRD_018",
           after.status_code == 302 and "notfound" in (after.headers.get("Location") or ""),
           "Mở lại sản phẩm đã xoá -> HTTP %s %s"
           % (after.status_code, after.headers.get("Location")))

    r = sales.get(R.BASE + "/contract?action=view&id=" + ctr_id, allow_redirects=False)
    expect("ST_PRD_019", r.status_code == 200,
           "Hợp đồng cũ sau khi sản phẩm bị xoá: HTTP %s, mở bình thường"
           % r.status_code)


# ==========================================================================
# Luồng 5 — Phân quyền và giám sát
# ==========================================================================
def flow_access():
    print("\n[Luồng 5] Phân quyền và giám sát hệ thống")
    admin, _ = R.login("admin")
    sales, _ = R.login("sales")
    tech, _ = R.login("tech")
    cskh, _ = R.login("cskh")
    run = R.RUN

    def create_customer(session, tag):
        d = {"action": "create", "customerName": "ACL %s %s" % (tag, run),
             "customerType": "Doanh nghiệp", "customerGroup": "Khách hàng mới",
             "taxCode": ("5" + str(abs(hash(tag)) % 10) + run)[:13],
             "phone": "095" + run + str(abs(hash(tag)) % 10),
             "email": "acl%s%s@example.vn" % (tag[:2], run), "accountOwnerId": "15",
             "districtId": "1", "addressDetail": "So 1"}
        rr = U.upload(session, "/customer", d, {})
        mm = re.search(r"id=(\d+)", rr.headers.get("Location") or "")
        return mm.group(1) if mm else None, rr

    aid, ra = create_customer(admin, "admin")
    ap = R.post(admin, "/product", {"action": "create",
                                    "productName": "ACL SP " + run,
                                    "categoryId": "1", "unitPrice": "1000"})
    at = R.post(admin, "/ticket", {"action": "create", "enterpriseId": "1",
                                   "ticketType": "Lỗi phần mềm", "priority": "Cao",
                                   "receptionChannel": "Điện thoại",
                                   "assignedTechnicianId": "16",
                                   "description": "ACL admin " + run})
    ae = R.post(admin, "/employee", {"action": "create", "lastName": "Acl",
                                     "firstName": "Admin" + run,
                                     "citizenId": "0088" + run, "gender": "Nam",
                                     "dateOfBirth": "1995-01-01",
                                     "hireDate": "2024-01-01", "roleId": "2",
                                     "departmentId": "2", "districtId": "1",
                                     "addressDetail": "So 1",
                                     "personalEmail": "acladmin%s@gmail.com" % run,
                                     "phone": "0988" + run + "3"})
    ok_admin = all(x is not None and "error" not in (x.headers.get("Location") or "")
                   for x in (ra, ap, at, ae))
    expect("ST_ACL_001", aid is not None and ok_admin,
           "Admin tạo được khách hàng/sản phẩm/phiếu/nhân viên: %s"
           % ("tất cả" if ok_admin else "có thao tác bị chặn"))
    admin_emp_id = re.search(r"id=(\d+)", ae.headers.get("Location") or "")

    sid, rs = create_customer(sales, "sales")
    sc = U.upload(sales, "/contract", {"action": "create",
                                       "title": "ACL HD " + run,
                                       "contractType": "Bảo trì",
                                       "enterpriseId": sid or "1", "ownerId": "15",
                                       "signDate": time.strftime("%Y-%m-%d"),
                                       "effectiveDate": time.strftime(
                                           "%Y-%m-%d", time.localtime(time.time() + 5 * 86400)),
                                       "endDate": "2027-12-31"}, {})
    expect("ST_ACL_002", sid is not None and "error" not in (sc.headers.get("Location") or ""),
           "Sales tạo được khách hàng và hợp đồng -> %s" % sc.headers.get("Location"))

    r = R.post(tech, "/product", {"action": "create", "productName": "ACL tech " + run,
                                  "categoryId": "1", "unitPrice": "1000"})
    expect("ST_ACL_003", "error" not in (r.headers.get("Location") or ""),
           "Kỹ thuật tạo được sản phẩm -> %s" % r.headers.get("Location"))

    r = R.post(cskh, "/ticket", {"action": "create", "enterpriseId": "1",
                                 "ticketType": "Lỗi phần mềm", "priority": "Cao",
                                 "receptionChannel": "Điện thoại",
                                 "assignedTechnicianId": "16",
                                 "description": "ACL cskh " + run})
    expect("ST_ACL_004", "error" not in (r.headers.get("Location") or ""),
           "CSKH tạo được phiếu hỗ trợ -> %s" % r.headers.get("Location"))

    v1 = sales.get(R.BASE + "/product?action=list", allow_redirects=False).status_code
    v2 = sales.get(R.BASE + "/ticket?action=list", allow_redirects=False).status_code
    b1 = R.post(sales, "/product", {"action": "create", "productName": "x"}).status_code
    b2 = R.post(sales, "/ticket", {"action": "update", "ticketId": "3"}).status_code
    expect("ST_ACL_005", v1 == 200 and v2 == 200 and b1 == 403 and b2 == 403,
           "Sales xem sản phẩm/phiếu HTTP %s/%s; tạo sản phẩm %s, cập nhật phiếu %s"
           % (v1, v2, b1, b2))

    b1 = R.post(tech, "/customer", {"action": "create"}).status_code
    b2 = R.post(tech, "/contract", {"action": "update", "contractId": "1"}).status_code
    expect("ST_ACL_006", b1 == 403 and b2 == 403,
           "Kỹ thuật tạo khách hàng %s, cập nhật hợp đồng %s" % (b1, b2))

    b1 = R.post(cskh, "/customer", {"action": "update", "customerId": "1"}).status_code
    b2 = R.post(cskh, "/contract", {"action": "create"}).status_code
    b3 = R.post(cskh, "/product", {"action": "update", "productId": "1"}).status_code
    expect("ST_ACL_007", b1 == 403 and b2 == 403 and b3 == 403,
           "CSKH cập nhật khách hàng %s, tạo hợp đồng %s, cập nhật sản phẩm %s"
           % (b1, b2, b3))

    direct = R.post(sales, "/product", {"action": "delete", "id": "1"}).status_code
    expect("ST_ACL_008", direct == 403,
           "Gọi thẳng endpoint xoá sản phẩm bằng Sales (bỏ qua giao diện) -> HTTP %s"
           % direct)

    r = tech.get(R.BASE + "/customer?action=exportExcel", allow_redirects=False)
    expect("ST_ACL_009", r.status_code == 200 and len(r.content) > 0,
           "Kỹ thuật xuất Excel khách hàng -> HTTP %s, %d byte"
           % (r.status_code, len(r.content)))

    tech2, _ = R.login("tech2")
    d = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
         "priority": "Cao", "receptionChannel": "Điện thoại",
         "assignedTechnicianId": "16", "description": "ACL giao tech01 " + run}
    rr = R.post(cskh, "/ticket", d)
    mm = re.search(r"id=(\d+)", rr.headers.get("Location") or "")
    if mm:
        own = mm.group(1)
        r = R.post(tech, "/ticket", {"action": "update", "ticketId": own,
                                     "status": "Đang xử lý",
                                     "resolutionSummary": "Tech xu ly"})
        expect("ST_ACL_010", r.status_code == 302
               and "error" not in (r.headers.get("Location") or ""),
               "tech01 cập nhật phiếu của mình -> HTTP %s %s"
               % (r.status_code, r.headers.get("Location")))

        r = R.post(tech2, "/ticket", {"action": "update", "ticketId": own,
                                      "status": "Đã đóng"})
        expect("ST_ACL_011", r.status_code == 403,
               "tech02 cập nhật phiếu của tech01 -> HTTP %s" % r.status_code)

        R.post(tech, "/ticket", {"action": "update", "ticketId": own,
                                 "status": "Đang xử lý",
                                 "assignedTechnicianId": "18"})
        import subprocess
        exe = r"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe"
        out = subprocess.run([exe, "-h127.0.0.1", "-uroot", "-p1234", "-N", "-B",
                              "poscs_bbtest", "-e",
                              "SELECT assigned_technician_id FROM technicalrequests "
                              "WHERE ticket_id=%s" % own],
                             capture_output=True, text=True)
        assignee = out.stdout.strip().splitlines()[-1] if out.stdout.strip() else "?"
        expect("ST_ACL_012", assignee == "16",
               "tech01 thử đổi người phụ trách sang 18 -> CSDL vẫn ghi %s" % assignee)
    else:
        for cid in ("ST_ACL_010", "ST_ACL_011", "ST_ACL_012"):
            record(cid, "N/A", "Không tạo được phiếu để giao cho kỹ thuật viên", "")

    if admin_emp_id:
        eid = admin_emp_id.group(1)
        r = R.post(admin, "/employee", {"action": "update", "userId": eid,
                                        "lastName": "Acl", "firstName": "Admin" + run,
                                        "citizenId": "0088" + run, "gender": "Nam",
                                        "dateOfBirth": "1995-01-01",
                                        "hireDate": "2024-01-01", "roleId": "4",
                                        "departmentId": "2", "districtId": "1",
                                        "addressDetail": "So 1",
                                        "personalEmail": "acladmin%s@gmail.com" % run,
                                        "phone": "0988" + run + "3"})
        expect("ST_ACL_013", "error" not in (r.headers.get("Location") or ""),
               "Đổi vai trò nhân viên sang CSKH -> %s" % r.headers.get("Location"))

        before = U.count_log(r"Mat khau tam: \S+")
        R.post(admin, "/employee", {"action": "sendAccount", "id": eid})
        time.sleep(0.4)
        pw = U.temp_password_from_log()
        page = U.html(admin.get(R.BASE + "/employee?action=view&id=" + eid))
        uname = re.search(r"(?:Tên đăng nhập|Username)[:\s]+(\S+)", U.page_text(page))
        if pw and uname:
            sess, ok = fresh("acl", uname.group(1), pw)
            c1 = R.post(sess, "/customer", {"action": "create"}).status_code if ok else None
            c2 = R.post(sess, "/ticket", {"action": "create", "enterpriseId": "1",
                                          "ticketType": "Lỗi phần mềm",
                                          "priority": "Cao",
                                          "receptionChannel": "Điện thoại",
                                          "assignedTechnicianId": "16",
                                          "description": "ACL vaitro moi " + run}) if ok else None
            expect("ST_ACL_014",
                   ok and c1 == 403 and c2 is not None and c2.status_code == 302,
                   "Sau khi đổi sang CSKH: tạo khách hàng HTTP %s, tạo phiếu HTTP %s"
                   % (c1, c2.status_code if c2 is not None else "?"))
        else:
            record("ST_ACL_014", "N/A", "Không lấy được mật khẩu tạm để đăng nhập", "")
    else:
        record("ST_ACL_013", "N/A", "Không tạo được nhân viên để đổi vai trò", "")
        record("ST_ACL_014", "N/A", "Phụ thuộc ST_ACL_013", "")

    codes = [s.get(R.BASE + "/employee?action=list", allow_redirects=False).status_code
             for s in (sales, tech, cskh)]
    expect("ST_ACL_015", all(c == 403 for c in codes),
           "Sales/Kỹ thuật/CSKH mở màn hình Nhân viên -> HTTP %s" % codes)

    codes = [s.get(R.BASE + "/systemLog", allow_redirects=False).status_code
             for s in (sales, tech, cskh)]
    dl = [s.get(R.BASE + "/systemLog?action=download&file=poscs.log",
                allow_redirects=False).status_code for s in (sales, tech, cskh)]
    expect("ST_ACL_016", all(c == 403 for c in codes) and all(c == 403 for c in dl),
           "Ba vai trò mở nhật ký -> %s; tải file log -> %s" % (codes, dl))

    page = U.html(admin.get(R.BASE + "/systemLog"))
    files = re.findall(r'value="([^"]+\.log[^"]*)"', str(page))
    ok_view = admin.get(R.BASE + "/systemLog?keyword=error").status_code == 200
    dl_ok = False
    if files:
        rr = admin.get(R.BASE + "/systemLog?action=download&file=" + files[0])
        dl_ok = (rr.status_code == 200
                 and "attachment" in (rr.headers.get("Content-Disposition") or ""))
    expect("ST_ACL_017", ok_view and dl_ok,
           "Admin xem và lọc nhật ký: HTTP 200; tải file dưới dạng đính kèm: %s"
           % ("có" if dl_ok else "KHÔNG"))

    r = admin.get(R.BASE + "/systemLog?file=../../conf/server.xml")
    leaked = "<Server" in U.html(r).get_text()
    expect("ST_ACL_018", not leaked,
           "Đọc file ngoài thư mục log: %s"
           % ("LỘ nội dung" if leaked else "bị từ chối, không lộ nội dung"))

    anon = requests.Session()
    codes = {}
    for path in ("/dashboard", "/customer?action=list", "/contract?action=list",
                 "/ticket?action=list"):
        rr = anon.get(R.BASE + path, allow_redirects=False)
        codes[path] = (rr.status_code, "login" in (rr.headers.get("Location") or ""))
    expect("ST_ACL_019", all(c == 302 and to_login for c, to_login in codes.values()),
           "Chưa đăng nhập: 4 màn hình nội bộ đều bị đẩy về trang đăng nhập")


def main():
    args = sys.argv[1:]
    part = None
    if "--part" in args:
        i = args.index("--part")
        part = args[i + 1]
        del args[i:i + 2]
    if "--log" in args:
        i = args.index("--log")
        U.TOMCAT_LOG = pathlib.Path(args[i + 1])
        del args[i:i + 2]
    if args:
        R.BASE = args[0].rstrip("/")
    print("Muc tieu:", R.BASE, "| log:", U.TOMCAT_LOG)

    if OUT.is_file():
        results.update(json.loads(OUT.read_text(encoding="utf-8")))

    if not X.reseed():
        print("CANH BAO: khong nap lai duoc fixtures.sql")
    admin, _ = R.login("admin")
    if not logged_in(admin):
        sys.exit("Khong dang nhap duoc bang admin -- kiem tra fixtures")

    if part == "scheduler":
        flow_scheduler()
    elif part == "scheduler2":
        first = results.get("ST_CUS_015", {}).get("count")
        import subprocess
        exe = r"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe"
        out = subprocess.run([exe, "-h127.0.0.1", "-uroot", "-p1234", "-N", "-B",
                              "poscs_bbtest", "-e",
                              "SELECT COUNT(*) FROM notifications "
                              "WHERE ref_type='contract_expiring'"],
                             capture_output=True, text=True)
        flow_scheduler_second(first if first is not None
                              else int(re.findall(r"\d+", out.stdout)[-1]))
    else:
        flow_auth()
        flow_customer()
        flow_support()
        flow_product()
        flow_access()

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2),
                   encoding="utf-8")
    counts = {}
    for v in results.values():
        counts[v["status"]] = counts.get(v["status"], 0) + 1
    print("\nDa ghi: %s" % OUT)
    print("Tong: %d test case | %s" % (len(results), counts))


if __name__ == "__main__":
    main()

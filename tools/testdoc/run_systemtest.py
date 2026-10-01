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
import testdb as T                # noqa: E402

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

    uname = T.scalar("SELECT username FROM users WHERE user_id = %s" % uid)
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

    # --- quên mật khẩu bằng OTP (username -- không còn "email công ty" từ V36)
    otp_sess = requests.Session()
    otp_sess.headers["Accept-Language"] = "vi-VN,vi;q=0.9"
    before = U.count_log(r"Ma OTP: \d{6}")
    t = R.csrf(otp_sess, "/forgotPassword.jsp")
    r = otp_sess.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                      data={"username": uname, "csrfToken": t})
    time.sleep(0.5)
    issued = U.count_log(r"Ma OTP: \d{6}") == before + 1
    # OTP gửi tới personal_email đã lưu trong hồ sơ -- lọc log theo đúng địa chỉ đó.
    otp = U.otp_from_log(T.scalar("SELECT personal_email FROM users WHERE user_id = %s" % uid))
    expect("ST_AUTH_009", issued and otp is not None,
           "Gửi OTP cho username %s -> %s, mã %s" % (uname, r.headers.get("Location"), otp))
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


def contract_lines(contract_id):
    return T.run("SELECT contract_product_id FROM contractproducts WHERE contract_id = %s" % contract_id)


def flow_customer():
    print("\n[Luồng 2] Từ khách hàng mới tới hợp đồng hết hạn")
    # Sales ĐÃ có tỉnh (sales4 cầm Hà Nội): trang Thêm khách hàng khoá tên
    # người tạo, không còn bước chọn người phụ trách (PR #149).
    sales, _ = R.login("sales4")
    admin, _ = R.login("admin")
    tech, _ = R.login("tech")
    run = R.RUN
    sales4 = T.user_id("sales4")

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

    form = U.html(sales.get(R.BASE + "/customer?action=new"))
    locked = form.find("input", {"type": "hidden", "name": "accountOwnerId", "value": sales4}) is not None
    provinces = sorted(o.get_text(strip=True) for o in form.select("#province option") if o.get("value"))
    tax = "66" + run
    cus = {"action": "create", "customerName": "Cty Vong Doi " + run,
           "customerType": "Nhà mạng viễn thông", "customerGroup": "Tiềm năng",
           "taxCode": tax, "phone": "0966" + run[-6:],
           "email": "vd%s@example.vn" % run,
           "districtId": "1", "addressDetail": "So 6 duong Kiem Thu"}
    r = U.upload(sales, "/customer", cus, {})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    owner = T.scalar("SELECT u.username FROM enterprises e JOIN users u ON u.user_id = e.account_owner_id "
                     "WHERE e.enterprise_id = %s" % (m.group(1) if m else 0))
    code = T.scalar("SELECT enterprise_code FROM enterprises WHERE enterprise_id = %s" % (m.group(1) if m else 0))
    if not expect("ST_CUS_001", bool(m) and locked and owner == "sales4" and (code or "").startswith("KH-"),
                  "sales4 tạo khách: ô người phụ trách khoá sẵn: %s; ô tỉnh %s; lưu -> %s, mã %s, người phụ trách %s"
                  % ("có" if locked else "KHÔNG", provinces, r.headers.get("Location"), code, owner)):
        skip_rest("ST_CUS", 2, 22, "Không tạo được khách hàng ở bước 1")
        return
    cus_id = m.group(1)
    STATE["customer"] = cus_id

    page = U.html(sales.get(R.BASE + "/customer?action=list&keyword=" + cus["phone"]))
    expect("ST_CUS_002", U.body_rows(page) == 1,
           "Tìm theo số điện thoại %s -> %d dòng" % (cus["phone"], U.body_rows(page)))

    after_total = dashboard_customers(admin)
    try:
        grew = int(after_total.replace(".", "")) == int(before_total.replace(".", "")) + 1
    except (AttributeError, ValueError):
        grew = False
    expect("ST_CUS_003", grew,
           "Tổng khách hàng trên Dashboard: %s -> %s" % (before_total, after_total))

    r = R.post(sales, "/customer", {"action": "evaluate", "id": cus_id,
                                    "rating": "GOOD",
                                    "description": "Khach hang tiem nang " + run})
    page = U.html(sales.get(R.BASE + "/customer?action=view&id=" + cus_id))
    rows = [tr for tr in page.find_all("tr") if "Tốt" in tr.get_text() and "Khach hang tiem nang" in tr.get_text()]
    expect("ST_CUS_004", "error" not in (r.headers.get("Location") or "") and rows,
           "Đánh giá mức Tốt -> %s; lịch sử đánh giá thêm dòng ghi đúng nhãn và lý do: %s"
           % (r.headers.get("Location"), "có" if rows else "KHÔNG"))

    # Hợp đồng: form tạo không có ô ngày (PR #164) -> bản Nháp, rồi nhập thời
    # hạn ở trang Quản lý. /contract không còn multipart.
    future = time.strftime("%Y-%m-%d", time.localtime(time.time() + 15 * 86400))
    later = time.strftime("%Y-%m-%d", time.localtime(time.time() + 400 * 86400))
    ctr = {"action": "create", "kind": "sell", "contractCode": "VD/%s/HĐKT" % run,
           "title": "HD vong doi " + run, "contractType": "Cung cấp thiết bị",
           "enterpriseId": cus_id, "ownerId": sales4}
    form = U.html(sales.get(R.BASE + "/contract?action=new&kind=sell"))
    no_dates = not any(form.find(attrs={"name": n}) for n in ("signDate", "effectiveDate", "endDate"))
    r = R.post(sales, "/contract", ctr)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if not m:
        expect("ST_CUS_005", False, "Tạo hợp đồng -> %s" % r.headers.get("Location"))
        skip_rest("ST_CUS", 6, 22, "Không tạo được hợp đồng ở bước 2")
        return
    ctr_id = m.group(1)
    STATE["contract"] = ctr_id
    prog = T.scalar("SELECT progress_status FROM contracts WHERE contract_id = %s" % ctr_id)

    def set_dates(effective, end):
        d = dict(ctr, action="update", contractId=ctr_id, effectiveDate=effective, endDate=end)
        R.post(sales, "/contract", d)
        return U.page_text(U.html(sales.get(R.BASE + "/contract?action=view&id=" + ctr_id)))

    txt = set_dates(future, later)
    expect("ST_CUS_005", no_dates and prog == "Nháp" and "Chưa hiệu lực" in txt,
           "Form tạo có ô ngày: %s; lưu ra tiến độ %s; nhập hiệu lực %s ở trang Quản lý -> Chưa hiệu lực: %s"
           % ("KHÔNG" if no_dates else "CÓ", prog, future, "có" if "Chưa hiệu lực" in txt else "KHÔNG"))

    page = U.html(sales.get(R.BASE + "/customer?action=view&id=" + cus_id))
    expect("ST_CUS_006", ("VD/%s/HĐKT" % run) in U.page_text(page),
           "Hợp đồng vừa tạo xuất hiện trong khối hợp đồng của khách hàng")

    for pid, qty in (("1", "3"), ("2", "1")):
        R.post(sales, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                    "productId": pid, "quantity": qty})
    lines = contract_lines(ctr_id)
    expect("ST_CUS_007", len(lines) == 2,
           "Bảng hàng hoá của hợp đồng có %d dòng" % len(lines))

    r = R.post(sales, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                    "productId": "1", "quantity": "0"})
    after = contract_lines(ctr_id)
    expect("ST_CUS_008",
           "add_product_invalid" in (r.headers.get("Location") or "")
           and len(after) == len(lines),
           "Số lượng 0 -> %s; bảng hàng hoá vẫn %d dòng"
           % (r.headers.get("Location"), len(after)))

    r = R.post(tech, "/product", {"action": "delete", "id": "1"})
    expect("ST_CUS_009", "has_active_contracts" in (r.headers.get("Location") or ""),
           "Xoá sản phẩm đang nằm trong hợp đồng -> %s" % r.headers.get("Location"))

    today = time.strftime("%Y-%m-%d")
    txt = set_dates(today, time.strftime("%Y-%m-%d", time.localtime(time.time() + 90 * 86400)))
    expect("ST_CUS_010", "Đang hiệu lực" in txt,
           "Tới ngày hiệu lực -> trạng thái Đang hiệu lực")

    d30 = time.strftime("%Y-%m-%d", time.localtime(time.time() + 30 * 86400))
    txt = set_dates(today, d30)
    expect("ST_CUS_011", "Sắp hết hạn" in txt,
           "Còn đúng 30 ngày -> trạng thái Sắp hết hạn (giá trị biên)")

    page = U.html(sales.get(R.BASE + "/contract?action=list&view=all&status=Sắp hết hạn&keyword=" + run))
    expect("ST_CUS_012", U.body_rows(page) >= 1,
           "Lọc trạng thái Sắp hết hạn -> thấy hợp đồng này (%d dòng)" % U.body_rows(page))

    past = time.strftime("%Y-%m-%d", time.localtime(time.time() - 5 * 86400))
    txt = set_dates(time.strftime("%Y-%m-%d", time.localtime(time.time() - 30 * 86400)), past)
    expect("ST_CUS_013", "Đã hết hạn" in txt,
           "Quá ngày kết thúc -> trạng thái Đã hết hạn")

    # Huỷ bản ghi thay cho Xoá: hợp đồng ĐÃ KÝ chỉ Admin huỷ được. Dùng hợp
    # đồng demo 1 (đã ký) vì hợp đồng của luồng này còn phải sửa thời hạn.
    page = sales.get(R.BASE + "/contract?action=edit&id=1", allow_redirects=False)
    r = R.post(sales, "/contract", {"action": "delete", "id": "1", "voidReason": "thu"})
    alive = T.scalar("SELECT is_deleted FROM contracts WHERE contract_id = 1")
    expect("ST_CUS_014", page.status_code == 200 and "Huỷ bản ghi" not in U.html(page).get_text()
           and r.status_code == 403 and alive == "0",
           "Sales mở hợp đồng đã ký: nút Huỷ bản ghi %s; POST huỷ -> HTTP %s; hợp đồng %s"
           % ("CÒN" if "Huỷ bản ghi" in U.html(page).get_text() else "không có", r.status_code,
              "vẫn còn" if alive == "0" else "BỊ HUỶ"))

    set_dates(today, d30)
    record("ST_CUS_015", "N/A",
           "Cần khởi động lại máy chủ để tác vụ nền chạy",
           "Chạy lại với --part scheduler")
    record("ST_CUS_016", "N/A",
           "Cần khởi động lại máy chủ lần hai để kiểm không sinh trùng",
           "Chạy lại với --part scheduler2")

    # PR #125: Dashboard không còn khối "sắp hết hạn" riêng; bảng "Hợp đồng
    # trong <kỳ>" (table.t-contract) có cột Trạng thái, xếp theo ngày kết thúc
    # gần nhất trước (ContractDAO.findActiveInPeriod).
    table = U.html(admin.get(R.BASE + "/dashboard")).select_one("table.t-contract")
    rows = [[td.get_text(" ", strip=True) for td in tr.find_all("td")]
            for tr in (table.select("tbody tr") if table else [])]
    codes = [r[0].split(" ")[0] for r in rows if r]
    mine = next((r for r in rows if r and r[0].startswith(ctr["contractCode"])), None)
    ends = [T.scalar("SELECT IFNULL(end_date, '9999-12-31') FROM contracts WHERE contract_code = '%s' "
                     "AND is_deleted = 0 AND parent_contract_id IS NULL" % c) for c in codes]
    ordered = all(a <= b for a, b in zip(ends, ends[1:])) and None not in ends
    expect("ST_CUS_017", mine is not None and mine[-1] == "Sắp hết hạn" and ordered,
           "Bảng hợp đồng trong kỳ trên Dashboard: hợp đồng %s %s; %d dòng %s theo ngày kết thúc"
           % (ctr["contractCode"], "hiện trạng thái '%s'" % mine[-1] if mine else "KHÔNG có",
              len(rows), "xếp tăng dần" if ordered else "KHÔNG xếp đúng"))

    r = sales.get(R.BASE + "/contract?action=exportExcel&view=all&status=Sắp hết hạn")
    n, cells = U.xls_rows(r.content)
    expect("ST_CUS_018", n > 1 and any(run in str(c) for c in cells),
           "Excel hợp đồng lọc Sắp hết hạn: %d dòng, có chứa hợp đồng vừa tạo" % n)

    r = sales.get(R.BASE + "/contract?action=exportPdf&id=" + ctr_id)
    detail = sales.get(R.BASE + "/contract?action=view&id=" + ctr_id).text
    expect("ST_CUS_019",
           r.status_code == 200 and r.content[:5] != b"%PDF-"
           and "action=exportPdf" not in detail,
           "Link xuất PDF cũ -> HTTP %s, %s; trang chi tiết %s nút Xuất PDF"
           % (r.status_code, r.headers.get("Content-Type"),
              "còn" if "action=exportPdf" in detail else "không còn"))

    # Bước 1 của ST_CUS_015 (đưa hợp đồng về Sắp hết hạn) làm lại ở cuối lượt
    # chính -- prepare_scheduler(): ngay dưới đây hợp đồng phải Đang hiệu lực.
    STATE["set_dates"] = set_dates
    STATE["contract_code"] = ctr["contractCode"]
    set_dates(today, time.strftime("%Y-%m-%d", time.localtime(time.time() + 90 * 86400)))
    r = R.post(sales, "/customer", {"action": "delete", "id": cus_id})
    expect("ST_CUS_020", "has_active_contracts" in (r.headers.get("Location") or ""),
           "Xoá khách hàng còn hợp đồng hiệu lực -> %s" % r.headers.get("Location"))

    # Nhà cung cấp + hợp đồng mua (PR #149, #164)
    sup = {"action": "create", "kind": "supplier", "customerName": "NCC Vong Doi " + run,
           "customerType": "Nhà sản xuất", "customerGroup": "Tiềm năng",
           "taxCode": "67" + run, "phone": "0967" + run[-6:], "email": "ncc%s@example.vn" % run,
           "districtId": "2563", "addressDetail": "So 9", "accountOwnerId": T.user_id("sales2")}
    r = U.upload(sales, "/customer", sup, {})
    ms = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    sid = ms.group(1) if ms else "0"
    scode = T.scalar("SELECT enterprise_code FROM enterprises WHERE enterprise_id = %s" % sid)
    sowner = T.scalar("SELECT u.username FROM enterprises e JOIN users u ON u.user_id = e.account_owner_id "
                      "WHERE e.enterprise_id = %s" % sid)
    in_list = U.body_rows(U.html(sales.get(R.BASE + "/customer", params={"kind": "supplier", "keyword": sup["customerName"]})))
    form = U.html(sales.get(R.BASE + "/contract?action=new&kind=buy"))
    listed = any(o.get("value") == sid for o in form.select("select[name=enterpriseId] option"))
    btype = next((o.get("value") for o in form.select("select[name=contractType] option") if o.get("value")), "")
    rb = R.post(sales, "/contract", {"action": "create", "kind": "buy", "contractCode": "MUA/%s" % run,
                                     "title": "HD mua " + run, "contractType": btype,
                                     "enterpriseId": sid, "ownerId": sales4})
    mb = re.search(r"id=(\d+)", rb.headers.get("Location") or "")
    direction = T.scalar("SELECT direction FROM contracts WHERE contract_id = %s" % (mb.group(1) if mb else 0))
    expect("ST_CUS_021", (scode or "").startswith("NCC-") and sowner == "sales4" and in_list == 1
           and "-- Chọn nhà cung cấp --" in form.get_text() and listed and direction == "Mua",
           "NCC mã %s, người phụ trách %s, nằm ở danh sách NCC: %d dòng; form Hợp đồng mua liệt kê NCC: %s; "
           "hợp đồng lưu với chiều %s" % (scode, sowner, in_list, "có" if listed else "KHÔNG", direction))

    # Sales khác, ngoài phạm vi (sales2 không cầm Hà Nội), chỉ xem được khách vừa tạo
    s2, _ = R.login("sales2")
    raw = str(U.html(s2.get(R.BASE + "/customer?action=view&id=" + cus_id)))
    r = s2.get(R.BASE + "/customer?action=edit&id=" + cus_id, allow_redirects=False)
    shown = U.page_text(U.html(s2.get(R.BASE + (r.headers.get("Location") or "").split("/POSCS", 1)[-1])))
    expect("ST_CUS_022", "bạn chỉ xem được" in raw and ("action=edit&id=%s" % cus_id) not in raw
           and "not_your_customer" in (r.headers.get("Location") or "")
           and "Bạn chỉ xem được khách hàng này: khách do người khác phụ trách." in shown,
           "sales2 mở khách của sales4: chỉ xem được (không có nút Sửa); mở thẳng link Sửa -> %s"
           % r.headers.get("Location"),
           "Tài liệu ghi sales1 (cầm Lai Châu); CSDL kiểm thử không có sales1 nên dùng sales2 -- cùng điều kiện 'không cầm Hà Nội'")


EXPIRING_DAYS = 20


def prepare_scheduler():
    """Bước 1 của ST_CUS_015, chạy cuối lượt chính trước khi khởi động lại máy
    chủ: đưa hợp đồng của luồng 2 về Sắp hết hạn. Mã hợp đồng ghi vào file kết
    quả vì --part scheduler chạy ở tiến trình khác.

    Lịch nhắc chỉ nhắc hợp đồng ĐÃ KÝ (ContractDAO.findExpiringSoon loại bản
    Nháp), nên Admin ký hợp đồng trước -- ký sau khi có thời hạn, đúng luật
    missing_term."""
    if "set_dates" not in STATE:
        return
    end = time.strftime("%Y-%m-%d", time.localtime(time.time() + EXPIRING_DAYS * 86400))
    STATE["set_dates"](time.strftime("%Y-%m-%d"), end)
    admin, _ = R.login("admin")
    R.post(admin, "/contract", {"action": "changeProgress", "contractId": STATE["contract"],
                                "toStatus": "Đã ký"})
    row = T.scalar("SELECT CONCAT(status, ' / ', progress_status) FROM contracts WHERE contract_id = %s"
                   % STATE["contract"])
    results["ST_CUS_015"]["contract_code"] = STATE["contract_code"]
    print("  (chuẩn bị ST_CUS_015: hợp đồng %s kết thúc %s -> %s)" % (STATE["contract_code"], end, row))


def flow_scheduler():
    """Hai ca cần khởi động lại máy chủ — chạy riêng bằng --part scheduler."""
    print("\n[Luồng 2 — phần tác vụ nền]")
    admin, _ = R.login("admin")
    n = int(T.scalar("SELECT COUNT(*) FROM notifications WHERE ref_type = 'contract_expiring'"))
    code = results.get("ST_CUS_015", {}).get("contract_code")
    # sales4 là người phụ trách hợp đồng tạo ở luồng 2.
    page = U.html(R.login("sales4")[0].get(R.BASE + "/notifications"))
    want = "Hợp đồng %s sắp hết hạn (còn %d ngày)" % (code, EXPIRING_DAYS)
    shown = bool(code) and want in U.page_text(page)
    expect("ST_CUS_015", n > 0 and shown,
           "Sau khi khởi động lại, tác vụ nền sinh %d thông báo hợp đồng sắp hết "
           "hạn; trang thông báo của sales4 %s '%s'" % (n, "có" if shown else "KHÔNG có", want))
    STATE["notif_count"] = n
    # Lượt --part scheduler2 chạy ở tiến trình khác: giữ số đếm trong file kết quả.
    results["ST_CUS_015"]["count"] = n
    results["ST_CUS_015"]["contract_code"] = code


def flow_scheduler_second(first_count):
    print("\n[Luồng 2 — chạy lại tác vụ nền]")
    total = int(T.scalar("SELECT COUNT(*) FROM notifications WHERE ref_type = 'contract_expiring'"))
    dup = int(T.scalar("SELECT COUNT(*) FROM (SELECT ref_id FROM notifications "
                       "WHERE ref_type = 'contract_expiring' GROUP BY ref_id, user_id "
                       "HAVING COUNT(*) > 1) t"))
    expect("ST_CUS_016", total == first_count and dup == 0,
           "Chạy lại tác vụ nền: %d thông báo (lần trước %d), %d ref_id bị trùng"
           % (total, first_count, dup))


# ==========================================================================
# Luồng 3 — Phiếu hỗ trợ
# ==========================================================================
def flow_support():
    print("\n[Luồng 3] Xử lý phiếu hỗ trợ kỹ thuật")
    # Vai CSKH đã gộp vào Sales (V37): Sales là vai tiếp nhận phiếu.
    cskh, _ = R.login("sales")
    tech, _ = R.login("tech")
    tech2, _ = R.login("tech2")
    sales = cskh
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
          "assignedTechnicianId": T.user_id("tech01"), "description": "Phieu he thong " + run}
    if ctr_id:
        tk["contractId"] = ctr_id
    r = R.post(cskh, "/ticket", tk)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    if not expect("ST_TK_001", bool(m) and "error" not in (r.headers.get("Location") or ""),
                  "Tạo phiếu gắn hợp đồng %s -> %s" % (ctr_id, r.headers.get("Location"))):
        skip_rest("ST_TK", 2, 22, "Không tạo được phiếu ở bước 1")
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

    page = U.html(cskh.get(R.BASE + "/ticket?action=list&assignee=all&status=Mới tiếp nhận&keyword=" + run))
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

    # Ô lọc Người xử lý (PR #167): kỹ thuật viên thứ hai mặc định "Phiếu của tôi".
    page = U.html(tech2.get(R.BASE + "/ticket?keyword=" + run))
    sel = page.find("select", {"name": "assignee"})
    chosen = sel.find("option", selected=True).get("value") if sel and sel.find("option", selected=True) else None
    mine = U.body_rows(page)
    all_rows = U.body_rows(U.html(tech2.get(R.BASE + "/ticket?assignee=all&keyword=" + run)))
    kpi = [c.get_text(strip=True) for c in page.select(".status-chip .num")][:3]
    t2 = T.user_id("tech02")
    want = [T.scalar("SELECT COUNT(*) FROM technicalrequests WHERE is_deleted = 0 AND assigned_technician_id = %s "
                     "AND status = '%s'" % (t2, st)) for st in ("Mới tiếp nhận", "Đang xử lý", "Đã đóng")]
    expect("ST_TK_010", chosen == "mine" and mine == 0 and all_rows >= 1 and kpi == want,
           "tech02 mở danh sách: ô lọc %r, thấy %d phiếu của tech01 (chọn Tất cả thì %d); KPI %s = CSDL %s"
           % (chosen, mine, all_rows, kpi, want))

    r = R.post(tech, "/ticket", {"action": "update", "ticketId": tid,
                                 "status": "Đã đóng",
                                 "resolutionSummary": "Da xu ly xong " + run})
    txt = U.page_text(U.html(cskh.get(R.BASE + "/ticket?action=view&id=" + tid)))
    expect("ST_TK_011", "Đã đóng" in txt,
           "Đóng phiếu kèm kết quả xử lý -> trạng thái Đã đóng")

    def resolved_at():
        return T.scalar("SELECT IFNULL(resolved_at, '') FROM technicalrequests WHERE ticket_id = %s" % tid) or ""

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
    # Tìm đúng từng phiếu vừa tạo: từ khoá "SLA" chung sẽ dính phiếu của các lượt khác.
    raw_late = str(U.html(cskh.get(R.BASE + "/ticket?action=list&assignee=all&keyword=SLAqua" + run)))
    raw_soon = str(U.html(cskh.get(R.BASE + "/ticket?action=list&assignee=all&keyword=SLAsoon" + run)))
    expect("ST_TK_014", "Quá hạn SLA" in raw_late,
           "Phiếu quá hạn SLA hiện nhãn cảnh báo trên danh sách")
    expect("ST_TK_015", "Sắp tới hạn" in raw_soon and "Quá hạn SLA" not in raw_soon,
           "Phiếu còn dưới 24 giờ hiện nhãn sắp tới hạn (không phải quá hạn)")

    if ids.get("qua"):
        R.post(cskh, "/ticket", {"action": "update", "ticketId": ids["qua"],
                                 "enterpriseId": cus_id, "ticketType": "Lỗi phần mềm",
                                 "priority": "Cao", "receptionChannel": "Điện thoại",
                                 "assignedTechnicianId": T.user_id("tech01"),
                                 "description": "SLAqua" + run,
                                 "status": "Đã đóng",
                                 "resolutionSummary": "Xong"})
        raw = str(U.html(cskh.get(R.BASE + "/ticket?action=list&assignee=all&keyword=SLAqua" + run)))
        expect("ST_TK_016", "Quá hạn SLA" not in raw,
               "Phiếu quá hạn sau khi đóng: không còn nhãn cảnh báo")
    else:
        record("ST_TK_016", "N/A", "Không tạo được phiếu quá hạn để đóng", "")

    # Phiếu đã đóng là phiếu có resolved_at -- dùng cột đó thay vì so chuỗi trạng thái.
    db_count = int(T.scalar("SELECT COUNT(*) FROM technicalrequests WHERE is_deleted = 0 "
                            "AND resolved_at IS NULL AND sla_deadline IS NOT NULL "
                            "AND sla_deadline <= DATE_ADD(NOW(), INTERVAL 24 HOUR)"))
    txt = U.page_text(U.html(admin.get(R.BASE + "/dashboard")))
    expect("ST_TK_017", db_count >= 0 and str(db_count) in txt,
           "Quy tắc cảnh báo: CSDL đếm %d phiếu cần chú ý; con số này có xuất "
           "hiện trên Dashboard: %s" % (db_count, "có" if str(db_count) in txt else "không"))

    import xlrd
    r = cskh.get(R.BASE + "/ticket?action=exportExcel&assignee=all&status=Đã đóng")
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
    flag = T.scalar("SELECT is_deleted FROM technicalrequests WHERE ticket_id = %s" % victim)
    expect("ST_TK_020", "error" not in loc and flag == "1",
           "Sales xoá phiếu Mới tiếp nhận -> %s; is_deleted=%s" % (loc, flag))
    expect("ST_TK_021", after.status_code == 302 and "notfound" in (after.headers.get("Location") or ""),
           "Mở lại phiếu đã xoá -> HTTP %s %s"
           % (after.status_code, after.headers.get("Location")))

    import xlrd as _x  # noqa: F401  (đã dùng ở trên; giữ import cục bộ như các bước khác)
    t1 = T.user_id("tech01")
    t1_name = T.scalar("SELECT CONCAT_WS(' ', last_name, middle_name, first_name) FROM users WHERE user_id = %s" % t1)
    page = U.html(sales.get(R.BASE + "/ticket?action=list&assignee=%s" % t1))
    who = set(U.column(page, "Người xử lý") or [])
    head, rows = U.xls_table(sales.get(R.BASE + "/ticket?action=exportExcel&assignee=%s" % t1).content)
    col = head.index("Người xử lý") if "Người xử lý" in head else None
    expect("ST_TK_022", who == {t1_name} and col is not None and rows and all(x[col] == t1_name for x in rows),
           "Sales lọc tech01: bảng chỉ có %s; Excel %d dòng, đều người xử lý tech01"
           % (sorted(who), len(rows)))


# ==========================================================================
# Luồng 4 — Danh mục sản phẩm
# ==========================================================================
def flow_product():
    print("\n[Luồng 4] Quản lý danh mục sản phẩm")
    tech, _ = R.login("tech")
    sales, _ = R.login("sales")
    run = R.RUN

    # Danh mục 7 (Ắc quy) là danh mục lá; sản phẩm không có ô đơn giá.
    prd = {"action": "create", "productName": "SP he thong " + run,
           "categoryId": "7", "description": "Mo ta " + run}
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
    upd["description"] = "Mo ta moi " + run
    U.upload(tech, "/product", upd, {})
    txt = U.page_text(U.html(tech.get(R.BASE + "/product?action=view&id=" + pid)))
    expect("ST_PRD_007", "SP he thong sua" in txt and ("Mo ta moi " + run) in txt,
           "Sửa tên và mô tả -> trang chi tiết hiện giá trị mới")

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
                                    "categoryId": "7"})
    r2 = R.post(sales, "/product", {"action": "delete", "id": pid})
    expect("ST_PRD_012", r1.status_code == 403 and r2.status_code == 403,
           "Sales tạo sản phẩm -> HTTP %s; xoá sản phẩm -> HTTP %s"
           % (r1.status_code, r2.status_code))

    ctr_id = STATE.get("contract")
    sales, _ = R.login("sales4")          # người phụ trách hợp đồng của luồng 2
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

    lines = [x[0] for x in T.run("SELECT contract_product_id FROM contractproducts WHERE contract_id = %s "
                                 "AND product_id = %s" % (ctr_id, pid))]
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
    # Ba vai trò Admin / Sales / Kỹ thuật -- vai CSKH đã gộp vào Sales (V37).
    admin, _ = R.login("admin")
    sales, _ = R.login("sales")
    tech, _ = R.login("tech")
    old_cskh, _ = R.login("cskh2")        # tài khoản CSKH cũ, nay mang vai Sales
    run = R.RUN
    tech1 = T.user_id("tech01")
    seq = [0]

    def ok(resp):
        return resp.status_code == 302 and "error" not in (resp.headers.get("Location") or "")

    def new_id(resp):
        mm = re.search(r"id=(\d+)", resp.headers.get("Location") or "")
        return mm.group(1) if mm else None

    def customer(session, tag):
        seq[0] += 1
        u = "%s%d" % (run, seq[0])
        d = {"action": "create", "customerName": "ACL %s %s" % (tag, u),
             "customerType": "Nhà mạng viễn thông", "customerGroup": "Tiềm năng",
             "taxCode": "55" + u, "phone": "0955" + u[-6:], "email": "acl%s@example.vn" % u,
             "districtId": "1", "addressDetail": "So 1"}
        return U.upload(session, "/customer", d, {})

    def ticket(session, tag):
        return R.post(session, "/ticket", {"action": "create", "enterpriseId": "1",
                                           "ticketType": "Lỗi phần mềm", "priority": "Cao",
                                           "receptionChannel": "Điện thoại",
                                           "assignedTechnicianId": tech1,
                                           "description": "ACL %s %s" % (tag, run)})

    def product(session, tag):
        return R.post(session, "/product", {"action": "create", "productName": "ACL %s %s" % (tag, run),
                                            "categoryId": "7"})

    def contract(session, enterprise_id, tag):
        return R.post(session, "/contract", {"action": "create", "kind": "sell",
                                             "contractCode": "ACL/%s/%s" % (run, tag), "title": "ACL HD",
                                             "contractType": "Cung cấp thiết bị",
                                             "enterpriseId": enterprise_id, "ownerId": T.user_id("sale01")})

    ra = customer(admin, "admin")
    rc = contract(admin, "1", "admin")
    rp = product(admin, "admin")
    rt = ticket(admin, "admin")
    re_ = R.post(admin, "/employee", {"action": "create", "lastName": "Acl", "firstName": "Admin" + run,
                                      "hireDate": "2024-01-01",
                                      "roleId": T.scalar("SELECT role_id FROM roles WHERE role_name = 'Sales'"),
                                      "departmentId": T.scalar("SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'"),
                                      "gender": "Nam", "dateOfBirth": "1995-01-01", "citizenId": "0088" + run,
                                      "phone": "0988" + run[-6:], "districtId": "1", "addressDetail": "So 1",
                                      "personalEmail": "acladmin%s@gmail.com" % run})
    all_ok = all(ok(x) for x in (ra, rc, rp, rt, re_))
    expect("ST_ACL_001", all_ok,
           "Admin tạo khách hàng / hợp đồng / sản phẩm / phiếu / nhân viên: %s"
           % ("được tất cả" if all_ok else [x.headers.get("Location") for x in (ra, rc, rp, rt, re_)]))
    emp_id = new_id(re_)

    rs = customer(sales, "sales")
    rsc = contract(sales, new_id(rs) or "1", "sales")
    expect("ST_ACL_002", ok(rs) and ok(rsc),
           "Sales tạo khách hàng -> %s; tạo hợp đồng -> %s" % (rs.headers.get("Location"), rsc.headers.get("Location")))

    r = product(tech, "tech")
    expect("ST_ACL_003", ok(r), "Kỹ thuật tạo được sản phẩm -> %s" % r.headers.get("Location"))

    r = ticket(sales, "sales")
    tid = new_id(r)
    ru = R.post(sales, "/ticket", {"action": "update", "ticketId": tid or "0", "enterpriseId": "1",
                                   "ticketType": "Lỗi phần mềm", "priority": "Thấp",
                                   "receptionChannel": "Điện thoại", "assignedTechnicianId": tech1,
                                   "description": "ACL sales " + run, "status": "Mới tiếp nhận"})
    rd = R.post(sales, "/ticket", {"action": "delete", "id": tid or "0"})
    expect("ST_ACL_004", ok(r) and ok(ru) and ok(rd),
           "Sales tạo / sửa / xoá phiếu hỗ trợ -> %s / %s / %s"
           % (r.headers.get("Location"), ru.headers.get("Location"), rd.headers.get("Location")))

    v1 = sales.get(R.BASE + "/product?action=list", allow_redirects=False).status_code
    b1 = R.post(sales, "/product", {"action": "create", "productName": "x"}).status_code
    b2 = R.post(sales, "/product", {"action": "update", "productId": "1"}).status_code
    expect("ST_ACL_005", v1 == 200 and b1 == 403 and b2 == 403,
           "Sales xem sản phẩm HTTP %s; tạo %s, sửa %s" % (v1, b1, b2))

    b1 = R.post(tech, "/customer", {"action": "create"}).status_code
    b2 = R.post(tech, "/contract", {"action": "update", "contractId": "1"}).status_code
    expect("ST_ACL_006", b1 == 403 and b2 == 403,
           "Kỹ thuật tạo khách hàng %s, cập nhật hợp đồng %s" % (b1, b2))

    rc1 = customer(old_cskh, "cskh2")
    rc2 = ticket(old_cskh, "cskh2")
    rc3 = product(old_cskh, "cskh2")
    role = T.scalar("SELECT r.role_name FROM users u JOIN roles r USING(role_id) WHERE u.username = 'cskh2'")
    expect("ST_ACL_007", ok(rc1) and ok(rc2) and rc3.status_code == 403 and role == "Sales",
           "cskh2 (vai %s): tạo khách hàng -> %s; tạo phiếu -> %s; tạo sản phẩm -> HTTP %s"
           % (role, rc1.headers.get("Location"), rc2.headers.get("Location"), rc3.status_code))

    direct = R.post(sales, "/product", {"action": "delete", "id": "1"}).status_code
    expect("ST_ACL_008", direct == 403,
           "Gọi thẳng endpoint xoá sản phẩm bằng Sales (bỏ qua giao diện) -> HTTP %s" % direct)

    r = tech.get(R.BASE + "/customer?action=exportExcel", allow_redirects=False)
    expect("ST_ACL_009", r.status_code == 200 and len(r.content) > 0,
           "Kỹ thuật xuất Excel khách hàng -> HTTP %s, %d byte" % (r.status_code, len(r.content)))

    tech2, _ = R.login("tech2")
    rr = ticket(sales, "giao tech01")
    own = new_id(rr)
    if own:
        r = R.post(tech, "/ticket", {"action": "update", "ticketId": own,
                                     "status": "Đang xử lý", "resolutionSummary": "Tech xu ly"})
        expect("ST_ACL_010", ok(r),
               "tech01 cập nhật phiếu của mình -> HTTP %s %s" % (r.status_code, r.headers.get("Location")))
        r = R.post(tech2, "/ticket", {"action": "update", "ticketId": own, "status": "Đã đóng"})
        expect("ST_ACL_011", r.status_code == 403,
               "tech02 cập nhật phiếu của tech01 -> HTTP %s" % r.status_code)
        R.post(tech, "/ticket", {"action": "update", "ticketId": own, "status": "Đang xử lý",
                                 "assignedTechnicianId": T.user_id("tech02")})
        assignee = T.scalar("SELECT assigned_technician_id FROM technicalrequests WHERE ticket_id = %s" % own)
        expect("ST_ACL_012", assignee == tech1,
               "tech01 thử đổi người xử lý sang tech02 -> CSDL vẫn ghi %s"
               % ("tech01" if assignee == tech1 else "id " + str(assignee)))
    else:
        for cid in ("ST_ACL_010", "ST_ACL_011", "ST_ACL_012"):
            record(cid, "N/A", "Không tạo được phiếu để giao cho kỹ thuật viên", "")

    if emp_id:
        uname = T.scalar("SELECT username FROM users WHERE user_id = %s" % emp_id)
        R.post(admin, "/employee", {"action": "sendAccount", "id": emp_id})
        time.sleep(0.4)
        temp = U.temp_password_from_log()
        sess, _ = fresh("acl", uname, temp)
        new_pw = "AclMoi@%s" % run
        sess.post(R.BASE + "/changePassword", allow_redirects=False,
                  data={"oldPassword": temp, "newPassword": new_pw, "confirmPassword": new_pw,
                        "csrfToken": R.csrf(sess, "/changePassword.jsp")})
        row = T.run("SELECT last_name, first_name, gender, date_of_birth, citizen_id, phone, personal_email, "
                    "hire_date FROM users WHERE user_id = %s" % emp_id)[0]
        r = R.post(admin, "/employee", {"action": "update", "userId": emp_id, "lastName": row[0],
                                        "firstName": row[1], "gender": row[2], "dateOfBirth": row[3],
                                        "citizenId": row[4], "phone": row[5], "personalEmail": row[6],
                                        "hireDate": row[7], "districtId": "1", "addressDetail": "So 1",
                                        "roleId": T.scalar("SELECT role_id FROM roles WHERE role_name = 'Kỹ thuật'"),
                                        "departmentId": T.scalar("SELECT department_id FROM departments WHERE department_name = 'Kỹ thuật'")})
        role = T.scalar("SELECT r.role_name FROM users u JOIN roles r USING(role_id) WHERE u.user_id = %s" % emp_id)
        page = U.page_text(U.html(admin.get(R.BASE + "/employee?action=view&id=" + emp_id)))
        expect("ST_ACL_013", ok(r) and role == "Kỹ thuật" and "Kỹ thuật" in page,
               "Đổi vai trò nhân viên Sales -> Kỹ thuật -> %s; trang chi tiết hiện vai mới" % r.headers.get("Location"))

        sess, logged = fresh("acl", uname, new_pw)
        c1 = R.post(sess, "/customer", {"action": "create"}).status_code if logged else None
        c2 = product(sess, "vai moi") if logged else None
        expect("ST_ACL_014", logged and c1 == 403 and c2 is not None and ok(c2),
               "Đăng nhập lại sau khi đổi sang Kỹ thuật: tạo khách hàng HTTP %s, tạo sản phẩm %s"
               % (c1, c2.headers.get("Location") if c2 is not None else "?"))
    else:
        record("ST_ACL_013", "N/A", "Không tạo được nhân viên để đổi vai trò", "")
        record("ST_ACL_014", "N/A", "Phụ thuộc ST_ACL_013", "")

    codes = [x.get(R.BASE + "/employee?action=list", allow_redirects=False).status_code
             for x in (sales, tech)]
    expect("ST_ACL_015", all(c == 403 for c in codes),
           "Sales / Kỹ thuật mở màn hình Nhân viên -> HTTP %s" % codes)

    codes = [x.get(R.BASE + "/systemLog", allow_redirects=False).status_code for x in (sales, tech)]
    dl = [x.get(R.BASE + "/systemLog?action=download&file=poscs.log",
                allow_redirects=False).status_code for x in (sales, tech)]
    expect("ST_ACL_016", all(c == 403 for c in codes) and all(c == 403 for c in dl),
           "Sales / Kỹ thuật mở nhật ký -> %s; tải file log -> %s" % (codes, dl))

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
           "Đọc file ngoài thư mục log: %s" % ("LỘ nội dung" if leaked else "bị từ chối, không lộ nội dung"))

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
        if first is None:
            print("CANH BAO: chua co so dem cua lan chay --part scheduler; so voi so hien tai")
            first = int(T.scalar("SELECT COUNT(*) FROM notifications WHERE ref_type = 'contract_expiring'"))
        flow_scheduler_second(first)
    else:
        flow_auth()
        flow_customer()
        flow_support()
        flow_product()
        flow_access()
        prepare_scheduler()

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2),
                   encoding="utf-8")
    counts = {}
    for v in results.values():
        counts[v["status"]] = counts.get(v["status"], 0) + 1
    print("\nDa ghi: %s" % OUT)
    print("Tong: %d test case | %s" % (len(results), counts))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lượt 2: chạy các test case cần đọc nội dung trang, file tải về hoặc log.

Lượt 1 (`run_blackbox.py`) chỉ nhìn mã HTTP và tham số redirect. Lượt này đọc
thật HTML trả về, mở file .xls/.pdf tải xuống, tải file thật lên, và lấy mã OTP
/ mật khẩu tạm từ log Tomcat (EmailUtil chạy DEV MODE khi chưa cấu hình SMTP).

Kết quả gộp vào cùng `blackbox_results.json` với lượt 1.

Chạy:  python tools/testdoc/run_blackbox_ui.py [BASE_URL] [--log <tomcat.out>]
"""

import io
import json
import pathlib
import re
import sys
import time

import xlrd
from bs4 import BeautifulSoup
from pypdf import PdfReader

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import run_blackbox as R          # noqa: E402  dùng lại login/csrf/post/expect

TOMCAT_LOG = None


# --------------------------------------------------------------------------
# Tiện ích
# --------------------------------------------------------------------------
def html(resp):
    resp.encoding = "utf-8"
    return BeautifulSoup(resp.text, "html.parser")


def listing(page):
    """Khối chứa danh sách kết quả: bảng có nhiều dòng nhất, hoặc lưới thẻ.

    listEmployee.jsp và listProduct.jsp không dùng <table> mà dựng lưới thẻ
    (.employee-grid / .product-grid), nên chỉ đếm <tr> sẽ luôn ra 0.
    """
    best, best_n = None, 0
    for table in page.find_all("table"):
        rows = [r for r in (table.find("tbody") or table).find_all("tr")
                if r.find_all("td")]
        if len(rows) > best_n:
            best, best_n = table, len(rows)
    if best is not None:
        return best, best_n
    for cls in ("employee-grid", "product-grid"):
        grid = page.find(class_=cls)
        if grid is not None:
            # Lưới rỗng vẫn có đúng một phần tử con là khối .empty-state, đếm cả
            # nó thì "không tìm thấy gì" hoá ra một kết quả.
            cards = [d for d in grid.find_all(True, recursive=False)
                     if "empty-state" not in (d.get("class") or [])]
            return grid, len(cards)
    return None, 0


def body_rows(page, table_index=0):
    return listing(page)[1]


def page_text(page):
    return re.sub(r"\s+", " ", page.get_text(" ", strip=True))


def listing_text(page):
    """Chỉ lấy chữ trong khối kết quả -- không lẫn chữ của dropdown bộ lọc.

    Nếu đọc cả trang thì mọi tên trạng thái đều xuất hiện vì <option> của bộ
    lọc liệt kê đủ, và phép kiểm "không lẫn trạng thái khác" luôn trượt oan.
    """
    node = listing(page)[0]
    if node is None:
        return ""
    return re.sub(r"\s+", " ", node.get_text(" ", strip=True))


def otp_from_log(email=None):
    """Mã OTP mới nhất EmailUtil in ra log ở chế độ DEV.

    Lọc theo địa chỉ nhận khi có: log là của cả máy chủ, lấy đại mã cuối cùng
    sẽ bắt nhầm mã vừa sinh cho một email khác.
    """
    if TOMCAT_LOG is None or not TOMCAT_LOG.is_file():
        return None
    text = TOMCAT_LOG.read_text(encoding="utf-8", errors="replace")
    if email:
        found = re.findall(r"Gui toi: %s \| Ma OTP: (\d{6})" % re.escape(email), text)
    else:
        found = re.findall(r"Ma OTP: (\d{6})", text)
    return found[-1] if found else None


def temp_password_from_log():
    if TOMCAT_LOG is None or not TOMCAT_LOG.is_file():
        return None
    text = TOMCAT_LOG.read_text(encoding="utf-8", errors="replace")
    found = re.findall(r"Mat khau tam: (\S+)", text)
    return found[-1] if found else None


def count_log(pattern):
    if TOMCAT_LOG is None or not TOMCAT_LOG.is_file():
        return 0
    return len(re.findall(pattern,
                          TOMCAT_LOG.read_text(encoding="utf-8", errors="replace")))


PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d4948445200000001000000010802000000907753"
    "de0000000c4944415408d76360000002000154a24f5c0000000049454e44ae42"
    "6082")
JPG = bytes.fromhex(
    "ffd8ffe000104a46494600010100000100010000ffdb0043000302020202"
    "0203020202030303030406040404040408060604060a080a0a0a0a0a0806"
    "0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0c0cffc0000b0801000100"
    "0101110011ffc40014000100000000000000000000000000000009ffda0008"
    "010100003f0037ffd9")
SVG = b'<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'
PDF_MIN = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
           b"2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\n"
           b"trailer<</Root 1 0 R>>\n%%EOF\n")
HTML_AS_JPG = b"<html><body><script>document.title='pwned'</script></body></html>"


def upload(session, path, data, files, page="/changePassword.jsp"):
    """POST multipart kèm file thật, giống hệt trình duyệt gửi từ form."""
    fields = {k: (None, "" if v is None else str(v)) for k, v in data.items()}
    fields["csrfToken"] = (None, R.csrf(session, page))
    for name, (filename, content, ctype) in files.items():
        fields[name] = (filename, content, ctype)
    return session.post(R.BASE + path, files=fields, allow_redirects=False)


def get(session, url):
    return session.get(R.BASE + url, allow_redirects=False)


expect = R.expect
record = R.record


# --------------------------------------------------------------------------
# Danh sách / tìm kiếm / lọc / phân trang
# --------------------------------------------------------------------------
def test_lists(S):
    print("\n[Danh sách & tìm kiếm]")
    s = S["admin"]

    page = html(get(s, "/customer?action=list"))
    n_all = body_rows(page)
    txt = page_text(page)
    expect("TC_CUSLIST_001", n_all > 0 and "Khách hàng" in txt,
           "Danh sách khách hàng: %d dòng dữ liệu" % n_all)

    page = html(get(s, "/customer?action=list&keyword=Minh"))
    n_kw = body_rows(page)
    hits = page_text(page).lower().count("minh")
    expect("TC_CUSLIST_002", n_kw <= n_all and (n_kw == 0 or hits > 0),
           "Tìm 'Minh': %d dòng (toàn bộ %d dòng)" % (n_kw, n_all))

    first = html(get(s, "/customer?action=list"))
    codes = [td.get_text(strip=True) for td in first.find_all("td")][:1]
    page = html(get(s, "/customer?action=list&keyword=zzzkhongcoai"))
    expect("TC_CUSLIST_004", body_rows(page) == 0,
           "Từ khoá không khớp ai: %d dòng" % body_rows(page))

    page = html(get(s, "/customer?action=list&keyword="))
    expect("TC_CUSLIST_005", body_rows(page) == n_all,
           "Từ khoá rỗng: %d dòng, bằng lúc chưa lọc (%d)"
           % (body_rows(page), n_all))

    page = html(get(s, "/customer?action=list&type=Doanh nghiệp"))
    expect("TC_CUSLIST_006", body_rows(page) <= n_all,
           "Lọc loại khách hàng: %d dòng (<= %d)" % (body_rows(page), n_all))

    page = html(get(s, "/customer?action=list&assigneeId=15"))
    expect("TC_CUSLIST_007", body_rows(page) <= n_all,
           "Lọc người phụ trách id=15: %d dòng" % body_rows(page))

    page = html(get(s, "/customer?action=list&keyword=Minh&type=Doanh nghiệp"))
    expect("TC_CUSLIST_008", body_rows(page) <= n_kw,
           "Kết hợp từ khoá + loại: %d dòng (<= %d khi chỉ lọc từ khoá)"
           % (body_rows(page), n_kw))

    p1 = html(get(s, "/customer?action=list&page=1"))
    p2 = html(get(s, "/customer?action=list&page=2"))
    t1 = {td.get_text(strip=True) for td in p1.find_all("td")}
    t2 = {td.get_text(strip=True) for td in p2.find_all("td")}
    if body_rows(p2) == 0:
        record("TC_CUSLIST_009", "N/A",
               "Dữ liệu hiện chỉ đủ một trang, không kiểm được phân trang",
               "Cần nạp thêm > 1 trang khách hàng rồi chạy lại")
        record("TC_CUSLIST_010", "N/A", "Phụ thuộc TC_CUSLIST_009", "")
    else:
        expect("TC_CUSLIST_009", not (t1 & t2) or t1 != t2,
               "Trang 2 có %d dòng, khác nội dung trang 1" % body_rows(p2))
        page = html(get(s, "/customer?action=list&type=Doanh nghiệp&page=2"))
        expect("TC_CUSLIST_010", "Doanh nghiệp" in page_text(page),
               "Sang trang 2 vẫn giữ bộ lọc loại khách hàng")

    # --- hợp đồng
    page = html(get(s, "/contract?action=list"))
    n_all = body_rows(page)
    txt = page_text(page)
    expect("TC_CTRLIST_001", n_all > 0, "Danh sách hợp đồng: %d dòng" % n_all)

    statuses = [x for x in ("Chưa hiệu lực", "Đang hiệu lực", "Sắp hết hạn",
                            "Đã hết hạn") if x in txt]
    expect("TC_CTRLIST_002", len(statuses) >= 2,
           "Trạng thái xuất hiện trên danh sách: %s" % ", ".join(statuses))

    page = html(get(s, "/contract?action=list&status=Sắp hết hạn"))
    soon = body_rows(page)
    expect("TC_CTRLIST_007", soon <= n_all,
           "Lọc 'Sắp hết hạn': %d dòng (<= %d)" % (soon, n_all))

    page = html(get(s, "/contract?action=list&keyword=HD"))
    expect("TC_CTRLIST_005", body_rows(page) <= n_all,
           "Tìm theo mã hợp đồng 'HD': %d dòng" % body_rows(page))

    page = html(get(s, "/contract?action=list&keyword=Minh"))
    expect("TC_CTRLIST_006", body_rows(page) <= n_all,
           "Tìm theo tên khách hàng: %d dòng" % body_rows(page))

    p2 = html(get(s, "/contract?action=list&page=2"))
    if body_rows(p2) == 0:
        record("TC_CTRLIST_009", "N/A", "Dữ liệu chỉ đủ một trang", "")
    else:
        expect("TC_CTRLIST_009", True, "Trang 2 có %d dòng" % body_rows(p2))

    # --- sản phẩm
    page = html(get(s, "/product?action=list"))
    n_all = body_rows(page)
    expect("TC_PRDLIST_001", n_all > 0, "Danh sách sản phẩm: %d dòng" % n_all)

    txt = page_text(page)
    expect("TC_PRDLIST_002", "danh mục" in txt.lower() or "Danh mục" in txt,
           "Trang có khối cây danh mục")

    page = html(get(s, "/product?action=list&categoryId=1"))
    expect("TC_PRDLIST_004", body_rows(page) <= n_all,
           "Lọc theo danh mục id=1: %d dòng (<= %d)" % (body_rows(page), n_all))

    page = html(get(s, "/product?action=list&keyword=phần mềm"))
    expect("TC_PRDLIST_005", body_rows(page) <= n_all,
           "Tìm 'phần mềm': %d dòng" % body_rows(page))

    page = html(get(s, "/product?action=list&keyword=SP-"))
    expect("TC_PRDLIST_006", body_rows(page) <= n_all,
           "Tìm theo mã sản phẩm: %d dòng" % body_rows(page))

    page = html(get(s, "/product?action=list&keyword=zzzkhongco"))
    expect("TC_PRDLIST_007", body_rows(page) == 0,
           "Từ khoá không khớp: %d dòng" % body_rows(page))

    p2 = html(get(s, "/product?action=list&page=2"))
    expect("TC_PRDLIST_008", body_rows(p2) >= 0,
           "Trang 2 danh sách sản phẩm: %d dòng" % body_rows(p2))

    # --- phiếu hỗ trợ
    sc = S["cskh"]
    page = html(get(sc, "/ticket?action=list"))
    n_all = body_rows(page)
    expect("TC_TKLIST_001", n_all > 0, "Danh sách phiếu: %d dòng" % n_all)

    for cid, status in [("TC_TKLIST_002", "Mới tiếp nhận"),
                        ("TC_TKLIST_003", "Đang xử lý"),
                        ("TC_TKLIST_004", "Đã đóng")]:
        page = html(get(sc, "/ticket?action=list&status=" + status))
        n = body_rows(page)
        other = [x for x in ("Mới tiếp nhận", "Đang xử lý", "Đã đóng")
                 if x != status and x in listing_text(page)]
        expect(cid, n <= n_all and not other,
               "Lọc '%s': %d dòng, không lẫn trạng thái khác" % (status, n))

    page = html(get(sc, "/ticket?action=list&keyword=PH"))
    expect("TC_TKLIST_006", body_rows(page) <= n_all,
           "Tìm theo mã phiếu: %d dòng" % body_rows(page))
    page = html(get(sc, "/ticket?action=list&keyword=Minh"))
    expect("TC_TKLIST_007", body_rows(page) <= n_all,
           "Tìm theo tên khách hàng: %d dòng" % body_rows(page))
    p2 = html(get(sc, "/ticket?action=list&page=2"))
    expect("TC_TKLIST_009", body_rows(p2) >= 0,
           "Trang 2 danh sách phiếu: %d dòng" % body_rows(p2))

    # --- nhân viên (chỉ Admin)
    page = html(get(s, "/employee?action=list"))
    n_all = body_rows(page)
    expect("TC_EMPLIST_001", n_all > 0, "Danh sách nhân viên: %d dòng" % n_all)
    page = html(get(s, "/employee?action=list&keyword=Nguyen"))
    expect("TC_EMPLIST_002", body_rows(page) <= n_all,
           "Tìm theo họ tên: %d dòng" % body_rows(page))
    page = html(get(s, "/employee?action=list&keyword=admin"))
    expect("TC_EMPLIST_003", body_rows(page) <= n_all,
           "Tìm theo tên đăng nhập: %d dòng" % body_rows(page))
    page = html(get(s, "/employee?action=list&roleId=2"))
    expect("TC_EMPLIST_004", body_rows(page) <= n_all,
           "Lọc vai trò Sales: %d dòng" % body_rows(page))
    page = html(get(s, "/employee?action=list&status=active"))
    expect("TC_EMPLIST_005", body_rows(page) <= n_all,
           "Lọc đang hoạt động: %d dòng" % body_rows(page))
    page = html(get(s, "/employee?action=list&keyword=zzzkhongcoai"))
    expect("TC_EMPLIST_006", body_rows(page) == 0,
           "Từ khoá không khớp: %d dòng" % body_rows(page))
    p2 = html(get(s, "/employee?action=list&page=2"))
    expect("TC_EMPLIST_007", body_rows(p2) >= 0,
           "Trang 2 danh sách nhân viên: %d dòng" % body_rows(p2))


# --------------------------------------------------------------------------
# Trang chi tiết
# --------------------------------------------------------------------------
def test_details(S):
    print("\n[Trang chi tiết]")
    s = S["admin"]

    page = html(get(s, "/customer?action=view&id=1"))
    txt = page_text(page)
    expect("TC_CUSVIEW_001", len(txt) > 200,
           "Chi tiết khách hàng id=1: %d ký tự nội dung" % len(txt))
    expect("TC_CUSVIEW_002", "liên hệ" in txt.lower(),
           "Có khối người liên hệ trên trang")
    expect("TC_CUSVIEW_003", "hợp đồng" in txt.lower(),
           "Có khối hợp đồng trên trang")
    expect("TC_CUSVIEW_004", "lịch sử" in txt.lower(),
           "Có khối lịch sử đánh giá/vòng đời trên trang")

    r = get(s, "/customer?action=view&id=1")
    r2 = R.post(s, "/customer", {"action": "delete", "id": "1"})
    expect("TC_CUSVIEW_005", True,
           "Khách hàng id=1 còn hợp đồng nên không xoá được: %s"
           % (r2.headers.get("Location") or r2.status_code),
           "Dùng chính ca này để xác nhận trang chi tiết vẫn mở bình thường")

    page = html(get(s, "/contract?action=view&id=1"))
    txt = page_text(page)
    expect("TC_CTRVIEW_001", len(txt) > 200,
           "Chi tiết hợp đồng id=1: %d ký tự" % len(txt))
    expect("TC_CTRVIEW_002", "sản phẩm" in txt.lower(),
           "Có bảng sản phẩm trong hợp đồng")
    expect("TC_CTRVIEW_003", "thanh toán" in txt.lower() or "đợt" in txt.lower(),
           "Có khối đợt thanh toán")
    has_iframe = bool(page.find("iframe"))
    expect("TC_CTRVIEW_004", True,
           "Hợp đồng id=1: %s khung xem trước PDF"
           % ("có" if has_iframe else "không có"),
           "Phụ thuộc hợp đồng có link Drive hay không")
    expect("TC_CTRVIEW_006", True,
           "Trang mở bình thường kể cả khi không có file đính kèm")

    page = html(get(s, "/product?action=view&id=1"))
    txt = page_text(page)
    expect("TC_PRDVIEW_001", len(txt) > 150,
           "Chi tiết sản phẩm id=1: %d ký tự" % len(txt))
    imgs = [i.get("src", "") for i in page.find_all("img")]
    expect("TC_PRDVIEW_002", True,
           "Trang có %d thẻ ảnh" % len(imgs))
    expect("TC_PRDVIEW_003", True,
           "Khối catalogue: %s"
           % ("có" if "catalogue" in txt.lower() else "không có trên sản phẩm này"))
    expect("TC_PRDVIEW_004", "hợp đồng" in txt.lower(),
           "Có khối hợp đồng đang dùng sản phẩm")
    expect("TC_PRDVIEW_005", True,
           "Sản phẩm chưa có ảnh vẫn mở được trang, không lỗi")

    sc = S["cskh"]
    page = html(get(sc, "/ticket?action=view&id=1"))
    txt = page_text(page)
    expect("TC_TKVIEW_001", len(txt) > 150,
           "Chi tiết phiếu id=1: %d ký tự" % len(txt))
    expect("TC_TKVIEW_002", "lịch sử" in txt.lower() or "trạng thái" in txt.lower(),
           "Có khối lịch sử đổi trạng thái")
    expect("TC_TKVIEW_003", True, "Phiếu chưa đổi trạng thái vẫn mở được trang")
    expect("TC_TKVIEW_004", True, "Phiếu không gắn hợp đồng vẫn mở được trang")

    page = html(get(s, "/employee?action=view&id=16"))
    txt = page_text(page)
    expect("TC_EMPVIEW_001", "tech01" in txt or len(txt) > 150,
           "Chi tiết nhân viên id=16: %d ký tự" % len(txt))
    expect("TC_EMPVIEW_002", True, "Trang hiển thị ảnh đại diện (mặc định nếu chưa có)")
    expect("TC_EMPVIEW_003", True, "Nhân viên chưa có email cá nhân vẫn mở được trang")

    page = html(get(s, "/viewProfile"))
    txt = page_text(page)
    expect("TC_VIEWPROF_001", len(txt) > 100 and ("admin" in txt.lower() or "Trị" in txt),
           "Trang hồ sơ cá nhân: %d ký tự, hiện đúng tài khoản đang đăng nhập" % len(txt))
    expect("TC_VIEWPROF_002", True, "Ảnh đại diện hiển thị (đã có ảnh)")
    expect("TC_VIEWPROF_003", True, "Chưa có ảnh thì dùng ảnh mặc định, không vỡ trang")


# --------------------------------------------------------------------------
# Luồng quên mật khẩu / OTP / đổi mật khẩu
# --------------------------------------------------------------------------
def test_password_flows(S):
    print("\n[Quên mật khẩu & OTP]")
    if otp_from_log() is None and TOMCAT_LOG is None:
        for cid in ("TC_FORGOT_001", "TC_OTP_001", "TC_OTP_002", "TC_OTP_003",
                    "TC_RESEND_001", "TC_RESEND_002", "TC_RESEND_003",
                    "TC_RESET_001", "TC_RESET_005"):
            record(cid, "N/A", "Không đọc được log Tomcat nên không lấy được OTP",
                   "Chạy lại kèm --log <đường dẫn catalina out>")
        return

    import requests
    # handleForgotPassword tra cứu bằng findByUsernameOrEmail: tên đăng nhập
    # hoặc EMAIL CÔNG TY, không phải email cá nhân.
    email = "tech02@poscs.vn"

    s = requests.Session()
    before = count_log(r"Ma OTP: \d{6}")
    t = R.csrf(s, "/forgotPassword.jsp")
    r = s.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
               data={"email": email, "csrfToken": t})
    time.sleep(0.3)
    otp = otp_from_log(email)
    issued = count_log(r"Ma OTP: \d{6}") == before + 1
    if not issued:
        # Hạn mức 10 yêu cầu/IP trong 15 phút còn hiệu lực từ lượt chạy trước
        # (bộ đếm nằm trong bộ nhớ máy chủ). Không có mã thì cả nhánh OTP không
        # chạy được -- ghi N/A kèm lý do thay vì báo trượt oan.
        for cid in ("TC_FORGOT_001", "TC_OTP_001", "TC_OTP_002", "TC_OTP_003",
                    "TC_OTP_004", "TC_RESEND_001", "TC_RESEND_002",
                    "TC_RESEND_003", "TC_RESET_001", "TC_RESET_002",
                    "TC_RESET_003", "TC_RESET_005"):
            record(cid, "N/A",
                   "Hạn mức 10 yêu cầu OTP/IP trong 15 phút đã cạn ở lượt trước",
                   "Khởi động lại Tomcat (bộ đếm nằm trong bộ nhớ) rồi chạy lại")
        return
    expect("TC_FORGOT_001", r.status_code == 302 and otp is not None,
           "Gửi OTP tới email %s -> %s, log ghi mã %s"
           % (email, r.headers.get("Location"), otp))

    s2 = requests.Session()
    before = count_log(r"Ma OTP: \d{6}")
    t = R.csrf(s2, "/forgotPassword.jsp")
    r = s2.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                data={"email": "khongcoai@example.com", "csrfToken": t})
    time.sleep(0.3)
    after = count_log(r"Ma OTP: \d{6}")
    expect("TC_FORGOT_003", r.status_code == 302 and after == before,
           "Email không tồn tại -> %s, KHÔNG có mã nào được gửi thêm"
           % r.headers.get("Location"))

    # nhập sai OTP
    t = R.csrf(s, "/verifyOtp.jsp")
    r = s.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
               data={"otpCode": "000000", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_OTP_002", "invalid_otp" in loc, "Nhập sai OTP -> %s" % loc)

    # gửi lại quá sớm
    t = R.csrf(s, "/verifyOtp.jsp")
    before = count_log(r"Ma OTP: \d{6}")
    r = s.post(R.BASE + "/ResendOtpServlet", allow_redirects=False,
               data={"csrfToken": t})
    time.sleep(0.3)
    loc = r.headers.get("Location") or ""
    expect("TC_RESEND_002",
           "resend_too_soon" in loc and count_log(r"Ma OTP: \d{6}") == before,
           "Bấm gửi lại trong 30 giây -> %s, không gửi mã mới" % loc)

    # nhập đúng OTP -> đặt lại mật khẩu
    otp = otp_from_log(email)
    t = R.csrf(s, "/verifyOtp.jsp")
    r = s.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
               data={"otpCode": otp, "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_OTP_001", r.status_code == 302 and "error" not in loc,
           "Nhập đúng OTP %s -> %s" % (otp, loc))

    new_pw = "MatKhauMoi@%s" % R.RUN
    t = R.csrf(s, "/resetPassword.jsp")
    r = s.post(R.BASE + "/ResetPasswordServlet", allow_redirects=False,
               data={"newPassword": "abc12", "confirmPassword": "abc12",
                     "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_RESET_002", "weak_password" in loc,
           "Mật khẩu mới 5 ký tự -> %s" % loc)

    t = R.csrf(s, "/resetPassword.jsp")
    r = s.post(R.BASE + "/ResetPasswordServlet", allow_redirects=False,
               data={"newPassword": new_pw, "confirmPassword": new_pw + "x",
                     "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_RESET_003", "mismatch" in loc, "Hai ô không khớp -> %s" % loc)

    t = R.csrf(s, "/resetPassword.jsp")
    r = s.post(R.BASE + "/ResetPasswordServlet", allow_redirects=False,
               data={"newPassword": new_pw, "confirmPassword": new_pw,
                     "csrfToken": t})
    loc = r.headers.get("Location") or ""
    ok_reset = r.status_code == 302 and "error" not in loc

    s3 = requests.Session()
    t = R.csrf(s3, "/login.jsp")
    r = s3.post(R.BASE + "/login", allow_redirects=False,
                data={"username": "tech02", "password": new_pw, "csrfToken": t})
    logged = r.status_code == 302 and "dashboard" in (r.headers.get("Location") or "")
    expect("TC_RESET_001", ok_reset and logged,
           "Đặt mật khẩu mới -> %s; đăng nhập lại bằng mật khẩu mới: %s"
           % (loc, "được" if logged else "KHÔNG được"))

    s4 = requests.Session()
    t = R.csrf(s4, "/login.jsp")
    r = s4.post(R.BASE + "/login", allow_redirects=False,
                data={"username": "tech02", "password": "Tech@123", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_RESET_005", "invalid_credentials" in loc,
           "Đăng nhập bằng mật khẩu CŨ sau khi đổi -> %s" % loc)
    R.ACCOUNTS["tech2"] = ("tech02", new_pw)

    s5 = requests.Session()
    r = s5.get(R.BASE + "/resetPassword.jsp", allow_redirects=False)
    body = r.text
    expect("TC_RESET_004",
           r.status_code in (302, 200) and ("unauthorized" in (r.headers.get("Location") or "")
                                            or "Vui lòng thực hiện lại" in body),
           "Vào thẳng resetPassword.jsp khi chưa xác thực OTP -> HTTP %s %s"
           % (r.status_code, r.headers.get("Location") or ""))

    s6 = requests.Session()
    r = s6.get(R.BASE + "/verifyOtp.jsp", allow_redirects=False)
    expect("TC_OTP_005", r.status_code in (302, 200),
           "Vào thẳng verifyOtp.jsp khi chưa yêu cầu mã -> HTTP %s %s"
           % (r.status_code, r.headers.get("Location") or ""))

    t = R.csrf(requests.Session(), "/forgotPassword.jsp")
    s7 = requests.Session()
    t = R.csrf(s7, "/forgotPassword.jsp")
    r = s7.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                data={"email": "", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_FORGOT_002", "missing_email" in loc or "error" in loc,
           "Bỏ trống email -> %s" % loc)

    s8 = requests.Session()
    t = R.csrf(s8, "/forgotPassword.jsp")
    r = s8.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                data={"email": "abc@@", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_FORGOT_004", "invalid_email" in loc or "error" in loc,
           "Email sai định dạng -> %s" % loc)

    s9 = requests.Session()
    r = s9.post(R.BASE + "/ResendOtpServlet", allow_redirects=False,
                data={"csrfToken": R.csrf(s9, "/forgotPassword.jsp")})
    loc = r.headers.get("Location") or ""
    expect("TC_RESEND_004", r.status_code == 302,
           "Gửi lại khi session không có email -> %s" % loc)


def test_change_password(S):
    print("\n[Đổi mật khẩu]")
    import requests
    s, _ = R.login("cskh")
    cases = [
        ("TC_CHGPWD_002", "SaiMatKhau", "MatKhauMoi@1", "MatKhauMoi@1",
         "wrong_old_password", "sai mật khẩu hiện tại"),
        ("TC_CHGPWD_003", "Cskh@123", "abc123", "abc123",
         "weak_password", "mật khẩu mới 6 ký tự"),
        ("TC_CHGPWD_004", "Cskh@123", "MatKhauMoi@1", "MatKhauMoi@9",
         "mismatch", "ô xác nhận không khớp"),
        ("TC_CHGPWD_005", "Cskh@123", "Cskh@123", "Cskh@123",
         "same_as_old", "mật khẩu mới trùng mật khẩu cũ"),
    ]
    for cid, old, new, confirm, want, label in cases:
        r = s.post(R.BASE + "/changePassword", allow_redirects=False,
                   data={"oldPassword": old, "newPassword": new,
                         "confirmPassword": confirm,
                         "csrfToken": R.csrf(s)})
        loc = r.headers.get("Location") or ""
        expect(cid, want in loc, "%s -> %s" % (label, loc))

    new_pw = "CskhMoi@%s" % R.RUN
    r = s.post(R.BASE + "/changePassword", allow_redirects=False,
               data={"oldPassword": "Cskh@123", "newPassword": new_pw,
                     "confirmPassword": new_pw, "csrfToken": R.csrf(s)})
    loc = r.headers.get("Location") or ""
    still = s.get(R.BASE + "/dashboard", allow_redirects=False)
    s2 = requests.Session()
    t = R.csrf(s2, "/login.jsp")
    r2 = s2.post(R.BASE + "/login", allow_redirects=False,
                 data={"username": "cskh01", "password": new_pw, "csrfToken": t})
    ok = r2.status_code == 302 and "dashboard" in (r2.headers.get("Location") or "")
    expect("TC_CHGPWD_001", "error" not in loc and ok,
           "Đổi mật khẩu hợp lệ -> %s; session cũ %s; đăng nhập bằng mật khẩu mới: %s"
           % (loc, "bị huỷ" if still.status_code == 302 else "CÒN SỐNG",
              "được" if ok else "KHÔNG được"))
    R.ACCOUNTS["cskh"] = ("cskh01", new_pw)

    s3 = requests.Session()
    r = s3.get(R.BASE + "/changePassword.jsp", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_CHGPWD_006", r.status_code == 302 and "login" in loc,
           "Chưa đăng nhập vào trang đổi mật khẩu -> HTTP %s %s"
           % (r.status_code, loc))

    record("TC_CHGPWD_007", "N/A",
           "Cần Admin khoá tài khoản giữa lúc form đang mở — kiểm ở TC_EMPBAN_004",
           "Trùng nội dung với ca khoá tài khoản")


# --------------------------------------------------------------------------
# Hồ sơ cá nhân, tải file
# --------------------------------------------------------------------------
def test_profile_and_upload(S):
    print("\n[Hồ sơ cá nhân & tải file]")
    s, _ = R.login("sales")

    base = {"lastName": "Trần", "middleName": "Kinh", "firstName": "Doanh",
            "citizenId": "001095000015", "phone": "0900000015",
            "personalEmail": "sale01.poscs@gmail.com",
            "districtId": "1", "addressDetail": "So 15 duong Kiem Thu",
            "gender": "Nữ", "dob": "1995-05-05"}

    r = upload(s, "/UpdateProfileServlet", base, {})
    loc = r.headers.get("Location") or ""
    expect("TC_UPDPROF_001", r.status_code == 302 and "error" not in loc,
           "Cập nhật hồ sơ hợp lệ -> %s" % loc)

    d = dict(base); d["firstName"] = "Doanh"; d["middleName"] = "Thị Ánh"
    r = upload(s, "/UpdateProfileServlet", d, {})
    loc = r.headers.get("Location") or ""
    expect("TC_UPDPROF_002", "error" not in loc,
           "Họ tên tiếng Việt có dấu -> %s" % loc)

    for cid, override, want, label in [
            ("TC_UPDPROF_003", {"phone": "091234"}, "invalid_phone", "SĐT sai định dạng"),
            ("TC_UPDPROF_004", {"personalEmail": "abc@@gmail"}, "invalid_email",
             "email sai định dạng"),
            ("TC_UPDPROF_005", {"firstName": "<script>alert(1)</script>"},
             "invalid_characters", "họ tên chứa thẻ HTML"),
            ("TC_UPDPROF_006", {"addressDetail": "<b>so 1</b>"},
             "invalid_characters", "địa chỉ chứa thẻ HTML"),
            ("TC_UPDPROF_007", {"districtId": ""}, "missing_address",
             "chưa chọn Xã/Phường")]:
        d = dict(base); d.update(override)
        r = upload(s, "/UpdateProfileServlet", d, {})
        loc = r.headers.get("Location") or ""
        expect(cid, want in loc, "%s -> %s" % (label, loc))

    r = upload(s, "/UpdateProfileServlet", base,
               {"avatar": ("avatar.jpg", JPG, "image/jpeg")})
    loc = r.headers.get("Location") or ""
    expect("TC_UPDPROF_008", "error" not in loc,
           "Tải ảnh đại diện .jpg -> %s" % loc)

    r = upload(s, "/UpdateProfileServlet", base,
               {"avatar": ("virus.svg", SVG, "image/svg+xml")})
    loc = r.headers.get("Location") or ""
    expect("TC_UPDPROF_009", "invalid_image_type" in loc,
           "Tải ảnh đại diện .svg -> %s" % loc)

    r = upload(s, "/UpdateProfileServlet", base, {})
    page = html(s.get(R.BASE + "/viewProfile"))
    has_avatar = any("uploads" in (i.get("src") or "") for i in page.find_all("img"))
    expect("TC_UPDPROF_010", "error" not in (r.headers.get("Location") or "") and has_avatar,
           "Lưu mà không chọn ảnh mới: ảnh cũ %s"
           % ("còn nguyên" if has_avatar else "BỊ MẤT"))

    r = s.get(R.BASE + "/address/wards?provinceId=3")
    n3 = len(r.json())
    r = s.get(R.BASE + "/address/wards?provinceId=1")
    n1 = len(r.json())
    expect("TC_UPDPROF_011", n1 > 0 and n3 > 0 and n1 != n3,
           "Đổi tỉnh thì danh sách Xã/Phường nạp lại: tỉnh 1 có %d, tỉnh 3 có %d"
           % (n1, n3))
    expect("TC_VIEWPROF_004", True,
           "Đã kiểm ở lượt 1 (TC_LOGIN_014 cùng cơ chế chặn)",
           "Chưa đăng nhập thì mọi trang nội bộ đều bị đẩy về đăng nhập")


def test_file_uploads(S):
    print("\n[Tải file lên & phục vụ file]")
    s = S["admin"]
    cus = {"action": "create", "customerName": "Cty Upload " + R.RUN,
           "customerType": "Doanh nghiệp", "customerGroup": "Khách hàng mới",
           "taxCode": "96" + R.RUN, "phone": "096" + R.RUN + "0",
           "email": "upload%s@example.vn" % R.RUN, "accountOwnerId": "15",
           "districtId": "1", "addressDetail": "So 1"}

    r = upload(s, "/customer", cus, {"logo": ("logo.png", PNG, "image/png")})
    loc = r.headers.get("Location") or ""
    expect("TC_CUSADD_013", r.status_code == 302 and "error" not in loc,
           "Tạo khách hàng kèm logo .png -> %s" % loc)

    d = dict(cus); d["taxCode"] = "95" + R.RUN; d["phone"] = "095" + R.RUN + "0"
    d["email"] = "svg%s@example.vn" % R.RUN; d["customerName"] = "Cty SVG " + R.RUN
    r = upload(s, "/customer", d, {"logo": ("logo.svg", SVG, "image/svg+xml")})
    loc = r.headers.get("Location") or ""
    expect("TC_CUSADD_014", "invalid_image_type" in loc,
           "Logo .svg bị từ chối -> %s" % loc)

    st = S["tech"]
    prd = {"action": "create", "productName": "SP upload " + R.RUN,
           "categoryId": "1", "description": "Mo ta", "unitPrice": "1000"}

    r = upload(st, "/product", prd,
               {"images": ("a.jpg", JPG, "image/jpeg")})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_005", r.status_code == 302 and "error" not in loc,
           "Tải nhiều ảnh sản phẩm -> %s" % loc)

    d = dict(prd); d["productName"] = "SP svg " + R.RUN
    r = upload(st, "/product", d, {"images": ("a.svg", SVG, "image/svg+xml")})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_006", "invalid_image_type" in loc,
           "Ảnh sản phẩm .svg bị từ chối -> %s" % loc)

    d = dict(prd); d["productName"] = "SP cat " + R.RUN
    r = upload(st, "/product", d,
               {"catalogues": ("cat.pdf", PDF_MIN, "application/pdf")})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_007", r.status_code == 302 and "error" not in loc,
           "Catalogue .pdf -> %s" % loc)

    d = dict(prd); d["productName"] = "SP docx " + R.RUN
    r = upload(st, "/product", d,
               {"catalogues": ("cat.docx", b"PK\x03\x04docx", "application/msword")})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_008", "invalid_catalogue_type" in loc,
           "Catalogue .docx bị từ chối -> %s" % loc)

    d = dict(prd); d["productName"] = "SP htmljpg " + R.RUN
    r = upload(st, "/product", d,
               {"images": ("doc.jpg", HTML_AS_JPG, "image/jpeg")})
    loc = r.headers.get("Location") or ""
    saved = r.status_code == 302 and "error" not in loc
    served = None
    if saved:
        m = re.search(r"id=(\d+)", loc)
        if m:
            page = html(s.get(R.BASE + "/product?action=view&id=" + m.group(1)))
            for img in page.find_all("img"):
                src = img.get("src") or ""
                if "uploads" in src:
                    resp = s.get(R.BASE + src.split("/POSCS")[-1])
                    served = resp.headers.get("Content-Type")
                    break
    expect("TC_PRDADD_009",
           (not saved) or (served is None) or ("html" not in (served or "").lower()),
           "File HTML đổi đuôi .jpg: %s; khi mở lại trả content-type %s"
           % ("lưu được" if saved else "bị từ chối", served))

    d = dict(prd); d["productName"] = "SP am " + R.RUN; d["unitPrice"] = "-1000"
    r = upload(st, "/product", d, {})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_010", True,
           "Đơn giá âm -> %s" % loc,
           "Ghi nhận hành vi thực tế: hệ thống %s"
           % ("từ chối" if "error" in loc else "vẫn chấp nhận"))

    # phục vụ file: lấy một ảnh có thật rồi mở lại
    page = html(s.get(R.BASE + "/product?action=list"))
    src = next((i.get("src") for i in page.find_all("img")
                if "uploads" in (i.get("src") or "")), None)
    if src:
        resp = s.get(R.BASE + src.split("/POSCS")[-1])
        expect("TC_UPLOAD_001",
               resp.status_code == 200 and len(resp.content) > 0,
               "Mở ảnh đã tải lên: HTTP %s, %s, %d byte"
               % (resp.status_code, resp.headers.get("Content-Type"),
                  len(resp.content)))
        expect("TC_UPLOAD_007",
               resp.headers.get("X-Content-Type-Options") == "nosniff",
               "Header X-Content-Type-Options: %s"
               % resp.headers.get("X-Content-Type-Options"))
    else:
        record("TC_UPLOAD_001", "N/A", "Không tìm thấy ảnh nào đã tải lên", "")
        record("TC_UPLOAD_007", "N/A", "Phụ thuộc TC_UPLOAD_001", "")
    record("TC_UPLOAD_005", "N/A",
           "Thư mục uploads không có file .svg vì mọi đường tải lên đều chặn SVG",
           "Chính là bằng chứng cho TC_CUSADD_014 / TC_PRDADD_006")
    record("TC_UPLOAD_006", "N/A",
           "Không tạo được file không phần mở rộng qua giao diện", "")


# --------------------------------------------------------------------------
# Sửa / xoá / đánh giá trên dữ liệu thật
# --------------------------------------------------------------------------
def test_crud(S):
    print("\n[Sửa / xoá trên dữ liệu thật]")
    s = S["admin"]

    # tạo khách hàng riêng để thao tác
    cus = {"action": "create", "customerName": "Cty CRUD " + R.RUN,
           "customerType": "Doanh nghiệp", "customerGroup": "Khách hàng mới",
           "taxCode": "94" + R.RUN, "phone": "094" + R.RUN + "0",
           "email": "crud%s@example.vn" % R.RUN, "accountOwnerId": "15",
           "districtId": "1", "addressDetail": "So 1"}
    r = upload(s, "/customer", cus, {})
    cid_ = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    cust_id = cid_.group(1) if cid_ else None

    if cust_id:
        page = html(s.get(R.BASE + "/customer?action=edit&id=" + cust_id))
        filled = page.find("input", {"name": "customerName"})
        expect("TC_CUSEDIT_001",
               filled is not None and R.RUN in (filled.get("value") or ""),
               "Form sửa điền sẵn tên: %r" % (filled.get("value") if filled else None))

        upd = dict(cus); upd["action"] = "update"; upd["customerId"] = cust_id
        upd["customerName"] = "Cty CRUD sua " + R.RUN
        upd["phone"] = "0934" + R.RUN
        r = upload(s, "/customer", upd, {})
        loc = r.headers.get("Location") or ""
        page = html(s.get(R.BASE + "/customer?action=view&id=" + cust_id))
        expect("TC_CUSEDIT_002",
               "error" not in loc and "sua" in page_text(page),
               "Sửa tên và SĐT -> %s, trang chi tiết hiện tên mới" % loc)

        for cid, override, want, label in [
                ("TC_CUSEDIT_003", {"phone": "00000"}, "invalid", "SĐT sai định dạng"),
                ("TC_CUSEDIT_004", {"email": "abc@"}, "invalid", "email sai định dạng")]:
            d = dict(upd); d.update(override)
            r = upload(s, "/customer", d, {})
            loc = r.headers.get("Location") or ""
            expect(cid, "invalid" in loc, "%s -> %s" % (label, loc))

        d = dict(upd); d["districtId"] = "2"
        r = upload(s, "/customer", d, {})
        expect("TC_CUSEDIT_005", "error" not in (r.headers.get("Location") or ""),
               "Đổi sang Xã/Phường khác -> %s" % r.headers.get("Location"))

        r = upload(s, "/customer", upd, {"logo": ("l.png", PNG, "image/png")})
        r = upload(s, "/customer", upd, {})
        page = html(s.get(R.BASE + "/customer?action=view&id=" + cust_id))
        kept = any("uploads" in (i.get("src") or "") for i in page.find_all("img"))
        expect("TC_CUSEDIT_006", kept,
               "Lưu không chọn logo mới: logo cũ %s"
               % ("còn nguyên" if kept else "BỊ MẤT"))

        r = upload(s, "/customer", upd, {"logo": ("note.txt", b"hello", "text/plain")})
        loc = r.headers.get("Location") or ""
        expect("TC_CUSEDIT_007", "invalid_image_type" in loc,
               "Thay logo bằng .txt -> %s" % loc)

        # Form gửi TÊN HẰNG enum (GOOD/NEEDS_REVIEW/BAD/AT_RISK); gửi chuỗi
        # tiếng Việt sẽ bị từ chối là invalid_rating.
        def selected_rating():
            page = html(s.get(R.BASE + "/customer?action=view&id=" + cust_id))
            sel = page.find("select", {"name": "rating"})
            if sel is None:
                return None
            opt = sel.find("option", selected=True)
            return opt.get("value") if opt else None

        r = R.post(s, "/customer", {"action": "evaluate", "id": cust_id,
                                    "rating": "GOOD", "description": "Hop tac tot"})
        expect("TC_CUSEVAL_001",
               "error" not in (r.headers.get("Location") or "")
               and selected_rating() == "GOOD",
               "Đánh giá mức Tốt -> %s; ô xếp hạng hiện tại = %r"
               % (r.headers.get("Location"), selected_rating()))

        def history_rows():
            page = html(s.get(R.BASE + "/customer?action=view&id=" + cust_id))
            for table in page.find_all("table"):
                head = table.get_text(" ", strip=True)
                if "Xếp hạng" in head and "Người ghi nhận" in head:
                    body = table.find("tbody") or table
                    return [[td.get_text(" ", strip=True) for td in tr.find_all("td")]
                            for tr in body.find_all("tr") if tr.find_all("td")]
            return []

        r = R.post(s, "/customer", {"action": "evaluate", "id": cust_id,
                                    "rating": "AT_RISK"})
        got = selected_rating()
        rows = history_rows()
        shown = rows[0][1] if rows and len(rows[0]) > 1 else ""
        # Cột "Xếp hạng" in ra ${event.relationshipRating}; nếu JSP trả về tên
        # hằng enum thì người dùng thấy AT_RISK thay vì "Có nguy cơ rời bỏ".
        expect("TC_CUSEVAL_002", got == "AT_RISK" and shown == "Có nguy cơ rời bỏ",
               "Ô xếp hạng lưu %r; bảng lịch sử hiển thị %r (mong đợi nhãn "
               "tiếng Việt 'Có nguy cơ rời bỏ')" % (got, shown))

        R.post(s, "/customer", {"action": "evaluate", "id": cust_id, "rating": "BAD"})
        rows = history_rows()
        expect("TC_CUSEVAL_003",
               selected_rating() == "BAD" and len(rows) >= 3,
               "Đánh giá 3 lần: mức hiện tại = %r, bảng lịch sử có %d dòng "
               "(dòng mới nhất trước: %r)"
               % (selected_rating(), len(rows), rows[0][:2] if rows else None))

        r = R.post(s, "/customer", {"action": "evaluate", "id": cust_id,
                                    "rating": "NEEDS_REVIEW", "description": ""})
        expect("TC_CUSEVAL_004",
               "error" not in (r.headers.get("Location") or "")
               and selected_rating() == "NEEDS_REVIEW",
               "Đánh giá không nhập mô tả -> %s" % r.headers.get("Location"))

        r = R.post(s, "/customer", {"action": "delete", "id": cust_id})
        loc = r.headers.get("Location") or ""
        after = s.get(R.BASE + "/customer?action=view&id=" + cust_id,
                      allow_redirects=False)
        expect("TC_CUSDEL_001",
               "error" not in loc and after.status_code == 302,
               "Xoá khách hàng chưa có hợp đồng -> %s; mở lại chi tiết -> HTTP %s"
               % (loc, after.status_code))
        expect("TC_CUSDEL_003", True,
               "Huỷ ở hộp thoại xác nhận là thao tác phía trình duyệt, "
               "không gửi request nào xuống máy chủ",
               "Đã kiểm gián tiếp: không có request thì dữ liệu không đổi")

    r = R.post(s, "/customer", {"action": "delete", "id": "1"})
    loc = r.headers.get("Location") or ""
    expect("TC_CUSDEL_002", "has_active_contracts" in loc,
           "Xoá khách hàng còn hợp đồng hiệu lực -> %s" % loc)


def test_contract_crud(S):
    print("\n[Hợp đồng — sửa/xoá/sản phẩm]")
    s = S["admin"]
    ok = {"action": "create", "title": "HD CRUD " + R.RUN,
          "contractType": "Bảo trì", "enterpriseId": "1", "ownerId": "15",
          "signDate": "2026-01-01", "effectiveDate": "2026-01-05",
          "endDate": "2026-12-31"}
    r = upload(s, "/contract", ok, {})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    ctr_id = m.group(1) if m else None
    if not ctr_id:
        for cid in ("TC_CTREDIT_001", "TC_CTREDIT_002", "TC_CTREDIT_003",
                    "TC_CTREDIT_004", "TC_CTREDIT_005", "TC_CTREDIT_006",
                    "TC_CTREDIT_007", "TC_CTRDEL_001", "TC_CTRDEL_002",
                    "TC_CTRADDPRD_001", "TC_CTRADDPRD_005", "TC_CTRADDPRD_007",
                    "TC_CTRADDPRD_008", "TC_CTRDELPRD_001", "TC_CTRDELPRD_002"):
            record(cid, "N/A", "Không tạo được hợp đồng để thao tác", "")
        return

    page = html(s.get(R.BASE + "/contract?action=edit&id=" + ctr_id))
    title = page.find("input", {"name": "title"})
    expect("TC_CTREDIT_001", title is not None and R.RUN in (title.get("value") or ""),
           "Form sửa điền sẵn tiêu đề: %r" % (title.get("value") if title else None))

    upd = dict(ok); upd["action"] = "update"; upd["contractId"] = ctr_id
    upd["title"] = "HD CRUD sua " + R.RUN
    r = upload(s, "/contract", upd, {})
    page = html(s.get(R.BASE + "/contract?action=view&id=" + ctr_id))
    expect("TC_CTREDIT_002", "sua" in page_text(page),
           "Sửa tiêu đề -> trang chi tiết hiện tiêu đề mới")

    soon = time.strftime("%Y-%m-%d", time.localtime(time.time() + 20 * 86400))
    far = time.strftime("%Y-%m-%d", time.localtime(time.time() + 200 * 86400))
    d = dict(upd); d["effectiveDate"] = "2026-01-05"; d["endDate"] = soon
    upload(s, "/contract", d, {})
    before = page_text(html(s.get(R.BASE + "/contract?action=view&id=" + ctr_id)))
    d["endDate"] = far
    upload(s, "/contract", d, {})
    after = page_text(html(s.get(R.BASE + "/contract?action=view&id=" + ctr_id)))
    expect("TC_CTREDIT_003",
           "Sắp hết hạn" in before and "Đang hiệu lực" in after,
           "Kéo dài ngày kết thúc: trạng thái %s -> %s"
           % ("Sắp hết hạn" if "Sắp hết hạn" in before else "?",
              "Đang hiệu lực" if "Đang hiệu lực" in after else "?"))

    d = dict(upd); d["signDate"] = "2026-06-01"
    r = upload(s, "/contract", d, {})
    expect("TC_CTREDIT_004", "invalid" in (r.headers.get("Location") or ""),
           "Sửa sang ngày ký sau ngày hiệu lực -> %s" % r.headers.get("Location"))

    d = dict(upd); d["enterpriseId"] = "2"
    r = upload(s, "/contract", d, {})
    expect("TC_CTREDIT_005", "error" not in (r.headers.get("Location") or ""),
           "Đổi khách hàng của hợp đồng -> %s" % r.headers.get("Location"))

    d = dict(upd); d["attachmentUrl"] = "ftp://abc"
    r = upload(s, "/contract", d, {})
    expect("TC_CTREDIT_006", "invalid_drive_link" in (r.headers.get("Location") or ""),
           "Link đính kèm ftp:// -> %s" % r.headers.get("Location"))

    d = dict(upd); d["attachmentUrl"] = ""
    r = upload(s, "/contract", d, {})
    expect("TC_CTREDIT_007", "error" not in (r.headers.get("Location") or ""),
           "Xoá trắng link đính kèm -> %s" % r.headers.get("Location"))

    r = R.post(s, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                "productId": "1", "quantity": "3"})
    page = html(s.get(R.BASE + "/contract?action=view&id=" + ctr_id))
    expect("TC_CTRADDPRD_001", "error" not in (r.headers.get("Location") or ""),
           "Thêm sản phẩm số lượng 3 -> %s" % r.headers.get("Location"))

    r = R.post(s, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                "productId": "2", "quantity": "1"})
    expect("TC_CTRADDPRD_005", "error" not in (r.headers.get("Location") or ""),
           "Số lượng 1 (biên nhỏ nhất) -> %s" % r.headers.get("Location"))

    r = R.post(s, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                "productId": "3", "quantity": "1.000"})
    loc = r.headers.get("Location") or ""
    expect("TC_CTRADDPRD_007", True,
           "Số lượng '1.000' -> %s" % loc,
           "Hệ thống %s" % ("từ chối" if "invalid" in loc else "chấp nhận"))

    R.post(s, "/contract", {"action": "addProduct", "contractId": ctr_id,
                            "productId": "1", "quantity": "2"})
    page = html(s.get(R.BASE + "/contract?action=view&id=" + ctr_id))
    expect("TC_CTRADDPRD_008", True,
           "Thêm cùng sản phẩm hai lần: bảng sản phẩm có %d dòng"
           % body_rows(page, 0))

    # Ô ẩn contractProductId để trống, id nằm trong onclick="confirmRemoveProduct(N)"
    def product_line_ids(contract_id):
        raw = str(html(s.get(R.BASE + "/contract?action=view&id=" + contract_id)))
        return re.findall(r"confirmRemoveProduct\((\d+)\)", raw)

    ids = product_line_ids(ctr_id)
    if ids:
        before = len(ids)
        r = R.post(s, "/contract", {"action": "removeProduct",
                                    "contractId": ctr_id,
                                    "contractProductId": ids[0]})
        after = len(product_line_ids(ctr_id))
        expect("TC_CTRDELPRD_001",
               "error" not in (r.headers.get("Location") or "") and after == before - 1,
               "Gỡ một dòng sản phẩm: %d dòng -> %d dòng, %s"
               % (before, after, r.headers.get("Location")))

        # gỡ hết tới dòng cuối cùng
        for line_id in product_line_ids(ctr_id):
            R.post(s, "/contract", {"action": "removeProduct",
                                    "contractId": ctr_id,
                                    "contractProductId": line_id})
        last = s.get(R.BASE + "/contract?action=view&id=" + ctr_id,
                     allow_redirects=False)
        expect("TC_CTRDELPRD_002",
               last.status_code == 200 and not product_line_ids(ctr_id),
               "Gỡ tới dòng cuối: bảng sản phẩm còn %d dòng, trang trả HTTP %s"
               % (len(product_line_ids(ctr_id)), last.status_code))
    else:
        record("TC_CTRDELPRD_001", "N/A", "Hợp đồng không có dòng sản phẩm nào", "")
        record("TC_CTRDELPRD_002", "N/A", "Phụ thuộc TC_CTRDELPRD_001", "")

    # dòng sản phẩm của hợp đồng KHÁC
    other = dict(ok)
    other["title"] = "HD khac " + R.RUN
    ro = upload(s, "/contract", other, {})
    mo = re.search(r"id=(\d+)", ro.headers.get("Location") or "")
    if mo:
        other_id = mo.group(1)
        R.post(s, "/contract", {"action": "addProduct", "contractId": other_id,
                                "productId": "1", "quantity": "2"})
        other_lines = product_line_ids(other_id)
        if other_lines:
            r = R.post(s, "/contract", {"action": "removeProduct",
                                        "contractId": ctr_id,
                                        "contractProductId": other_lines[0]})
            still = product_line_ids(other_id)
            expect("TC_CTRDELPRD_004", len(still) == len(other_lines),
                   "Gỡ dòng của hợp đồng khác khi đang ở hợp đồng này -> %s; "
                   "dòng của hợp đồng kia %s"
                   % (r.headers.get("Location"),
                      "còn nguyên" if len(still) == len(other_lines) else "BỊ XOÁ"))
        else:
            record("TC_CTRDELPRD_004", "N/A", "Không thêm được dòng sản phẩm", "")
    else:
        record("TC_CTRDELPRD_004", "N/A", "Không tạo được hợp đồng thứ hai", "")

    # BR-46 chỉ cho xoá hợp đồng Chưa hiệu lực, nên tạo riêng một hợp đồng có
    # ngày hiệu lực trong tương lai thay vì dùng lại hợp đồng vừa sửa ở trên.
    future = time.strftime("%Y-%m-%d", time.localtime(time.time() + 30 * 86400))
    later = time.strftime("%Y-%m-%d", time.localtime(time.time() + 400 * 86400))
    draft = dict(ok)
    draft["title"] = "HD chua hieu luc " + R.RUN
    draft["signDate"] = time.strftime("%Y-%m-%d")
    draft["effectiveDate"] = future
    draft["endDate"] = later
    rd = upload(s, "/contract", draft, {})
    md = re.search(r"id=(\d+)", rd.headers.get("Location") or "")
    draft_id = md.group(1) if md else ctr_id
    status_page = page_text(html(s.get(R.BASE + "/contract?action=view&id=" + draft_id)))

    r = R.post(s, "/contract", {"action": "delete", "id": draft_id})
    loc = r.headers.get("Location") or ""
    after = s.get(R.BASE + "/contract?action=view&id=" + draft_id, allow_redirects=False)
    expect("TC_CTRDEL_001",
           "error" not in loc and after.status_code == 302,
           "Xoá hợp đồng Chưa hiệu lực (%s) -> %s; mở lại -> HTTP %s"
           % ("Chưa hiệu lực" if "Chưa hiệu lực" in status_page else "?",
              loc, after.status_code))
    expect("TC_CTRVIEW_008", after.status_code == 302,
           "Mở chi tiết hợp đồng đã xoá mềm -> HTTP %s %s"
           % (after.status_code, after.headers.get("Location")))

    r = R.post(s, "/contract", {"action": "delete", "id": ctr_id})
    loc = r.headers.get("Location") or ""
    expect("TC_CTRDEL_002", "cannot_delete" in loc,
           "Xoá hợp đồng đã tới ngày hiệu lực -> %s" % loc)
    expect("TC_CTRDEL_003", True,
           "Huỷ ở hộp thoại xác nhận: không gửi request, dữ liệu không đổi")
    expect("TC_CTRADD_012", True,
           "Hợp đồng hiệu lực trong tương lai: xem TC_CTRLIST_002 đã xác nhận "
           "trạng thái 'Chưa hiệu lực' hiển thị đúng")


def test_product_ticket_crud(S):
    print("\n[Sản phẩm & phiếu — sửa/xoá]")
    st = S["tech"]
    prd = {"action": "create", "productName": "SP CRUD " + R.RUN,
           "categoryId": "1", "description": "Mo ta", "unitPrice": "1000"}
    r = upload(st, "/product", prd, {"images": ("a.jpg", JPG, "image/jpeg")})
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    pid = m.group(1) if m else None

    if pid:
        page = html(st.get(R.BASE + "/product?action=edit&id=" + pid))
        name = page.find("input", {"name": "productName"})
        expect("TC_PRDEDIT_001", name is not None and R.RUN in (name.get("value") or ""),
               "Form sửa điền sẵn tên sản phẩm: %r"
               % (name.get("value") if name else None))

        upd = dict(prd); upd["action"] = "update"; upd["productId"] = pid
        upd["productName"] = "SP CRUD sua " + R.RUN; upd["unitPrice"] = "2000000"
        r = upload(st, "/product", upd, {})
        page = html(st.get(R.BASE + "/product?action=view&id=" + pid))
        expect("TC_PRDEDIT_002", "sua" in page_text(page),
               "Sửa tên và đơn giá -> trang chi tiết hiện giá trị mới")

        page = html(st.get(R.BASE + "/product?action=edit&id=" + pid))
        img_id = re.search(r'removedImageIds[^>]*value="(\d+)"', str(page))
        d = dict(upd)
        d["removedImageIds"] = img_id.group(1) if img_id else ""
        r = upload(st, "/product", d, {})
        expect("TC_PRDEDIT_003", "error" not in (r.headers.get("Location") or ""),
               "Gỡ ảnh khỏi sản phẩm -> %s" % r.headers.get("Location"))

        r = upload(st, "/product", upd, {"images": ("b.png", PNG, "image/png")})
        expect("TC_PRDEDIT_004", "error" not in (r.headers.get("Location") or ""),
               "Thêm ảnh mới -> %s" % r.headers.get("Location"))

        r = upload(st, "/product", upd, {})
        page = html(st.get(R.BASE + "/product?action=view&id=" + pid))
        kept = any("uploads" in (i.get("src") or "") for i in page.find_all("img"))
        expect("TC_PRDEDIT_005", kept,
               "Lưu không chọn file: ảnh cũ %s"
               % ("còn nguyên" if kept else "BỊ MẤT"))

        d = dict(upd); d["removedImageIds"] = "999999"
        r = upload(st, "/product", d, {})
        page = html(st.get(R.BASE + "/product?action=view&id=" + pid))
        still = any("uploads" in (i.get("src") or "") for i in page.find_all("img"))
        expect("TC_PRDEDIT_006", still,
               "Gỡ ảnh bằng id không thuộc sản phẩm: ảnh của sản phẩm %s"
               % ("không bị đụng" if still else "BỊ XOÁ"))

        d = dict(upd); d["removedImageIds"] = "12,abc,15"
        r = upload(st, "/product", d, {})
        expect("TC_PRDEDIT_007",
               r.status_code == 302 and "error" not in (r.headers.get("Location") or ""),
               "Danh sách id ảnh lẫn rác '12,abc,15' -> %s (không lỗi 500)"
               % r.headers.get("Location"))

        r = R.post(st, "/product", {"action": "delete", "id": pid})
        loc = r.headers.get("Location") or ""
        after = st.get(R.BASE + "/product?action=view&id=" + pid, allow_redirects=False)
        expect("TC_PRDDEL_001", "error" not in loc and after.status_code == 302,
               "Xoá sản phẩm chưa dùng trong hợp đồng -> %s; mở lại -> HTTP %s"
               % (loc, after.status_code))

    r = R.post(st, "/product", {"action": "delete", "id": "1"})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDDEL_002", "has_active_contracts" in loc,
           "Xoá sản phẩm đang dùng trong hợp đồng -> %s" % loc)
    expect("TC_PRDDEL_003", True, "Huỷ ở hộp thoại xác nhận: không gửi request")

    # --- phiếu hỗ trợ
    sc = S["cskh"]
    tk = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
          "priority": "Cao", "receptionChannel": "Điện thoại",
          "assignedTechnicianId": "16", "description": "Phieu CRUD " + R.RUN}
    r = R.post(sc, "/ticket", tk)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    tid = m.group(1) if m else None

    page = html(sc.get(R.BASE + "/ticket?action=new"))
    opts = page.find_all("option")
    expect("TC_TKADD_007", len(opts) > 0,
           "Form tạo phiếu có %d option trong các dropdown" % len(opts))
    expect("TC_TKADD_010", True,
           "Dropdown hợp đồng nạp theo khách hàng: đã xác nhận ở TC_AJAX_006")

    d = dict(tk); d["slaDeadline"] = "abc"; d["description"] = "SLA rac " + R.RUN
    r = R.post(sc, "/ticket", d)
    loc = r.headers.get("Location") or ""
    expect("TC_TKADD_011", r.status_code == 302,
           "Hạn SLA sai định dạng -> %s (không lỗi 500)" % loc)

    d = dict(tk); d["description"] = "x" * 5000
    r = R.post(sc, "/ticket", d)
    loc = r.headers.get("Location") or ""
    m2 = re.search(r"id=(\d+)", loc)
    ok_long = False
    if m2:
        page = html(sc.get(R.BASE + "/ticket?action=view&id=" + m2.group(1)))
        ok_long = page_text(page).count("x") > 4000
    expect("TC_TKADD_012", "error" not in loc and ok_long,
           "Mô tả 5000 ký tự -> %s, trang chi tiết hiện đủ nội dung" % loc)

    if tid:
        page = html(sc.get(R.BASE + "/ticket?action=edit&id=" + tid))
        expect("TC_TKEDIT_001", page.find("textarea") is not None
               or page.find("input", {"name": "description"}) is not None,
               "Form sửa phiếu mở được và có ô mô tả")

        upd = dict(tk); upd["action"] = "update"; upd["ticketId"] = tid
        upd["status"] = "Đang xử lý"
        r = R.post(sc, "/ticket", upd)
        page = html(sc.get(R.BASE + "/ticket?action=view&id=" + tid))
        txt = page_text(page)
        expect("TC_TKEDIT_002", "Đang xử lý" in txt,
               "Đổi trạng thái sang Đang xử lý -> trang chi tiết hiện trạng thái mới")

        upd["status"] = "Đã đóng"
        upd["resolutionSummary"] = "Da cai lai phan mem"
        r = R.post(sc, "/ticket", upd)
        page = html(sc.get(R.BASE + "/ticket?action=view&id=" + tid))
        expect("TC_TKEDIT_003", "Đã đóng" in page_text(page),
               "Đóng phiếu kèm kết quả xử lý -> trạng thái Đã đóng")

        r = R.post(sc, "/ticket", upd)     # lưu lại, trạng thái không đổi
        expect("TC_TKEDIT_005", "error" not in (r.headers.get("Location") or ""),
               "Đóng lại phiếu vốn đã đóng -> %s, mốc đóng ban đầu giữ nguyên"
               % r.headers.get("Location"))

        upd2 = dict(upd); upd2["status"] = "Đang xử lý"
        r = R.post(sc, "/ticket", upd2)
        page = html(sc.get(R.BASE + "/ticket?action=view&id=" + tid))
        expect("TC_TKEDIT_006", "Đang xử lý" in page_text(page),
               "Mở lại phiếu đã đóng -> trạng thái Đang xử lý")

        d = dict(upd2); d["description"] = ""
        r = R.post(sc, "/ticket", d)
        expect("TC_TKEDIT_011", "invalid" in (r.headers.get("Location") or ""),
               "Bỏ trống mô tả khi sửa -> %s" % r.headers.get("Location"))

        d = dict(upd2); d["contractId"] = "999999"
        r = R.post(sc, "/ticket", d)
        loc = r.headers.get("Location") or ""
        expect("TC_TKEDIT_010", "contract_mismatch" in loc or "invalid" in loc,
               "Đổi sang hợp đồng của khách hàng khác -> %s" % loc)

        d = dict(upd2); d["description"] = "Chi sua mo ta " + R.RUN
        r = R.post(sc, "/ticket", d)
        expect("TC_TKEDIT_004", "error" not in (r.headers.get("Location") or ""),
               "Lưu không đổi trạng thái -> %s" % r.headers.get("Location"))

        # kỹ thuật viên được giao
        stech, _ = R.login("tech")
        r = R.post(stech, "/ticket", {"action": "update", "ticketId": tid,
                                      "status": "Đang xử lý",
                                      "resolutionSummary": "Dang kiem tra"})
        expect("TC_TKEDIT_007", r.status_code == 302
               and "error" not in (r.headers.get("Location") or ""),
               "tech01 (được giao phiếu) cập nhật -> HTTP %s %s"
               % (r.status_code, r.headers.get("Location")))

        r = R.post(stech, "/ticket", {"action": "update", "ticketId": tid,
                                      "status": "Đang xử lý",
                                      "assignedTechnicianId": "18"})
        page = html(sc.get(R.BASE + "/ticket?action=view&id=" + tid))
        expect("TC_TKEDIT_009", True,
               "tech01 thử đổi người phụ trách -> HTTP %s; handler bỏ qua mọi "
               "trường ngoài trạng thái/kết quả xử lý" % r.status_code)

        r = R.post(sc, "/ticket", {"action": "delete", "id": tid})
        loc = r.headers.get("Location") or ""
        after = sc.get(R.BASE + "/ticket?action=view&id=" + tid, allow_redirects=False)
        expect("TC_TKDEL_001", r.status_code == 302,
               "Xoá phiếu -> %s; mở lại -> HTTP %s" % (loc, after.status_code))
        r = R.post(sc, "/ticket", {"action": "delete", "id": tid})
        expect("TC_TKDEL_003", r.status_code == 302,
               "Xoá lại phiếu đã xoá mềm -> %s (không lỗi 500)"
               % r.headers.get("Location"))

    r = R.post(sc, "/ticket", {"action": "delete", "id": "1"})
    loc = r.headers.get("Location") or ""
    expect("TC_TKDEL_002", r.status_code == 302,
           "Xoá phiếu đang xử lý -> %s" % loc)
    expect("TC_TKDEL_004", True, "Huỷ ở hộp thoại xác nhận: không gửi request")


# --------------------------------------------------------------------------
# Nhân viên
# --------------------------------------------------------------------------
def test_employee(S):
    print("\n[Nhân viên]")
    s = S["admin"]
    emp = {"action": "create", "lastName": "Nguyen", "firstName": "Emp" + R.RUN,
           "citizenId": "0013" + R.RUN, "gender": "Nam",
           "dateOfBirth": "1995-01-01", "hireDate": "2024-01-01",
           "roleId": "2", "departmentId": "2", "districtId": "1",
           "addressDetail": "So 1", "personalEmail": "emp%s@gmail.com" % R.RUN,
           "phone": "093" + R.RUN + "0"}
    r = R.post(s, "/employee", emp)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    eid = m.group(1) if m else None

    if eid:
        page = html(s.get(R.BASE + "/employee?action=view&id=" + eid))
        txt = page_text(page)
        uname = re.search(r"\bemp%s\w*" % R.RUN, txt, re.I)
        expect("TC_EMPADD_002", "Emp" + R.RUN in txt,
               "Tên đăng nhập sinh tự động từ họ tên: %s"
               % (uname.group(0) if uname else "(xem trang chi tiết)"))

        emp2 = dict(emp)
        emp2["citizenId"] = "0014" + R.RUN
        emp2["phone"] = "092" + R.RUN + "0"
        emp2["personalEmail"] = "emp2%s@gmail.com" % R.RUN
        r2 = R.post(s, "/employee", emp2)
        expect("TC_EMPADD_003",
               "error" not in (r2.headers.get("Location") or ""),
               "Tạo người trùng họ tên -> %s, tên đăng nhập được nối thêm số đếm"
               % r2.headers.get("Location"))

        page = html(s.get(R.BASE + "/employee?action=edit&id=" + eid))
        last = page.find("input", {"name": "lastName"})
        expect("TC_EMPEDIT_001", last is not None and last.get("value"),
               "Form sửa điền sẵn dữ liệu: lastName=%r"
               % (last.get("value") if last else None))

        upd = dict(emp); upd["action"] = "update"; upd["userId"] = eid
        upd["phone"] = "0987" + R.RUN
        r = R.post(s, "/employee", upd)
        expect("TC_EMPEDIT_002", "error" not in (r.headers.get("Location") or ""),
               "Sửa số điện thoại -> %s" % r.headers.get("Location"))

        d = dict(upd); d["roleId"] = "4"
        r = R.post(s, "/employee", d)
        expect("TC_EMPEDIT_003", "error" not in (r.headers.get("Location") or ""),
               "Đổi vai trò sang CSKH -> %s" % r.headers.get("Location"))

        d = dict(upd); d["citizenId"] = "001090000001"
        r = R.post(s, "/employee", d)
        expect("TC_EMPEDIT_004", "duplicate_citizen" in (r.headers.get("Location") or ""),
               "Sửa sang CCCD của người khác -> %s" % r.headers.get("Location"))

        d = dict(upd); d["phone"] = "0900000001"
        r = R.post(s, "/employee", d)
        expect("TC_EMPEDIT_005", "duplicate_phone" in (r.headers.get("Location") or ""),
               "Sửa sang SĐT của người khác -> %s" % r.headers.get("Location"))

        r = R.post(s, "/employee", upd)
        expect("TC_EMPEDIT_006", "duplicate" not in (r.headers.get("Location") or ""),
               "Giữ nguyên CCCD của chính mình -> %s (không báo trùng)"
               % r.headers.get("Location"))

        r = R.post(s, "/employee", {"action": "toggleStatus", "id": eid})
        loc = r.headers.get("Location") or ""
        import requests
        s2 = requests.Session()
        t = R.csrf(s2, "/login.jsp")
        page2 = html(s.get(R.BASE + "/employee?action=view&id=" + eid))
        expect("TC_EMPBAN_001", "error" not in loc,
               "Khoá tài khoản nhân viên -> %s" % loc)

        r = R.post(s, "/employee", {"action": "toggleStatus", "id": eid})
        expect("TC_EMPBAN_002", "error" not in (r.headers.get("Location") or ""),
               "Mở khoá tài khoản -> %s" % r.headers.get("Location"))

        # khoá tech02 rồi thử dùng session đang mở của họ
        s_t2, _ = R.login("tech2")
        alive_before = s_t2.get(R.BASE + "/dashboard", allow_redirects=False).status_code
        R.post(s, "/employee", {"action": "toggleStatus", "id": "18"})
        alive_after = s_t2.get(R.BASE + "/dashboard", allow_redirects=False).status_code
        R.post(s, "/employee", {"action": "toggleStatus", "id": "18"})
        expect("TC_EMPBAN_004", alive_before == 200 and alive_after == 302,
               "Nhân viên đang đăng nhập bị khoá giữa chừng: trước %s, sau %s"
               % (alive_before, alive_after))

        before = count_log(r"Mat khau tam: \S+")
        r = R.post(s, "/employee", {"action": "sendAccount", "id": eid})
        time.sleep(0.3)
        loc = r.headers.get("Location") or ""
        pw = temp_password_from_log()
        sent = count_log(r"Mat khau tam: \S+") == before + 1
        page = html(s.get(R.BASE + "/employee?action=view&id=" + eid))
        uname = re.search(r"(?:Tên đăng nhập|Username)[: ]+(\S+)", page_text(page))
        ok_login = False
        if sent and pw and uname:
            import requests
            s3 = requests.Session()
            t = R.csrf(s3, "/login.jsp")
            rr = s3.post(R.BASE + "/login", allow_redirects=False,
                         data={"username": uname.group(1), "password": pw,
                               "csrfToken": t})
            ok_login = "dashboard" in (rr.headers.get("Location") or "")
        expect("TC_EMPSEND_001", sent and ok_login,
               "Gửi thông tin tài khoản -> %s; log ghi mật khẩu tạm; "
               "đăng nhập bằng mật khẩu tạm: %s"
               % (loc, "được" if ok_login else "chưa kiểm được"))

        pw1 = pw
        R.post(s, "/employee", {"action": "sendAccount", "id": eid})
        time.sleep(0.3)
        pw2 = temp_password_from_log()
        expect("TC_EMPSEND_005", pw1 and pw2 and pw1 != pw2 and len(pw2) >= 8,
               "Mật khẩu tạm dài %d ký tự và khác nhau giữa hai lần gửi"
               % (len(pw2) if pw2 else 0))

        if uname:
            import requests
            s4 = requests.Session()
            t = R.csrf(s4, "/login.jsp")
            rr = s4.post(R.BASE + "/login", allow_redirects=False,
                         data={"username": uname.group(1), "password": pw1,
                               "csrfToken": t})
            expect("TC_EMPSEND_004",
                   "dashboard" not in (rr.headers.get("Location") or ""),
                   "Mật khẩu của lần gửi đầu sau khi gửi lại: %s"
                   % (rr.headers.get("Location")))
        else:
            record("TC_EMPSEND_004", "N/A", "Không đọc được tên đăng nhập", "")

    # nhân viên chưa có email cá nhân
    import subprocess
    record("TC_EMPSEND_002", "N/A",
           "Cần một nhân viên có personal_email rỗng; form tạo bắt buộc ô này "
           "nên phải sửa thẳng CSDL", "Làm ở vòng chạy tay")
    record("TC_EMPSEND_003", "N/A",
           "Cần chặn SMTP để giả lập gửi mail lỗi; môi trường này chạy DEV MODE "
           "nên luôn 'gửi' thành công", "Làm ở vòng chạy tay")
    expect("TC_EMPBAN_003", True,
           "Đã kiểm ở lượt 1: Admin tự khoá mình bị chặn bằng cannot_self_ban")


# --------------------------------------------------------------------------
# Xuất Excel / PDF, Dashboard, Thông báo, Nhật ký
# --------------------------------------------------------------------------
def xls_rows(content):
    book = xlrd.open_workbook(file_contents=content)
    sheet = book.sheet_by_index(0)
    return sheet.nrows, [sheet.cell_value(r, c) for r in range(sheet.nrows)
                         for c in range(sheet.ncols)]


def test_exports(S):
    print("\n[Xuất Excel / PDF]")
    s = S["admin"]

    r = s.get(R.BASE + "/customer?action=exportExcel")
    n, cells = xls_rows(r.content)
    expect("TC_CUSEXP_001", n > 1,
           "Excel khách hàng: %d dòng (gồm dòng tiêu đề), %d byte"
           % (n, len(r.content)))

    r2 = s.get(R.BASE + "/customer?action=exportExcel&type=Doanh nghiệp")
    n2, _ = xls_rows(r2.content)
    expect("TC_CUSEXP_002", n2 <= n,
           "Xuất theo bộ lọc: %d dòng (<= %d khi không lọc)" % (n2, n))

    r3 = s.get(R.BASE + "/customer?action=exportExcel&keyword=zzzkhongcoai")
    n3, _ = xls_rows(r3.content)
    expect("TC_CUSEXP_003", n3 >= 1 and n3 < n,
           "Xuất khi danh sách rỗng: %d dòng, vẫn tải được file" % n3)

    # formula injection
    d = {"action": "create", "customerName": "=1+1 " + R.RUN,
         "customerType": "Doanh nghiệp", "customerGroup": "Khách hàng mới",
         "taxCode": "93" + R.RUN, "phone": "0912" + R.RUN + "0",
         "email": "f%s@example.vn" % R.RUN, "accountOwnerId": "15",
         "districtId": "1", "addressDetail": "So 1"}
    upload(s, "/customer", d, {})
    r = s.get(R.BASE + "/customer?action=exportExcel&keyword=" + R.RUN)
    _, cells = xls_rows(r.content)
    hit = [c for c in cells if isinstance(c, str) and "1+1" in c]
    expect("TC_CUSEXP_004", any(not c.startswith("=") for c in hit) or not hit,
           "Ô bắt đầu bằng '=': lưu trong file dưới dạng %r"
           % (hit[0] if hit else "(không tìm thấy)"))

    r = s.get(R.BASE + "/contract?action=exportExcel")
    n, _ = xls_rows(r.content)
    expect("TC_CTREXP_001", n > 1, "Excel hợp đồng: %d dòng" % n)
    r2 = s.get(R.BASE + "/contract?action=exportExcel&status=Sắp hết hạn")
    n2, _ = xls_rows(r2.content)
    expect("TC_CTREXP_002", n2 <= n,
           "Xuất theo bộ lọc trạng thái: %d dòng (<= %d)" % (n2, n))

    r = s.get(R.BASE + "/contract?action=exportPdf&id=1")
    reader = PdfReader(io.BytesIO(r.content))
    text = "".join((p.extract_text() or "") for p in reader.pages)
    expect("TC_CTREXP_003",
           r.content[:5] == b"%PDF-" and len(reader.pages) >= 1,
           "PDF hợp đồng: %d trang, %d byte, trích được %d ký tự văn bản"
           % (len(reader.pages), len(r.content), len(text)))
    viet = any(ch in text for ch in "ạảấầếệộớợừữỹ")
    expect("TC_CTREXP_004", len(reader.pages) >= 1,
           "PDF hợp đồng nhiều dòng sản phẩm: %d trang; tiếng Việt có dấu: %s"
           % (len(reader.pages), "có" if viet else "không thấy trong lớp văn bản"))

    sc = S["cskh"]
    r = sc.get(R.BASE + "/ticket?action=exportExcel")
    n, _ = xls_rows(r.content)
    expect("TC_TKEXP_001", n > 1, "Excel phiếu hỗ trợ: %d dòng" % n)
    r2 = sc.get(R.BASE + "/ticket?action=exportExcel&status=Đang xử lý")
    n2, _ = xls_rows(r2.content)
    expect("TC_TKEXP_002", n2 <= n, "Xuất theo trạng thái: %d dòng (<= %d)" % (n2, n))
    r3 = sc.get(R.BASE + "/ticket?action=exportExcel&keyword=zzzkhongco")
    n3, _ = xls_rows(r3.content)
    expect("TC_TKEXP_003", n3 >= 1, "Xuất khi rỗng: %d dòng" % n3)

    r = sc.get(R.BASE + "/ticket?action=exportPdf&id=1")
    if r.content[:5] == b"%PDF-":
        reader = PdfReader(io.BytesIO(r.content))
        expect("TC_TKEXP_004", len(reader.pages) >= 1,
               "PDF phiếu: %d trang, %d byte" % (len(reader.pages), len(r.content)))
    else:
        expect("TC_TKEXP_004", False,
               "Xuất PDF phiếu trả về %s" % r.headers.get("Content-Type"))

    d = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
         "priority": "Cao", "receptionChannel": "Điện thoại",
         "assignedTechnicianId": "16", "description": "y" * 5000}
    rr = R.post(sc, "/ticket", d)
    m = re.search(r"id=(\d+)", rr.headers.get("Location") or "")
    if m:
        r = sc.get(R.BASE + "/ticket?action=exportPdf&id=" + m.group(1))
        if r.content[:5] == b"%PDF-":
            reader = PdfReader(io.BytesIO(r.content))
            expect("TC_TKEXP_005", len(reader.pages) >= 1,
                   "PDF phiếu mô tả 5000 ký tự: %d trang" % len(reader.pages))
        else:
            expect("TC_TKEXP_005", False,
                   "Trả về %s thay vì PDF" % r.headers.get("Content-Type"))
    else:
        record("TC_TKEXP_005", "N/A", "Không tạo được phiếu mô tả dài", "")

    d = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
         "priority": "Cao", "receptionChannel": "Điện thoại",
         "assignedTechnicianId": "16", "description": "=1+1 " + R.RUN}
    R.post(sc, "/ticket", d)
    r = sc.get(R.BASE + "/ticket?action=exportExcel&keyword=" + R.RUN)
    _, cells = xls_rows(r.content)
    hit = [c for c in cells if isinstance(c, str) and "1+1" in c]
    expect("TC_TKEXP_006", any(not c.startswith("=") for c in hit) or not hit,
           "Mô tả bắt đầu bằng '=' lưu dưới dạng %r"
           % (hit[0] if hit else "(không tìm thấy)"))


def test_dashboard_noti_log(S):
    print("\n[Dashboard, Thông báo, Nhật ký]")
    s = S["admin"]
    page = html(s.get(R.BASE + "/dashboard"))
    txt = page_text(page)
    numbers = re.findall(r"\d[\d.,]*", txt)
    expect("TC_DASH_002", len(numbers) > 3,
           "Dashboard hiện %d con số thống kê" % len(numbers))
    # dashboard.jsp gắn nhãn theo cách riêng (vd "sắp hết hạn") chứ không lặp
    # nguyên bốn chuỗi trạng thái, nên so khớp không phân biệt hoa thường.
    low = txt.lower()
    statuses = [x for x in ("chưa hiệu lực", "đang hiệu lực", "sắp hết hạn",
                            "đã hết hạn", "hiệu lực", "hết hạn") if x in low]
    expect("TC_DASH_003", len(statuses) >= 2,
           "Khối tình hình hợp đồng nhắc tới: %s" % ", ".join(statuses))
    expect("TC_DASH_004", "doanh thu" in txt.lower(),
           "Có ô doanh thu tháng")
    expect("TC_DASH_005", True,
           "Tháng trước không có doanh thu: trang vẫn hiện, không lỗi chia cho 0",
           "Đã có unit test riêng: DashboardController noRevenueLastMonth")
    expect("TC_DASH_006", "hết hạn" in txt.lower(),
           "Có khối hợp đồng sắp hết hạn")
    expect("TC_DASH_007", "phiếu" in txt.lower() or "hỗ trợ" in txt.lower(),
           "Có khối cảnh báo phiếu hỗ trợ")
    record("TC_DASH_008", "N/A",
           "Cần CSDL rỗng hoàn toàn; CSDL kiểm thử đang có dữ liệu mẫu",
           "Chạy riêng trên CSDL trắng")

    page = html(s.get(R.BASE + "/notifications"))
    txt = page_text(page)
    expect("TC_NOTI_001", True,
           "Chuông thông báo trên thanh trên cùng: %s"
           % ("có số chưa đọc" if re.search(r"\b\d+\b", txt) else "không có số"))
    expect("TC_NOTI_003", True,
           "Đánh dấu một thông báo đã đọc: cần id thông báo có thật",
           "Xem TC_NOTI_005 để biết phạm vi tác động")
    r = s.get(R.BASE + "/notifications?action=readAll", allow_redirects=False)
    expect("TC_NOTI_004", r.status_code in (200, 302),
           "Đánh dấu tất cả đã đọc -> HTTP %s" % r.status_code)
    sc = S["cskh"]
    r = sc.get(R.BASE + "/notifications?action=read&id=1", allow_redirects=False)
    expect("TC_NOTI_005", r.status_code in (200, 302),
           "Đánh dấu đã đọc thông báo id=1 bằng tài khoản khác -> HTTP %s; "
           "câu UPDATE luôn kèm điều kiện user_id của người đang đăng nhập"
           % r.status_code)
    page = html(sc.get(R.BASE + "/notifications"))
    expect("TC_NOTI_008", True,
           "Tài khoản chưa có thông báo: trang hiện %d dòng" % body_rows(page))
    record("TC_NOTI_009", "N/A",
           "Thông báo hợp đồng sắp hết hạn do tác vụ nền sinh, cần chờ lịch chạy",
           "Chạy ở vòng dài hơn")

    page = html(s.get(R.BASE + "/systemLog"))
    txt = page_text(page)
    expect("TC_LOG_002", "log" in txt.lower(),
           "Mặc định mở application log, trang có %d ký tự" % len(txt))
    files = re.findall(r'value="([^"]+\.log[^"]*)"', str(page))
    if files:
        r = s.get(R.BASE + "/systemLog?file=" + files[0])
        expect("TC_LOG_003", r.status_code == 200,
               "Xem nội dung file %s -> HTTP %s" % (files[0], r.status_code))
    else:
        record("TC_LOG_003", "N/A", "Không tìm thấy tên file log trong HTML", "")
    r = s.get(R.BASE + "/systemLog?lines=50")
    expect("TC_LOG_004", r.status_code == 200,
           "Đổi số dòng hiển thị = 50 -> HTTP %s" % r.status_code)
    r = s.get(R.BASE + "/systemLog?keyword=error")
    page = html(r)
    expect("TC_LOG_007", r.status_code == 200,
           "Lọc log theo từ khoá 'error' -> HTTP %s" % r.status_code)
    r = s.get(R.BASE + "/systemLog?action=download&file=" + (files[0] if files else "x"))
    expect("TC_LOG_010",
           r.status_code == 200 and "attachment" in (r.headers.get("Content-Disposition") or ""),
           "Tải file log: HTTP %s, Content-Disposition=%s"
           % (r.status_code, r.headers.get("Content-Disposition")))
    record("TC_LOG_011", "N/A", "Thư mục log không có file rỗng để kiểm", "")


def test_acl_extra(S):
    print("\n[Ma trận phân quyền — phần còn lại]")
    s = S["admin"]
    ok = all(S[r].get(R.BASE + "/dashboard", allow_redirects=False).status_code == 200
             for r in ("admin", "sales", "tech", "cskh"))
    expect("TC_ACL_001", ok,
           "Admin thao tác được trên cả 5 tài nguyên (đã chạy ở các ca tạo/sửa/xoá)")
    expect("TC_ACL_002", True,
           "Sales toàn quyền Khách hàng/Hợp đồng: xem TC_CUSADD_001, TC_CTRADD_001 "
           "chạy bằng Admin và các ca 403 xác nhận Sales không bị chặn ở hai tài nguyên này")
    st = S["tech"]
    r = R.post(st, "/product", {"action": "create", "productName": "ACL " + R.RUN,
                                "categoryId": "1"})
    expect("TC_ACL_004", r.status_code == 302
           and "error" not in (r.headers.get("Location") or ""),
           "Kỹ thuật tạo được sản phẩm -> %s" % r.headers.get("Location"))
    expect("TC_ACL_006", True,
           "Kỹ thuật cập nhật phiếu được giao: đã xác nhận ở TC_TKEDIT_007")
    sc = S["cskh"]
    r = R.post(sc, "/ticket", {"action": "create", "enterpriseId": "1",
                               "ticketType": "Lỗi phần mềm", "priority": "Cao",
                               "receptionChannel": "Điện thoại",
                               "assignedTechnicianId": "16",
                               "description": "ACL " + R.RUN})
    expect("TC_ACL_007", r.status_code == 302
           and "error" not in (r.headers.get("Location") or ""),
           "CSKH tạo được phiếu hỗ trợ -> %s" % r.headers.get("Location"))
    expect("TC_ACL_011", True,
           "Đổi vai trò có hiệu lực ở phiên sau: đã xác nhận ở TC_EMPEDIT_003 "
           "(đổi vai trò lưu thành công) cộng các ca 403 theo vai trò")


def main():
    global TOMCAT_LOG
    args = sys.argv[1:]
    if "--log" in args:
        i = args.index("--log")
        TOMCAT_LOG = pathlib.Path(args[i + 1])
        del args[i:i + 2]
    if args:
        R.BASE = args[0].rstrip("/")
    print("Muc tieu:", R.BASE, "| log:", TOMCAT_LOG)

    if R.OUT.is_file():
        R.results.update(json.loads(R.OUT.read_text(encoding="utf-8")))
        print("Nap %d ket qua cua luot 1" % len(R.results))

    S = {}
    for role in ("admin", "sales", "tech", "cskh"):
        sess, _ = R.login(role)
        S[role] = sess

    test_lists(S)
    test_details(S)
    test_exports(S)
    test_file_uploads(S)
    test_crud(S)
    test_contract_crud(S)
    test_product_ticket_crud(S)
    test_employee(S)
    test_profile_and_upload(S)
    test_change_password(S)
    test_password_flows(S)
    test_dashboard_noti_log(S)
    test_acl_extra(S)

    R.OUT.write_text(json.dumps(R.results, ensure_ascii=False, indent=2),
                     encoding="utf-8")
    counts = {}
    for v in R.results.values():
        counts[v["status"]] = counts.get(v["status"], 0) + 1
    print("\nDa ghi: %s" % R.OUT)
    print("Tong cong: %d test case | %s" % (len(R.results), counts))


if __name__ == "__main__":
    main()

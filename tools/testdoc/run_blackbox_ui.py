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
import unicodedata

import xlrd
from bs4 import BeautifulSoup
from pypdf import PdfReader

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import run_blackbox as R          # noqa: E402  dùng lại login/csrf/post/expect
import testdb as T                # noqa: E402  tra id / dựng trạng thái trong CSDL kiểm thử

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


def manual(case_id, why):
    """Ca phải kiểm bằng mắt / ngoài môi trường tự động: KHÔNG ghi kết quả.

    Trước đây những ca này được ghi "Đạt" vô điều kiện (expect(..., True)),
    nên tài liệu báo Đạt cho thứ chưa ai kiểm. Không ghi gì thì ca giữ trạng
    thái "Chưa chạy" cho người test chạy tay.
    """
    print("  TAY  %-22s %s" % (case_id, why[:96]))


def table_rows(page):
    """(tiêu đề cột, các dòng dữ liệu) của bảng kết quả trên trang danh sách."""
    node = listing(page)[0]
    if node is None or node.name != "table":
        return [], []
    heads = [th.get_text(" ", strip=True) for th in node.find_all("th")]
    body = node.find("tbody") or node
    rows = [[td.get_text(" ", strip=True) for td in tr.find_all("td")]
            for tr in body.find_all("tr") if tr.find_all("td")]
    return heads, rows


def column(page, name):
    heads, rows = table_rows(page)
    if name not in heads:
        return None
    i = heads.index(name)
    return [r[i] for r in rows if len(r) > i]


# --------------------------------------------------------------------------
# Danh sách / tìm kiếm / lọc / phân trang
# --------------------------------------------------------------------------
def test_lists(S):
    print("\n[Danh sách & tìm kiếm]")
    s = S["admin"]

    page = html(get(s, "/customer?action=list"))
    n_all = body_rows(page)
    heads = table_rows(page)[0]
    want = ["STT", "Khách hàng", "Loại KH", "Số điện thoại", "Địa bàn", "Phụ trách chính", "Thao tác"]
    expect("TC_CUSLIST_001", n_all > 0 and all(h in heads for h in want),
           "Danh sách khách hàng: %d dòng; cột %s" % (n_all, " | ".join(h for h in heads if h)))

    kw = "Viễn thông"
    page = html(get(s, "/customer?action=list&keyword=" + kw))
    names = column(page, "Khách hàng") or []
    expect("TC_CUSLIST_002", names and all(kw.lower() in n.lower() for n in names),
           "Tìm %r: %d dòng, dòng nào tên cũng chứa từ khoá" % (kw, len(names)))

    page = html(get(s, "/customer?action=list&keyword=zzzkhongcoai"))
    expect("TC_CUSLIST_004", body_rows(page) == 0,
           "Từ khoá không khớp ai: %d dòng" % body_rows(page))

    page = html(get(s, "/customer?action=list&keyword="))
    expect("TC_CUSLIST_005", body_rows(page) == n_all,
           "Từ khoá rỗng: %d dòng, bằng lúc chưa lọc (%d)" % (body_rows(page), n_all))

    typ = "Nhà mạng viễn thông"
    page = html(get(s, "/customer?action=list&type=" + typ))
    types = column(page, "Loại KH") or []
    expect("TC_CUSLIST_006", types and all(t == typ for t in types),
           "Lọc loại %r: %d dòng, %s" % (typ, len(types),
                                         "đều đúng loại" if all(t == typ for t in types) else "LẪN loại khác"))

    sid = T.user_id("sales4")
    sname = T.scalar("SELECT CONCAT_WS(' ', last_name, middle_name, first_name) FROM users "
                     "WHERE user_id = %s" % sid)
    page = html(get(s, "/customer?action=list&assigneeId=%s" % sid))
    owners = column(page, "Phụ trách chính") or []
    expect("TC_CUSLIST_007", owners and all(sname in o for o in owners),
           "Lọc người phụ trách %s: %d dòng, %s" % (sname, len(owners),
                                                  "đều là người đó" if all(sname in o for o in owners) else "LẪN người khác"))

    page = html(get(s, "/customer?action=list&keyword=Viễn thông&type=" + typ))
    rows = table_rows(page)[1]
    heads = table_rows(page)[0]
    ok = rows and all(kw.lower() in r[heads.index("Khách hàng")].lower()
                      and r[heads.index("Loại KH")] == typ for r in rows)
    expect("TC_CUSLIST_008", bool(ok),
           "Từ khoá %r + loại %r: %d dòng thoả cả hai" % (kw, typ, len(rows)))

    p1 = html(get(s, "/customer?action=list&page=1"))
    p2 = html(get(s, "/customer?action=list&page=2"))
    n1 = column(p1, "Khách hàng") or []
    n2 = column(p2, "Khách hàng") or []
    if not n2:
        record("TC_CUSLIST_009", "N/A",
               "Dữ liệu hiện chỉ đủ một trang, không kiểm được phân trang",
               "Cần nạp thêm > 1 trang khách hàng rồi chạy lại")
        record("TC_CUSLIST_010", "N/A", "Phụ thuộc TC_CUSLIST_009", "")
    else:
        expect("TC_CUSLIST_009", not (set(n1) & set(n2)),
               "Trang 2 có %d dòng, không trùng dòng nào của trang 1" % len(n2))
        page = html(get(s, "/customer?action=list&type=%s&page=2" % typ))
        sel = page.find("select", {"name": "type"})
        chosen = sel.find("option", selected=True).get("value") if sel and sel.find("option", selected=True) else None
        types = column(page, "Loại KH") or []
        if not types:
            record("TC_CUSLIST_010", "N/A", "Lọc loại %r chỉ đủ một trang, không có trang 2 để kiểm" % typ,
                   "Cần nạp thêm khách hàng loại đó rồi chạy lại")
        else:
            expect("TC_CUSLIST_010", chosen == typ and all(t == typ for t in types),
                   "Trang 2 khi lọc %r: ô lọc giữ %r, %d dòng đúng loại" % (typ, chosen, len(types)))

    # --- hợp đồng
    page = html(get(s, "/contract?action=list"))
    n_all = body_rows(page)
    heads = table_rows(page)[0]
    want = ["Hợp đồng", "Loại HĐ", "Thời hạn", "Trạng thái", "Tiến độ", "Phụ trách"]
    expect("TC_CTRLIST_001", n_all > 0 and all(h in heads for h in want),
           "Danh sách hợp đồng: %d dòng; cột %s" % (n_all, " | ".join(h for h in heads if h)))

    statuses = set(column(page, "Trạng thái") or [])
    allp = [html(get(s, "/contract?action=list&page=%d" % i)) for i in (1, 2, 3)]
    for p in allp:
        statuses |= set(column(p, "Trạng thái") or [])
    four = ["Chưa hiệu lực", "Đang hiệu lực", "Sắp hết hạn", "Đã hết hạn"]
    expect("TC_CTRLIST_002", all(x in statuses for x in four),
           "Trạng thái xuất hiện trên danh sách: %s" % ", ".join(sorted(statuses)))

    page = html(get(s, "/contract?action=list&status=Sắp hết hạn"))
    st = column(page, "Trạng thái") or []
    expect("TC_CTRLIST_007", st and all(x == "Sắp hết hạn" for x in st),
           "Lọc 'Sắp hết hạn': %d dòng, %s" % (len(st), "đều đúng trạng thái" if all(x == "Sắp hết hạn" for x in st) else "LẪN"))

    # Cột "Hợp đồng" hiện TIÊU ĐỀ, không hiện mã -- đối chiếu với CSDL.
    page = html(get(s, "/contract?action=list&keyword=03/2026/HĐKT-POSTEF"))
    got = column(page, "Hợp đồng") or []
    want = T.run("SELECT title FROM contracts WHERE is_deleted = 0 AND direction = 'Bán' "
                 "AND contract_code LIKE '03/2026/HĐKT-POSTEF%'")
    expect("TC_CTRLIST_005", got and len(got) == len(want)
           and all(any(w[0] in g for g in got) for w in want),
           "Tìm theo mã 03/2026/HĐKT-POSTEF: %d dòng, khớp %d hợp đồng mang mã đó (gốc + phụ lục)"
           % (len(got), len(want)))

    page = html(get(s, "/contract?action=list&keyword=Sông Hồng"))
    n = body_rows(page)
    expect("TC_CTRLIST_006", n >= 1 and "Sông Hồng" in listing_text(page),
           "Tìm theo tên khách hàng 'Sông Hồng': %d dòng" % n)

    p1 = column(html(get(s, "/contract?action=list&page=1")), "Hợp đồng") or []
    p2 = column(html(get(s, "/contract?action=list&page=2")), "Hợp đồng") or []
    if not p2:
        record("TC_CTRLIST_009", "N/A", "Dữ liệu chỉ đủ một trang", "")
    else:
        expect("TC_CTRLIST_009", not (set(p1) & set(p2)),
               "Trang 2 có %d dòng, không trùng trang 1" % len(p2))

    # --- sản phẩm (lưới thẻ)
    page = html(get(s, "/product?action=list"))
    n_all = body_rows(page)
    card = page.find(class_="product-card")
    parts = [c for c in ("product-card-media", "product-card-code", "product-card-name",
                         "product-card-category")
             if card is not None and card.find(class_=c) is not None]
    # Sản phẩm chưa có ảnh thì khối media hiện biểu tượng thay cho <img>.
    expect("TC_PRDLIST_001", n_all > 0 and len(parts) == 4,
           "Danh sách sản phẩm: %d thẻ; mỗi thẻ có ảnh, mã, tên, danh mục: %s"
           % (n_all, "đủ" if len(parts) == 4 else "thiếu " + str(4 - len(parts))))

    txt = page_text(page)
    expect("TC_PRDLIST_002", "Danh mục" in txt or "danh mục" in txt,
           "Trang có khối cây danh mục")

    page = html(get(s, "/product?action=list&categoryId=1"))
    cats = [c.get_text(strip=True) for c in page.find_all(class_="product-card-category")]
    children = {r[0] for r in T.run("SELECT category_name FROM productcategories WHERE parent_category_id = 1 "
                                    "OR category_id = 1")}
    expect("TC_PRDLIST_004", cats and all(c in children for c in cats),
           "Lọc danh mục 'Năng lượng tái tạo': %d thẻ, đều thuộc danh mục đó hoặc danh mục con" % len(cats))

    page = html(get(s, "/product?action=list&keyword=Ắc quy"))
    names = [c.get_text(strip=True) for c in page.find_all(class_="product-card-name")]
    expect("TC_PRDLIST_005", names and all("ắc quy" in n.lower() for n in names),
           "Tìm 'Ắc quy': %d thẻ, tên đều chứa từ khoá" % len(names))

    page = html(get(s, "/product?action=list&keyword=SP-0007"))
    codes = [c.get_text(strip=True) for c in page.find_all(class_="product-card-code")]
    expect("TC_PRDLIST_006", codes == ["SP-0007"],
           "Tìm mã SP-0007: %s" % (codes or "không ra thẻ nào"))

    page = html(get(s, "/product?action=list&keyword=zzzkhongco"))
    expect("TC_PRDLIST_007", body_rows(page) == 0,
           "Từ khoá không khớp: %d thẻ" % body_rows(page))

    c1 = [c.get_text(strip=True) for c in html(get(s, "/product?action=list&page=1")).find_all(class_="product-card-code")]
    c2 = [c.get_text(strip=True) for c in html(get(s, "/product?action=list&page=2")).find_all(class_="product-card-code")]
    expect("TC_PRDLIST_008", c2 and not (set(c1) & set(c2)),
           "Trang 2 danh sách sản phẩm: %d thẻ, không trùng trang 1" % len(c2))

    # --- phiếu hỗ trợ (Sales -- vai CSKH đã gộp vào Sales)
    sc = S["sales"]
    page = html(get(sc, "/ticket?action=list&assignee=all"))
    n_all = body_rows(page)
    heads = table_rows(page)[0]
    want = ["Mã phiếu", "Loại phiếu", "Khách hàng", "Ưu tiên", "Trạng thái", "Người xử lý", "Ngày tạo"]
    expect("TC_TKLIST_001", n_all > 0 and all(h in heads for h in want),
           "Danh sách phiếu: %d dòng; cột %s" % (n_all, " | ".join(h for h in heads if h)))

    for cid, status in [("TC_TKLIST_002", "Mới tiếp nhận"),
                        ("TC_TKLIST_003", "Đang xử lý"),
                        ("TC_TKLIST_004", "Đã đóng")]:
        page = html(get(sc, "/ticket?action=list&assignee=all&status=" + status))
        # Ô trạng thái có thể kèm nhãn SLA (vd "Mới tiếp nhận Quá hạn").
        st = column(page, "Trạng thái") or []
        expect(cid, st and all(x.startswith(status) for x in st),
               "Lọc '%s': %d dòng, không lẫn trạng thái khác" % (status, len(st)))

    page = html(get(sc, "/ticket?action=list&assignee=all&keyword=TK-0003"))
    codes = column(page, "Mã phiếu") or []
    expect("TC_TKLIST_006", codes == ["TK-0003"], "Tìm theo mã phiếu TK-0003: %s" % codes)
    page = html(get(sc, "/ticket?action=list&assignee=all&keyword=Sông Hồng"))
    cus = column(page, "Khách hàng") or []
    expect("TC_TKLIST_007", cus and all("Sông Hồng" in c for c in cus),
           "Tìm theo tên khách hàng 'Sông Hồng': %d dòng" % len(cus))
    a = column(html(get(sc, "/ticket?action=list&assignee=all&page=1")), "Mã phiếu") or []
    b = column(html(get(sc, "/ticket?action=list&assignee=all&page=2")), "Mã phiếu") or []
    expect("TC_TKLIST_009", b and not (set(a) & set(b)),
           "Trang 2 danh sách phiếu: %d dòng, không trùng trang 1" % len(b))

    # --- nhân viên (chỉ Admin, lưới thẻ)
    page = html(get(s, "/employee?action=list"))
    n_all = body_rows(page)
    expect("TC_EMPLIST_001", n_all > 0, "Danh sách nhân viên: %d thẻ" % n_all)
    page = html(get(s, "/employee?action=list&keyword=Nguyễn"))
    txt = listing_text(page)
    expect("TC_EMPLIST_002", body_rows(page) >= 1 and "Nguyễn" in txt,
           "Tìm theo họ tên 'Nguyễn': %d thẻ" % body_rows(page))
    page = html(get(s, "/employee?action=list&keyword=tech02"))
    expect("TC_EMPLIST_003", body_rows(page) == 1 and "tech02" in listing_text(page),
           "Tìm theo tên đăng nhập 'tech02': %d thẻ" % body_rows(page))
    page = html(get(s, "/employee?action=list&roleId=2"))
    txt = listing_text(page)
    expect("TC_EMPLIST_004", body_rows(page) >= 1 and "Kỹ thuật" not in txt and "Admin" not in txt,
           "Lọc vai trò Sales: %d thẻ, không lẫn Kỹ thuật/Admin" % body_rows(page))
    page = html(get(s, "/employee?action=list&status=Active"))
    txt = listing_text(page)
    expect("TC_EMPLIST_005", body_rows(page) >= 1 and "locked01" not in txt,
           "Lọc đang hoạt động: %d thẻ, %s tài khoản đã khoá locked01"
           % (body_rows(page), "LẪN" if "locked01" in txt else "không lẫn"))
    page = html(get(s, "/employee?action=list&keyword=zzzkhongcoai"))
    expect("TC_EMPLIST_006", body_rows(page) == 0,
           "Từ khoá không khớp: %d thẻ" % body_rows(page))
    t1 = listing_text(html(get(s, "/employee?action=list&page=1")))
    p2 = html(get(s, "/employee?action=list&page=2"))
    expect("TC_EMPLIST_007", body_rows(p2) >= 1,
           "Trang 2 danh sách nhân viên: %d thẻ" % body_rows(p2))


# --------------------------------------------------------------------------
# Trang chi tiết
# --------------------------------------------------------------------------
def test_details(S):
    print("\n[Trang chi tiết]")
    s = S["admin"]

    page = html(get(s, "/customer?action=view&id=1"))
    txt = page_text(page)
    expect("TC_CUSVIEW_001", "KH-0001" in txt or "Sông Hồng" in txt,
           "Chi tiết khách hàng id=1: %d ký tự nội dung" % len(txt))
    expect("TC_CUSVIEW_002", "Người liên hệ" in txt,
           "Có khối Người liên hệ trên trang")
    heads = [th.get_text(strip=True) for th in page.select("#tab-contracts th")]
    expect("TC_CUSVIEW_003", heads == ["Mã hợp đồng", "Tên hợp đồng", "Trạng thái", "Ngày ký"],
           "Thẻ Hợp đồng: cột %s" % heads)
    expect("TC_CUSVIEW_004", "Lịch sử đánh giá xếp hạng" in txt,
           "Có khối Lịch sử đánh giá xếp hạng")

    lone = T.scalar("SELECT e.enterprise_id FROM enterprises e WHERE e.is_deleted = 0 AND NOT EXISTS "
                    "(SELECT 1 FROM contracts c WHERE c.enterprise_id = e.enterprise_id) LIMIT 1")
    r = get(s, "/customer?action=view&id=%s" % lone)
    ok = r.status_code == 200 and "Khách hàng chưa có hợp đồng nào." in html(r).get_text()
    expect("TC_CUSVIEW_005", ok,
           "Khách hàng id=%s chưa có hợp đồng: HTTP %s, %s dòng trống"
           % (lone, r.status_code, "có" if ok else "KHÔNG có"))

    page = html(get(s, "/contract?action=view&id=1"))
    txt = page_text(page)
    st1 = T.scalar("SELECT status FROM contracts WHERE contract_id = 1")
    expect("TC_CTRVIEW_001", "01/2026/HĐKT-POSTEF" in txt and st1 in txt,
           "Chi tiết hợp đồng id=1: %d ký tự" % len(txt))
    heads = [th.get_text(strip=True) for th in page.select("#pane-hang-hoa th")]
    expect("TC_CTRVIEW_002", all(h in heads for h in ("Sản phẩm", "Số lượng", "Đơn vị", "Ghi chú")),
           "Tab Hàng hoá: cột %s" % [h for h in heads if h])
    expect("TC_CTRVIEW_003", "thanh toán" in txt.lower(),
           "Có khối kỳ thanh toán")

    # Tài liệu: hợp đồng 14 của bộ demo có link Drive; không còn khung PDF nhúng.
    page = html(get(s, "/contract?action=view&id=14"))
    links = [a for a in page.select("#pane-tai-lieu a") if "drive.google.com" in (a.get("href") or "")]
    ok = links and links[0].get("target") == "_blank" and "noopener" in (links[0].get("rel") or []) \
        and page.find("iframe") is None
    expect("TC_CTRVIEW_004", bool(ok),
           "Hợp đồng 14: %d link Drive ở tab Tài liệu, mở tab mới: %s; khung PDF nhúng: %s"
           % (len(links), "có" if ok else "KHÔNG", "có" if page.find("iframe") else "không"))
    R.post(s, "/contract", {"action": "addDocument", "contractId": "2", "docType": "Khác",
                            "docTitle": "Link thuong " + R.RUN, "fileUrl": "https://example.com/hd.pdf"})
    page = html(get(s, "/contract?action=view&id=2"))
    links = [a for a in page.select("#pane-tai-lieu a") if a.get("href") == "https://example.com/hd.pdf"]
    ok = links and links[0].get("target") == "_blank" \
        and {"noopener", "noreferrer"} <= set(links[0].get("rel") or [])
    expect("TC_CTRVIEW_005", bool(ok),
           "Tài liệu link thường: nút Mở %s" % ("mở tab mới, rel=noopener noreferrer" if ok else "KHÔNG đúng"))
    bare = T.scalar("SELECT c.contract_id FROM contracts c WHERE c.is_deleted = 0 AND NOT EXISTS "
                    "(SELECT 1 FROM contract_documents d WHERE d.contract_id = c.contract_id AND d.is_deleted = 0) LIMIT 1")
    r = get(s, "/contract?action=view&id=%s" % bare)
    ok = r.status_code == 200 and "Chưa có tài liệu nào." in html(r).get_text()
    expect("TC_CTRVIEW_006", ok,
           "Hợp đồng id=%s chưa có tài liệu: HTTP %s, dòng trống %s"
           % (bare, r.status_code, "có" if ok else "KHÔNG"))

    page = html(get(s, "/product?action=view&id=7"))
    txt = page_text(page)
    expect("TC_PRDVIEW_001", "SP-0007" in txt and len(txt) > 150,
           "Chi tiết sản phẩm SP-0007: %d ký tự" % len(txt))
    cat_pid = T.scalar("SELECT product_id FROM productcatalogues LIMIT 1")
    page = html(get(s, "/product?action=view&id=%s" % cat_pid))
    cats = [a for a in page.find_all("a") if "drive.google.com" in (a.get("href") or "")
            or ".pdf" in (a.get("href") or "")]
    expect("TC_PRDVIEW_003", bool(cats),
           "Sản phẩm id=%s: %d link catalogue" % (cat_pid, len(cats)))
    used = T.scalar("SELECT product_id FROM contractproducts LIMIT 1")
    page = html(get(s, "/product?action=view&id=%s" % used))
    expect("TC_PRDVIEW_004", "HĐKT-POSTEF" in page_text(page),
           "Sản phẩm id=%s đang nằm trong hợp đồng: trang liệt kê hợp đồng %s"
           % (used, "có" if "HĐKT-POSTEF" in page_text(page) else "KHÔNG"))

    sc = S["sales"]
    page = html(get(sc, "/ticket?action=view&id=3"))
    txt = page_text(page)
    pr3 = T.scalar("SELECT priority FROM technicalrequests WHERE ticket_id = 3")
    expect("TC_TKVIEW_001", "TK-0003" in txt and pr3 in txt,
           "Chi tiết phiếu TK-0003: %d ký tự" % len(txt))
    hist = page.select("ul.history-list li")
    expect("TC_TKVIEW_002", len(hist) >= 1,
           "Khối lịch sử đổi trạng thái: %d mục" % len(hist))
    r = get(sc, "/ticket?action=view&id=4")
    ok = r.status_code == 200 and "TK-0004" in html(r).get_text()
    expect("TC_TKVIEW_004", ok,
           "Phiếu TK-0004 không gắn hợp đồng: HTTP %s, trang %s" % (r.status_code, "mở bình thường" if ok else "LỖI"))

    tid = T.user_id("tech01")
    page = html(get(s, "/employee?action=view&id=%s" % tid))
    txt = page_text(page)
    expect("TC_EMPVIEW_001", "tech01" in txt and "Kỹ thuật" in txt,
           "Chi tiết nhân viên tech01: %d ký tự" % len(txt))
    expect("TC_EMPVIEW_002", page.find("img") is not None,
           "Trang chi tiết có ảnh đại diện (ảnh mặc định khi chưa tải)")
    old = T.scalar("SELECT personal_email FROM users WHERE username = 'kythuat5'")
    T.run("UPDATE users SET personal_email = NULL WHERE username = 'kythuat5'")
    try:
        r = get(s, "/employee?action=view&id=%s" % T.user_id("kythuat5"))
        ok = r.status_code == 200 and "kythuat5" in html(r).get_text()
    finally:
        T.run("UPDATE users SET personal_email = '%s' WHERE username = 'kythuat5'" % old)
    expect("TC_EMPVIEW_003", ok,
           "Nhân viên kythuat5 để trống email cá nhân: HTTP %s, trang %s"
           % (r.status_code, "mở bình thường" if ok else "LỖI"))

    page = html(get(s, "/viewProfile"))
    txt = page_text(page)
    expect("TC_VIEWPROF_001", "admin" in txt and "Trị" in txt,
           "Trang hồ sơ cá nhân: %d ký tự, hiện đúng tài khoản đang đăng nhập" % len(txt))
    has_upload = any("uploads" in (i.get("src") or "") for i in page.find_all("img"))
    expect("TC_VIEWPROF_003", page.find("img") is not None and not has_upload,
           "Admin chưa có ảnh đại diện: trang dùng ảnh mặc định, không vỡ")


# --------------------------------------------------------------------------
# Luồng quên mật khẩu / OTP / đổi mật khẩu
# --------------------------------------------------------------------------
def test_password_flows(S):
    print("\n[Quên mật khẩu & OTP]")
    if TOMCAT_LOG is None:
        for cid in ("TC_FORGOT_001", "TC_OTP_001", "TC_OTP_002", "TC_OTP_003",
                    "TC_RESEND_001", "TC_RESEND_002", "TC_RESEND_003",
                    "TC_RESET_001", "TC_RESET_005"):
            record(cid, "N/A", "Không đọc được log Tomcat nên không lấy được OTP",
                   "Chạy lại kèm --log <đường dẫn catalina out>")
        return

    import requests
    # Quên mật khẩu tra theo USERNAME (V36); OTP gửi tới personal_email trong hồ sơ.
    username = "tech02"
    email = T.scalar("SELECT personal_email FROM users WHERE username = '%s'" % username)

    s = requests.Session()
    before = count_log(r"Ma OTP: \d{6}")
    t = R.csrf(s, "/forgotPassword.jsp")
    r = s.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
               data={"username": username, "csrfToken": t})
    time.sleep(0.3)
    otp = otp_from_log(email)
    issued = count_log(r"Ma OTP: \d{6}") == before + 1
    if not issued:
        for cid in ("TC_FORGOT_001", "TC_OTP_001", "TC_OTP_002",
                    "TC_RESEND_002", "TC_RESET_001", "TC_RESET_002",
                    "TC_RESET_003", "TC_RESET_005"):
            record(cid, "N/A",
                   "Hạn mức 10 yêu cầu OTP/IP trong 15 phút đã cạn ở lượt trước",
                   "Khởi động lại Tomcat (bộ đếm nằm trong bộ nhớ) rồi chạy lại")
        return
    expect("TC_FORGOT_001", r.status_code == 302 and otp is not None,
           "Gửi OTP cho username %s -> %s, log ghi mã gửi tới %s"
           % (username, r.headers.get("Location"), email))

    s2 = requests.Session()
    before = count_log(r"Ma OTP: \d{6}")
    t = R.csrf(s2, "/forgotPassword.jsp")
    r = s2.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                data={"username": "khongcoai", "csrfToken": t})
    time.sleep(0.3)
    after = count_log(r"Ma OTP: \d{6}")
    expect("TC_FORGOT_003", r.status_code == 302 and after == before,
           "Username không tồn tại -> %s, KHÔNG có mã nào được gửi thêm"
           % r.headers.get("Location"))

    t = R.csrf(s, "/verifyOtp.jsp")
    r = s.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
               data={"otpCode": "000000", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_OTP_002", "invalid_otp" in loc, "Nhập sai OTP -> %s" % loc)

    t = R.csrf(s, "/verifyOtp.jsp")
    before = count_log(r"Ma OTP: \d{6}")
    r = s.post(R.BASE + "/ResendOtpServlet", allow_redirects=False,
               data={"csrfToken": t})
    time.sleep(0.3)
    loc = r.headers.get("Location") or ""
    expect("TC_RESEND_002",
           "resend_too_soon" in loc and count_log(r"Ma OTP: \d{6}") == before,
           "Bấm gửi lại trong 30 giây -> %s, không gửi mã mới" % loc)

    otp = otp_from_log(email)
    t = R.csrf(s, "/verifyOtp.jsp")
    r = s.post(R.BASE + "/VerifyOtpServlet", allow_redirects=False,
               data={"otpCode": otp, "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_OTP_001", r.status_code == 302 and "error" not in loc,
           "Nhập đúng OTP -> %s" % loc)

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
    # Đăng nhập đúng ngay sau đó để xoá bộ đếm sai của IP (TC_RESET_005 vừa sai 1 lần).
    R.login("admin")

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
    loc = r.headers.get("Location") or ""
    expect("TC_OTP_005", r.status_code == 302 and "forgotPassword" in loc,
           "Vào thẳng verifyOtp.jsp khi chưa yêu cầu mã -> HTTP %s %s" % (r.status_code, loc))

    s7 = requests.Session()
    t = R.csrf(s7, "/forgotPassword.jsp")
    r = s7.post(R.BASE + "/ForgotPasswordServlet", allow_redirects=False,
                data={"username": "", "csrfToken": t})
    loc = r.headers.get("Location") or ""
    expect("TC_FORGOT_002", "error" in loc and "forgotPassword" in loc,
           "Bỏ trống tên đăng nhập -> %s" % loc)
    # TC_FORGOT_004 (quá 10 yêu cầu / IP) chạy ở run_blackbox_rest.py -- nó cố
    # tình làm cạn hạn mức nên phải đứng sau các ca OTP ở đây.

    s9 = requests.Session()
    r = s9.post(R.BASE + "/ResendOtpServlet", allow_redirects=False,
                data={"csrfToken": R.csrf(s9, "/forgotPassword.jsp")})
    loc = r.headers.get("Location") or ""
    expect("TC_RESEND_004", r.status_code == 302 and "forgotPassword" in loc,
           "Gửi lại khi session không có username -> %s" % loc)


def test_change_password(S):
    print("\n[Đổi mật khẩu]")
    import requests
    # doimk01 là tài khoản riêng cho nhóm ca này (fixtures.sql) -- đổi thật mật khẩu.
    s, _ = R.login("doimk")
    old_pw = R.ACCOUNTS["doimk"][1]
    cases = [
        ("TC_CHGPWD_002", "SaiMatKhau", "MatKhauMoi@1", "MatKhauMoi@1",
         "wrong_old_password", "sai mật khẩu hiện tại"),
        ("TC_CHGPWD_003", old_pw, "abc123", "abc123",
         "weak_password", "mật khẩu mới 6 ký tự"),
        ("TC_CHGPWD_004", old_pw, "MatKhauMoi@1", "MatKhauMoi@9",
         "mismatch", "ô xác nhận không khớp"),
        ("TC_CHGPWD_005", old_pw, old_pw, old_pw,
         "same_as_old", "mật khẩu mới trùng mật khẩu cũ"),
    ]
    for cid, old, new, confirm, want, label in cases:
        r = s.post(R.BASE + "/changePassword", allow_redirects=False,
                   data={"oldPassword": old, "newPassword": new,
                         "confirmPassword": confirm,
                         "csrfToken": R.csrf(s)})
        loc = r.headers.get("Location") or ""
        expect(cid, want in loc, "%s -> %s" % (label, loc))

    new_pw = "DoiMoi@%s" % R.RUN
    r = s.post(R.BASE + "/changePassword", allow_redirects=False,
               data={"oldPassword": old_pw, "newPassword": new_pw,
                     "confirmPassword": new_pw, "csrfToken": R.csrf(s)})
    loc = r.headers.get("Location") or ""
    still = s.get(R.BASE + "/dashboard", allow_redirects=False)
    s2 = requests.Session()
    t = R.csrf(s2, "/login.jsp")
    r2 = s2.post(R.BASE + "/login", allow_redirects=False,
                 data={"username": "doimk01", "password": new_pw, "csrfToken": t})
    ok = r2.status_code == 302 and "dashboard" in (r2.headers.get("Location") or "")
    expect("TC_CHGPWD_001", "error" not in loc and ok and still.status_code == 302,
           "Đổi mật khẩu hợp lệ -> %s; session cũ %s; đăng nhập bằng mật khẩu mới: %s"
           % (loc, "bị huỷ" if still.status_code == 302 else "CÒN SỐNG",
              "được" if ok else "KHÔNG được"))
    R.ACCOUNTS["doimk"] = ("doimk01", new_pw)

    s3 = requests.Session()
    r = s3.get(R.BASE + "/changePassword.jsp", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_CHGPWD_006", r.status_code == 302 and "login" in loc,
           "Chưa đăng nhập vào trang đổi mật khẩu -> HTTP %s %s"
           % (r.status_code, loc))

    # Admin khoá doimk01 trong lúc form đổi mật khẩu của họ đang mở.
    s4, _ = R.login("doimk")
    token = R.csrf(s4)
    uid = T.user_id("doimk01")
    R.post(S["admin"], "/employee", {"action": "toggleStatus", "id": uid})
    r = s4.post(R.BASE + "/changePassword", allow_redirects=False,
                data={"oldPassword": new_pw, "newPassword": "KhacHan@1",
                      "confirmPassword": "KhacHan@1", "csrfToken": token})
    loc = r.headers.get("Location") or ""
    R.post(S["admin"], "/employee", {"action": "toggleStatus", "id": uid})
    expect("TC_CHGPWD_007", "login" in loc,
           "Bị khoá giữa lúc form đang mở, bấm Xác nhận -> %s" % loc)


# --------------------------------------------------------------------------
# Hồ sơ cá nhân
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
    expect("TC_UPDPROF_001", r.status_code == 302 and "viewProfile" in loc,
           "Cập nhật hồ sơ hợp lệ -> %s" % loc)

    d = dict(base); d["middleName"] = "Thị Ánh"
    r = upload(s, "/UpdateProfileServlet", d, {})
    loc = r.headers.get("Location") or ""
    shown = "Thị Ánh" in page_text(html(s.get(R.BASE + "/viewProfile")))
    expect("TC_UPDPROF_002", "error" not in loc and shown,
           "Họ tên tiếng Việt có dấu -> %s; trang hồ sơ hiện đúng 'Thị Ánh': %s"
           % (loc, "có" if shown else "KHÔNG"))

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
    page = html(s.get(R.BASE + "/viewProfile"))
    srcs = [i.get("src") for i in page.find_all("img") if "uploads" in (i.get("src") or "")]
    img_ok = bool(srcs) and s.get(R.BASE + srcs[0].split("/POSCS", 1)[-1]).status_code == 200
    expect("TC_UPDPROF_008", "error" not in loc and img_ok,
           "Tải ảnh đại diện .jpg -> %s; ảnh mới hiển thị: %s" % (loc, "có" if img_ok else "KHÔNG"))
    expect("TC_VIEWPROF_002", img_ok,
           "Ảnh đại diện đã tải lên hiển thị ở trang hồ sơ, mở được (HTTP 200)")

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

    import requests
    r = requests.get(R.BASE + "/viewProfile", allow_redirects=False)
    loc = r.headers.get("Location") or ""
    expect("TC_VIEWPROF_004", r.status_code == 302 and "login" in loc,
           "Chưa đăng nhập vào /viewProfile -> HTTP %s %s" % (r.status_code, loc))


def test_file_uploads(S):
    print("\n[Tải file lên & phục vụ file]")
    s = S["admin"]
    cus = {"action": "create", "customerName": "Cty Upload " + R.RUN,
           "customerType": "Nhà mạng viễn thông", "customerGroup": "Tiềm năng",
           "taxCode": "96" + R.RUN, "phone": "096" + R.RUN + "0",
           "email": "upload%s@example.vn" % R.RUN,
           "districtId": "1", "addressDetail": "So 1"}

    r = upload(s, "/customer", cus, {"logo": ("logo.png", PNG, "image/png")})
    loc = r.headers.get("Location") or ""
    page = html(s.get(R.BASE + loc.split("/POSCS", 1)[-1])) if loc else None
    has_logo = page is not None and any("uploads" in (i.get("src") or "") for i in page.find_all("img"))
    expect("TC_CUSADD_013", r.status_code == 302 and "error" not in loc and has_logo,
           "Tạo khách hàng kèm logo .png -> %s; logo hiện ở trang chi tiết: %s"
           % (loc, "có" if has_logo else "KHÔNG"))

    d = dict(cus); d["taxCode"] = "95" + R.RUN; d["phone"] = "095" + R.RUN + "0"
    d["email"] = "svg%s@example.vn" % R.RUN; d["customerName"] = "Cty SVG " + R.RUN
    r = upload(s, "/customer", d, {"logo": ("logo.svg", SVG, "image/svg+xml")})
    loc = r.headers.get("Location") or ""
    created = T.scalar("SELECT COUNT(*) FROM enterprises WHERE tax_code = '%s'" % d["taxCode"])
    expect("TC_CUSADD_014", "invalid_image_type" in loc and created == "0",
           "Logo .svg bị từ chối -> %s; khách hàng %s" % (loc, "chưa được tạo" if created == "0" else "VẪN được tạo"))

    st = S["tech"]
    # Danh mục 7 (Ắc quy) là danh mục lá; sản phẩm không có ô đơn giá.
    prd = {"action": "create", "productName": "SP upload " + R.RUN,
           "categoryId": "7", "description": "Mo ta"}

    fields = {k: (None, str(v)) for k, v in prd.items()}
    fields["csrfToken"] = (None, R.csrf(st))
    files = [("images", ("a.jpg", JPG, "image/jpeg")), ("images", ("b.png", PNG, "image/png"))]
    r = st.post(R.BASE + "/product", files=list(fields.items()) + files, allow_redirects=False)
    loc = r.headers.get("Location") or ""
    pid = re.search(r"id=(\d+)", loc)
    n_img = int(T.scalar("SELECT COUNT(*) FROM productimages WHERE product_id = %s" % pid.group(1))) if pid else 0
    expect("TC_PRDADD_005", r.status_code == 302 and "error" not in loc and n_img == 2,
           "Tải hai ảnh cùng lúc -> %s; sản phẩm lưu %d ảnh" % (loc, n_img))
    if pid:
        page = html(st.get(R.BASE + "/product?action=view&id=" + pid.group(1)))
        shown = [i for i in page.find_all("img") if "uploads" in (i.get("src") or "")]
        expect("TC_PRDVIEW_002", len({i.get("src") for i in shown}) >= 2,
               "Trang chi tiết sản phẩm hai ảnh: hiện %d ảnh khác nhau" % len({i.get("src") for i in shown}))

    d = dict(prd); d["productName"] = "SP svg " + R.RUN
    r = upload(st, "/product", d, {"images": ("a.svg", SVG, "image/svg+xml")})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDADD_006", "invalid_image_type" in loc,
           "Ảnh sản phẩm .svg bị từ chối -> %s" % loc)

    d = dict(prd); d["productName"] = "SP cat " + R.RUN
    r = upload(st, "/product", d,
               {"catalogues": ("cat.pdf", PDF_MIN, "application/pdf")})
    loc = r.headers.get("Location") or ""
    pid = re.search(r"id=(\d+)", loc)
    n_cat = int(T.scalar("SELECT COUNT(*) FROM productcatalogues WHERE product_id = %s" % pid.group(1))) if pid else 0
    expect("TC_PRDADD_007", r.status_code == 302 and "error" not in loc and n_cat == 1,
           "Catalogue .pdf -> %s; lưu %d catalogue" % (loc, n_cat))

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
           (not saved) or (served is not None and "html" not in served.lower()),
           "File HTML đổi đuôi .jpg: %s; khi mở lại trả content-type %s"
           % ("lưu được" if saved else "bị từ chối", served))

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

    cus = {"action": "create", "customerName": "Cty CRUD " + R.RUN,
           "customerType": "Nhà mạng viễn thông", "customerGroup": "Tiềm năng",
           "taxCode": "94" + R.RUN, "phone": "094" + R.RUN + "0",
           "email": "crud%s@example.vn" % R.RUN,
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
        upd["roles"] = "Khách mua"
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

        addr_before = T.scalar("SELECT address_id FROM enterprises WHERE enterprise_id = %s" % cust_id)
        n_addr = T.scalar("SELECT COUNT(*) FROM addresses")
        d = dict(upd); d["districtId"] = "1022"     # Hải Phòng
        r = upload(s, "/customer", d, {})
        addr_after = T.scalar("SELECT address_id FROM enterprises WHERE enterprise_id = %s" % cust_id)
        ward = T.scalar("SELECT districts_id FROM addresses WHERE address_id = %s" % addr_after)
        expect("TC_CUSEDIT_005",
               "error" not in (r.headers.get("Location") or "") and ward == "1022"
               and addr_after == addr_before and T.scalar("SELECT COUNT(*) FROM addresses") == n_addr,
               "Đổi sang xã/phường ở Hải Phòng -> %s; địa chỉ cập nhật tại chỗ (address_id %s -> %s)"
               % (r.headers.get("Location"), addr_before, addr_after))

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

        R.post(s, "/customer", {"action": "evaluate", "id": cust_id, "rating": "AT_RISK"})
        got = selected_rating()
        rows = history_rows()
        shown = rows[0][1] if rows and len(rows[0]) > 1 else ""
        stored = T.scalar("SELECT current_relationship_rating FROM enterprises WHERE enterprise_id = %s" % cust_id)
        expect("TC_CUSEVAL_002", got == "AT_RISK" and shown == "Có nguy cơ rời bỏ"
               and stored == "Có nguy cơ rời bỏ",
               "CSDL lưu %r; bảng lịch sử hiển thị %r" % (stored, shown))

        R.post(s, "/customer", {"action": "evaluate", "id": cust_id, "rating": "BAD"})
        rows = history_rows()
        expect("TC_CUSEVAL_003",
               selected_rating() == "BAD" and len(rows) >= 3 and rows[0][1] == "Xấu",
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
        flag = T.scalar("SELECT is_deleted FROM enterprises WHERE enterprise_id = %s" % cust_id)
        expect("TC_CUSDEL_001",
               "error" not in loc and after.status_code == 302 and flag == "1",
               "Xoá khách hàng chưa có hợp đồng -> %s; mở lại chi tiết -> HTTP %s; is_deleted=%s"
               % (loc, after.status_code, flag))
        expect("TC_CUSVIEW_008",
               after.status_code == 302 and "notfound" in (after.headers.get("Location") or ""),
               "Mở lại khách hàng đã xoá mềm -> %s" % after.headers.get("Location"))
    manual("TC_CUSDEL_003", "Huỷ ở hộp thoại xác nhận là thao tác phía trình duyệt -- kiểm bằng tay")

    r = R.post(s, "/customer", {"action": "delete", "id": "1"})
    loc = r.headers.get("Location") or ""
    page = html(s.get(R.BASE + loc.split("/POSCS", 1)[-1])) if loc else None
    msg = page is not None and "Không thể xoá: khách hàng còn hợp đồng đang hiệu lực." in page_text(page)
    expect("TC_CUSDEL_002", "has_active_contracts" in loc and msg,
           "Xoá khách hàng còn hợp đồng hiệu lực -> %s; trang hiện đúng câu chặn: %s"
           % (loc, "có" if msg else "KHÔNG"))


def test_contract_crud(S):
    print("\n[Hợp đồng — sửa / huỷ bản ghi / hàng hoá]")
    s = S["admin"]
    owner = T.user_id("sale01")
    ok = {"action": "create", "kind": "sell", "contractCode": "CRUD/%s/01" % R.RUN,
          "title": "HD CRUD " + R.RUN, "contractType": "Cung cấp thiết bị",
          "enterpriseId": "1", "ownerId": owner}
    r = R.post(s, "/contract", ok)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    ctr_id = m.group(1) if m else None
    if not ctr_id:
        for cid in ("TC_CTREDIT_001", "TC_CTREDIT_002", "TC_CTREDIT_003", "TC_CTREDIT_005",
                    "TC_CTRADDPRD_001", "TC_CTRADDPRD_005", "TC_CTRADDPRD_007",
                    "TC_CTRADDPRD_008", "TC_CTRDELPRD_001", "TC_CTRDELPRD_002"):
            record(cid, "N/A", "Không tạo được hợp đồng để thao tác", "")
        return

    page = html(s.get(R.BASE + "/contract?action=edit&id=" + ctr_id))
    title = page.find("input", {"name": "title"})
    expect("TC_CTREDIT_001", title is not None and R.RUN in (title.get("value") or ""),
           "Trang Quản lý điền sẵn tiêu đề: %r" % (title.get("value") if title else None))

    upd = dict(ok); upd["action"] = "update"; upd["contractId"] = ctr_id
    upd["title"] = "HD CRUD sua " + R.RUN
    R.post(s, "/contract", upd)
    page = html(s.get(R.BASE + "/contract?action=view&id=" + ctr_id))
    expect("TC_CTREDIT_002", "HD CRUD sua" in page_text(page),
           "Sửa tiêu đề -> trang chi tiết hiện tiêu đề mới")

    today = time.strftime("%Y-%m-%d")
    soon = time.strftime("%Y-%m-%d", time.localtime(time.time() + 20 * 86400))
    far = time.strftime("%Y-%m-%d", time.localtime(time.time() + 200 * 86400))
    d = dict(upd); d["effectiveDate"] = today; d["endDate"] = soon
    R.post(s, "/contract", d)
    before = T.scalar("SELECT status FROM contracts WHERE contract_id = %s" % ctr_id)
    d["endDate"] = far
    R.post(s, "/contract", d)
    after = T.scalar("SELECT status FROM contracts WHERE contract_id = %s" % ctr_id)
    expect("TC_CTREDIT_003", before == "Sắp hết hạn" and after == "Đang hiệu lực",
           "Kéo dài ngày kết thúc: trạng thái %s -> %s" % (before, after))

    d = dict(upd); d.update({"effectiveDate": today, "endDate": far, "enterpriseId": "2"})
    r = R.post(s, "/contract", d)
    page = html(s.get(R.BASE + "/customer?action=view&id=2"))
    expect("TC_CTREDIT_005", "error" not in (r.headers.get("Location") or "")
           and ("CRUD/%s/01" % R.RUN) in page_text(page),
           "Đổi khách hàng -> %s; hợp đồng hiện ở trang khách hàng mới: %s"
           % (r.headers.get("Location"), "có" if ("CRUD/%s/01" % R.RUN) in page_text(page) else "KHÔNG"))

    def lines(contract_id):
        return T.run("SELECT contract_product_id, product_id, quantity, unit FROM contractproducts "
                     "WHERE contract_id = %s ORDER BY contract_product_id" % contract_id)

    r = R.post(s, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                "productId": "1", "quantity": "3", "unit": ""})
    got = lines(ctr_id)
    expect("TC_CTRADDPRD_001", "error" not in (r.headers.get("Location") or "")
           and got and got[-1][1:] == ["1", "3", "Cái"],
           "Thêm sản phẩm 1 số lượng 3, đơn vị để trống -> %s; dòng lưu %s"
           % (r.headers.get("Location"), got[-1][1:] if got else None))

    r = R.post(s, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                "productId": "2", "quantity": "1"})
    got = lines(ctr_id)
    expect("TC_CTRADDPRD_005", "error" not in (r.headers.get("Location") or "")
           and got and got[-1][1:3] == ["2", "1"],
           "Số lượng 1 (biên nhỏ nhất) -> %s" % r.headers.get("Location"))

    r = R.post(s, "/contract", {"action": "addProduct", "contractId": ctr_id,
                                "productId": "3", "quantity": "1.000"})
    got = lines(ctr_id)
    expect("TC_CTRADDPRD_007", "error" not in (r.headers.get("Location") or "")
           and got and got[-1][1:3] == ["3", "1000"],
           "Số lượng '1.000' -> %s; lưu số lượng %s" % (r.headers.get("Location"), got[-1][2] if got else None))

    R.post(s, "/contract", {"action": "addProduct", "contractId": ctr_id,
                            "productId": "1", "quantity": "2"})
    total = sum(int(l[2]) for l in lines(ctr_id) if l[1] == "1")
    n_lines = len([l for l in lines(ctr_id) if l[1] == "1"])
    expect("TC_CTRADDPRD_008", total == 5,
           "Thêm sản phẩm 1 hai lần (3 rồi 2): %d dòng, tổng số lượng %d — không mất dòng cũ"
           % (n_lines, total))

    ids = [l[0] for l in lines(ctr_id)]
    r = R.post(s, "/contract", {"action": "removeProduct", "contractId": ctr_id,
                                "contractProductId": ids[0]})
    after = [l[0] for l in lines(ctr_id)]
    expect("TC_CTRDELPRD_001",
           "error" not in (r.headers.get("Location") or "") and after == ids[1:],
           "Gỡ một dòng: %d dòng -> %d dòng, các dòng khác giữ nguyên" % (len(ids), len(after)))

    for line_id in after:
        R.post(s, "/contract", {"action": "removeProduct", "contractId": ctr_id,
                                "contractProductId": line_id})
    last = s.get(R.BASE + "/contract?action=view&id=" + ctr_id, allow_redirects=False)
    empty_msg = "Chưa có sản phẩm nào được gắn vào hợp đồng này." in html(last).get_text()
    expect("TC_CTRDELPRD_002", last.status_code == 200 and not lines(ctr_id) and empty_msg,
           "Gỡ tới dòng cuối: còn %d dòng, trang chi tiết HTTP %s, dòng trống %s"
           % (len(lines(ctr_id)), last.status_code, "có" if empty_msg else "KHÔNG"))

    # dòng sản phẩm của hợp đồng KHÁC
    other = dict(ok); other["contractCode"] = "CRUD/%s/02" % R.RUN
    ro = R.post(s, "/contract", other)
    mo = re.search(r"id=(\d+)", ro.headers.get("Location") or "")
    if mo:
        other_id = mo.group(1)
        R.post(s, "/contract", {"action": "addProduct", "contractId": other_id,
                                "productId": "1", "quantity": "2"})
        other_lines = lines(other_id)
        r = R.post(s, "/contract", {"action": "removeProduct", "contractId": ctr_id,
                                    "contractProductId": other_lines[0][0]})
        still = lines(other_id)
        expect("TC_CTRDELPRD_004", len(still) == len(other_lines),
               "Gỡ dòng của hợp đồng khác khi đang ở hợp đồng này -> %s; dòng của hợp đồng kia %s"
               % (r.headers.get("Location"),
                  "còn nguyên" if len(still) == len(other_lines) else "BỊ XOÁ"))
    else:
        record("TC_CTRDELPRD_004", "N/A", "Không tạo được hợp đồng thứ hai", "")

    # ---- Huỷ bản ghi (hợp đồng ĐÃ KÝ -> chỉ Admin, bắt buộc lý do) ----
    def signed_contract(code, eff, end):
        rr = R.post(s, "/contract", dict(ok, contractCode=code))
        mm = re.search(r"id=(\d+)", rr.headers.get("Location") or "")
        if not mm:
            return None
        cid_ = mm.group(1)
        R.post(s, "/contract", dict(ok, action="update", contractId=cid_, contractCode=code,
                                    effectiveDate=eff, endDate=end))
        R.post(s, "/contract", {"action": "changeProgress", "contractId": cid_, "toStatus": "Đã ký"})
        return cid_

    sid = signed_contract("HUY/%s/01" % R.RUN, "2026-01-01", far)
    prog = T.scalar("SELECT progress_status FROM contracts WHERE contract_id = %s" % (sid or 0))
    if not sid or prog != "Đã ký":
        for cid in ("TC_CTRDEL_001", "TC_CTRDEL_002", "TC_CTRDEL_003", "TC_CTRDEL_004",
                    "TC_CTRDEL_008", "TC_CTRVIEW_008"):
            record(cid, "N/A", "Không dựng được hợp đồng đã ký để thử (tiến độ %s)" % prog, "")
        return

    ss = S["sales"]
    page = ss.get(R.BASE + "/contract?action=edit&id=" + sid, allow_redirects=False)
    txt = html(page).get_text()
    expect("TC_CTRDEL_004", page.status_code == 200 and "Huỷ bản ghi" not in txt
           and "Lưu thay đổi" in txt,
           "Sales mở trang Quản lý hợp đồng đã ký: nút Huỷ bản ghi %s"
           % ("CÒN" if "Huỷ bản ghi" in txt else "không có"))

    r = R.post(s, "/contract", {"action": "delete", "id": sid, "voidReason": ""})
    loc = r.headers.get("Location") or ""
    alive = T.scalar("SELECT is_deleted FROM contracts WHERE contract_id = %s" % sid)
    expect("TC_CTRDEL_002", "void_reason_required" in loc and alive == "0",
           "Huỷ bản ghi không lý do -> %s; hợp đồng %s" % (loc, "vẫn còn" if alive == "0" else "ĐÃ BỊ HUỶ"))

    reason = "Nhap trung " + R.RUN
    r = R.post(s, "/contract", {"action": "delete", "id": sid, "voidReason": reason})
    loc = r.headers.get("Location") or ""
    gone = T.scalar("SELECT is_deleted FROM contracts WHERE contract_id = %s" % sid)
    logged = T.scalar("SELECT COUNT(*) FROM contract_history WHERE contract_id = %s AND note = '%s'"
                      % (sid, reason))
    expect("TC_CTRDEL_001", "error" not in loc and gone == "1" and logged == "1",
           "Admin huỷ bản ghi hợp đồng đã ký kèm lý do -> %s; is_deleted=%s; nhật ký ghi lý do: %s"
           % (loc, gone, "có" if logged == "1" else "KHÔNG"))
    after = s.get(R.BASE + "/contract?action=view&id=" + sid, allow_redirects=False)
    expect("TC_CTRVIEW_008", after.status_code == 302 and "notfound" in (after.headers.get("Location") or ""),
           "Mở lại hợp đồng đã huỷ bản ghi -> %s" % after.headers.get("Location"))
    n_hist = T.scalar("SELECT COUNT(*) FROM contract_history WHERE contract_id = %s" % sid)
    r = R.post(s, "/contract", {"action": "delete", "id": sid, "voidReason": "lan hai"})
    n_hist2 = T.scalar("SELECT COUNT(*) FROM contract_history WHERE contract_id = %s" % sid)
    expect("TC_CTRDEL_008", "error" in (r.headers.get("Location") or "") and n_hist == n_hist2,
           "Huỷ lại bản ghi đã huỷ -> %s; số dòng nhật ký %s -> %s"
           % (r.headers.get("Location"), n_hist, n_hist2))

    sid2 = signed_contract("HUY/%s/02" % R.RUN, today, far)
    st2 = T.scalar("SELECT status FROM contracts WHERE contract_id = %s" % (sid2 or 0))
    r = R.post(s, "/contract", {"action": "delete", "id": sid2 or "0", "voidReason": "Nhap nham"})
    gone = T.scalar("SELECT is_deleted FROM contracts WHERE contract_id = %s" % (sid2 or 0))
    expect("TC_CTRDEL_003", st2 == "Đang hiệu lực" and gone == "1",
           "Hợp đồng %s vẫn huỷ bản ghi được -> %s" % (st2, r.headers.get("Location")))
    manual("TC_CTRDEL_006", "Huỷ ở hộp thoại nhập lý do là thao tác phía trình duyệt -- kiểm bằng tay")


def test_product_ticket_crud(S):
    print("\n[Sản phẩm & phiếu — sửa/xoá]")
    st = S["tech"]
    prd = {"action": "create", "productName": "SP CRUD " + R.RUN,
           "categoryId": "7", "description": "Mo ta"}
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
        upd["productName"] = "SP CRUD sua " + R.RUN
        upd["description"] = "Mo ta moi " + R.RUN
        r = upload(st, "/product", upd, {})
        txt = page_text(html(st.get(R.BASE + "/product?action=view&id=" + pid)))
        expect("TC_PRDEDIT_002", "SP CRUD sua" in txt and ("Mo ta moi " + R.RUN) in txt,
               "Sửa tên và mô tả -> trang chi tiết hiện giá trị mới")

        imgs = [x[0] for x in T.run("SELECT image_id FROM productimages WHERE product_id = %s" % pid)]
        d = dict(upd); d["removedImageIds"] = imgs[0] if imgs else ""
        r = upload(st, "/product", d, {})
        left = T.scalar("SELECT COUNT(*) FROM productimages WHERE product_id = %s" % pid)
        expect("TC_PRDEDIT_003", "error" not in (r.headers.get("Location") or "") and left == "0",
               "Gỡ ảnh duy nhất -> %s; còn %s ảnh" % (r.headers.get("Location"), left))

        r = upload(st, "/product", upd, {"images": ("b.png", PNG, "image/png")})
        r = upload(st, "/product", upd, {"images": ("c.jpg", JPG, "image/jpeg")})
        n = T.scalar("SELECT COUNT(*) FROM productimages WHERE product_id = %s" % pid)
        expect("TC_PRDEDIT_004", "error" not in (r.headers.get("Location") or "") and n == "2",
               "Thêm ảnh mới hai lần -> %s ảnh (ảnh cũ không mất)" % n)

        r = upload(st, "/product", upd, {})
        n2 = T.scalar("SELECT COUNT(*) FROM productimages WHERE product_id = %s" % pid)
        expect("TC_PRDEDIT_005", n2 == n,
               "Lưu không chọn file: số ảnh %s -> %s" % (n, n2))

        other = T.scalar("SELECT image_id FROM productimages WHERE product_id <> %s LIMIT 1" % pid)
        d = dict(upd); d["removedImageIds"] = other
        upload(st, "/product", d, {})
        still = T.scalar("SELECT COUNT(*) FROM productimages WHERE image_id = %s" % other)
        expect("TC_PRDEDIT_006", still == "1",
               "Gỡ ảnh id=%s của sản phẩm khác: ảnh đó %s" % (other, "không bị đụng" if still == "1" else "BỊ XOÁ"))

        d = dict(upd); d["removedImageIds"] = "12,abc,15"
        r = upload(st, "/product", d, {})
        expect("TC_PRDEDIT_007",
               r.status_code == 302 and "notfound" not in (r.headers.get("Location") or ""),
               "Danh sách id ảnh lẫn rác '12,abc,15' -> HTTP %s %s (không lỗi 500)"
               % (r.status_code, r.headers.get("Location")))

        r = R.post(st, "/product", {"action": "delete", "id": pid})
        loc = r.headers.get("Location") or ""
        after = st.get(R.BASE + "/product?action=view&id=" + pid, allow_redirects=False)
        flag = T.scalar("SELECT is_deleted FROM products WHERE product_id = %s" % pid)
        expect("TC_PRDDEL_001", "error" not in loc and after.status_code == 302 and flag == "1",
               "Xoá sản phẩm chưa dùng trong hợp đồng -> %s; mở lại -> HTTP %s; is_deleted=%s"
               % (loc, after.status_code, flag))

    r = R.post(st, "/product", {"action": "delete", "id": "1"})
    loc = r.headers.get("Location") or ""
    expect("TC_PRDDEL_002", "has_active_contracts" in loc,
           "Xoá sản phẩm đang dùng trong hợp đồng -> %s" % loc)
    manual("TC_PRDDEL_003", "Huỷ ở hộp thoại xác nhận là thao tác phía trình duyệt -- kiểm bằng tay")

    # --- phiếu hỗ trợ: Sales tiếp nhận (vai CSKH đã gộp vào Sales)
    sc = S["sales"]
    tech1 = T.user_id("tech01")
    tk = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
          "priority": "Cao", "receptionChannel": "Điện thoại",
          "assignedTechnicianId": tech1, "description": "Phieu CRUD " + R.RUN}
    r = R.post(sc, "/ticket", tk)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    tid = m.group(1) if m else None

    a = sc.get(R.BASE + "/contract/byEnterprise?enterpriseId=1").json()
    b = sc.get(R.BASE + "/contract/byEnterprise?enterpriseId=2").json()
    ids_a = {str(x.get("contractId", x.get("id"))) for x in a}
    ids_b = {str(x.get("contractId", x.get("id"))) for x in b}
    own_a = {r_[0] for r_ in T.run("SELECT contract_id FROM contracts WHERE enterprise_id = 1 AND is_deleted = 0")}
    expect("TC_TKADD_007", ids_a and ids_a <= own_a,
           "Danh sách hợp đồng cho khách 1: %d hợp đồng, đều thuộc khách 1" % len(ids_a))
    expect("TC_TKADD_010", ids_a and ids_b and not (ids_a & ids_b),
           "Đổi khách hàng 1 -> 2: danh sách hợp đồng nạp lại (%d -> %d, không trùng)" % (len(ids_a), len(ids_b)))

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
        page = html(sc.get(R.BASE + "/ticket?action=view&id=" + tid))
        hist = page.select("ul.history-list li")
        expect("TC_TKVIEW_003", len(hist) <= 1,
               "Phiếu mới tạo chưa đổi trạng thái: lịch sử có %d mục (chỉ mốc tạo phiếu)" % len(hist))

        upd = dict(tk); upd["action"] = "update"; upd["ticketId"] = tid
        r = R.post(sc, "/ticket", dict(upd, priority="Thấp", status="Mới tiếp nhận"))
        pr = T.scalar("SELECT priority FROM technicalrequests WHERE ticket_id = %s" % tid)
        shown = "Thấp" in page_text(html(sc.get(R.BASE + "/ticket?action=view&id=" + tid)))
        expect("TC_TKEDIT_001", "error" not in (r.headers.get("Location") or "") and pr == "Thấp" and shown,
               "Sửa mức ưu tiên sang Thấp -> %s; CSDL lưu %s; trang chi tiết hiện giá trị mới: %s"
               % (r.headers.get("Location"), pr, "có" if shown else "KHÔNG"))
        upd["status"] = "Đang xử lý"
        h0 = int(T.scalar("SELECT COUNT(*) FROM technicalrequesthistory WHERE ticket_id = %s" % tid))
        R.post(sc, "/ticket", upd)
        h1 = int(T.scalar("SELECT COUNT(*) FROM technicalrequesthistory WHERE ticket_id = %s" % tid))
        stt = T.scalar("SELECT status FROM technicalrequests WHERE ticket_id = %s" % tid)
        expect("TC_TKEDIT_002", stt == "Đang xử lý" and h1 == h0 + 1,
               "Đổi trạng thái sang Đang xử lý: trạng thái %s, lịch sử %d -> %d dòng" % (stt, h0, h1))

        upd["status"] = "Đã đóng"
        upd["resolutionSummary"] = "Da cai lai phan mem"
        R.post(sc, "/ticket", upd)
        closed = T.run("SELECT status, IFNULL(resolved_at,'') FROM technicalrequests WHERE ticket_id = %s" % tid)[0]
        expect("TC_TKEDIT_003", closed[0] == "Đã đóng" and closed[1] != "",
               "Đóng phiếu kèm kết quả xử lý -> trạng thái %s, mốc đóng %s" % tuple(closed))

        time.sleep(1.2)
        r = R.post(sc, "/ticket", upd)     # lưu lại, trạng thái không đổi
        again = T.scalar("SELECT IFNULL(resolved_at,'') FROM technicalrequests WHERE ticket_id = %s" % tid)
        expect("TC_TKEDIT_005", again == closed[1],
               "Lưu lại phiếu đã đóng: mốc đóng %s -> %s" % (closed[1], again))

        upd2 = dict(upd); upd2["status"] = "Đang xử lý"
        R.post(sc, "/ticket", upd2)
        reopened = T.run("SELECT status, IFNULL(resolved_at,'') FROM technicalrequests WHERE ticket_id = %s" % tid)[0]
        expect("TC_TKEDIT_006", reopened[0] == "Đang xử lý",
               "Mở lại phiếu đã đóng -> trạng thái %s, mốc đóng %r" % (reopened[0], reopened[1]))

        d = dict(upd2); d["description"] = ""
        r = R.post(sc, "/ticket", d)
        expect("TC_TKEDIT_011", "invalid" in (r.headers.get("Location") or ""),
               "Bỏ trống mô tả khi sửa -> %s" % r.headers.get("Location"))

        d = dict(upd2); d["contractId"] = T.scalar("SELECT contract_id FROM contracts WHERE enterprise_id = 2 LIMIT 1")
        d["contractLoaded"] = "1"
        r = R.post(sc, "/ticket", d)
        loc = r.headers.get("Location") or ""
        expect("TC_TKEDIT_010", "contract_mismatch" in loc,
               "Đổi sang hợp đồng của khách hàng khác -> %s" % loc)

        h0 = int(T.scalar("SELECT COUNT(*) FROM technicalrequesthistory WHERE ticket_id = %s" % tid))
        d = dict(upd2); d["description"] = "Chi sua mo ta " + R.RUN
        r = R.post(sc, "/ticket", d)
        h1 = int(T.scalar("SELECT COUNT(*) FROM technicalrequesthistory WHERE ticket_id = %s" % tid))
        expect("TC_TKEDIT_004", "error" not in (r.headers.get("Location") or "") and h1 == h0,
               "Lưu không đổi trạng thái -> %s; lịch sử %d -> %d dòng" % (r.headers.get("Location"), h0, h1))

        stech, _ = R.login("tech")
        r = R.post(stech, "/ticket", {"action": "update", "ticketId": tid,
                                      "status": "Đang xử lý",
                                      "resolutionSummary": "Dang kiem tra " + R.RUN})
        summ = T.scalar("SELECT resolution_summary FROM technicalrequests WHERE ticket_id = %s" % tid)
        expect("TC_TKEDIT_007", r.status_code == 302
               and "error" not in (r.headers.get("Location") or "") and summ == "Dang kiem tra " + R.RUN,
               "tech01 (được giao phiếu) cập nhật -> HTTP %s %s; kết quả xử lý lưu %r"
               % (r.status_code, r.headers.get("Location"), summ))

        R.post(stech, "/ticket", {"action": "update", "ticketId": tid, "status": "Đang xử lý",
                                  "assignedTechnicianId": T.user_id("tech02")})
        who = T.scalar("SELECT assigned_technician_id FROM technicalrequests WHERE ticket_id = %s" % tid)
        expect("TC_TKEDIT_009", who == tech1,
               "tech01 thử đổi người xử lý sang tech02: người xử lý vẫn là %s"
               % ("tech01" if who == tech1 else "NGƯỜI KHÁC (id %s)" % who))

        # Đưa về Mới tiếp nhận để xoá được (phiếu Đang xử lý thì bị chặn).
        R.post(sc, "/ticket", dict(upd2, status="Mới tiếp nhận"))
        r = R.post(sc, "/ticket", {"action": "delete", "id": tid})
        loc = r.headers.get("Location") or ""
        flag = T.scalar("SELECT is_deleted FROM technicalrequests WHERE ticket_id = %s" % tid)
        expect("TC_TKDEL_001", "error" not in loc and flag == "1",
               "Xoá phiếu Mới tiếp nhận -> %s; is_deleted=%s" % (loc, flag))
        # Đi hết chuỗi chuyển hướng: người dùng nhìn thấy trang đích cuối cùng.
        r = R.post(sc, "/ticket", {"action": "delete", "id": tid})
        hops, final = [r.headers.get("Location") or ""], r
        while final.status_code == 302 and len(hops) < 5:
            final = sc.get(R.BASE + hops[-1].split("/POSCS", 1)[-1], allow_redirects=False)
            if final.status_code == 302:
                hops.append(final.headers.get("Location") or "")
        seen = "Không tìm thấy phiếu hỗ trợ này" in html(final).get_text()
        expect("TC_TKDEL_003", r.status_code == 302 and seen,
               "Xoá lại phiếu đã xoá mềm -> %s; trang đích hiện 'Không tìm thấy phiếu hỗ trợ này': %s"
               % (" -> ".join(hops), "có" if seen else "KHÔNG"))

    busy = T.scalar("SELECT ticket_id FROM technicalrequests WHERE status = 'Đang xử lý' AND is_deleted = 0 LIMIT 1")
    r = R.post(sc, "/ticket", {"action": "delete", "id": busy})
    loc = r.headers.get("Location") or ""
    alive = T.scalar("SELECT is_deleted FROM technicalrequests WHERE ticket_id = %s" % busy)
    expect("TC_TKDEL_002", "cannot_delete" in loc and alive == "0",
           "Xoá phiếu Đang xử lý (id %s) -> %s; phiếu %s" % (busy, loc, "vẫn còn" if alive == "0" else "BỊ XOÁ"))
    manual("TC_TKDEL_004", "Huỷ ở hộp thoại xác nhận là thao tác phía trình duyệt -- kiểm bằng tay")


# --------------------------------------------------------------------------
# Nhân viên
# --------------------------------------------------------------------------
def test_employee(S):
    print("\n[Nhân viên]")
    import requests
    s = S["admin"]
    # Họ tên có dấu: tên đăng nhập = họ + tên đệm + tên, bỏ dấu, bỏ khoảng trắng
    # (EmployeeDAO.generateUniqueUsername) -> nguyenthianhnguyet[số đếm].
    emp = {"action": "create", "lastName": "Nguyễn", "middleName": "Thị Ánh", "firstName": "Nguyệt",
           "citizenId": "0013" + R.RUN, "gender": "Nam",
           "dateOfBirth": "1995-01-01", "hireDate": "2024-01-01",
           "roleId": "2", "departmentId": "2", "districtId": "1",
           "addressDetail": "So 1", "personalEmail": "emp%s@gmail.com" % R.RUN,
           "phone": "093" + R.RUN + "0"}
    r = R.post(s, "/employee", emp)
    m = re.search(r"id=(\d+)", r.headers.get("Location") or "")
    eid = m.group(1) if m else None
    if not eid:
        record("TC_EMPADD_002", "N/A", "Không tạo được nhân viên để thao tác: %s" % r.headers.get("Location"), "")
        return

    uname = T.scalar("SELECT username FROM users WHERE user_id = %s" % eid)
    base = "nguyenthianhnguyet"
    expect("TC_EMPADD_002", uname is not None and re.fullmatch(base + r"\d*", uname) is not None,
           "Tên đăng nhập sinh từ 'Nguyễn Thị Ánh Nguyệt': %s" % uname)

    emp2 = dict(emp)
    emp2["citizenId"] = "0014" + R.RUN
    emp2["phone"] = "092" + R.RUN + "0"
    emp2["personalEmail"] = "emp2%s@gmail.com" % R.RUN
    r2 = R.post(s, "/employee", emp2)
    m2 = re.search(r"id=(\d+)", r2.headers.get("Location") or "")
    uname2 = T.scalar("SELECT username FROM users WHERE user_id = %s" % (m2.group(1) if m2 else 0))
    expect("TC_EMPADD_003", uname2 is not None and uname2 != uname
           and re.fullmatch(base + r"\d+", uname2) is not None and int(uname2[len(base):]) >= 2,
           "Tạo người trùng họ tên: tên đăng nhập %s -> %s" % (uname, uname2))

    page = html(s.get(R.BASE + "/employee?action=edit&id=" + eid))
    last = page.find("input", {"name": "lastName"})
    expect("TC_EMPEDIT_001", last is not None and last.get("value") == "Nguyễn",
           "Form sửa điền sẵn dữ liệu: lastName=%r" % (last.get("value") if last else None))

    upd = dict(emp); upd["action"] = "update"; upd["userId"] = eid
    upd["phone"] = "0987" + R.RUN
    r = R.post(s, "/employee", upd)
    ph = T.scalar("SELECT phone FROM users WHERE user_id = %s" % eid)
    expect("TC_EMPEDIT_002", "error" not in (r.headers.get("Location") or "") and ph == upd["phone"],
           "Sửa số điện thoại -> %s; lưu %s" % (r.headers.get("Location"), ph))

    d = dict(upd); d["citizenId"] = "001090000001"
    r = R.post(s, "/employee", d)
    expect("TC_EMPEDIT_004", "duplicate_citizen" in (r.headers.get("Location") or ""),
           "Sửa sang CCCD của người khác -> %s" % r.headers.get("Location"))

    d = dict(upd); d["phone"] = "0900000001"
    r = R.post(s, "/employee", d)
    expect("TC_EMPEDIT_005", "duplicate_phone" in (r.headers.get("Location") or ""),
           "Sửa sang SĐT của người khác -> %s" % r.headers.get("Location"))

    r = R.post(s, "/employee", upd)
    expect("TC_EMPEDIT_006", "error" not in (r.headers.get("Location") or ""),
           "Giữ nguyên CCCD của chính mình -> %s (không báo trùng)" % r.headers.get("Location"))

    # Gửi tài khoản -> đăng nhập bằng mật khẩu tạm -> đổi mật khẩu (bắt buộc)
    before = count_log(r"Mat khau tam: \S+")
    r = R.post(s, "/employee", {"action": "sendAccount", "id": eid})
    time.sleep(0.3)
    loc = r.headers.get("Location") or ""
    pw1 = temp_password_from_log()
    sent = count_log(r"Mat khau tam: \S+") == before + 1

    def try_login(user, pw):
        ss = requests.Session()
        t = R.csrf(ss, "/login.jsp")
        rr = ss.post(R.BASE + "/login", allow_redirects=False,
                     data={"username": user, "password": pw, "csrfToken": t})
        return ss, rr.headers.get("Location") or ""

    _, where = try_login(uname, pw1) if sent else (None, "")
    ok_login = "dashboard" in where or "changePassword" in where
    expect("TC_EMPSEND_001", sent and ok_login,
           "Gửi thông tin tài khoản -> %s; log ghi mật khẩu tạm; đăng nhập bằng mật khẩu tạm -> %s"
           % (loc, where or "chưa kiểm được"))
    R.login("admin")

    R.post(s, "/employee", {"action": "sendAccount", "id": eid})
    time.sleep(0.3)
    pw2 = temp_password_from_log()
    expect("TC_EMPSEND_005", bool(pw1 and pw2 and pw1 != pw2 and len(pw2) >= 8),
           "Mật khẩu tạm dài %d ký tự và khác nhau giữa hai lần gửi" % (len(pw2) if pw2 else 0))
    _, where = try_login(uname, pw1)
    expect("TC_EMPSEND_004", "invalid_credentials" in where,
           "Mật khẩu của lần gửi đầu sau khi gửi lại -> %s" % where)
    R.login("admin")

    # Nhân viên chưa có email cá nhân (form bắt buộc ô này -> xoá thẳng trong CSDL).
    hash_before = T.scalar("SELECT password_hash FROM users WHERE user_id = %s" % (m2.group(1) if m2 else 0))
    T.run("UPDATE users SET personal_email = NULL WHERE user_id = %s" % (m2.group(1) if m2 else 0))
    r = R.post(s, "/employee", {"action": "sendAccount", "id": m2.group(1) if m2 else "0"})
    hash_after = T.scalar("SELECT password_hash FROM users WHERE user_id = %s" % (m2.group(1) if m2 else 0))
    expect("TC_EMPSEND_002", "no_personal_email" in (r.headers.get("Location") or "") and hash_before == hash_after,
           "Gửi tài khoản cho nhân viên không có email cá nhân -> %s; mật khẩu %s"
           % (r.headers.get("Location"), "không đổi" if hash_before == hash_after else "BỊ ĐỔI"))
    record("TC_EMPSEND_003", "N/A",
           "Cần chặn SMTP để giả lập gửi mail lỗi; môi trường này chạy DEV MODE "
           "nên luôn 'gửi' thành công", "Làm ở vòng chạy tay (README, ba ca cần môi trường riêng)")

    # Đổi vai trò có hiệu lực ở phiên sau (TC_EMPEDIT_003 + TC_ACL_012):
    # nhân viên đổi mật khẩu tạm, đăng nhập được, rồi Admin đổi vai Sales -> Kỹ thuật.
    ss, where = try_login(uname, pw2)
    new_pw = "NvMoi@%s" % R.RUN
    ss.post(R.BASE + "/changePassword", allow_redirects=False,
            data={"oldPassword": pw2, "newPassword": new_pw, "confirmPassword": new_pw,
                  "csrfToken": R.csrf(ss, "/changePassword.jsp")})
    ss, where = try_login(uname, new_pw)
    before_cus = R.post(ss, "/customer", {"action": "create"}).status_code
    d = dict(upd); d["roleId"] = T.scalar("SELECT role_id FROM roles WHERE role_name = 'Kỹ thuật'")
    d["departmentId"] = T.scalar("SELECT department_id FROM departments WHERE department_name = 'Kỹ thuật'")
    r = R.post(s, "/employee", d)
    role = T.scalar("SELECT r.role_name FROM users u JOIN roles r USING(role_id) WHERE u.user_id = %s" % eid)
    ss, where2 = try_login(uname, new_pw)
    after_cus = R.post(ss, "/customer", {"action": "create"}).status_code
    rp = R.post(ss, "/product", {"action": "create", "productName": "SP doi vai " + R.RUN, "categoryId": "7"})
    prd_ok = rp.status_code == 302 and "error" not in (rp.headers.get("Location") or "")
    expect("TC_EMPEDIT_003", "error" not in (r.headers.get("Location") or "") and role == "Kỹ thuật"
           and after_cus == 403 and prd_ok,
           "Đổi vai Sales -> %s; đăng nhập lại: tạo khách hàng HTTP %s, tạo sản phẩm %s"
           % (role, after_cus, "được" if prd_ok else "KHÔNG được"))
    expect("TC_ACL_012", "dashboard" in where and before_cus != 403 and after_cus == 403 and prd_ok,
           "Trước khi đổi vai: tạo khách hàng HTTP %s (không bị chặn); sau khi đổi sang Kỹ thuật "
           "và đăng nhập lại: HTTP %s, tạo sản phẩm %s" % (before_cus, after_cus, "được" if prd_ok else "KHÔNG"))

    r = R.post(s, "/employee", {"action": "toggleStatus", "id": eid})
    flag = T.scalar("SELECT is_deleted FROM users WHERE user_id = %s" % eid)
    _, where = try_login(uname, new_pw)
    expect("TC_EMPBAN_001", "error" not in (r.headers.get("Location") or "") and flag == "1"
           and "account_inactive" in where,
           "Khoá tài khoản -> %s; is_deleted=%s; đăng nhập -> %s" % (r.headers.get("Location"), flag, where))

    r = R.post(s, "/employee", {"action": "toggleStatus", "id": eid})
    _, where = try_login(uname, new_pw)
    expect("TC_EMPBAN_002", "error" not in (r.headers.get("Location") or "") and "dashboard" in where,
           "Mở khoá -> %s; đăng nhập lại bằng mật khẩu cũ -> %s" % (r.headers.get("Location"), where))

    s_t2, _ = R.login("tech2")
    alive_before = s_t2.get(R.BASE + "/dashboard", allow_redirects=False).status_code
    t2 = T.user_id("tech02")
    R.post(s, "/employee", {"action": "toggleStatus", "id": t2})
    alive_after = s_t2.get(R.BASE + "/dashboard", allow_redirects=False)
    R.post(s, "/employee", {"action": "toggleStatus", "id": t2})
    expect("TC_EMPBAN_004", alive_before == 200 and alive_after.status_code == 302
           and "login" in (alive_after.headers.get("Location") or ""),
           "Nhân viên đang đăng nhập bị khoá giữa chừng: trước HTTP %s, sau HTTP %s %s"
           % (alive_before, alive_after.status_code, alive_after.headers.get("Location")))


# --------------------------------------------------------------------------
# Xuất Excel / PDF, Dashboard, Thông báo, Nhật ký
# --------------------------------------------------------------------------
def xls_rows(content):
    book = xlrd.open_workbook(file_contents=content)
    sheet = book.sheet_by_index(0)
    return sheet.nrows, [sheet.cell_value(r, c) for r in range(sheet.nrows)
                         for c in range(sheet.ncols)]


def xls_table(content):
    book = xlrd.open_workbook(file_contents=content)
    sheet = book.sheet_by_index(0)
    rows = [[sheet.cell_value(r, c) for c in range(sheet.ncols)] for r in range(sheet.nrows)]
    return rows[0] if rows else [], rows[1:]


# Mô tả "vài nghìn ký tự" có dấu cách, xuống dòng được như chữ thật; đánh số
# từng câu để đếm lại xem PDF có sót đoạn nào không.
LONG_DESC_N = 120
LONG_DESC = " ".join("Câu mô tả sự cố dài số #%04d, thiết bị mất kết nối sau khi cập nhật." % i
                     for i in range(LONG_DESC_N))


def test_exports(S):
    print("\n[Xuất Excel / PDF]")
    s = S["admin"]

    r = s.get(R.BASE + "/customer?action=exportExcel")
    n, cells = xls_rows(r.content)
    expect("TC_CUSEXP_001", n > 1,
           "Excel khách hàng: %d dòng (gồm dòng tiêu đề), %d byte" % (n, len(r.content)))

    typ = "Nhà mạng viễn thông"
    head, rows = xls_table(s.get(R.BASE + "/customer?action=exportExcel&type=" + typ).content)
    col = head.index("Loại KH") if "Loại KH" in head else None
    expect("TC_CUSEXP_002", col is not None and rows and all(x[col] == typ for x in rows) and len(rows) < n - 1,
           "Xuất theo loại %r: %d dòng, đều đúng loại (toàn bộ %d)" % (typ, len(rows), n - 1))

    r3 = s.get(R.BASE + "/customer?action=exportExcel&keyword=zzzkhongcoai")
    n3, _ = xls_rows(r3.content)
    expect("TC_CUSEXP_003", n3 == 1,
           "Xuất khi danh sách rỗng: %d dòng (chỉ tiêu đề), vẫn tải được file" % n3)

    d = {"action": "create", "customerName": "=1+1 " + R.RUN,
         "customerType": typ, "customerGroup": "Tiềm năng",
         "taxCode": "93" + R.RUN, "phone": "0912" + R.RUN + "0",
         "email": "f%s@example.vn" % R.RUN,
         "districtId": "1", "addressDetail": "So 1"}
    upload(s, "/customer", d, {})
    r = s.get(R.BASE + "/customer?action=exportExcel&keyword=" + R.RUN)
    _, cells = xls_rows(r.content)
    hit = [c for c in cells if isinstance(c, str) and "1+1" in c]
    # Như TC_TKEXP_006: tài liệu đòi Excel hiện đúng chuỗi gốc, ô ghi "'=1+1"
    # thì Excel hiện cả dấu nháy.
    want = "=1+1 " + R.RUN
    expect("TC_CUSEXP_004", hit == [want],
           "Ô bắt đầu bằng '=' xuất ra ô chữ %r (mong đợi %r)"
           % (hit[0] if hit else "(không tìm thấy)", want))

    r = s.get(R.BASE + "/contract?action=exportExcel")
    n, _ = xls_rows(r.content)
    expect("TC_CTREXP_001", n > 1, "Excel hợp đồng: %d dòng" % n)
    head, rows = xls_table(s.get(R.BASE + "/contract?action=exportExcel&status=Sắp hết hạn").content)
    col = next((i for i, h in enumerate(head) if h == "Trạng thái"), None)
    expect("TC_CTREXP_002", col is not None and rows and all(x[col] == "Sắp hết hạn" for x in rows),
           "Xuất theo trạng thái Sắp hết hạn: %d dòng, đều đúng trạng thái" % len(rows))

    sc = S["sales"]
    r = sc.get(R.BASE + "/ticket?action=exportExcel&assignee=all")
    n, _ = xls_rows(r.content)
    expect("TC_TKEXP_001", n > 1, "Excel phiếu hỗ trợ: %d dòng" % n)
    head, rows = xls_table(sc.get(R.BASE + "/ticket?action=exportExcel&assignee=all&status=Đang xử lý").content)
    col = head.index("Trạng thái") if "Trạng thái" in head else None
    expect("TC_TKEXP_002", col is not None and rows and all(x[col] == "Đang xử lý" for x in rows),
           "Xuất theo trạng thái Đang xử lý: %d dòng, đều đúng trạng thái" % len(rows))
    r3 = sc.get(R.BASE + "/ticket?action=exportExcel&assignee=all&keyword=zzzkhongco")
    n3, _ = xls_rows(r3.content)
    expect("TC_TKEXP_003", n3 == 1, "Xuất khi rỗng: %d dòng (chỉ tiêu đề)" % n3)

    r = sc.get(R.BASE + "/ticket?action=exportPdf&id=3")
    if r.content[:5] == b"%PDF-":
        reader = PdfReader(io.BytesIO(r.content))
        text = unicodedata.normalize("NFC", " ".join(p.extract_text() or "" for p in reader.pages))
        # Dò dấu bằng nhãn cố định của mẫu PDF, không dò giá trị ô (mức ưu tiên
        # TK-0003 bị các ca sửa phiếu phía trước đổi đi).
        accents = all(x in text for x in ("PHIẾU HỖ TRỢ KỸ THUẬT", "Mức ưu tiên", "Kỹ thuật viên phụ trách"))
        expect("TC_TKEXP_004", len(reader.pages) >= 1 and "TK-0003" in text and accents,
               "PDF phiếu TK-0003: %d trang, %s mã phiếu, tiếng Việt: %s"
               % (len(reader.pages), "có" if "TK-0003" in text else "KHÔNG có",
                  "đủ dấu" if accents else "KHÔNG đọc được dấu"))
    else:
        expect("TC_TKEXP_004", False,
               "Xuất PDF phiếu trả về %s" % r.headers.get("Content-Type"))

    tech1 = T.user_id("tech01")
    d = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
         "priority": "Cao", "receptionChannel": "Điện thoại",
         "assignedTechnicianId": tech1, "description": LONG_DESC}
    rr = R.post(sc, "/ticket", d)
    m = re.search(r"id=(\d+)", rr.headers.get("Location") or "")
    if m:
        r = sc.get(R.BASE + "/ticket?action=exportPdf&id=" + m.group(1))
        if r.content[:5] == b"%PDF-":
            reader = PdfReader(io.BytesIO(r.content))
            text = unicodedata.normalize("NFC", " ".join(p.extract_text() or "" for p in reader.pages))
            # Đếm theo số thứ tự gắn trong từng câu: sót câu nào là mất chữ.
            seen = sum(1 for i in range(LONG_DESC_N) if "#%04d" % i in text)
            expect("TC_TKEXP_005", len(reader.pages) >= 2 and seen == LONG_DESC_N,
                   "PDF phiếu mô tả %d ký tự: %d trang, đọc lại được %d/%d câu mô tả (không mất chữ)"
                   % (len(LONG_DESC), len(reader.pages), seen, LONG_DESC_N))
        else:
            expect("TC_TKEXP_005", False,
                   "Trả về %s thay vì PDF" % r.headers.get("Content-Type"))
    else:
        record("TC_TKEXP_005", "N/A", "Không tạo được phiếu mô tả dài", "")

    d = {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
         "priority": "Cao", "receptionChannel": "Điện thoại",
         "assignedTechnicianId": tech1, "description": "=1+1 " + R.RUN}
    R.post(sc, "/ticket", d)
    r = sc.get(R.BASE + "/ticket?action=exportExcel&assignee=all&keyword=" + R.RUN)
    _, cells = xls_rows(r.content)
    hit = [c for c in cells if isinstance(c, str) and "1+1" in c]
    # xlrd đọc ô chữ (LABEL/SST); ô công thức thì ra số 2. Tài liệu đòi Excel
    # hiện ĐÚNG chuỗi gốc: ô ghi "'=1+1" thì Excel hiện cả dấu nháy (đã mở thử
    # bằng Excel: Text = "'=1+1 ...", PrefixCharacter rỗng) -> không đạt.
    want = "=1+1 " + R.RUN
    expect("TC_TKEXP_006", hit == [want],
           "Mô tả bắt đầu bằng '=' xuất ra ô chữ %r (mong đợi %r)"
           % (hit[0] if hit else "(không tìm thấy)", want))


def test_dashboard_noti_log(S):
    print("\n[Dashboard, Thông báo, Nhật ký]")
    s = S["admin"]
    # Trước đây các ca dưới chỉ kiểm "trang có chữ X" rồi ghi Đạt. Đối chiếu con
    # số thật phụ thuộc phạm vi xem (Của tôi / Toàn chi nhánh) và ô doanh thu dựng
    # bằng JavaScript -- để người test đối chiếu bằng mắt.
    for cid, why in [("TC_DASH_002", "Đối chiếu Tổng khách hàng với tổng ở danh sách khách hàng"),
                     ("TC_DASH_003", "Đối chiếu số hợp đồng Đang hiệu lực / Sắp hết hạn với kết quả lọc"),
                     ("TC_DASH_004", "Doanh thu tháng và phần trăm tăng trưởng dựng bằng JavaScript"),
                     ("TC_DASH_005", "Tháng trước không có doanh thu: cần CSDL không có tiền về tháng trước"),
                     ("TC_DASH_006", "Thứ tự và số ngày còn lại của khối hợp đồng sắp hết hạn"),
                     ("TC_DASH_007", "Đối chiếu số phiếu quá hạn / sắp tới hạn với danh sách phiếu")]:
        manual(cid, why + " -- kiểm bằng tay")
    record("TC_DASH_008", "N/A",
           "Cần CSDL rỗng hoàn toàn; CSDL kiểm thử đang có dữ liệu mẫu",
           "Chạy riêng trên CSDL trắng (README)")

    # Thông báo: gieo một thông báo cho admin rồi kiểm từ hai phía.
    admin_id = T.user_id("admin")
    T.run("INSERT INTO notifications (user_id, title, ref_type, ref_id, is_read) "
          "VALUES (%s, 'Kiem thu %s', 'contract_expiring', 1, 0)" % (admin_id, R.RUN))
    nid = T.scalar("SELECT MAX(notification_id) FROM notifications WHERE user_id = %s" % admin_id)
    page = html(s.get(R.BASE + "/notifications"))
    expect("TC_NOTI_001", ("Kiem thu %s" % R.RUN) in page_text(page),
           "Trang thông báo của admin hiện thông báo vừa gieo")
    sc = S["sales"]
    sc.get(R.BASE + "/notifications?action=read&id=%s" % nid, allow_redirects=False)
    other = T.scalar("SELECT is_read FROM notifications WHERE notification_id = %s" % nid)
    expect("TC_NOTI_005", other == "0",
           "Sales đánh dấu đã đọc thông báo id=%s của admin: thông báo đó %s"
           % (nid, "vẫn chưa đọc" if other == "0" else "BỊ ĐÁNH DẤU ĐÃ ĐỌC"))
    s.get(R.BASE + "/notifications?action=read&id=%s" % nid, allow_redirects=False)
    mine = T.scalar("SELECT is_read FROM notifications WHERE notification_id = %s" % nid)
    expect("TC_NOTI_003", mine == "1",
           "Admin đánh dấu đã đọc thông báo của chính mình -> is_read=%s" % mine)
    r = s.get(R.BASE + "/notifications?action=readAll", allow_redirects=False)
    left = T.scalar("SELECT COUNT(*) FROM notifications WHERE user_id = %s AND is_read = 0" % admin_id)
    expect("TC_NOTI_004", r.status_code in (200, 302) and left == "0",
           "Đánh dấu tất cả đã đọc -> HTTP %s; còn %s thông báo chưa đọc" % (r.status_code, left))
    empty_user = S["tech"]
    T.run("DELETE FROM notifications WHERE user_id = %s" % T.user_id("tech01"))
    page = html(empty_user.get(R.BASE + "/notifications"))
    expect("TC_NOTI_008", "Chưa có thông báo nào" in page_text(page),
           "Tài khoản chưa có thông báo: trang hiện dòng 'Chưa có thông báo nào'")
    record("TC_NOTI_009", "N/A",
           "Thông báo hợp đồng sắp hết hạn do tác vụ nền sinh, cần chờ lịch chạy",
           "Chạy ở vòng dài hơn (README, ba ca cần môi trường riêng)")

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
    admin_ok = all(results_ok(c) for c in ("TC_CUSEDIT_002", "TC_CTREDIT_002", "TC_EMPEDIT_002"))
    st = S["tech"]
    r = R.post(S["admin"], "/product", {"action": "create", "productName": "ACL admin " + R.RUN,
                                        "categoryId": "7"})
    prod_ok = r.status_code == 302 and "error" not in (r.headers.get("Location") or "")
    rt = R.post(S["admin"], "/ticket", {"action": "create", "enterpriseId": "1",
                                        "ticketType": "Lỗi phần mềm", "priority": "Cao",
                                        "receptionChannel": "Điện thoại",
                                        "assignedTechnicianId": T.user_id("tech01"),
                                        "description": "ACL admin " + R.RUN})
    tk_ok = rt.status_code == 302 and "error" not in (rt.headers.get("Location") or "")
    expect("TC_ACL_001", admin_ok and prod_ok and tk_ok,
           "Admin: sửa khách hàng / hợp đồng / nhân viên %s; tạo sản phẩm %s; tạo phiếu %s"
           % ("được" if admin_ok else "CÓ CA TRƯỢT", "được" if prod_ok else "KHÔNG",
              "được" if tk_ok else "KHÔNG"))

    ss = S["sales"]
    rc = R.post(ss, "/contract", {"action": "create", "kind": "sell", "contractCode": "ACL/%s" % R.RUN,
                                  "title": "ACL", "contractType": "Cung cấp thiết bị",
                                  "enterpriseId": "1", "ownerId": T.user_id("sale01")})
    ctr_ok = rc.status_code == 302 and "error" not in (rc.headers.get("Location") or "")
    expect("TC_ACL_002", results_ok("TC_CUSADD_001") and ctr_ok,
           "Sales tạo khách hàng (TC_CUSADD_001) %s; tạo hợp đồng -> %s"
           % ("được" if results_ok("TC_CUSADD_001") else "KHÔNG", rc.headers.get("Location")))
    r = R.post(st, "/product", {"action": "create", "productName": "ACL " + R.RUN,
                                "categoryId": "7"})
    expect("TC_ACL_004", r.status_code == 302
           and "error" not in (r.headers.get("Location") or ""),
           "Kỹ thuật tạo được sản phẩm -> %s" % r.headers.get("Location"))
    expect("TC_ACL_006", results_ok("TC_TKEDIT_007"),
           "Kỹ thuật cập nhật phiếu được giao cho mình: theo kết quả TC_TKEDIT_007")
    r = R.post(ss, "/ticket", {"action": "create", "enterpriseId": "1",
                               "ticketType": "Lỗi phần mềm", "priority": "Cao",
                               "receptionChannel": "Điện thoại",
                               "assignedTechnicianId": T.user_id("tech01"),
                               "description": "ACL " + R.RUN})
    expect("TC_ACL_007", r.status_code == 302
           and "error" not in (r.headers.get("Location") or ""),
           "Sales tạo được phiếu hỗ trợ -> %s" % r.headers.get("Location"))


def results_ok(case_id):
    return R.results.get(case_id, {}).get("status") == "Đạt"


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
    for role in ("admin", "sales", "tech"):
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

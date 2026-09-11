#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sinh tài liệu Report 5.2 - Integration Test (Blackbox) theo mẫu SEP490.

Khác với tài liệu Unit Test, đây là kiểm thử hộp đen chạy tay trên giao diện
nên không sinh được từ mã nguồn. Nội dung test case nằm trong các file đặc tả
`blackbox/*.json`, script này chỉ lo trình bày ra đúng mẫu.

Cột "Kết quả thực tế" và "Trạng thái" để TRỐNG (Chưa chạy) cho tới khi có
người thực sự chạy - tài liệu không ghi "Đạt" cho thứ chưa ai bấm thử.

Chạy:  python tools/testdoc/gen_integration_blackbox.py [-o <đường dẫn .xlsx>]
Mặc định ghi ra <Documents>/POSCS_IntegrationTest_Blackbox.xlsx
"""

import collections
import datetime
import json
import pathlib
import sys

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = pathlib.Path(__file__).resolve().parents[2]
SPEC_DIR = pathlib.Path(__file__).with_name("blackbox")
RESULTS = pathlib.Path(__file__).with_name("blackbox_results.json")
DEFAULT_OUT = (pathlib.Path.home() / "Documents"
               / "POSCS_IntegrationTest_Blackbox.xlsx")

PROJECT_NAME = "POSCS - Point Of Sale & Customer Support System"
PROJECT_CODE = "POSCS"
DOC_CODE = "POSCS_IntegrationTest_v1.0"
CREATOR = "G82"
ISSUE_DATE = datetime.date.today()
NOT_RUN = "Chưa chạy"

# Thứ tự module trong tài liệu.
SPEC_ORDER = ["auth", "customer", "contract", "product", "ticket",
              "employee", "misc"]

FONT_NAME = "Times New Roman"
FONT_SIZE = 13
TITLE_SIZE = 16

THIN = Side(style="thin", color="FF999999")
BOX = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
HDR_FILL = PatternFill("solid", fgColor="FFD9E1F2")
LBL_FILL = PatternFill("solid", fgColor="FFF2F2F2")
TITLE_FONT = Font(name=FONT_NAME, bold=True, size=TITLE_SIZE)
BOLD = Font(name=FONT_NAME, bold=True, size=FONT_SIZE)
BASE = Font(name=FONT_NAME, size=FONT_SIZE)
WRAP = Alignment(wrap_text=True, vertical="top")
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)

CASE_HEADERS = [
    ("Mã test case", 16),
    ("Mô tả test case", 34),
    ("Các bước thực hiện", 46),
    ("Tiền điều kiện", 28),
    ("Dữ liệu kiểm thử", 32),
    ("Hậu điều kiện", 26),
    ("Kết quả mong đợi", 42),
    ("Kết quả thực tế", 26),
    ("Trạng thái", 13),
    ("Người thực thi", 15),
    ("Ngày thực thi", 14),
    ("Ghi chú", 20),
    ("KQ thực tế vòng 2", 26),
    ("Trạng thái", 13),
    ("Người thực thi", 15),
]


def load_results():
    """Kết quả chạy thật do run_blackbox.py ghi lại. Không có thì để trống."""
    if not RESULTS.is_file():
        print("  Chua co %s -- moi test case de trang thai \"%s\""
              % (RESULTS.name, NOT_RUN))
        return {}
    data = json.loads(RESULTS.read_text(encoding="utf-8"))
    print("  Doc %d ket qua chay that tu %s" % (len(data), RESULTS.name))
    return data


def load_specs():
    """Đọc đặc tả test case, giữ đúng thứ tự module trong SPEC_ORDER."""
    if not SPEC_DIR.is_dir():
        sys.exit("Khong thay thu muc dac ta: %s" % SPEC_DIR)
    functions = []
    for name in SPEC_ORDER:
        path = SPEC_DIR / ("%s.json" % name)
        if not path.is_file():
            print("  BO QUA - chua co %s" % path.name)
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        for fn in data["functions"]:
            fn["module"] = data["module"]
            functions.append(fn)
    if not functions:
        sys.exit("Khong doc duoc chuc nang nao trong %s" % SPEC_DIR)
    return functions


def check(functions):
    """Bắt lỗi đặc tả trước khi ghi file, thay vì để lọt vào tài liệu nộp."""
    problems = []
    seen_sheet, seen_id = set(), set()
    for fn in functions:
        sheet = fn["sheet"]
        if len(sheet) > 31:
            problems.append("Ten sheet qua 31 ky tu: %s" % sheet)
        if sheet in seen_sheet:
            problems.append("Trung ten sheet: %s" % sheet)
        seen_sheet.add(sheet)
        if not fn.get("cases"):
            problems.append("Chuc nang khong co test case: %s" % sheet)
        for i, case in enumerate(fn.get("cases", []), start=1):
            cid = "%s_%03d" % (fn["prefix"], i)
            if cid in seen_id:
                problems.append("Trung ma test case: %s" % cid)
            seen_id.add(cid)
            for field in ("d", "s", "e"):
                if not case.get(field):
                    problems.append("%s thieu truong '%s'" % (cid, field))
    return problems


def put(ws, row, col, value, font=None, fill=None, align=None, border=True):
    cell = ws.cell(row=row, column=col, value=value)
    cell.font = font or BASE
    if fill:
        cell.fill = fill
    if align:
        cell.alignment = align
    if border:
        cell.border = BOX
    return cell


def build_cover(wb):
    ws = wb.create_sheet("Trang bìa")
    for col, width in zip("ABCDEF", (26, 46, 16, 16, 22, 30)):
        ws.column_dimensions[col].width = width
    ws.merge_cells("B2:E2")
    put(ws, 2, 2, "TÀI LIỆU KIỂM THỬ TÍCH HỢP (HỘP ĐEN)",
        font=TITLE_FONT, align=CENTER, border=False)
    rows = [("Tên dự án", PROJECT_NAME, "Người lập", CREATOR),
            ("Mã dự án", PROJECT_CODE, "Ngày phát hành", ISSUE_DATE),
            ("Mã tài liệu", DOC_CODE, "Phiên bản", "1.0")]
    for i, (label, value, label2, value2) in enumerate(rows, start=4):
        put(ws, i, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 2, value)
        put(ws, i, 5, label2, font=BOLD, fill=LBL_FILL)
        put(ws, i, 6, value2)
    put(ws, 9, 1, "Lịch sử thay đổi", font=BOLD, border=False)
    for j, head in enumerate(["Ngày hiệu lực", "Phiên bản", "Mục thay đổi",
                              "*A,D,M", "Mô tả thay đổi", "Tham chiếu"], 1):
        put(ws, 10, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    put(ws, 11, 1, ISSUE_DATE)
    put(ws, 11, 2, "1.0")
    put(ws, 11, 3, "Toàn bộ")
    put(ws, 11, 4, "A")
    put(ws, 11, 5, "Tạo mới tài liệu kiểm thử tích hợp hộp đen")
    put(ws, 11, 6, "")
    return ws


def build_index(wb, functions):
    ws = wb.create_sheet("Danh sách chức năng")
    for col, width in zip("ABCDEF", (7, 26, 34, 30, 52, 40)):
        ws.column_dimensions[col].width = width
    put(ws, 1, 3, "DANH SÁCH CHỨC NĂNG KIỂM THỬ", font=TITLE_FONT, border=False)
    info = [("Tên dự án", PROJECT_NAME),
            ("Mã dự án", PROJECT_CODE),
            ("Môi trường thực thi kiểm thử",
             "1. Apache Tomcat 11 (triển khai file WAR của POSCS)\n"
             "2. MySQL 8.0 trở lên, nạp sẵn db/schema.sql và dữ liệu mẫu\n"
             "3. Trình duyệt Chrome / Edge bản mới nhất\n"
             "4. Tài khoản thuộc đủ 4 vai trò: Admin, Sales, Kỹ thuật, CSKH")]
    for i, (label, value) in enumerate(info, start=3):
        put(ws, i, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 3, value, align=WRAP)
    for j, head in enumerate(["STT", "Module", "Tên chức năng", "Tên sheet",
                              "Mô tả", "Tiền điều kiện"], 1):
        put(ws, 8, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    for no, fn in enumerate(functions, start=1):
        r = 8 + no
        put(ws, r, 1, no, align=CENTER)
        put(ws, r, 2, fn["module"], align=WRAP)
        cell = put(ws, r, 3, fn["name"], align=WRAP)
        cell.hyperlink = "#'%s'!A1" % fn["sheet"]
        cell.font = Font(name=FONT_NAME, size=FONT_SIZE,
                         color="FF0563C1", underline="single")
        put(ws, r, 4, fn["sheet"], align=WRAP)
        put(ws, r, 5, fn.get("description", ""), align=WRAP)
        put(ws, r, 6, fn.get("precondition", ""), align=WRAP)
    return ws


def tally(fn, results):
    """(đạt, trượt, chưa chạy, n/a) của một chức năng theo kết quả thật."""
    counts = collections.Counter()
    for i in range(1, len(fn["cases"]) + 1):
        cid = "%s_%03d" % (fn["prefix"], i)
        counts[results.get(cid, {}).get("status", NOT_RUN)] += 1
    return (counts["Đạt"], counts["Trượt"], counts[NOT_RUN], counts["N/A"])


def build_statistics(wb, functions, results):
    ws = wb.create_sheet("Thống kê")
    for col, width in zip("ABCDEFGH", (7, 24, 36, 11, 11, 13, 9, 18)):
        ws.column_dimensions[col].width = width
    put(ws, 1, 2, "THỐNG KÊ KẾT QUẢ KIỂM THỬ", font=TITLE_FONT, border=False)
    info = [("Tên dự án", PROJECT_NAME, "Người lập", CREATOR),
            ("Mã dự án", PROJECT_CODE, "Người rà soát/phê duyệt", ""),
            ("Mã tài liệu", "POSCS_Test Report_v1.0", "Ngày phát hành",
             ISSUE_DATE)]
    for i, (label, value, label2, value2) in enumerate(info, start=3):
        put(ws, i, 2, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 3, value)
        put(ws, i, 5, label2, font=BOLD, fill=LBL_FILL)
        put(ws, i, 7, value2)
    put(ws, 6, 2, "Ghi chú", font=BOLD, fill=LBL_FILL)
    ran = sum(1 for v in results.values() if v.get("status") != NOT_RUN)
    put(ws, 6, 3,
        ("Vòng 1: %d test case đã chạy thật trên bản triển khai Tomcat + MySQL "
         "(lượt tự động qua HTTP). Số còn lại ở trạng thái \"%s\", cần chạy tay "
         "trên giao diện rồi điền kết quả." % (ran, NOT_RUN)) if results else
        ("Toàn bộ test case đang ở trạng thái \"%s\" - điền kết quả sau mỗi "
         "vòng chạy tay." % NOT_RUN), align=WRAP)
    for j, head in enumerate(["STT", "Module", "Chức năng", "Đạt", "Trượt",
                              NOT_RUN, "N/A", "Tổng số test case"], 2):
        put(ws, 9, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    row = 10
    for no, fn in enumerate(functions, start=1):
        n = len(fn["cases"])
        passed, failed, notrun, na = tally(fn, results)
        put(ws, row, 2, no, align=CENTER)
        put(ws, row, 3, fn["module"], align=WRAP)
        put(ws, row, 4, fn["name"], align=WRAP)
        for j, value in enumerate([passed, failed, notrun, na, n], start=5):
            put(ws, row, j, value, align=CENTER)
        row += 1
    put(ws, row, 4, "Tổng cộng", font=BOLD, fill=LBL_FILL)
    for j in range(5, 10):
        col = get_column_letter(j)
        put(ws, row, j, "=SUM(%s10:%s%d)" % (col, col, row - 1),
            font=BOLD, fill=LBL_FILL, align=CENTER)
    total = row
    put(ws, row + 2, 3, "Độ bao phủ kiểm thử", font=BOLD)
    put(ws, row + 2, 5, "=IF(I%d=0,0,100)" % total, align=CENTER)
    put(ws, row + 2, 6, "%", border=False)
    put(ws, row + 3, 3, "Độ bao phủ thành công", font=BOLD)
    put(ws, row + 3, 5, "=IF(I%d=0,0,ROUND(E%d/I%d*100,1))"
        % (total, total, total), align=CENTER)
    put(ws, row + 3, 6, "%", border=False)
    return ws


def build_function_sheet(wb, fn, results):
    ws = wb.create_sheet(fn["sheet"])
    for j, (_, width) in enumerate(CASE_HEADERS, start=1):
        ws.column_dimensions[get_column_letter(j)].width = width

    n = len(fn["cases"])
    info = [
        ("Tên dự án", PROJECT_NAME),
        ("Module", fn["module"]),
        ("Tên chức năng", fn["name"]),
        ("Màn hình / đường dẫn", fn.get("screen", "")),
        ("Người lập", CREATOR),
        ("Ngày lập", ISSUE_DATE),
        ("Người rà soát", ""),
        ("Ngày rà soát", ""),
        ("Tiền điều kiện chung", fn.get("precondition", "")),
        ("Số lượng test case", n),
    ]
    for r, (label, value) in enumerate(info, start=1):
        put(ws, r, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, r, 2, value, align=WRAP)
        put(ws, r, 3, None)
        ws.merge_cells(start_row=r, start_column=2, end_row=r, end_column=3)

    passed, failed, notrun, na = tally(fn, results)
    res = len(info) + 2
    put(ws, res, 1, "Kết quả chạy", font=BOLD, fill=HDR_FILL, align=CENTER)
    for j, head in enumerate(["Đạt", "Trượt", NOT_RUN, "N/A"], start=2):
        put(ws, res, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    put(ws, res + 1, 1, "Vòng 1", font=BOLD, fill=LBL_FILL)
    for j, value in enumerate([passed, failed, notrun, na], start=2):
        put(ws, res + 1, j, value, align=CENTER)
    put(ws, res + 2, 1, "Vòng 2", font=BOLD, fill=LBL_FILL)
    for j, value in enumerate([0, 0, n, 0], start=2):
        put(ws, res + 2, j, value, align=CENTER)

    head = res + 4
    for j, (label, _) in enumerate(CASE_HEADERS, start=1):
        put(ws, head, j, label, font=BOLD, fill=HDR_FILL, align=CENTER)

    for i, case in enumerate(fn["cases"], start=1):
        r = head + i
        steps = case["s"]
        steps = "\n".join("%d. %s" % (k, t) for k, t in enumerate(steps, 1)) \
            if isinstance(steps, list) else steps
        cid = "%s_%03d" % (fn["prefix"], i)
        got = results.get(cid)
        note = case.get("n", "")
        if got and got.get("note"):
            note = (note + " | " if note else "") + got["note"]
        values = [
            cid,
            case["d"],
            steps,
            case.get("p", fn.get("precondition", "")),
            case.get("t", "-"),
            case.get("o", "-"),
            case["e"],
            got["actual"] if got else "",
            got["status"] if got else NOT_RUN,
            "Tự động (run_blackbox.py)" if got else "",
            ISSUE_DATE if got else "",
            note, "", NOT_RUN, "",
        ]
        for j, value in enumerate(values, start=1):
            put(ws, r, j, value,
                align=CENTER if j in (1, 9, 14) else WRAP)
    ws.freeze_panes = ws.cell(row=head + 1, column=3).coordinate
    return ws


def apply_font(wb):
    normal = wb._named_styles["Normal"]
    normal.font = Font(name=FONT_NAME, size=FONT_SIZE)
    for ws in wb.worksheets:
        for row in ws.iter_rows():
            for cell in row:
                old = cell.font
                if old.name != FONT_NAME:
                    cell.font = Font(name=FONT_NAME,
                                     size=old.size or FONT_SIZE,
                                     bold=old.bold, italic=old.italic,
                                     underline=old.underline, color=old.color)


def parse_out_path(argv):
    if len(argv) >= 2 and argv[0] in ("-o", "--out"):
        return pathlib.Path(argv[1]).expanduser()
    if argv:
        sys.exit("Tham so khong hop le. Dung: gen_integration_blackbox.py "
                 "[-o <duong dan .xlsx>]")
    return DEFAULT_OUT


def main(argv=()):
    out = parse_out_path(list(argv))
    functions = load_specs()
    results = load_results()
    problems = check(functions)
    if problems:
        print("DAC TA CO LOI - chua ghi file:")
        for item in problems:
            print("  -", item)
        sys.exit(1)

    wb = Workbook()
    wb.remove(wb.active)
    build_cover(wb)
    build_index(wb, functions)
    build_statistics(wb, functions, results)
    for fn in functions:
        build_function_sheet(wb, fn, results)
    apply_font(wb)
    out.parent.mkdir(parents=True, exist_ok=True)
    wb.save(out)

    by_module = collections.Counter()
    for fn in functions:
        by_module[fn["module"]] += len(fn["cases"])
    status_counts = collections.Counter()
    for fn in functions:
        p_, f_, n_, a_ = tally(fn, results)
        status_counts.update({"Đạt": p_, "Trượt": f_, NOT_RUN: n_, "N/A": a_})
    print("Da ghi: %s" % out)
    print("  %d chuc nang, %d test case" % (len(functions), sum(by_module.values())))
    print("  Vong 1: Dat %d | Truot %d | %s %d | N/A %d"
          % (status_counts["Đạt"], status_counts["Trượt"], NOT_RUN,
             status_counts[NOT_RUN], status_counts["N/A"]))
    for module, count in by_module.items():
        print("    %-28s %3d" % (module, count))


if __name__ == "__main__":
    main(sys.argv[1:])

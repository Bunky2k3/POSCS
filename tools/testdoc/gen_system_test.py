#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sinh tài liệu Report 5.3 - System Test theo mẫu SEP490.

Khác Report 5.2: 5.2 kiểm từng chức năng một cách độc lập, còn 5.3 kiểm các
LUỒNG NGHIỆP VỤ chạy xuyên nhiều màn hình và nhiều vai trò — thứ chỉ hỏng khi
ghép các mảnh lại với nhau. Mỗi sheet là một luồng, các bước trong luồng phụ
thuộc nhau theo đúng thứ tự.

Nội dung nằm ở `systemtest/*.json`; kết quả chạy thật ở `systemtest_results.json`
(do run_systemtest.py ghi). Test case chưa chạy giữ trạng thái "Chưa chạy".

Chạy:  python tools/testdoc/gen_system_test.py [-o <đường dẫn .xlsx>]
"""

import collections
import datetime
import json
import pathlib
import sys

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

SPEC_DIR = pathlib.Path(__file__).with_name("systemtest")
RESULTS = pathlib.Path(__file__).with_name("systemtest_results.json")
DEFAULT_OUT = pathlib.Path.home() / "Documents" / "POSCS_SystemTest.xlsx"

PROJECT_NAME = "POSCS - Point Of Sale & Customer Support System"
PROJECT_CODE = "POSCS"
DOC_CODE = "POSCS_SystemTest_v1.0"
CREATOR = "G82"
ISSUE_DATE = datetime.date.today()
NOT_RUN = "Chưa chạy"
ROUNDS = 3

SPEC_ORDER = ["auth", "customer_contract", "support", "product", "access"]

FONT_NAME = "Times New Roman"
FONT_SIZE = 13
TITLE_SIZE = 16

THIN = Side(style="thin", color="FF999999")
BOX = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
HDR_FILL = PatternFill("solid", fgColor="FFD9E1F2")
LBL_FILL = PatternFill("solid", fgColor="FFF2F2F2")
SECTION_FILL = PatternFill("solid", fgColor="FFFFF2CC")
TITLE_FONT = Font(name=FONT_NAME, bold=True, size=TITLE_SIZE)
BOLD = Font(name=FONT_NAME, bold=True, size=FONT_SIZE)
BASE = Font(name=FONT_NAME, size=FONT_SIZE)
WRAP = Alignment(wrap_text=True, vertical="top")
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)

CASE_HEADERS = [("Mã test case", 17), ("Mô tả test case", 36),
                ("Các bước thực hiện", 50), ("Kết quả mong đợi", 44),
                ("Tiền điều kiện", 28)]
ROUND_HEADERS = [("Vòng %d", 12), ("Ngày chạy", 13), ("Người chạy", 14)]
NOTE_HEADER = ("Ghi chú", 26)


def load_results():
    if not RESULTS.is_file():
        print("  Chua co %s -- moi test case de \"%s\"" % (RESULTS.name, NOT_RUN))
        return {}
    data = json.loads(RESULTS.read_text(encoding="utf-8"))
    print("  Doc %d ket qua chay that tu %s" % (len(data), RESULTS.name))
    return data


def load_specs():
    if not SPEC_DIR.is_dir():
        sys.exit("Khong thay thu muc dac ta: %s" % SPEC_DIR)
    flows = []
    for name in SPEC_ORDER:
        path = SPEC_DIR / ("%s.json" % name)
        if not path.is_file():
            print("  BO QUA - chua co %s" % path.name)
            continue
        flows.append(json.loads(path.read_text(encoding="utf-8")))
    if not flows:
        sys.exit("Khong doc duoc luong nao trong %s" % SPEC_DIR)
    return flows


def cases_of(flow):
    """Trả về [(mã test case, case, tên nhóm bước)] theo đúng thứ tự trong luồng."""
    out, index = [], 0
    for section in flow["sections"]:
        for case in section["cases"]:
            index += 1
            out.append(("%s_%03d" % (flow["prefix"], index), case, section["name"]))
    return out


def check(flows):
    problems, seen_sheet, seen_id = [], set(), set()
    for flow in flows:
        if len(flow["sheet"]) > 31:
            problems.append("Ten sheet qua 31 ky tu: %s" % flow["sheet"])
        if flow["sheet"] in seen_sheet:
            problems.append("Trung ten sheet: %s" % flow["sheet"])
        seen_sheet.add(flow["sheet"])
        items = cases_of(flow)
        if not items:
            problems.append("Luong khong co test case: %s" % flow["sheet"])
        for cid, case, _ in items:
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


def tally(flow, results):
    counts = collections.Counter()
    for cid, _, _ in cases_of(flow):
        counts[results.get(cid, {}).get("status", NOT_RUN)] += 1
    return counts


def build_cover(wb):
    ws = wb.create_sheet("Trang bìa")
    for col, width in zip("ABCDEF", (26, 46, 16, 16, 22, 30)):
        ws.column_dimensions[col].width = width
    ws.merge_cells("B2:E2")
    put(ws, 2, 2, "TÀI LIỆU KIỂM THỬ HỆ THỐNG", font=TITLE_FONT, align=CENTER,
        border=False)
    for i, (label, value, label2, value2) in enumerate(
            [("Tên dự án", PROJECT_NAME, "Người lập", CREATOR),
             ("Mã dự án", PROJECT_CODE, "Ngày phát hành", ISSUE_DATE),
             ("Mã tài liệu", DOC_CODE, "Phiên bản", "1.0")], start=4):
        put(ws, i, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 2, value)
        put(ws, i, 5, label2, font=BOLD, fill=LBL_FILL)
        put(ws, i, 6, value2)
    put(ws, 9, 1, "Lịch sử thay đổi", font=BOLD, border=False)
    for j, head in enumerate(["Ngày hiệu lực", "Phiên bản", "Mục thay đổi",
                              "*A,D,M", "Mô tả thay đổi", "Tham chiếu"], 1):
        put(ws, 10, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    for j, value in enumerate([ISSUE_DATE, "1.0", "Toàn bộ", "A",
                               "Tạo mới tài liệu kiểm thử hệ thống", ""], 1):
        put(ws, 11, j, value)
    return ws


def build_index(wb, flows):
    ws = wb.create_sheet("Danh sách luồng")
    for col, width in zip("ABCDE", (7, 34, 26, 62, 44)):
        ws.column_dimensions[col].width = width
    put(ws, 1, 2, "DANH SÁCH LUỒNG NGHIỆP VỤ KIỂM THỬ", font=TITLE_FONT,
        border=False)
    info = [("Tên dự án", PROJECT_NAME), ("Mã dự án", PROJECT_CODE),
            ("Môi trường thực thi kiểm thử",
             "1. Apache Tomcat 11 (triển khai WAR của POSCS)\n"
             "2. MySQL 8.0 trở lên, nạp db/schema.sql và tài khoản mẫu\n"
             "3. Trình duyệt Chrome / Edge bản mới nhất\n"
             "4. Tài khoản đủ 4 vai trò: Admin, Sales, Kỹ thuật, CSKH")]
    for i, (label, value) in enumerate(info, start=3):
        put(ws, i, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 2, value, align=WRAP)
    for j, head in enumerate(["STT", "Tên luồng", "Tên sheet", "Mô tả",
                              "Tiền điều kiện"], 1):
        put(ws, 8, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    for no, flow in enumerate(flows, start=1):
        r = 8 + no
        put(ws, r, 1, no, align=CENTER)
        cell = put(ws, r, 2, flow["name"], align=WRAP)
        cell.hyperlink = "#'%s'!A1" % flow["sheet"]
        cell.font = Font(name=FONT_NAME, size=FONT_SIZE, color="FF0563C1",
                         underline="single")
        put(ws, r, 3, flow["sheet"], align=WRAP)
        put(ws, r, 4, flow.get("description", ""), align=WRAP)
        put(ws, r, 5, flow.get("precondition", ""), align=WRAP)
    return ws


def build_statistics(wb, flows, results):
    ws = wb.create_sheet("Thống kê")
    for col, width in zip("ABCDEFGH", (7, 36, 11, 11, 13, 9, 18, 10)):
        ws.column_dimensions[col].width = width
    put(ws, 1, 2, "THỐNG KÊ KẾT QUẢ KIỂM THỬ HỆ THỐNG", font=TITLE_FONT,
        border=False)
    for i, (label, value, label2, value2) in enumerate(
            [("Tên dự án", PROJECT_NAME, "Người lập", CREATOR),
             ("Mã dự án", PROJECT_CODE, "Người rà soát/phê duyệt", ""),
             ("Mã tài liệu", "POSCS_Test Report_v1.0", "Ngày phát hành",
              ISSUE_DATE)], start=3):
        put(ws, i, 2, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 3, value)
        put(ws, i, 5, label2, font=BOLD, fill=LBL_FILL)
        put(ws, i, 7, value2)
    ran = sum(1 for v in results.values() if v.get("status") != NOT_RUN)
    put(ws, 6, 2, "Ghi chú", font=BOLD, fill=LBL_FILL)
    put(ws, 6, 3,
        ("Vòng 1: %d test case đã chạy thật trên bản triển khai Tomcat + MySQL. "
         "Vòng 2 và 3 chạy lại sau khi sửa lỗi." % ran) if results
        else "Toàn bộ test case đang ở trạng thái \"%s\"." % NOT_RUN, align=WRAP)
    for j, head in enumerate(["STT", "Luồng nghiệp vụ", "Đạt", "Trượt", NOT_RUN,
                              "N/A", "Tổng số test case"], 2):
        put(ws, 9, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    row = 10
    for no, flow in enumerate(flows, start=1):
        counts = tally(flow, results)
        total = sum(counts.values())
        put(ws, row, 2, no, align=CENTER)
        put(ws, row, 3, flow["name"], align=WRAP)
        for j, value in enumerate([counts["Đạt"], counts["Trượt"],
                                   counts[NOT_RUN], counts["N/A"], total],
                                  start=4):
            put(ws, row, j, value, align=CENTER)
        row += 1
    put(ws, row, 3, "Tổng cộng", font=BOLD, fill=LBL_FILL)
    for j in range(4, 9):
        col = get_column_letter(j)
        put(ws, row, j, "=SUM(%s10:%s%d)" % (col, col, row - 1),
            font=BOLD, fill=LBL_FILL, align=CENTER)
    total_row = row
    put(ws, row + 2, 3, "Độ bao phủ kiểm thử", font=BOLD)
    put(ws, row + 2, 4, "=IF(H%d=0,0,ROUND((H%d-F%d)/H%d*100,1))"
        % (total_row, total_row, total_row, total_row), align=CENTER)
    put(ws, row + 2, 5, "%", border=False)
    put(ws, row + 3, 3, "Độ bao phủ thành công", font=BOLD)
    put(ws, row + 3, 4, "=IF(H%d=0,0,ROUND(D%d/H%d*100,1))"
        % (total_row, total_row, total_row), align=CENTER)
    put(ws, row + 3, 5, "%", border=False)
    return ws


def build_flow_sheet(wb, flow, results):
    ws = wb.create_sheet(flow["sheet"])
    widths = [w for _, w in CASE_HEADERS]
    for _ in range(ROUNDS):
        widths += [w for _, w in ROUND_HEADERS]
    widths.append(NOTE_HEADER[1])
    for j, width in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(j)].width = width

    items = cases_of(flow)
    counts = tally(flow, results)

    info = [("Luồng nghiệp vụ", flow["name"]),
            ("Yêu cầu kiểm thử", flow.get("description", "")),
            ("Tiền điều kiện chung", flow.get("precondition", "")),
            ("Vai trò tham gia", flow.get("roles", "")),
            ("Số lượng test case", len(items))]
    for r, (label, value) in enumerate(info, start=1):
        put(ws, r, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, r, 2, value, align=WRAP)
        put(ws, r, 3, None)
        ws.merge_cells(start_row=r, start_column=2, end_row=r, end_column=3)

    res = len(info) + 2
    put(ws, res, 1, "Vòng chạy", font=BOLD, fill=HDR_FILL, align=CENTER)
    for j, head in enumerate(["Đạt", "Trượt", NOT_RUN, "N/A"], start=2):
        put(ws, res, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    for k in range(1, ROUNDS + 1):
        put(ws, res + k, 1, "Vòng %d" % k, font=BOLD, fill=LBL_FILL)
        values = ([counts["Đạt"], counts["Trượt"], counts[NOT_RUN], counts["N/A"]]
                  if k == 1 else [0, 0, len(items), 0])
        for j, value in enumerate(values, start=2):
            put(ws, res + k, j, value, align=CENTER)

    head = res + ROUNDS + 2
    col = 1
    for label, _ in CASE_HEADERS:
        put(ws, head, col, label, font=BOLD, fill=HDR_FILL, align=CENTER)
        col += 1
    for k in range(1, ROUNDS + 1):
        for label, _ in ROUND_HEADERS:
            put(ws, head, col, label % k if "%d" in label else label,
                font=BOLD, fill=HDR_FILL, align=CENTER)
            col += 1
    put(ws, head, col, NOTE_HEADER[0], font=BOLD, fill=HDR_FILL, align=CENTER)
    last_col = col

    row = head + 1
    current_section = None
    for cid, case, section in items:
        if section != current_section:
            current_section = section
            put(ws, row, 1, section, font=BOLD, fill=SECTION_FILL)
            for j in range(2, last_col + 1):
                put(ws, row, j, None, fill=SECTION_FILL)
            ws.merge_cells(start_row=row, start_column=1,
                           end_row=row, end_column=last_col)
            row += 1

        got = results.get(cid)
        steps = case["s"]
        steps = "\n".join("%d. %s" % (i, t) for i, t in enumerate(steps, 1)) \
            if isinstance(steps, list) else steps
        note = case.get("n", "")
        if got and got.get("note"):
            note = (note + " | " if note else "") + got["note"]

        values = [cid, case["d"], steps, case["e"],
                  case.get("p", flow.get("precondition", ""))]
        values += [got["status"] if got else NOT_RUN,
                   ISSUE_DATE if got else "", "Tự động" if got else ""]
        for _ in range(ROUNDS - 1):
            values += [NOT_RUN, "", ""]
        values.append((got["actual"] + (" | " + note if note else ""))
                      if got else note)
        for j, value in enumerate(values, start=1):
            put(ws, row, j, value, align=CENTER if j in (1, 6, 9, 12) else WRAP)
        row += 1

    ws.freeze_panes = ws.cell(row=head + 1, column=2).coordinate
    return ws


def apply_font(wb):
    normal = wb._named_styles["Normal"]
    normal.font = Font(name=FONT_NAME, size=FONT_SIZE)
    for ws in wb.worksheets:
        for row in ws.iter_rows():
            for cell in row:
                old = cell.font
                if old.name != FONT_NAME:
                    cell.font = Font(name=FONT_NAME, size=old.size or FONT_SIZE,
                                     bold=old.bold, italic=old.italic,
                                     underline=old.underline, color=old.color)


def main(argv=()):
    argv = list(argv)
    out = DEFAULT_OUT
    if len(argv) >= 2 and argv[0] in ("-o", "--out"):
        out = pathlib.Path(argv[1]).expanduser()

    flows = load_specs()
    results = load_results()
    problems = check(flows)
    if problems:
        print("DAC TA CO LOI - chua ghi file:")
        for item in problems:
            print("  -", item)
        sys.exit(1)

    wb = Workbook()
    wb.remove(wb.active)
    build_cover(wb)
    build_index(wb, flows)
    build_statistics(wb, flows, results)
    for flow in flows:
        build_flow_sheet(wb, flow, results)
    apply_font(wb)
    out.parent.mkdir(parents=True, exist_ok=True)
    wb.save(out)

    total = collections.Counter()
    for flow in flows:
        total.update(tally(flow, results))
    print("Da ghi: %s" % out)
    print("  %d luong, %d test case" % (len(flows), sum(total.values())))
    print("  Vong 1: Dat %d | Truot %d | %s %d | N/A %d"
          % (total["Đạt"], total["Trượt"], NOT_RUN, total[NOT_RUN], total["N/A"]))


if __name__ == "__main__":
    main(sys.argv[1:])

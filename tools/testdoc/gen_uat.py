#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sinh tài liệu Report 5.4 - User Acceptance Test theo mẫu SEP490.

Đây là bản KHÁCH HÀNG KÝ NGHIỆM THU, nên script không tự ý đánh dấu chấp nhận.
Quy tắc: mỗi mục checklist khai một danh sách mã test case làm căn cứ; ô "Đạt"
chỉ được tích khi TẤT CẢ mã đó đều Đạt trong kết quả chạy thật của Report 5.2
và 5.3. Mục không có căn cứ tự động (tốc độ, bố cục, tài liệu bàn giao...) để
trống cả hai ô cho khách hàng tự xác nhận.

Cột "Căn cứ" là phần thêm so với mẫu gốc — để người ký đối chiếu ngược về đúng
test case đã chạy. Không cần thì xoá cột G.

Chạy:  python tools/testdoc/gen_uat.py [-o <đường dẫn .xlsx>]
"""

import collections
import datetime
import json
import pathlib
import sys

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side

HERE = pathlib.Path(__file__).parent
CHECKLIST = HERE / "uat_checklist.json"
RESULT_FILES = [HERE / "blackbox_results.json", HERE / "systemtest_results.json"]
DEFAULT_OUT = pathlib.Path.home() / "Documents" / "POSCS_UserAcceptanceTest.xlsx"

PROJECT_NAME = "POSCS - Point Of Sale & Customer Support System"
PROJECT_CODE = "POSCS"
DOC_CODE = "POSCS_UserAcceptanceTest_v1.0"
CREATOR = "G82"
ISSUE_DATE = datetime.date.today()

FONT_NAME = "Times New Roman"
FONT_SIZE = 13
TITLE_SIZE = 16

THIN = Side(style="thin", color="FF999999")
BOX = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
HDR_FILL = PatternFill("solid", fgColor="FFD9E1F2")
LBL_FILL = PatternFill("solid", fgColor="FFF2F2F2")
MODULE_FILL = PatternFill("solid", fgColor="FFFFF2CC")
TITLE_FONT = Font(name=FONT_NAME, bold=True, size=TITLE_SIZE)
BOLD = Font(name=FONT_NAME, bold=True, size=FONT_SIZE)
BASE = Font(name=FONT_NAME, size=FONT_SIZE)
WRAP = Alignment(wrap_text=True, vertical="top")
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)


def load_results():
    merged = {}
    for path in RESULT_FILES:
        if path.is_file():
            data = json.loads(path.read_text(encoding="utf-8"))
            merged.update(data)
            print("  Doc %d ket qua tu %s" % (len(data), path.name))
        else:
            print("  CANH BAO: chua co %s" % path.name)
    return merged


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


def verdict(refs, results):
    """(dấu tích, mô tả căn cứ). Chỉ tích Đạt khi mọi test case căn cứ đều Đạt."""
    if not refs:
        return None, "Cần khách hàng xác nhận trực tiếp"
    missing = [r for r in refs if r not in results]
    failed = [r for r in refs if results.get(r, {}).get("status") == "Trượt"]
    not_run = [r for r in refs if results.get(r, {}).get("status") in ("Chưa chạy", "N/A")]
    if missing:
        return None, "Chưa đối chiếu được: %s" % ", ".join(missing[:3])
    if failed:
        return False, "Test case chưa đạt: %s" % ", ".join(failed)
    if not_run:
        return None, "Chưa chạy: %s" % ", ".join(not_run[:3])
    return True, ", ".join(refs)


def build(wb, items, results):
    ws = wb.create_sheet("Checklist nghiệm thu")
    for col, width in zip("ABCDEFGH", (5, 7, 26, 13, 66, 8, 8, 40)):
        ws.column_dimensions[col].width = width

    put(ws, 1, 2, "CHECKLIST NGHIỆM THU HỆ THỐNG", font=TITLE_FONT, border=False)
    info = [("Tên dự án", PROJECT_NAME, "Người lập", CREATOR),
            ("Mã dự án", PROJECT_CODE, "Người nghiệm thu", ""),
            ("Mã tài liệu", DOC_CODE, "Ngày phát hành", ISSUE_DATE)]
    for i, (label, value, label2, value2) in enumerate(info, start=3):
        put(ws, i, 2, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 3, value)
        put(ws, i, 5, label2, font=BOLD, fill=LBL_FILL)
        put(ws, i, 6, value2)
    put(ws, 6, 2, "Ghi chú", font=BOLD, fill=LBL_FILL)
    put(ws, 6, 3,
        "Ô \"Đạt\" chỉ được tích sẵn khi toàn bộ test case nêu ở cột Căn cứ đã "
        "chạy thật và đều Đạt (Report 5.2 và 5.3). Mục không có căn cứ tự động "
        "để trống cho bên nghiệm thu tự đánh giá.", align=WRAP)
    ws.merge_cells(start_row=6, start_column=3, end_row=6, end_column=8)

    for j, head in enumerate(["STT", "Nhóm chức năng", "Mã", "Nội dung nghiệm thu",
                              "Đạt", "Chưa đạt", "Căn cứ"], start=2):
        put(ws, 9, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)

    row = 10
    current_module = None
    counts = collections.Counter()
    for no, item in enumerate(items, start=1):
        if item["m"] != current_module:
            current_module = item["m"]
            put(ws, row, 2, current_module, font=BOLD, fill=MODULE_FILL)
            for j in range(3, 9):
                put(ws, row, j, None, fill=MODULE_FILL)
            ws.merge_cells(start_row=row, start_column=2, end_row=row, end_column=8)
            row += 1

        ok, evidence = verdict(item.get("ref"), results)
        counts["Đạt" if ok else ("Chưa đạt" if ok is False else "Chờ xác nhận")] += 1
        put(ws, row, 2, no, align=CENTER)
        put(ws, row, 3, item["m"], align=WRAP)
        put(ws, row, 4, "CL-%03d" % no, align=CENTER)
        put(ws, row, 5, item["c"], align=WRAP)
        put(ws, row, 6, "X" if ok is True else None, align=CENTER)
        put(ws, row, 7, "X" if ok is False else None, align=CENTER)
        put(ws, row, 8, evidence, align=WRAP)
        row += 1

    row += 1
    put(ws, row, 3, "Tổng kết", font=BOLD, fill=LBL_FILL)
    put(ws, row, 5, "%d mục đã có bằng chứng Đạt / %d mục chờ bên nghiệm thu "
                    "xác nhận / %d mục chưa đạt"
        % (counts["Đạt"], counts["Chờ xác nhận"], counts["Chưa đạt"]), align=WRAP)
    ws.merge_cells(start_row=row, start_column=5, end_row=row, end_column=8)

    row += 3
    put(ws, row, 3, "Đại diện nhóm phát triển", font=BOLD, align=CENTER, border=False)
    put(ws, row, 5, "Đại diện bên nghiệm thu", font=BOLD, align=CENTER, border=False)
    put(ws, row + 1, 3, "(ký, ghi rõ họ tên)", align=CENTER, border=False)
    put(ws, row + 1, 5, "(ký, ghi rõ họ tên)", align=CENTER, border=False)
    ws.freeze_panes = "A10"
    return counts


def build_cover(wb):
    ws = wb.create_sheet("Trang bìa")
    for col, width in zip("ABCDEF", (26, 46, 16, 16, 22, 30)):
        ws.column_dimensions[col].width = width
    ws.merge_cells("B2:E2")
    put(ws, 2, 2, "TÀI LIỆU NGHIỆM THU NGƯỜI DÙNG", font=TITLE_FONT,
        align=CENTER, border=False)
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
                               "Tạo mới checklist nghiệm thu", ""], 1):
        put(ws, 11, j, value)
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

    data = json.loads(CHECKLIST.read_text(encoding="utf-8"))
    items = data["items"]
    results = load_results()

    wb = Workbook()
    wb.remove(wb.active)
    build_cover(wb)
    counts = build(wb, items, results)
    apply_font(wb)
    out.parent.mkdir(parents=True, exist_ok=True)
    wb.save(out)

    print("Da ghi: %s" % out)
    print("  %d muc checklist | Dat %d | Cho xac nhan %d | Chua dat %d"
          % (len(items), counts["Đạt"], counts["Chờ xác nhận"], counts["Chưa đạt"]))


if __name__ == "__main__":
    main(sys.argv[1:])

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sinh tài liệu Report 5.1 - Unit Test (Function) theo mẫu SEP490.

Nguồn dữ liệu:
  - test/**/*Test.java          -> danh sách test case (tên method @Test)
  - build/test/results/*.xml    -> kết quả Passed/Failed/Untested THẬT của lần
                                   `ant test` gần nhất. Không có file này thì
                                   script dừng, để tài liệu không bao giờ ghi
                                   "Passed" cho thứ chưa từng chạy.

Quy ước đặt tên test trong repo: <method>_<điều kiện>_<kỳ vọng>, ví dụ
`insert_noRowAffected_rollsBackAndReturnsMinusOne`. Script tách tên này thành
dòng Condition và dòng Confirm của ma trận UTCID.

Chạy:  python tools/testdoc/gen_unittest_function.py [-o <đường dẫn .xlsx>]

File .xlsx là tài liệu nộp, KHÔNG nằm trong repo. Mặc định ghi ra
<thư mục Documents của người dùng>/POSCS_UnitTest_Function.xlsx.
"""

import collections
import datetime
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = pathlib.Path(__file__).resolve().parents[2]
TEST_DIR = ROOT / "test"
RESULT_DIR = ROOT / "build" / "test" / "results"
DEFAULT_OUT = pathlib.Path.home() / "Documents" / "POSCS_UnitTest_Function.xlsx"
GUIDELINE = pathlib.Path(__file__).with_name("guideline_unittest.txt")
GLOSSARY = pathlib.Path(__file__).with_name("vi_glossary.json")

PROJECT_NAME = "POSCS - Point Of Sale & Customer Support System"
PROJECT_CODE = "POSCS"
DOC_CODE = "POSCS_UnitTest_v1.0"
CREATOR = "G82"
ISSUE_DATE = datetime.date.today()

# Integration test không thuộc tài liệu 5.1 - chúng nằm ở Report 5.2.
EXCLUDED_TEST_CLASSES = {
    "ContractStatusIntegrationTest",
    "DaoSchemaIntegrationTest",
    "WriteAndTransactionIntegrationTest",
}

# Lớp test -> lớp production, khi tên không chỉ là bỏ hậu tố "Test".
TEST_CLASS_TO_PROD = {
    "ContractLookupEndpointTest": "ContractController",
    "PasswordResetFlowTest": "AuthenticationController",
}

# Tiền tố tên test -> method thật trong code production.
# Controller là servlet điều phối theo tham số `action`, nên tiền tố test là tên
# action; ở đây quy về đúng handler xử lý action đó.
ALIASES = {
    ("AuthenticationFilterTest", "get"): "doFilter",
    ("AuthenticationFilterTest", "post"): "doFilter",
    ("AuthenticationFilterTest", "everyResponse"): "doFilter",
    ("FileStorageTest", "imageExtensions"): "IMAGE_EXTENSIONS",
    ("LogFilesTest", "sizeText"): "getSizeText",
    ("TextRulesTest", "freeText"): "isSafeFreeText",
    ("TextRulesTest", "url"): "isSafeHttpUrl",

    ("AuthenticationControllerTest", "login"): "handleLogin",
    ("AuthenticationControllerTest", "logout"): "doGet",
    ("AuthenticationControllerTest", "viewProfile"): "handleViewProfile",
    ("AuthenticationControllerTest", "changePassword"): "handleChangePassword",
    ("AuthenticationControllerTest", "updateProfile"): "handleUpdateProfile",

    ("PasswordResetFlowTest", "forgotPassword"): "handleForgotPassword",
    ("PasswordResetFlowTest", "verifyOtp"): "handleVerifyOtp",
    ("PasswordResetFlowTest", "resendOtp"): "handleResendOtp",
    ("PasswordResetFlowTest", "resetPassword"): "handleResetPassword",

    ("ContractControllerTest", "view"): "showDetail",
    ("ContractControllerTest", "create"): "handleCreate",
    ("ContractControllerTest", "update"): "handleUpdate",
    ("ContractControllerTest", "delete"): "handleDelete",
    ("ContractControllerTest", "addProduct"): "handleAddProduct",
    ("ContractControllerTest", "removeProduct"): "handleRemoveProduct",
    ("ContractControllerTest", "formPages"): "showEditForm",
    ("ContractControllerTest", "createForm"): "showCreateForm",

    ("CustomerControllerTest", "view"): "showDetail",
    ("CustomerControllerTest", "create"): "handleCreate",
    ("CustomerControllerTest", "delete"): "handleDelete",
    ("CustomerControllerTest", "evaluate"): "handleEvaluate",
    ("CustomerControllerTest", "createForm"): "showCreateForm",
    ("CustomerControllerTest", "editForm"): "showEditForm",

    ("EmployeeControllerTest", "create"): "handleCreate",
    ("EmployeeControllerTest", "update"): "handleUpdate",
    ("EmployeeControllerTest", "toggleStatus"): "handleToggleStatus",
    ("EmployeeControllerTest", "sendAccount"): "handleSendAccount",

    ("ProductControllerTest", "view"): "showDetail",
    ("ProductControllerTest", "create"): "handleCreate",
    ("ProductControllerTest", "update"): "handleUpdate",
    ("ProductControllerTest", "delete"): "handleDelete",
    ("ProductControllerTest", "createForm"): "showCreateForm",
    ("ProductControllerTest", "editForm"): "showEditForm",

    ("SystemLogControllerTest", "view"): "showLog",

    ("TechnicalSupportTicketControllerTest", "view"): "showDetail",
    ("TechnicalSupportTicketControllerTest", "create"): "handleCreate",
    ("TechnicalSupportTicketControllerTest", "update"): "handleUpdate",
    ("TechnicalSupportTicketControllerTest", "delete"): "handleDelete",
    ("TechnicalSupportTicketControllerTest", "newForm"): "showCreateForm",
    ("TechnicalSupportTicketControllerTest", "editForm"): "showEditForm",
}

# Các lớp test đặt tên theo kịch bản chứ không theo method (endpoint chỉ có một
# lối vào duy nhất) - gom cả lớp vào một sheet.
WHOLE_CLASS = {
    "AddressControllerTest": "doGet",
    "DashboardControllerTest": "doGet",
    "NotificationControllerTest": "doGet",
    "UploadFileControllerTest": "doGet",
    "ContractLookupEndpointTest": "listByEnterpriseAsJson",
}

# Phân loại Normal / Abnormal / Boundary. Kiểm tra B trước vì nhiều tên test
# biên cũng chứa từ khoá nghe như bất thường ("empty", "noRow").
BOUNDARY_WORDS = (
    "empty", "boundary", "maximum", "minimum", "exactly", "limit", "zero",
    "toolong", "maxlength", "whitespace", "trailing", "leading", "single",
    "onerow", "overflow", "outofrange", "pagesize", "truncat", "firstpage",
    "lastpage", "atthe",
)
ABNORMAL_WORDS = (
    "null", "invalid", "wrong", "error", "fail", "missing", "mismatch",
    "reject", "notfound", "unknown", "deactivated", "denied", "traversal",
    "duplicate", "nonnumeric", "nonexist", "throw", "blank", "weak",
    "disallow", "unauthorized", "forbidden", "corrupt", "unsupported",
    "illegal", "locked", "expired", "orphan", "sqlerror", "notlogged",
    "noaction", "malformed", "injection", "markup", "svg", "crash",
    "without", "not", "no",
)

FONT_NAME = "Times New Roman"

THIN = Side(style="thin", color="FF999999")
BOX = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
HDR_FILL = PatternFill("solid", fgColor="FFD9E1F2")
LBL_FILL = PatternFill("solid", fgColor="FFF2F2F2")
TITLE_FONT = Font(name=FONT_NAME, bold=True, size=14)
BOLD = Font(name=FONT_NAME, bold=True)
BASE = Font(name=FONT_NAME)
WRAP = Alignment(wrap_text=True, vertical="top")
CENTER = Alignment(horizontal="center", vertical="center")

TEST_PATTERN = re.compile(
    r"@Test\s*(?:\([^)]*\)\s*)?(?:@[\w.]+(?:\([^)]*\))?\s*)*"
    r"public\s+void\s+(\w+)\s*\(")


def humanize(text):
    """`noRowAffected` -> `no row affected`."""
    if not text:
        return ""
    spaced = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", text)
    spaced = re.sub(r"(?<=[A-Z])(?=[A-Z][a-z])", " ", spaced)
    spaced = spaced.replace("_", " ").strip()
    return spaced[0].upper() + spaced[1:] if spaced else ""


# Động từ/cụm mở đầu phần KỲ VỌNG trong tên test. Dùng để tách tên chỉ có hai
# đoạn (`url_javascriptSchemeRejected`) thành điều kiện + kỳ vọng.
OUTCOME_WORDS = (
    "is", "are", "returns", "return", "throws", "rejected", "accepted",
    "fails", "passes", "keeps", "does", "stays", "becomes", "redirects",
    "forwards", "sends", "writes", "logs", "rolls", "commits", "defaults",
    "falls", "stops", "skips", "uses", "adds", "removes", "marks", "sets",
    "invalidates", "locks", "resets", "trims", "escapes", "sanitizes",
    "blocks", "allows", "denies", "shows", "hides", "still", "stores",
    "creates", "deletes", "updates", "inserts", "generates", "renders",
    "counts", "caches", "retries", "reuses", "propagates", "closes",
    "ignores", "treats", "maps", "binds", "serves", "streams", "exports",
    "increments", "leaves", "wraps", "reads", "loads", "clears", "keeps",
)


def split_two_part(tail):
    """`javascriptSchemeRejected` -> ("javascriptScheme", "rejected").

    Tên chỉ có hai đoạn gộp cả điều kiện lẫn kỳ vọng; cắt tại từ mở đầu kỳ
    vọng đầu tiên. Không tìm thấy thì coi như trường hợp hợp lệ mặc định.
    """
    tokens = re.findall(r"[A-Z]?[a-z0-9]+|[A-Z]+(?![a-z])", tail)
    for i, token in enumerate(tokens):
        if i > 0 and token.lower() in OUTCOME_WORDS:
            return "".join(tokens[:i]), "".join(tokens[i:])
    return "", tail


# Kỳ vọng cho thấy đầu vào bị từ chối -> đầu vào đó là bất thường. Cần tín hiệu
# này vì danh sách từ khoá đầu vào không thể phủ hết ("javascriptScheme",
# "dataScheme"... chẳng chứa từ nào nghe có vẻ sai).
REJECTION_WORDS = (
    "reject", "error", "fail", "false", "throw", "minusone", "notfound",
    "unauthorized", "denied", "block", "invalid", "rollsback", "doesnot",
    "notcalled", "notsaved", "skip", "ignore", "refuse", "abort",
)


def classify(condition_raw, expectation_raw=""):
    """Phân loại N/A/B chủ yếu theo ĐIỀU KIỆN ĐẦU VÀO.

    Phần kỳ vọng chỉ dùng làm tín hiệu phụ cho nhãn A: nếu đầu ra là "bị từ
    chối" thì đầu vào phải là bất thường. Đây vẫn là suy đoán từ tên test -
    người viết test nên rà lại cột Type trước khi nộp.
    """
    low = (condition_raw or "").lower()
    for word in BOUNDARY_WORDS:
        if word in low:
            return "B"
    for word in ABNORMAL_WORDS:
        if low and word in low:
            return "A"
    expect = (expectation_raw or "").lower()
    for word in REJECTION_WORDS:
        if word in expect:
            return "A"
    return "N"


def load_glossary():
    """Bản dịch tiếng Việt cho chuỗi điều kiện/kỳ vọng sinh từ tên test."""
    data = json.loads(GLOSSARY.read_text(encoding="utf-8"))
    return {k: v for k, v in data.items() if not k.startswith("_")}


VI = load_glossary()
MISSING = set()


def vi(text):
    """Dịch sang tiếng Việt; chưa có trong từ điển thì giữ nguyên và ghi nhận."""
    if not text:
        return text
    if text in VI:
        return VI[text]
    if re.search(r"[À-ỹ]", text):      # đã là tiếng Việt sẵn
        return text
    MISSING.add(text)
    return text


def precondition_for(package, source):
    """Điều kiện tiền đề thật của lớp test, suy ra từ chính mã nguồn test."""
    if "mock(" not in source and "@Mock" not in source:
        return "Không cần mock - hàm thuần, gọi trực tiếp"
    if package.endswith(".dao"):
        return ("Mock Connection/PreparedStatement/ResultSet (JdbcStub); "
                "không kết nối CSDL thật")
    if package.endswith(".controller"):
        return ("Mock HttpServletRequest/HttpServletResponse/HttpSession và "
                "các DAO liên quan bằng Mockito")
    return "Mock các phụ thuộc bằng Mockito"


def load_results():
    """(classname, testname) -> Passed | Failed | Untested."""
    if not RESULT_DIR.is_dir():
        sys.exit("Chua co ket qua test. Chay `ant test` truoc khi sinh tai lieu.\n"
                 "  Thieu thu muc: %s" % RESULT_DIR)
    out = {}
    for path in RESULT_DIR.glob("*.xml"):
        for tc in ET.parse(path).getroot().iter("testcase"):
            if tc.find("failure") is not None or tc.find("error") is not None:
                status = "Failed"
            elif tc.find("skipped") is not None:
                status = "Untested"
            else:
                status = "Passed"
            out[(tc.get("classname"), tc.get("name"))] = status
    if not out:
        sys.exit("Khong doc duoc test case nao trong %s" % RESULT_DIR)
    return out


def collect():
    """Trả về OrderedDict[(prod_class, method)] -> list[dict] test case."""
    results = load_results()
    groups = collections.OrderedDict()
    unknown = []
    for path in sorted(TEST_DIR.rglob("*Test.java")):
        test_class = path.stem
        if test_class in EXCLUDED_TEST_CLASSES:
            continue
        package = "poscs." + path.parent.name
        prod_class = TEST_CLASS_TO_PROD.get(test_class, test_class[:-4])
        source = path.read_text(encoding="utf-8", errors="replace")
        precondition = precondition_for(package, source)
        for match in TEST_PATTERN.finditer(source):
            test_name = match.group(1)
            parts = test_name.split("_")
            if test_class in WHOLE_CLASS:
                method = WHOLE_CLASS[test_class]
                condition_raw = parts[0]
                expect_raw = " ".join(parts[1:])
                condition = humanize(parts[0])
                expectation = humanize(expect_raw)
            else:
                method = ALIASES.get((test_class, parts[0]), parts[0])
                if len(parts) >= 3:
                    condition_raw = parts[1]
                    expect_raw = " ".join(parts[2:])
                    condition = humanize(parts[1])
                    expectation = humanize(expect_raw)
                elif len(parts) == 2:
                    condition_raw, expect_raw = split_two_part(parts[1])
                    condition = humanize(condition_raw)
                    expectation = humanize(expect_raw)
                    condition = condition or "Dữ liệu hợp lệ (trường hợp mặc định)"
                else:
                    condition_raw = expect_raw = ""
                    condition = "Dữ liệu hợp lệ (trường hợp mặc định)"
                    expectation = humanize(parts[0])
                    unknown.append(test_name)
            status = results.get(("%s.%s" % (package, test_class), test_name))
            if status is None:
                unknown.append("%s.%s (khong co trong ket qua junit)"
                               % (test_class, test_name))
                status = "Untested"
            groups.setdefault((prod_class, method), []).append({
                "test_class": test_class,
                "package": package,
                "test_name": test_name,
                "condition": vi(condition) or "Dữ liệu hợp lệ (trường hợp mặc định)",
                "expectation": vi(expectation) or "Thực thi đúng như đặc tả",
                "type": classify(condition_raw, expect_raw),
                "status": status,
                "precondition": precondition,
            })
    return groups, unknown


def sheet_names(groups):
    """Tên sheet Excel tối đa 31 ký tự, không trùng nhau."""
    names, used = {}, set()
    for key in groups:
        prod_class, method = key
        if len(prod_class) + 1 + len(method) <= 31:
            name = "%s_%s" % (prod_class, method)
        else:
            # Rút gọn tên lớp trước - trong cùng một lớp thì tên method mới là
            # phần phân biệt các sheet. Giữ lại hậu tố loại lớp dưới dạng viết
            # tắt, nếu không `...TicketController` và `...TicketDAO` cắt ra
            # trông y hệt nhau.
            base, tag = prod_class, ""
            for suffix, short in (("Controller", "Ctrl"), ("DAO", "DAO"),
                                  ("Filter", "Flt"), ("Service", "Svc")):
                if prod_class.endswith(suffix):
                    base, tag = prod_class[:-len(suffix)], short
                    break
            room = max(4, 31 - len(tag) - 1 - len(method))
            name = ("%s%s_%s" % (base[:room], tag, method))[:31]
        if name in used:
            for i in range(2, 100):
                suffix = "~%d" % i
                name = name[:31 - len(suffix)] + suffix
                if name not in used:
                    break
        used.add(name)
        names[key] = name
    return names


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


def build_guideline(wb):
    ws = wb.create_sheet("Hướng dẫn")
    ws.column_dimensions["A"].width = 130
    lines = GUIDELINE.read_text(encoding="utf-8").split("\x1e")
    for i, line in enumerate(lines, start=1):
        cell = ws.cell(row=i, column=1, value=line)
        cell.alignment = Alignment(wrap_text=True, vertical="top")
        if i == 1:
            cell.font = TITLE_FONT
        if "\n" in line:
            ws.row_dimensions[i].height = 15 * (line.count("\n") + 1)
    return ws


def build_cover(wb):
    ws = wb.create_sheet("Trang bìa")
    for col, width in zip("ABCDEF", (22, 40, 14, 14, 18, 26)):
        ws.column_dimensions[col].width = width
    ws.merge_cells("B2:E2")
    put(ws, 2, 2, "TÀI LIỆU UNIT TEST", font=TITLE_FONT, align=CENTER, border=False)
    rows = [
        ("Tên dự án", PROJECT_NAME, "Người lập", CREATOR),
        ("Mã dự án", PROJECT_CODE, "Ngày phát hành", ISSUE_DATE),
        ("Mã tài liệu", DOC_CODE, "Phiên bản", "1.0"),
    ]
    for i, (label, value, label2, value2) in enumerate(rows, start=4):
        put(ws, i, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 2, value)
        put(ws, i, 5, label2, font=BOLD, fill=LBL_FILL)
        put(ws, i, 6, value2)
    put(ws, 9, 1, "Lịch sử thay đổi", font=BOLD, border=False)
    headers = ["Ngày hiệu lực", "Phiên bản", "Mục thay đổi", "*A,D,M",
               "Mô tả thay đổi", "Tham chiếu"]
    for j, head in enumerate(headers, start=1):
        put(ws, 10, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    put(ws, 11, 1, ISSUE_DATE)
    put(ws, 11, 2, "1.0")
    put(ws, 11, 3, "Toàn bộ")
    put(ws, 11, 4, "A")
    put(ws, 11, 5, "Tạo mới tài liệu Unit Test cho POSCS")
    put(ws, 11, 6, "")
    return ws


def build_method_list(wb, groups, names):
    ws = wb.create_sheet("Danh sách hàm")
    for col, width in zip("ABCDEF", (6, 30, 30, 34, 52, 30)):
        ws.column_dimensions[col].width = width
    put(ws, 2, 3, "DANH SÁCH HÀM ĐƯỢC KIỂM THỬ", font=TITLE_FONT, border=False)
    info = [("Tên dự án", PROJECT_NAME),
            ("Mã dự án", PROJECT_CODE),
            ("Định mức test case / KLOC", 100),
            ("Môi trường thực thi kiểm thử",
             "1. JDK 21\n2. Apache Ant (NetBeans project)\n"
             "3. JUnit 4.13.2 + Mockito 5.23.0 (lib-test/)\n"
             "4. Lệnh chạy: ant test")]
    for i, (label, value) in enumerate(info, start=4):
        put(ws, i, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 3, value, align=WRAP)
    headers = ["STT", "Tên lớp", "Tên hàm", "Tên sheet",
               "Mô tả", "Tiền điều kiện"]
    for j, head in enumerate(headers, start=1):
        put(ws, 10, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    row = 11
    for no, (key, cases) in enumerate(groups.items(), start=1):
        prod_class, method = key
        name = names[key]
        put(ws, row, 1, no, align=CENTER)
        put(ws, row, 2, prod_class)
        cell = put(ws, row, 3, method)
        cell.hyperlink = "#'%s'!A1" % name
        cell.font = Font(name=FONT_NAME, color="FF0563C1", underline="single")
        put(ws, row, 4, name)
        put(ws, row, 5, "%d test case, nguồn: %s.java"
            % (len(cases), cases[0]["test_class"]), align=WRAP)
        put(ws, row, 6, cases[0]["precondition"], align=WRAP)
        row += 1
    return ws


def build_statistics(wb, groups, names):
    ws = wb.create_sheet("Thống kê")
    for col, width in zip("ABCDEFGHI", (6, 42, 10, 10, 12, 8, 8, 8, 16)):
        ws.column_dimensions[col].width = width
    put(ws, 2, 1, "BÁO CÁO KẾT QUẢ UNIT TEST", font=TITLE_FONT, border=False)
    info = [("Tên dự án", PROJECT_NAME, "Người lập", CREATOR),
            ("Mã dự án", PROJECT_CODE, "Người rà soát/phê duyệt", ""),
            ("Mã tài liệu", "POSCS_Test Report_v1.0", "Ngày phát hành", ISSUE_DATE)]
    for i, (label, value, label2, value2) in enumerate(info, start=4):
        put(ws, i, 1, label, font=BOLD, fill=LBL_FILL)
        put(ws, i, 2, value)
        put(ws, i, 4, label2, font=BOLD, fill=LBL_FILL)
        put(ws, i, 6, value2)
    put(ws, 7, 1, "Ghi chú", font=BOLD, fill=LBL_FILL)
    put(ws, 7, 2, "Kết quả lấy từ lần chạy `ant test` ngày %s" % ISSUE_DATE)
    headers = ["STT", "Sheet hàm", "Đạt", "Trượt", "Chưa chạy",
               "N", "A", "B", "Tổng số test case"]
    for j, head in enumerate(headers, start=1):
        put(ws, 11, j, head, font=BOLD, fill=HDR_FILL, align=CENTER)
    row = 12
    for no, (key, cases) in enumerate(groups.items(), start=1):
        counts = collections.Counter(c["status"] for c in cases)
        types = collections.Counter(c["type"] for c in cases)
        put(ws, row, 1, no, align=CENTER)
        put(ws, row, 2, names[key])
        for j, value in enumerate([counts["Passed"], counts["Failed"],
                                   counts["Untested"], types["N"], types["A"],
                                   types["B"], len(cases)], start=3):
            put(ws, row, j, value, align=CENTER)
        row += 1
    put(ws, row, 2, "Tổng cộng", font=BOLD, fill=LBL_FILL)
    for j in range(3, 10):
        col = get_column_letter(j)
        put(ws, row, j, "=SUM(%s12:%s%d)" % (col, col, row - 1),
            font=BOLD, fill=LBL_FILL, align=CENTER)
    total_row = row
    put(ws, row + 2, 2, "Độ bao phủ kiểm thử", font=BOLD)
    put(ws, row + 2, 5, "=IF(I%d=0,0,100)" % total_row, align=CENTER)
    put(ws, row + 2, 6, "%", border=False)
    put(ws, row + 3, 2, "Độ bao phủ thành công", font=BOLD)
    put(ws, row + 3, 5, "=IF(I%d=0,0,ROUND(C%d/I%d*100,1))"
        % (total_row, total_row, total_row), align=CENTER)
    put(ws, row + 3, 6, "%", border=False)
    return ws


def build_case_sheet(wb, key, cases, name):
    prod_class, method = key
    ws = wb.create_sheet(name)
    ws.column_dimensions["A"].width = 11
    ws.column_dimensions["B"].width = 22
    ws.column_dimensions["C"].width = 3
    ws.column_dimensions["D"].width = 62
    for i in range(len(cases)):
        ws.column_dimensions[get_column_letter(5 + i)].width = 10

    last = 4 + len(cases)
    counts = collections.Counter(c["status"] for c in cases)
    types = collections.Counter(c["type"] for c in cases)

    put(ws, 1, 1, "Lớp kiểm thử", font=BOLD, fill=LBL_FILL)
    put(ws, 1, 3, prod_class)
    put(ws, 1, 4, "Hàm kiểm thử", font=BOLD, fill=LBL_FILL)
    put(ws, 1, 5, method)
    put(ws, 2, 1, "Người lập", font=BOLD, fill=LBL_FILL)
    put(ws, 2, 3, CREATOR)
    put(ws, 2, 4, "Người thực thi", font=BOLD, fill=LBL_FILL)
    put(ws, 2, 5, CREATOR)
    put(ws, 3, 1, "Yêu cầu kiểm thử", font=BOLD, fill=LBL_FILL)
    put(ws, 3, 3, "Kiểm thử đơn vị hàm %s() của lớp %s (mã nguồn test: %s.java)"
        % (method, prod_class, cases[0]["test_class"]), align=WRAP)

    labels = ["Đạt", "Trượt", "Chưa chạy", "N", "A", "B", "Tổng số test case"]
    values = [counts["Passed"], counts["Failed"], counts["Untested"],
              types["N"], types["A"], types["B"], len(cases)]
    for j, (label, value) in enumerate(zip(labels, values)):
        put(ws, 4, 1 + j, label, font=BOLD, fill=HDR_FILL, align=CENTER)
        put(ws, 5, 1 + j, value, align=CENTER)

    row = 7
    put(ws, row, 4, "UTCID", font=BOLD, fill=HDR_FILL, align=CENTER)
    for i in range(len(cases)):
        put(ws, row, 5 + i, "UTCID%02d" % (i + 1),
            font=BOLD, fill=HDR_FILL, align=CENTER)

    def block(title, rows_data, start):
        """rows_data: list[(nhãn cột B, text cột D, set cột được đánh dấu O)]"""
        r = start
        for idx, (label, text, marks) in enumerate(rows_data):
            put(ws, r, 1, title if idx == 0 else None,
                font=BOLD, fill=LBL_FILL, align=CENTER)
            put(ws, r, 2, label, font=BOLD if label else None, align=WRAP)
            put(ws, r, 3, None)
            put(ws, r, 4, text, align=WRAP)
            for i in range(len(cases)):
                put(ws, r, 5 + i, "O" if i in marks else None, align=CENTER)
            r += 1
        if r - start > 1:
            ws.merge_cells(start_row=start, start_column=1,
                           end_row=r - 1, end_column=1)
        return r

    # --- Condition -------------------------------------------------------
    cond_rows = [("Tiền điều kiện", cases[0]["precondition"],
                  set(range(len(cases))))]
    order, by_text = [], collections.defaultdict(set)
    for i, case in enumerate(cases):
        if case["condition"] not in by_text:
            order.append(case["condition"])
        by_text[case["condition"]].add(i)
    for idx, text in enumerate(order):
        cond_rows.append(("Đầu vào" if idx == 0 else "", text, by_text[text]))
    row = block("Điều kiện", cond_rows, 8) + 1

    # --- Confirm ---------------------------------------------------------
    order, by_text = [], collections.defaultdict(set)
    for i, case in enumerate(cases):
        if case["expectation"] not in by_text:
            order.append(case["expectation"])
        by_text[case["expectation"]].add(i)
    conf_rows = [("Kết quả trả về" if idx == 0 else "", text, by_text[text])
                 for idx, text in enumerate(order)]
    row = block("Xác nhận", conf_rows, row) + 1

    # --- Result ----------------------------------------------------------
    put(ws, row, 1, "Kết quả", font=BOLD, fill=LBL_FILL, align=CENTER)
    put(ws, row, 2, "Loại (N: thường, A: bất thường, B: biên)", align=WRAP)
    for i, case in enumerate(cases):
        put(ws, row, 5 + i, case["type"], align=CENTER)
    put(ws, row + 1, 1, None, fill=LBL_FILL)
    put(ws, row + 1, 2, "Đạt (P) / Trượt (F)", font=BOLD)
    for i, case in enumerate(cases):
        mark = {"Passed": "P", "Failed": "F", "Untested": "U"}[case["status"]]
        put(ws, row + 1, 5 + i, mark, align=CENTER)
    put(ws, row + 2, 1, None, fill=LBL_FILL)
    put(ws, row + 2, 2, "Ngày thực thi", font=BOLD)
    for i in range(len(cases)):
        put(ws, row + 2, 5 + i, ISSUE_DATE, align=CENTER)
    put(ws, row + 3, 1, None, fill=LBL_FILL)
    put(ws, row + 3, 2, "Mã lỗi ghi nhận", font=BOLD)
    for i in range(len(cases)):
        put(ws, row + 3, 5 + i, None)
    ws.merge_cells(start_row=row, start_column=1,
                   end_row=row + 3, end_column=1)

    # Truy vết ngược về JUnit: tên method test của từng UTCID.
    trace = row + 5
    put(ws, trace, 2, "Đối chiếu hàm JUnit", font=BOLD, fill=LBL_FILL)
    for i, case in enumerate(cases):
        put(ws, trace + i, 4, "UTCID%02d  ->  %s" % (i + 1, case["test_name"]),
            align=WRAP, border=False)
    ws.freeze_panes = "E8"
    return ws


def parse_out_path(argv):
    if len(argv) >= 2 and argv[0] in ("-o", "--out"):
        return pathlib.Path(argv[1]).expanduser()
    if argv:
        sys.exit("Tham so khong hop le. Dung: gen_unittest_function.py "
                 "[-o <duong dan .xlsx>]")
    return DEFAULT_OUT


def apply_font(wb):
    """Ép toàn bộ workbook về một font, giữ nguyên cỡ chữ và kiểu đậm/nghiêng."""
    normal = wb._named_styles["Normal"]
    normal.font = Font(name=FONT_NAME, size=normal.font.size or 11)
    for ws in wb.worksheets:
        for row in ws.iter_rows():
            for cell in row:
                old = cell.font
                if old.name != FONT_NAME:
                    cell.font = Font(name=FONT_NAME, size=old.size,
                                     bold=old.bold, italic=old.italic,
                                     underline=old.underline, color=old.color)


def main(argv=()):
    out = parse_out_path(list(argv))
    groups, unknown = collect()
    names = sheet_names(groups)
    wb = Workbook()
    wb.remove(wb.active)
    build_guideline(wb)
    build_cover(wb)
    build_method_list(wb, groups, names)
    build_statistics(wb, groups, names)
    for key, cases in groups.items():
        build_case_sheet(wb, key, cases, names[key])
    apply_font(wb)
    out.parent.mkdir(parents=True, exist_ok=True)
    wb.save(out)

    total = sum(len(v) for v in groups.values())
    status = collections.Counter(c["status"] for v in groups.values() for c in v)
    print("Da ghi: %s" % out)
    print("  %d sheet ham, %d test case (Passed %d / Failed %d / Untested %d)"
          % (len(groups), total, status["Passed"], status["Failed"],
             status["Untested"]))
    if MISSING:
        print("  CHUA CO BAN DICH - them %d chuoi sau vao %s:"
              % (len(MISSING), GLOSSARY.name))
        for item in sorted(MISSING):
            print('    "%s": "",' % item)
    if unknown:
        print("  CANH BAO - %d test chua map duoc:" % len(unknown))
        for item in unknown[:20]:
            print("    -", item)


if __name__ == "__main__":
    main(sys.argv[1:])

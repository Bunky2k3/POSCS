package poscs.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;

/**
 * Helper dùng chung cho xuất/nhập Excel qua Apache POI -- tránh lặp
 * boilerplate tạo workbook/style/response-header ở từng controller
 * (CustomerController, ContractController, ProductController).
 *
 * Dùng định dạng .xls (HSSF, Excel 97-2003) chứ KHÔNG phải .xlsx (XSSF) --
 * lib/poi-5.5.1.jar chỉ chứa phần lõi (org.apache.poi.ss, org.apache.poi.hssf),
 * không có org.apache.poi.xssf (nằm ở artifact poi-ooxml riêng, chưa có
 * trong lib/). HSSF vẫn mở/sửa bình thường bằng Excel, LibreOffice, Google
 * Sheets -- chỉ giới hạn 65.536 dòng/256 cột, dư sức cho quy mô dữ liệu
 * hiện tại (vài chục tới vài trăm dòng).
 */
public final class ExcelUtil {

    private static final String CONTENT_TYPE = "application/vnd.ms-excel";
    private static final String FILE_EXTENSION = ".xls";

    private ExcelUtil() {
    }

    /**
     * Tạo 1 workbook có đúng 1 sheet ("Data"), dòng đầu là header (in đậm,
     * nền xám nhạt), các dòng sau là dữ liệu, rồi stream thẳng ra response
     * dưới dạng file tải về. Không forward tới JSP nào sau khi gọi hàm này.
     */
    public static void writeWorkbook(HttpServletResponse response, String fileNamePrefix,
            String[] headers, List<Object[]> rows) throws IOException {
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            writeHeaderRow(workbook, sheet, headers);
            writeDataRows(sheet, headers.length, rows);
            setDefaultColumnWidths(sheet, headers.length);
            streamAsAttachment(response, workbook, fileNamePrefix);
        }
    }

    /**
     * Tạo file mẫu để nhập Excel: dòng đầu là header, có thể kèm dropdown
     * (data validation) cho 1 số cột -- ví dụ danh mục sản phẩm, loại/nhóm
     * khách hàng -- để người dùng không gõ sai giá trị. `dropdownOptions`:
     * key = chỉ số cột (0-based), value = danh sách giá trị hợp lệ cho cột
     * đó. `maxDataRows` = số dòng trống bên dưới header được áp dropdown
     * (Excel không cho áp validation cho "cả cột" một cách rẻ, nên giới hạn
     * ở mức đủ dùng, ví dụ 200 dòng).
     */
    public static void writeTemplate(HttpServletResponse response, String fileNamePrefix,
            String[] headers, Map<Integer, List<String>> dropdownOptions, int maxDataRows)
            throws IOException {
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            writeHeaderRow(workbook, sheet, headers);

            if (dropdownOptions != null && !dropdownOptions.isEmpty()) {
                addDropdownValidations(workbook, sheet, dropdownOptions, maxDataRows);
            }

            setDefaultColumnWidths(sheet, headers.length);
            streamAsAttachment(response, workbook, fileNamePrefix);
        }
    }

    /**
     * Excel giới hạn danh sách dropdown kiểu "liệt kê trực tiếp trong công
     * thức" (explicit list) ở khoảng 255 ký tự -- danh mục sản phẩm (33
     * mục) hay tỉnh/thành (34 mục) vượt giới hạn này dễ dàng và làm dropdown
     * bị Excel từ chối hoặc hiển thị sai. Với danh sách dài, ghi giá trị ra
     * 1 sheet ẩn ("RefData") rồi tham chiếu theo vùng ô thay vì liệt kê
     * trực tiếp -- không giới hạn số mục.
     */
    private static void addDropdownValidations(Workbook workbook, Sheet sheet,
            Map<Integer, List<String>> dropdownOptions, int maxDataRows) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        Sheet refSheet = null;
        int refCol = 0;
        for (Map.Entry<Integer, List<String>> entry : dropdownOptions.entrySet()) {
            int col = entry.getKey();
            List<String> options = entry.getValue();
            DataValidationConstraint constraint;
            if (options.size() <= 15 && totalLength(options) <= 200) {
                constraint = dvHelper.createExplicitListConstraint(options.toArray(new String[0]));
            } else {
                if (refSheet == null) {
                    refSheet = workbook.createSheet("RefData");
                    workbook.setSheetHidden(workbook.getSheetIndex(refSheet), true);
                }
                for (int i = 0; i < options.size(); i++) {
                    Row row = refSheet.getRow(i);
                    if (row == null) {
                        row = refSheet.createRow(i);
                    }
                    row.createCell(refCol).setCellValue(options.get(i));
                }
                String colLetter = CellReference.convertNumToColString(refCol);
                String formula = "RefData!$" + colLetter + "$1:$" + colLetter + "$" + options.size();
                constraint = dvHelper.createFormulaListConstraint(formula);
                refCol++;
            }
            CellRangeAddressList addressList = new CellRangeAddressList(1, maxDataRows, col, col);
            DataValidation validation = dvHelper.createValidation(constraint, addressList);
            validation.setShowErrorBox(true);
            validation.createErrorBox("Giá trị không hợp lệ", "Vui lòng chọn 1 giá trị trong danh sách thả xuống.");
            sheet.addValidationData(validation);
        }
    }

    private static int totalLength(List<String> options) {
        int len = 0;
        for (String s : options) {
            len += s.length() + 1;
        }
        return len;
    }

    /**
     * Đọc toàn bộ dòng dữ liệu (bỏ qua `headerRowIndex` dòng đầu) từ file
     * .xls tải lên, dùng cho import. Trả về danh sách các `Row` POI để
     * controller tự đọc cell theo cột bằng {@link #cellString(Row, int)}/
     * {@link #cellInt(Row, int)}. Bỏ qua các dòng hoàn toàn trống (thường do
     * người dùng để dư dòng cuối file khi chỉnh sửa).
     */
    public static List<Row> readRows(InputStream inputStream, int headerRowIndex) throws IOException {
        List<Row> result = new ArrayList<>();
        try (Workbook workbook = new HSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int i = headerRowIndex + 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row != null && !isRowBlank(row)) {
                    result.add(row);
                }
            }
        }
        return result;
    }

    /** Đọc cell dạng chuỗi an toàn -- trả "" nếu cell trống/không tồn tại, tự ép kiểu số/ngày về chuỗi nếu cần. */
    public static String cellString(Row row, int col) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value) && !Double.isInfinite(value)) {
                    return String.valueOf((long) value);
                }
                return String.valueOf(value);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (IllegalStateException ex) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
            default:
                return "";
        }
    }

    /** Đọc cell dạng số nguyên an toàn -- trả null nếu cell trống/không parse được. */
    public static Integer cellInt(Row row, int col) {
        String s = cellString(row, col);
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers riêng
    // ------------------------------------------------------------------

    private static void writeHeaderRow(Workbook workbook, Sheet sheet, String[] headers) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i, CellType.STRING);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private static void writeDataRows(Sheet sheet, int columnCount, List<Object[]> rows) {
        int rowIndex = 1;
        for (Object[] rowData : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int c = 0; c < columnCount && c < rowData.length; c++) {
                Object value = rowData[c];
                Cell cell = row.createCell(c);
                if (value == null) {
                    cell.setCellValue("");
                } else if (value instanceof Number) {
                    cell.setCellValue(((Number) value).doubleValue());
                } else if (value instanceof java.util.Date) {
                    cell.setCellValue(new SimpleDateFormat("dd/MM/yyyy").format((java.util.Date) value));
                } else {
                    cell.setCellValue(value.toString());
                }
            }
        }
    }

    /**
     * Đặt độ rộng cột cố định thay vì autoSizeColumn() -- autoSizeColumn cần
     * AWT đo font chữ, dễ ném lỗi/chạy chậm trên server chạy headless không
     * có font hệ thống cấu hình sẵn. 1 đơn vị độ rộng POI ~ 1/256 ký tự,
     * 6000 ~ 23 ký tự, đủ đọc được hầu hết nội dung cột mà không cần đo thật.
     */
    private static void setDefaultColumnWidths(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.setColumnWidth(i, 6000);
        }
    }

    private static void streamAsAttachment(HttpServletResponse response, Workbook workbook, String fileNamePrefix)
            throws IOException {
        String fileName = fileNamePrefix + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + FILE_EXTENSION;
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType(CONTENT_TYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);
        try (OutputStream out = response.getOutputStream()) {
            workbook.write(out);
        }
    }

    private static boolean isRowBlank(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            if (!cellString(row, c).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}

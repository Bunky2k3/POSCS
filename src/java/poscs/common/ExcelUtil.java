package poscs.common;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

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

    /**
     * Chặn formula injection khi xuất Excel.
     *
     * Excel coi ô bắt đầu bằng = + - @ (hoặc tab/xuống dòng rồi tới các ký tự
     * đó) là CÔNG THỨC chứ không phải chữ, dù ta ghi xuống dưới dạng text. Dữ
     * liệu trong file xuất ra là do người dùng nhập (tên khách hàng, ghi chú,
     * mô tả...), nên một người nhập tên dạng {@code =HYPERLINK(...)} có thể
     * khiến máy của đồng nghiệp mở file đó chạy công thức -- kéo dữ liệu ô
     * khác gửi ra ngoài, hoặc hiện link dụ bấm.
     *
     * Thêm dấu nháy đơn ở đầu: Excel hiểu đó là "ép kiểu chữ", hiển thị đúng
     * nội dung gốc mà không tính toán gì.
     */
    private static String neutralizeFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
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
                    cell.setCellValue(neutralizeFormula(value.toString()));
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
}

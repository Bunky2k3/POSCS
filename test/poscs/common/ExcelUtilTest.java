package poscs.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * ExcelUtil chặn formula injection bằng cờ quote prefix của ô, không nối dấu
 * nháy vào nội dung -- nối vào thì Excel hiện nguyên "'=1+1" cho người xem
 * (ca hộp đen TC_CUSEXP_004 / TC_TKEXP_006).
 */
public class ExcelUtilTest {

    private Row exportOneRow(Object... values) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                captured.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        });
        String[] headers = new String[values.length];
        Arrays.fill(headers, "Cột");
        List<Object[]> rows = List.<Object[]>of(values);

        ExcelUtil.writeWorkbook(response, "thu", headers, rows);

        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(captured.toByteArray()));
        return wb.getSheetAt(0).getRow(1);
    }

    /** Giữ đúng chữ người dùng nhập, ô là chữ (không phải công thức) và có cờ quote prefix. */
    @Test
    public void formulaLikeText_keepsOriginalTextWithQuotePrefix() throws Exception {
        Row row = exportOneRow("=1+1", "+84 912", "-5", "@SUM(A1)", "=HYPERLINK(\"http://x\")");
        for (int c = 0; c < 5; c++) {
            Cell cell = row.getCell(c);
            assertEquals(CellType.STRING, cell.getCellType());
            assertFalse("không còn dấu nháy trong nội dung: " + cell.getStringCellValue(),
                    cell.getStringCellValue().startsWith("'"));
            assertTrue("ô " + c + " phải có quote prefix", cell.getCellStyle().getQuotePrefixed());
        }
        assertEquals("=1+1", row.getCell(0).getStringCellValue());
        assertEquals("=HYPERLINK(\"http://x\")", row.getCell(4).getStringCellValue());
    }

    /** Chữ thường và số không bị gắn cờ -- số vẫn là số để Excel cộng được. */
    @Test
    public void plainTextAndNumbers_areLeftAlone() throws Exception {
        Row row = exportOneRow("Cáp quang", 1500000L, "");
        assertEquals("Cáp quang", row.getCell(0).getStringCellValue());
        assertFalse(row.getCell(0).getCellStyle().getQuotePrefixed());
        assertEquals(CellType.NUMERIC, row.getCell(1).getCellType());
        assertEquals(1500000d, row.getCell(1).getNumericCellValue(), 0);
        assertFalse(row.getCell(2).getCellStyle().getQuotePrefixed());
    }

    @Test
    public void looksLikeFormula_coversLeadingControlCharacters() {
        assertTrue(ExcelUtil.looksLikeFormula("\t=1+1"));
        assertTrue(ExcelUtil.looksLikeFormula("\r=1"));
        assertTrue(ExcelUtil.looksLikeFormula("\n=1"));
        assertFalse(ExcelUtil.looksLikeFormula(""));
        assertFalse(ExcelUtil.looksLikeFormula("A=1"));
    }
}

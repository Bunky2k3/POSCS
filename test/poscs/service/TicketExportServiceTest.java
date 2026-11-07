package poscs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jakarta.servlet.ServletContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import poscs.common.Period;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.TechnicalRequest;
import poscs.model.User;

public class TicketExportServiceTest {

    private TechnicalSupportTicketDAO ticketDAO;
    private TicketExportService exportService;
    private ServletContext servletContext;

    @Before
    public void setUp() {
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        exportService = new TicketExportService(ticketDAO);

        servletContext = mock(ServletContext.class);
        when(servletContext.getResourceAsStream("/WEB-INF/fonts/NotoSans-Regular.ttf"))
                .thenAnswer(inv -> new FileInputStream("web/WEB-INF/fonts/NotoSans-Regular.ttf"));
    }

    private TechnicalRequest createSampleTicket() {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketId(10);
        t.setTicketCode("TK-2026-001");
        t.setTicketType("Bảo hành");
        t.setPriority("Cao");
        t.setReceptionChannel("Điện thoại");
        t.setStatus("Đang xử lý");
        t.setCreatedDate(Date.valueOf("2026-08-15"));
        t.setSlaDeadline(Timestamp.valueOf("2026-08-16 10:00:00"));
        t.setResolvedAt(Timestamp.valueOf("2026-08-16 15:30:00"));
        t.setWarranty(true);
        t.setDescription("Hỏng nguồn máy chủ");
        t.setRootCause("Sét đánh lan truyền");
        t.setCauseCategory("Môi trường");
        t.setHandlingPlan("Thay thế bộ nguồn dự phòng");
        t.setResolutionSummary("Đã thay xong, thiết bị hoạt động ổn định");

        Enterprise e = new Enterprise();
        e.setEnterpriseName("Công ty Viễn thông Sông Hồng");
        t.setEnterprise(e);

        Contract c = new Contract();
        c.setContractCode("HD-2026-009");
        t.setContract(c);

        User tech = new User();
        tech.setLastName("Nguyễn");
        tech.setMiddleName("Văn");
        tech.setFirstName("Kỹ Thuật");
        t.setAssignedTechnician(tech);

        User creator = new User();
        creator.setLastName("Trần");
        creator.setMiddleName("Thị");
        creator.setFirstName("Tiếp Nhận");
        t.setCreatedByUser(creator);

        return t;
    }

    @Test
    public void buildExportRows_validTicket_mapsAll18ColumnsCorrectly() {
        TechnicalRequest t = createSampleTicket();
        List<Object[]> rows = exportService.buildExportRows(Collections.singletonList(t));

        assertEquals(1, rows.size());
        Object[] row = rows.get(0);
        assertEquals(18, row.length);
        assertEquals("TK-2026-001", row[0]);
        assertEquals("Bảo hành", row[1]);
        assertEquals("Công ty Viễn thông Sông Hồng", row[2]);
        assertEquals("HD-2026-009", row[3]);
        assertEquals("Cao", row[4]);
        assertEquals("Điện thoại", row[5]);
        assertEquals("Đang xử lý", row[6]);
        assertEquals("Nguyễn Văn Kỹ Thuật", row[7]);
        assertEquals("Trần Thị Tiếp Nhận", row[8]);
        assertEquals("15/08/2026", row[9]);
        assertEquals("16/08/2026 10:00", row[10]);
        assertEquals("16/08/2026 15:30", row[11]);
        assertEquals("Có", row[12]);
        assertEquals("Hỏng nguồn máy chủ", row[13]);
        assertEquals("Sét đánh lan truyền", row[14]);
        assertEquals("Môi trường", row[15]);
        assertEquals("Thay thế bộ nguồn dự phòng", row[16]);
        assertEquals("Đã thay xong, thiết bị hoạt động ổn định", row[17]);
    }

    @Test
    public void buildExportRows_withNullAndEmptyFields_safeFallback() {
        TechnicalRequest t = new TechnicalRequest();
        t.setWarranty(false);
        List<Object[]> rows = exportService.buildExportRows(Collections.singletonList(t));

        assertEquals(1, rows.size());
        Object[] row = rows.get(0);
        assertNull(row[0]);
        assertNull(row[1]);
        assertEquals("", row[2]);
        assertEquals("", row[3]);
        assertEquals("", row[7]);
        assertEquals("", row[8]);
        assertEquals("—", row[9]);
        assertEquals("—", row[10]);
        assertEquals("—", row[11]);
        assertEquals("Không", row[12]);
    }

    @Test
    public void buildExportRows_emptyOrNullList_returnsEmptyList() {
        assertTrue(exportService.buildExportRows(null).isEmpty());
        assertTrue(exportService.buildExportRows(Collections.emptyList()).isEmpty());

        List<TechnicalRequest> listWithNull = new ArrayList<>();
        listWithNull.add(null);
        assertTrue(exportService.buildExportRows(listWithNull).isEmpty());
    }

    @Test
    public void causeForPrint_variousCases() {
        TechnicalRequest t = new TechnicalRequest();
        assertNull(exportService.causeForPrint(null));

        t.setCauseCategory(null);
        t.setRootCause(null);
        assertNull(exportService.causeForPrint(t));

        t.setCauseCategory("   ");
        t.setRootCause("Lỗi phần cứng");
        assertEquals("Lỗi phần cứng", exportService.causeForPrint(t));

        t.setCauseCategory("Môi trường");
        t.setRootCause(null);
        assertEquals("[Môi trường]", exportService.causeForPrint(t));

        t.setRootCause("   ");
        assertEquals("[Môi trường]", exportService.causeForPrint(t));

        t.setCauseCategory("Do vận chuyển");
        t.setRootCause("Gãy chân cắm");
        assertEquals("[Do vận chuyển] Gãy chân cắm", exportService.causeForPrint(t));
    }

    @Test
    public void nz_formatDate_formatDateTime_helpers() {
        assertEquals("—", exportService.nz(null));
        assertEquals("—", exportService.nz("   "));
        assertEquals("Valid", exportService.nz("Valid"));

        assertEquals("—", exportService.formatDate(null));
        assertEquals("01/01/2026", exportService.formatDate(Date.valueOf("2026-01-01")));

        assertEquals("—", exportService.formatDateTime(null));
        assertEquals("01/01/2026 09:30", exportService.formatDateTime(Timestamp.valueOf("2026-01-01 09:30:00")));
    }

    @Test
    public void getPdfFileName_replacesSlashes() {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketCode("TK/2026/001");
        assertEquals("phieu_TK-2026-001.pdf", exportService.getPdfFileName(t));

        assertEquals("phieu_export.pdf", exportService.getPdfFileName(null));

        t.setTicketCode(null);
        t.setTicketId(42);
        assertEquals("phieu_42.pdf", exportService.getPdfFileName(t));

        t.setTicketId(0);
        assertEquals("phieu_export.pdf", exportService.getPdfFileName(t));
    }

    @Test
    public void getExcelFileNamePrefix_returnsExpected() {
        assertEquals("phieu_ho_tro", exportService.getExcelFileNamePrefix());
    }

    @Test
    public void exportTicketsToExcel_withOutputStream_writesValidWorkbook() throws IOException {
        TechnicalRequest t = createSampleTicket();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        exportService.exportTicketsToExcel(out, Collections.singletonList(t));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("Data", sheet.getSheetName());
            assertEquals(1, sheet.getLastRowNum());
            assertEquals("Mã phiếu", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("TK-2026-001", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Hỏng nguồn máy chủ", sheet.getRow(1).getCell(13).getStringCellValue());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void exportTicketsToExcel_nullOutputStream_throwsException() throws IOException {
        exportService.exportTicketsToExcel(null, Collections.emptyList());
    }

    @Test
    public void exportTicketsToExcel_withFilters_queriesDAO() throws IOException {
        TechnicalRequest t = createSampleTicket();
        Period p = Period.parse("2026", "q1");
        when(ticketDAO.findAll(1, Integer.MAX_VALUE, "máy chủ", "Đang xử lý", "Cao", p, 50))
                .thenReturn(Collections.singletonList(t));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exportTicketsToExcel(out, "máy chủ", "Đang xử lý", "Cao", p, 50);

        verify(ticketDAO).findAll(1, Integer.MAX_VALUE, "máy chủ", "Đang xử lý", "Cao", p, 50);
        assertTrue(out.size() > 0);
    }

    @Test
    public void exportTicketToPdf_validTicket_generatesValidPdf() throws Exception {
        TechnicalRequest t = createSampleTicket();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        exportService.exportTicketToPdf(out, t, servletContext);

        try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
            assertEquals(1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("PHIẾU HỖ TRỢ KỸ THUẬT"));
            assertTrue(text.contains("TK-2026-001"));
            assertTrue(text.contains("Hỏng nguồn máy chủ"));
            assertTrue(text.contains("[Môi trường]"));
        }
    }

    @Test
    public void exportTicketToPdf_longText_flowsOntoMultiplePages() throws Exception {
        TechnicalRequest t = createSampleTicket();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("Dong mo ta chi tiet thu ").append(i).append(". ");
        }
        sb.append("MO_TA_KET_THUC");
        t.setDescription(sb.toString());
        t.setResolutionSummary("KET_QUA_CUOI_CUNG");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exportTicketToPdf(out, t, servletContext);

        try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
            assertTrue("Nội dung dài phải chia ra nhiều hơn 1 trang", doc.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("MO_TA_KET_THUC"));
            assertTrue(text.contains("KET_QUA_CUOI_CUNG"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void exportTicketToPdf_nullOutputStream_throwsException() throws IOException {
        exportService.exportTicketToPdf(null, createSampleTicket(), servletContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void exportTicketToPdf_nullTicket_throwsException() throws IOException {
        exportService.exportTicketToPdf(new ByteArrayOutputStream(), null, servletContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void exportTicketToPdf_nullServletContext_throwsException() throws IOException {
        exportService.exportTicketToPdf(new ByteArrayOutputStream(), createSampleTicket(), null);
    }
}

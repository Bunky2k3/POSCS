package poscs.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.Role;
import poscs.model.TechnicalRequest;
import poscs.model.TechnicalRequestHistory;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho TechnicalSupportTicketController -- tập trung
 * vào phân quyền TICKET (Full access CSKH/Admin, cộng ngoại lệ Kỹ thuật
 * được tự cập nhật đúng phiếu giao cho mình -- AccessControl.
 * canUpdateAssignedTicket) và quy tắc stamp resolved_at chỉ 1 lần khi
 * chuyển sang "Đã đóng". JUnit 4 + Mockito, xem CustomerControllerTest.
 */
public class TechnicalSupportTicketControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private TechnicalSupportTicketController controller;
    private TechnicalSupportTicketDAO ticketDAO;
    private CustomerDAO customerDAO;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new TechnicalSupportTicketController();

        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        customerDAO = mock(CustomerDAO.class);
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);

        setField(controller, "ticketDAO", ticketDAO);
        setField(controller, "customerDAO", customerDAO);
        setField(controller, "contractDAO", contractDAO);
        setField(controller, "employeeDAO", employeeDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        loginAs("CSKH", 99); // role được Full access trên TICKET, xem PERMISSIONS.md

        // exportPdf đọc font tiếng Việt qua ServletContext. Dùng ĐÚNG file font
        // trong web/WEB-INF/fonts thay vì mock trả byte giả: PDType0Font.load
        // phân tích thật file TTF, font giả sẽ hỏng ngay ở đó.
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getResourceAsStream("/WEB-INF/fonts/NotoSans-Regular.ttf"))
                .thenAnswer(inv -> new FileInputStream("web/WEB-INF/fonts/NotoSans-Regular.ttf"));
        ServletConfig servletConfig = mock(ServletConfig.class);
        when(servletConfig.getServletContext()).thenReturn(servletContext);
        controller.init(servletConfig);
    }

    private void loginAs(String roleName, int userId) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** Phiếu hợp lệ đầy đủ trường bắt buộc (isValid), phục vụ các test cập nhật. */
    private TechnicalRequest fullyValidExistingTicket() {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketId(3);
        t.setEnterpriseId(10);
        t.setTicketType("Bảo hành");
        t.setPriority("Cao");
        t.setReceptionChannel("Điện thoại");
        t.setAssignedTechnicianId(50);
        t.setDescription("Thiết bị lỗi nguồn");
        t.setStatus("Đang xử lý");
        return t;
    }

    // ------------------------------------------------------------------
    // GET ?action=view
    // ------------------------------------------------------------------

    @Test
    public void view_ticketNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("123");
        when(ticketDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
    }

    @Test
    public void view_ticketFound_forwardsToDetailView() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest ticket = fullyValidExistingTicket();
        when(ticketDAO.findById(3)).thenReturn(ticket);
        when(ticketDAO.canDelete(3)).thenReturn(true);

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/customersupport/viewdetailTicket.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute("ticket", ticket);
        verify(dispatcher).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST ?action=create
    // ------------------------------------------------------------------

    private void stubValidCreateFields() {
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
    }

    @Test
    public void create_withoutFullAccess_returns403AndNeverInserts() throws Exception {
        loginAs("Sales", 1); // không có Full access trên TICKET
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(ticketDAO, never()).insert(any());
    }

    @Test
    public void create_missingDescription_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("description")).thenReturn(null);

        controller.doPost(request, response);

        verify(ticketDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=new&error=invalid");
    }

    @Test
    public void create_validFields_insertsAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(ticketDAO.generateNextTicketCode()).thenReturn("TK-0001");
        when(ticketDAO.insert(any(TechnicalRequest.class))).thenReturn(7);

        controller.doPost(request, response);

        verify(ticketDAO).insert(argThat((TechnicalRequest t) ->
                TechnicalSupportTicketDAO.STATUS_NEW.equals(t.getStatus()) && t.getCreatedBy() == 99));
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=7");
    }

    // ------------------------------------------------------------------
    // POST ?action=update -- phân quyền + BR stamp resolved_at
    // ------------------------------------------------------------------

    @Test
    public void update_ticketIdMissing_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn(null);

        controller.doPost(request, response);

        verify(ticketDAO, never()).update(any(), anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
    }

    @Test
    public void update_technicianNotAssignedToTicket_returns403() throws Exception {
        loginAs("Kỹ thuật", 1); // không phải Full access, và không phải người được giao phiếu này (assignedTechnicianId=50)
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(ticketDAO, never()).update(any(), anyInt(), any());
    }

    @Test
    public void update_assignedTechnician_canCloseOwnTicketAndStampsResolvedAt() throws Exception {
        loginAs("Kỹ thuật", 50); // đúng người được giao phiếu (assignedTechnicianId=50)
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn("3");
        when(request.getParameter("status")).thenReturn(TechnicalSupportTicketDAO.STATUS_CLOSED);
        when(request.getParameter("resolutionSummary")).thenReturn("Đã thay nguồn");
        TechnicalRequest existing = fullyValidExistingTicket(); // status ban đầu "Đang xử lý", resolvedAt=null
        when(ticketDAO.findById(3)).thenReturn(existing);
        when(ticketDAO.update(any(), anyInt(), any())).thenReturn(true);

        controller.doPost(request, response);

        verify(ticketDAO).update(argThat((TechnicalRequest t) ->
                TechnicalSupportTicketDAO.STATUS_CLOSED.equals(t.getStatus()) && t.getResolvedAt() != null),
                anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=3");
    }

    /**
     * changed_by phải lấy từ SESSION, không phải từ form: đây là cột dùng để
     * quy trách nhiệm "ai đổi trạng thái", mà form thì ai cũng sửa được.
     */
    @Test
    public void update_passesLoggedInUserAndInternalNoteToDao() throws Exception {
        loginAs("CSKH", 99);
        stubValidUpdateParams();
        when(request.getParameter("status")).thenReturn(TechnicalSupportTicketDAO.STATUS_CLOSED);
        when(request.getParameter("internalNote")).thenReturn("Khách xác nhận đã ổn");
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());

        controller.doPost(request, response);

        verify(ticketDAO).update(any(TechnicalRequest.class), eq(99), eq("Khách xác nhận đã ổn"));
    }

    /** Ô ghi chú để trống thì lưu NULL, đừng lưu chuỗi rỗng cho lịch sử lấm tấm ô trắng. */
    @Test
    public void update_blankInternalNote_isStoredAsNull() throws Exception {
        loginAs("CSKH", 99);
        stubValidUpdateParams();
        when(request.getParameter("status")).thenReturn(TechnicalSupportTicketDAO.STATUS_CLOSED);
        when(request.getParameter("internalNote")).thenReturn("   ");
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());

        controller.doPost(request, response);

        verify(ticketDAO).update(any(TechnicalRequest.class), eq(99), isNull());
    }

    /** Trang chi tiết phải bơm sẵn lịch sử cho JSP, không thì phần "Lịch sử xử lý" luôn trống. */
    @Test
    public void view_putsTicketHistoryIntoRequest() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());
        TechnicalRequestHistory h = new TechnicalRequestHistory();
        h.setFromStatus("Mới tiếp nhận");
        h.setToStatus("Đang xử lý");
        when(ticketDAO.findHistoryByTicketId(3)).thenReturn(java.util.List.of(h));
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/customersupport/viewdetailTicket.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        ArgumentCaptor<Object> history = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq("ticketHistory"), history.capture());
        assertEquals(1, ((java.util.List<?>) history.getValue()).size());
    }

    @Test
    public void update_alreadyClosedTicket_doesNotOverwriteOriginalResolvedAt() throws Exception {
        loginAs("CSKH", 99); // Full access
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn("3");
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
        when(request.getParameter("status")).thenReturn(TechnicalSupportTicketDAO.STATUS_CLOSED);
        when(request.getParameter("resolutionSummary")).thenReturn("Sửa lại ghi chú, không đổi trạng thái");

        TechnicalRequest existing = fullyValidExistingTicket();
        existing.setStatus(TechnicalSupportTicketDAO.STATUS_CLOSED);
        Timestamp originalResolvedAt = new Timestamp(1000L);
        existing.setResolvedAt(originalResolvedAt);
        when(ticketDAO.findById(3)).thenReturn(existing);
        when(ticketDAO.update(any(), anyInt(), any())).thenReturn(true);

        controller.doPost(request, response);

        verify(ticketDAO).update(argThat((TechnicalRequest t) -> t.getResolvedAt() == originalResolvedAt), anyInt(), any());
    }

    // ------------------------------------------------------------------
    // POST ?action=delete
    // ------------------------------------------------------------------

    @Test
    public void delete_withoutFullAccess_returns403() throws Exception {
        loginAs("Sales", 1);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(ticketDAO, never()).softDelete(anyInt());
    }

    @Test
    public void delete_notDeletable_blocksDeletion() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.canDelete(3)).thenReturn(false);

        controller.doPost(request, response);

        verify(ticketDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=3&error=cannot_delete");
    }

    @Test
    public void delete_deletable_softDeletesAndRedirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.canDelete(3)).thenReturn(true);

        controller.doPost(request, response);

        verify(ticketDAO).softDelete(3);
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket");
    }

    // ------------------------------------------------------------------
    // GET ?action=exportExcel
    // ------------------------------------------------------------------

    /**
     * Hứng byte mà controller ghi ra response, để mở lại bằng chính POI --
     * khác các test còn lại (chỉ verify lời gọi), test này chạy POI THẬT nên
     * bắt được cả lỗi sinh workbook lẫn lỗi thiếu thư viện lúc chạy.
     */
    private ByteArrayOutputStream captureResponseBody() throws Exception {
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
        return captured;
    }

    private TechnicalRequest ticketForExport() {
        TechnicalRequest t = fullyValidExistingTicket();
        t.setTicketCode("TK-0007");
        Enterprise e = new Enterprise();
        e.setEnterpriseName("Công ty Cổ phần Viễn thông Sông Hồng");
        t.setEnterprise(e);
        Contract c = new Contract();
        c.setContractCode("HD-0001");
        t.setContract(c);
        t.setCreatedDate(Date.valueOf("2026-08-20"));
        return t;
    }

    @Test
    public void exportExcel_writesRealWorkbookWithHeaderAndOneRowPerTicket() throws Exception {
        when(request.getParameter("action")).thenReturn("exportExcel");
        when(ticketDAO.findAll(eq(1), eq(Integer.MAX_VALUE), any(), any(), any()))
                .thenReturn(Arrays.asList(ticketForExport(), ticketForExport()));
        ByteArrayOutputStream captured = captureResponseBody();

        controller.doGet(request, response);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(captured.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("1 dòng header + 2 dòng phiếu", 2, sheet.getLastRowNum());
            assertEquals("Mã phiếu", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("TK-0007", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Công ty Cổ phần Viễn thông Sông Hồng",
                    sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("HD-0001", sheet.getRow(1).getCell(3).getStringCellValue());
        }
    }

    @Test
    public void exportExcel_sendsFileAsAttachmentNotHtml() throws Exception {
        when(request.getParameter("action")).thenReturn("exportExcel");
        when(ticketDAO.findAll(eq(1), eq(Integer.MAX_VALUE), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        captureResponseBody();

        controller.doGet(request, response);

        verify(response).setContentType("application/vnd.ms-excel");
        verify(response).setHeader(eq("Content-Disposition"), contains("attachment; filename=\"phieu_ho_tro_"));
        verify(request, never()).getRequestDispatcher(anyString());
    }

    @Test
    public void exportExcel_passesCurrentFiltersThroughAndIgnoresPaging() throws Exception {
        // Xuất theo đúng bộ lọc đang áp trên màn hình, nhưng KHÔNG phân trang:
        // người dùng lọc ra cái họ cần rồi muốn cả tập đó, không phải 1 trang.
        when(request.getParameter("action")).thenReturn("exportExcel");
        when(request.getParameter("keyword")).thenReturn("trạm BTS");
        when(request.getParameter("status")).thenReturn("Đang xử lý");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(ticketDAO.findAll(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        captureResponseBody();

        controller.doGet(request, response);

        verify(ticketDAO).findAll(1, Integer.MAX_VALUE, "trạm BTS", "Đang xử lý", "Cao");
    }

    @Test
    public void exportExcel_neutralisesFormulaSoExcelDoesNotExecuteIt() throws Exception {
        // Mô tả sự cố do người dùng nhập; bắt đầu bằng "=" là Excel coi như
        // công thức. ExcelUtil phải thêm dấu nháy đơn để nó thành chữ.
        when(request.getParameter("action")).thenReturn("exportExcel");
        TechnicalRequest t = ticketForExport();
        t.setDescription("=HYPERLINK(\"http://kegian.example\",\"Bấm vào đây\")");
        when(ticketDAO.findAll(eq(1), eq(Integer.MAX_VALUE), any(), any(), any()))
                .thenReturn(Collections.singletonList(t));
        ByteArrayOutputStream captured = captureResponseBody();

        controller.doGet(request, response);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(captured.toByteArray()))) {
            String moTa = wb.getSheetAt(0).getRow(1).getCell(13).getStringCellValue();
            assertTrue("Phải còn nguyên nội dung gốc cho người đọc: " + moTa,
                    moTa.contains("HYPERLINK"));
            assertNotEquals("Ô không được là công thức",
                    org.apache.poi.ss.usermodel.CellType.FORMULA,
                    wb.getSheetAt(0).getRow(1).getCell(13).getCellType());
        }
    }

    // ------------------------------------------------------------------
    // Hạn SLA -- không được mất khi sửa phiếu
    // ------------------------------------------------------------------

    @Test
    public void update_formOmitsSlaDeadline_keepsTheStoredOne() throws Exception {
        // Form sửa không bắt buộc nhập hạn SLA. Nếu handleUpdate không giữ lại
        // giá trị cũ thì mỗi lần Admin/CSKH bấm lưu là ghi đè sla_deadline
        // thành NULL -- phiếu biến mất khỏi ô "sắp/đã quá hạn" trên dashboard
        // và khỏi lịch nhắc SLA, vì cả hai đều lọc "sla_deadline IS NOT NULL".
        Timestamp stored = Timestamp.valueOf("2026-08-27 10:00:00");
        TechnicalRequest existing = fullyValidExistingTicket();
        existing.setSlaDeadline(stored);
        when(ticketDAO.findById(3)).thenReturn(existing);
        stubValidUpdateParams();
        when(request.getParameter("slaDeadline")).thenReturn(null);

        controller.doPost(request, response);

        ArgumentCaptor<TechnicalRequest> saved = ArgumentCaptor.forClass(TechnicalRequest.class);
        verify(ticketDAO).update(saved.capture(), anyInt(), any());
        assertEquals("Hạn SLA cũ phải được giữ nguyên", stored, saved.getValue().getSlaDeadline());
    }

    @Test
    public void update_formSendsNewSlaDeadline_overwritesTheStoredOne() throws Exception {
        TechnicalRequest existing = fullyValidExistingTicket();
        existing.setSlaDeadline(Timestamp.valueOf("2026-08-27 10:00:00"));
        when(ticketDAO.findById(3)).thenReturn(existing);
        stubValidUpdateParams();
        when(request.getParameter("slaDeadline")).thenReturn("2026-09-30T17:30");

        controller.doPost(request, response);

        ArgumentCaptor<TechnicalRequest> saved = ArgumentCaptor.forClass(TechnicalRequest.class);
        verify(ticketDAO).update(saved.capture(), anyInt(), any());
        assertEquals(Timestamp.valueOf("2026-09-30 17:30:00"), saved.getValue().getSlaDeadline());
    }

    @Test
    public void update_slaDeadlineUnparseable_keepsTheStoredOneInsteadOfWiping() throws Exception {
        // Giá trị rác (người dùng sửa tay URL, hoặc trình duyệt cũ gửi định dạng
        // khác) phải rơi về "coi như không nhập", KHÔNG được biến thành NULL.
        Timestamp stored = Timestamp.valueOf("2026-08-27 10:00:00");
        TechnicalRequest existing = fullyValidExistingTicket();
        existing.setSlaDeadline(stored);
        when(ticketDAO.findById(3)).thenReturn(existing);
        stubValidUpdateParams();
        when(request.getParameter("slaDeadline")).thenReturn("hom qua");

        controller.doPost(request, response);

        ArgumentCaptor<TechnicalRequest> saved = ArgumentCaptor.forClass(TechnicalRequest.class);
        verify(ticketDAO).update(saved.capture(), anyInt(), any());
        assertEquals(stored, saved.getValue().getSlaDeadline());
    }

    @Test
    public void create_readsSlaDeadlineFromForm() throws Exception {
        // Trước đây không có ô nhập nào nên phiếu tạo từ hệ thống luôn có
        // sla_deadline NULL, khiến dashboard và lịch nhắc SLA thành vô dụng.
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
        when(request.getParameter("slaDeadline")).thenReturn("2026-09-30T17:30");
        when(ticketDAO.generateNextTicketCode()).thenReturn("TK-0099");
        when(ticketDAO.insert(any(TechnicalRequest.class))).thenReturn(77);

        controller.doPost(request, response);

        ArgumentCaptor<TechnicalRequest> saved = ArgumentCaptor.forClass(TechnicalRequest.class);
        verify(ticketDAO).insert(saved.capture());
        assertEquals(Timestamp.valueOf("2026-09-30 17:30:00"), saved.getValue().getSlaDeadline());
    }

    /** Bộ tham số đủ để handleUpdate đi qua isValid và gọi tới ticketDAO.update. */
    private void stubValidUpdateParams() {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn("3");
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
        when(request.getParameter("status")).thenReturn("Đang xử lý");
        when(ticketDAO.update(any(TechnicalRequest.class), anyInt(), any())).thenReturn(true);
    }

    // ------------------------------------------------------------------
    // Hợp đồng gắn vào phiếu phải thuộc đúng khách hàng của phiếu
    // ------------------------------------------------------------------

    private Contract contractOwnedBy(int contractId, int enterpriseId) {
        Contract c = new Contract();
        c.setContractId(contractId);
        c.setEnterpriseId(enterpriseId);
        return c;
    }

    @Test
    public void update_contractBelongsToAnotherEnterprise_isRejected() throws Exception {
        // Không có ràng buộc nào ở CSDL cho cặp (enterprise_id, contract_id),
        // nên đây là tuyến chặn duy nhất. Thiếu nó là ghi được phiếu "khách
        // hàng B, hợp đồng của A" -- trang chi tiết, PDF và Excel đều hiện
        // hợp đồng của công ty khác.
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());
        stubValidUpdateParams();
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("contractId")).thenReturn("77");
        when(contractDAO.findById(77)).thenReturn(contractOwnedBy(77, 999)); // của khách hàng khác

        controller.doPost(request, response);

        verify(ticketDAO, never()).update(any(TechnicalRequest.class), anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=edit&id=3&error=contract_mismatch");
    }

    @Test
    public void update_contractBelongsToSameEnterprise_isAccepted() throws Exception {
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());
        stubValidUpdateParams();
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("contractId")).thenReturn("77");
        when(contractDAO.findById(77)).thenReturn(contractOwnedBy(77, 10));

        controller.doPost(request, response);

        verify(ticketDAO).update(any(TechnicalRequest.class), anyInt(), any());
    }

    @Test
    public void update_contractIdMissingAndPickerNeverLoaded_keepsOldContract() throws Exception {
        // Form gửi đi trước khi AJAX loadContracts() xong (mạng chậm / JS tắt):
        // không có marker contractLoaded -> phải GIỮ hợp đồng cũ, nếu không mỗi
        // lần lưu vội là mất liên kết hợp đồng.
        TechnicalRequest existing = fullyValidExistingTicket();
        existing.setContractId(55);
        when(ticketDAO.findById(3)).thenReturn(existing);
        stubValidUpdateParams();
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("contractLoaded")).thenReturn(null);
        when(contractDAO.findById(55)).thenReturn(contractOwnedBy(55, 10));

        controller.doPost(request, response);

        ArgumentCaptor<TechnicalRequest> saved = ArgumentCaptor.forClass(TechnicalRequest.class);
        verify(ticketDAO).update(saved.capture(), anyInt(), any());
        assertEquals(Integer.valueOf(55), saved.getValue().getContractId());
    }

    @Test
    public void update_contractClearedWhilePickerWasUsable_detachesInsteadOfRestoring() throws Exception {
        // Người dùng đổi sang khách hàng khác (JS xoá trắng ô hợp đồng) hoặc
        // chọn "Không gắn hợp đồng". Marker contractLoaded=1 chứng tỏ ô chọn đã
        // dùng được, nên để trống là CỐ Ý -- khôi phục hợp đồng cũ ở đây chính
        // là cách phiếu dính hợp đồng của khách hàng khác.
        TechnicalRequest existing = fullyValidExistingTicket();
        existing.setContractId(55);
        when(ticketDAO.findById(3)).thenReturn(existing);
        stubValidUpdateParams();
        when(request.getParameter("contractId")).thenReturn("");
        when(request.getParameter("contractLoaded")).thenReturn("1");

        controller.doPost(request, response);

        ArgumentCaptor<TechnicalRequest> saved = ArgumentCaptor.forClass(TechnicalRequest.class);
        verify(ticketDAO).update(saved.capture(), anyInt(), any());
        assertNull("Phải gỡ hẳn hợp đồng, không được giữ lại của khách hàng cũ",
                saved.getValue().getContractId());
    }

    @Test
    public void create_contractBelongsToAnotherEnterprise_isRejected() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
        when(request.getParameter("contractId")).thenReturn("77");
        when(contractDAO.findById(77)).thenReturn(contractOwnedBy(77, 999));

        controller.doPost(request, response);

        verify(ticketDAO, never()).insert(any(TechnicalRequest.class));
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=new&error=contract_mismatch");
    }

    @Test
    public void update_contractIdPointsAtDeletedContract_isRejected() throws Exception {
        // findById trả null (hợp đồng đã bị xoá mềm hoặc id bịa) -- không được
        // coi là hợp lệ chỉ vì "không tra được để so".
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());
        stubValidUpdateParams();
        when(request.getParameter("contractId")).thenReturn("77");
        when(contractDAO.findById(77)).thenReturn(null);

        controller.doPost(request, response);

        verify(ticketDAO, never()).update(any(TechnicalRequest.class), anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=edit&id=3&error=contract_mismatch");
    }

    // ------------------------------------------------------------------
    // GET ?action=new / ?action=edit -- trang form cũng phải gác quyền
    // ------------------------------------------------------------------

    @Test
    public void newForm_withoutFullAccess_returns403InsteadOfRenderingTheForm() throws Exception {
        loginAs("Kỹ thuật", 50); // chỉ View only trên TICKET
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(request, never()).getRequestDispatcher("/jsp/customersupport/addnewTicket.jsp");
    }

    @Test
    public void editForm_technicianNotAssignedToThisTicket_returns403() throws Exception {
        loginAs("Kỹ thuật", 51); // phiếu được giao cho kỹ thuật viên 50
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(request, never()).getRequestDispatcher("/jsp/customersupport/updateTicket.jsp");
    }

    /**
     * Ngoại lệ của PERMISSIONS.md: kỹ thuật viên được giao phiếu vẫn phải mở
     * được form sửa -- gác quyền ở GET không được chặt tay hơn handleUpdate,
     * không thì họ có quyền lưu nhưng không có đường vào để lưu.
     */
    @Test
    public void editForm_assignedTechnician_canOpenTheForm() throws Exception {
        loginAs("Kỹ thuật", 50); // đúng assignedTechnicianId của phiếu
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/customersupport/updateTicket.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(response, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(dispatcher).forward(request, response);
    }

    // ------------------------------------------------------------------
    // GET ?action=exportPdf
    // ------------------------------------------------------------------

    /** Mở lại file PDF controller vừa ghi ra, bằng chính PDFBox. */
    private PDDocument exportPdfAndReopen(TechnicalRequest ticket) throws Exception {
        ByteArrayOutputStream captured = captureResponseBody();
        when(request.getParameter("action")).thenReturn("exportPdf");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(ticket);

        controller.doGet(request, response);

        return Loader.loadPDF(captured.toByteArray());
    }

    @Test
    public void exportPdf_shortTicket_fitsOnASinglePage() throws Exception {
        try (PDDocument pdf = exportPdfAndReopen(ticketForExport())) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }

    /**
     * description/resolution_summary là cột TEXT nên dài bao nhiêu cũng được.
     * Bản đầu chỉ tạo đúng 1 trang A4 và cứ trừ dần toạ độ y, nên phần vượt
     * quá chiều cao trang bị vẽ ra NGOÀI vùng giấy: file mở lên vẫn bình
     * thường, chỉ mất hẳn phần cuối mà không báo gì. Chốt lại bằng 2 điều
     * kiểm: có sang trang mới, VÀ dòng cuối cùng thật sự đọc lại được.
     */
    @Test
    public void exportPdf_longDescription_flowsOntoNewPagesInsteadOfBeingDrawnOffPage() throws Exception {
        TechnicalRequest t = ticketForExport();
        StringBuilder longText = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            longText.append("Dong mo ta so ").append(i).append(". ");
        }
        longText.append("KETTHUCMOTA");
        t.setDescription(longText.toString());
        t.setResolutionSummary("KETTHUCKETQUA");

        try (PDDocument pdf = exportPdfAndReopen(t)) {
            assertTrue("Mô tả dài phải tràn sang trang mới, không được vẽ lố ra ngoài trang",
                    pdf.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue("Dòng cuối của mô tả phải còn trong file", text.contains("KETTHUCMOTA"));
            assertTrue("Phần kết quả xử lý phải còn trong file", text.contains("KETTHUCKETQUA"));
        }
    }
}

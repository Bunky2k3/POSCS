package poscs.controller;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.Province;
import poscs.model.User;
import poscs.model.Role;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cho DashboardController -- tổng hợp số liệu trang tổng quan. Phần lớn
 * là truyền dữ liệu thẳng từ DAO (ít giá trị test), nên tập trung vào phần
 * TÍNH TOÁN thật trong controller: % tăng trưởng doanh thu so tháng trước
 * (kể cả trường hợp chia cho 0 khi tháng trước chưa có doanh thu), và số
 * ngày còn lại tới hạn của từng hợp đồng sắp hết hạn.
 */
public class DashboardControllerTest {

    private DashboardController controller;
    private CustomerDAO customerDAO;
    private ContractDAO contractDAO;
    private TechnicalSupportTicketDAO ticketDAO;
    private EmployeeDAO employeeDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void setUp() throws Exception {
        controller = new DashboardController();
        customerDAO = mock(CustomerDAO.class);
        contractDAO = mock(ContractDAO.class);
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        setField(controller, "customerDAO", customerDAO);
        setField(controller, "contractDAO", contractDAO);
        setField(controller, "ticketDAO", ticketDAO);
        setField(controller, "employeeDAO", employeeDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        // Mặc định: KHÔNG ai đăng nhập -> trang rơi về phạm vi toàn chi nhánh,
        // đúng như các test cũ vẫn giả định. Test nào cần một người cụ thể thì
        // gọi dangNhap() bên dưới.
        when(request.getSession(false)).thenReturn(null);

        // Stub chung cho mọi test -- không phải trọng tâm nhưng bắt buộc để
        // tránh NPE khi controller đọc qua các map/list này.
        when(contractDAO.countStatusSummary(nullable(List.class), nullable(Period.class), nullable(String.class),
                anyBoolean(), nullable(List.class))).thenReturn(Collections.emptyMap());
        when(ticketDAO.countStatusSummary(nullable(List.class), nullable(Period.class), nullable(List.class)))
                .thenReturn(Collections.emptyMap());
        when(contractDAO.findActiveInPeriod(anyInt(), nullable(List.class), nullable(List.class), any(Period.class)))
                .thenReturn(Collections.emptyList());
        when(ticketDAO.findNeedingAttention(anyInt(), nullable(List.class), nullable(List.class)))
                .thenReturn(Collections.emptyList());
        // Hai hàm tiền: không stub thì trả null và controller ném NPE lúc so
        // sánh doanh thu tháng này với tháng trước.
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);
        when(contractDAO.sumInvoiceAmountInPeriod(nullable(Period.class), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);
        // doGet luôn forward /dashboard.jsp ở cuối -- không stub thì
        // getRequestDispatcher trả null và .forward() ném NPE ở MỌI test.
        RequestDispatcher defaultDispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(anyString())).thenReturn(defaultDispatcher);
    }

    /**
     * Giả lập một người đang đăng nhập. Phạm vi mặc định của Dashboard phụ
     * thuộc vai trò, nên test nào nói về phạm vi đều phải đi qua đây.
     */
    private User dangNhap(int userId, String roleName) {
        User user = new User();
        user.setUserId(userId);
        Role role = new Role();
        role.setRoleName(roleName);
        user.setRole(role);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(request.getSession(false)).thenReturn(session);
        when(employeeDAO.findTeamUserIds(userId)).thenReturn(List.of(userId));
        return user;
    }

    /**
     * Người dùng tự tích tỉnh trên thanh lọc.
     *
     * <p>Phải gửi KÈM cờ "provinceSet": thiếu nó thì controller coi như lần đầu
     * mở trang và tự tích địa bàn của người đăng nhập, đúng thứ cờ đó sinh ra
     * để phân biệt.
     */
    private void chonTinh(String... provinceIds) {
        when(request.getParameter("provinceSet")).thenReturn("1");
        when(request.getParameterValues("provinceId")).thenReturn(provinceIds);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void revenueGrewFromLastMonth_computesPositiveTrendPercent() throws Exception {
        LocalDate today = LocalDate.now();
        when(contractDAO.sumInvoiceAmountByMonth(eq(today.getYear()), eq(today.getMonthValue()), nullable(List.class),
                nullable(List.class)))
                .thenReturn(BigDecimal.valueOf(1_500_000));
        LocalDate lastMonth = today.minusMonths(1);
        when(contractDAO.sumInvoiceAmountByMonth(eq(lastMonth.getYear()), eq(lastMonth.getMonthValue()),
                nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.valueOf(1_000_000));

        controller.doGet(request, response);

        // (1.500.000 - 1.000.000) / 1.000.000 * 100 = 50.0%
        verify(request).setAttribute("revenueTrendPercent", 50.0);
    }

    @Test
    public void noRevenueLastMonth_skipsTrendPercentToAvoidDivisionByZero() throws Exception {
        LocalDate today = LocalDate.now();
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(request, never()).setAttribute(eq("revenueTrendPercent"), any());
    }

    @Test
    public void bangHopDong_dayGiaTriTungHopDongRaManHinh() throws Exception {
        LocalDate today = LocalDate.now();
        Contract c = new Contract();
        c.setContractId(9);
        c.setEndDate(Date.valueOf(today.plusDays(10)));
        c.setContractValue(BigDecimal.valueOf(5_000_000));
        List<Contract> inWindow = Arrays.asList(c);
        when(contractDAO.findActiveInPeriod(anyInt(), nullable(List.class), nullable(List.class), any(Period.class)))
                .thenReturn(inWindow);
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(request).setAttribute(eq("windowContracts"), eq(inWindow));
        verify(request).setAttribute(eq("contractValues"),
                argThat((Map<Integer, BigDecimal> m) -> BigDecimal.valueOf(5_000_000).compareTo(m.get(9)) == 0));
    }

    /**
     * Không chọn kỳ thì bảng hợp đồng lấy cửa sổ THÁNG ĐANG CHẠY.
     *
     * <p>Không phải "mọi thời điểm": bảng để trả lời "tháng này tôi đang cầm
     * những gì", đổ ra cả hợp đồng ba năm trước thì câu đó hết nghĩa. Cũng
     * không phải cửa sổ 30 ngày như bảng "sắp hết hạn" cũ -- chính nó làm
     * người có 8 hợp đồng đang chạy chỉ thấy 1 dòng.
     */
    @Test
    public void bangHopDong_khongChonKy_thiLayThangDangChay() throws Exception {
        LocalDate today = LocalDate.now();
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(contractDAO).findActiveInPeriod(anyInt(), nullable(List.class), nullable(List.class),
                argThat(w -> w.getFrom().toLocalDate().equals(today.withDayOfMonth(1))
                        && w.getTo().toLocalDate().getMonthValue() == today.getMonthValue()));
    }

    /** Chọn kỳ thì bảng đi theo kỳ đó, không cứng ở tháng hiện tại. */
    @Test
    public void bangHopDong_chonKy_thiDiTheoKy() throws Exception {
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("q3");
        when(contractDAO.sumInvoiceAmountInPeriod(any(Period.class), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(contractDAO).findActiveInPeriod(anyInt(), nullable(List.class), nullable(List.class),
                argThat(w -> "2026-07-01".equals(w.getFrom().toString())
                        && "2026-09-30".equals(w.getTo().toString())));
        verify(request).setAttribute("contractWindowLabel", "Quý 3/2026");
    }

    /**
     * Cột "Giá trị" ở bảng hợp đồng là giá trị theo ĐIỀU KHOẢN đã
     * cộng phụ lục, không phải tổng các kỳ thanh toán như trước V27. Hai thứ đó
     * lệch nhau đúng bằng công nợ, và hợp đồng vừa được phụ lục bổ sung mà vẫn
     * hiện con số cũ là thứ người dùng báo lại đầu tiên.
     */
    @Test
    public void bangHopDong_giaTriGomCaPhuLucDaKy() throws Exception {
        LocalDate today = LocalDate.now();
        Contract c = new Contract();
        c.setContractId(9);
        c.setEndDate(Date.valueOf(today.plusDays(10)));
        c.setContractValue(BigDecimal.valueOf(1_500_000_000L));
        c.setAmendmentValueSigned(BigDecimal.valueOf(250_000_000L));
        when(contractDAO.findActiveInPeriod(anyInt(), nullable(List.class), nullable(List.class), any(Period.class)))
                .thenReturn(Arrays.asList(c));
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(request).setAttribute(eq("contractValues"),
                argThat((Map<Integer, BigDecimal> m) ->
                        BigDecimal.valueOf(1_750_000_000L).compareTo(m.get(9)) == 0));
    }

    @Test
    public void alwaysForwardsToDashboardJsp() throws Exception {
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/dashboard.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(dispatcher).forward(request, response);
    }

    // ------------------------------------------------------------------
    // Lọc theo tỉnh
    // ------------------------------------------------------------------

    /**
     * Lọc tỉnh phải xuống TỚI MỌI truy vấn của trang. Bỏ sót một chỗ là trang
     * trộn hai phạm vi: ô KPI đếm cả nước nằm ngay cạnh bảng chỉ có dữ liệu
     * một tỉnh -- sai lệch kiểu đó người dùng không có cách nào tự nhận ra.
     */
    @Test
    public void provinceSelected_narrowsEveryQueryOnThePage() throws Exception {
        chonTinh("3");
        when(customerDAO.countUpToEndOfPeriod(eq(List.of(3)), nullable(Period.class), nullable(List.class))).thenReturn(2);
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), eq(List.of(3)), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);
        when(contractDAO.countStatusSummary(eq(List.of(3)), nullable(Period.class), nullable(String.class), anyBoolean(),
                nullable(List.class)))
                .thenReturn(Collections.emptyMap());
        when(ticketDAO.countStatusSummary(eq(List.of(3)), nullable(Period.class), nullable(List.class)))
                .thenReturn(Collections.emptyMap());
        when(contractDAO.findActiveInPeriod(anyInt(), eq(List.of(3)), nullable(List.class), any(Period.class))).thenReturn(Collections.emptyList());
        when(ticketDAO.findNeedingAttention(anyInt(), eq(List.of(3)), nullable(List.class))).thenReturn(Collections.emptyList());

        controller.doGet(request, response);

        verify(customerDAO).countUpToEndOfPeriod(eq(List.of(3)), nullable(Period.class), nullable(List.class));
        verify(customerDAO).countNewInPeriod(eq(List.of(3)), nullable(Period.class), nullable(List.class));
        verify(contractDAO).countStatusSummary(eq(List.of(3)), nullable(Period.class), nullable(String.class), anyBoolean(),
                nullable(List.class));
        verify(contractDAO).findActiveInPeriod(anyInt(), eq(List.of(3)), nullable(List.class), any(Period.class));
        verify(ticketDAO).countStatusSummary(eq(List.of(3)), nullable(Period.class), nullable(List.class));
        verify(ticketDAO).countOverdueOrDueSoon(eq(List.of(3)), nullable(List.class));
        verify(ticketDAO).findNeedingAttention(anyInt(), eq(List.of(3)), nullable(List.class));
        verify(contractDAO, times(2)).sumInvoiceAmountByMonth(anyInt(), anyInt(), eq(List.of(3)), nullable(List.class));
        verify(request).setAttribute("provinceFilters", List.of(3));
    }

    // ------------------------------------------------------------------
    // Phạm vi: của tôi / toàn chi nhánh
    // ------------------------------------------------------------------

    /**
     * Nhân viên mở trang ra thấy PHẦN VIỆC CỦA MÌNH trước -- đó là thứ họ vào
     * đây để xem. Mặc định này chỉ áp khi URL chưa có tham số scope.
     */
    @Test
    public void nhanVien_macDinhThayViecCuaMinh() throws Exception {
        dangNhap(7, "Sales");

        controller.doGet(request, response);

        verify(request).setAttribute("scopeFilter", "mine");
        verify(contractDAO).countStatusSummary(nullable(List.class), nullable(Period.class), nullable(String.class),
                anyBoolean(), eq(List.of(7)));
        verify(customerDAO).countUpToEndOfPeriod(nullable(List.class), nullable(Period.class), eq(List.of(7)));
        verify(ticketDAO).countStatusSummary(nullable(List.class), nullable(Period.class), eq(List.of(7)));
    }

    /**
     * Admin thì ngược lại: họ không đứng tên hợp đồng nào, lọc theo tên họ là
     * ra một trang trống trơn.
     */
    @Test
    public void admin_macDinhThayToanChiNhanh() throws Exception {
        dangNhap(1, "Admin");

        controller.doGet(request, response);

        verify(request).setAttribute("scopeFilter", "all");
        verify(contractDAO).countStatusSummary(nullable(List.class), nullable(Period.class), nullable(String.class),
                anyBoolean(), isNull());
    }

    /** Mặc định chỉ là mặc định: ai cũng chuyển qua lại được bằng ô chọn. */
    @Test
    public void nhanVien_chonToanChiNhanh_thiKhongLocNua() throws Exception {
        dangNhap(7, "Sales");
        when(request.getParameter("scope")).thenReturn("all");

        controller.doGet(request, response);

        verify(request).setAttribute("scopeFilter", "all");
        verify(contractDAO).countStatusSummary(nullable(List.class), nullable(Period.class), nullable(String.class),
                anyBoolean(), isNull());
    }

    @Test
    public void admin_chonCuaToi_thiLocTheoChinhMinh() throws Exception {
        dangNhap(1, "Admin");
        when(request.getParameter("scope")).thenReturn("mine");

        controller.doGet(request, response);

        verify(request).setAttribute("scopeFilter", "mine");
        verify(contractDAO).countStatusSummary(nullable(List.class), nullable(Period.class), nullable(String.class),
                anyBoolean(), eq(List.of(1)));
    }

    /**
     * "Của tôi" của một trưởng nhóm gồm CẢ CẤP DƯỚI -- mở trang ra phải thấy
     * phần việc của nhóm, không phải mỗi mấy hợp đồng đứng tên riêng họ.
     */
    @Test
    public void truongNhom_cuaToi_gomCaCapDuoi() throws Exception {
        dangNhap(7, "Sales");
        when(employeeDAO.findTeamUserIds(7)).thenReturn(List.of(7, 8, 9));

        controller.doGet(request, response);

        verify(request).setAttribute("teamSize", 3);
        verify(contractDAO).findActiveInPeriod(anyInt(), nullable(List.class), eq(List.of(7, 8, 9)), any(Period.class));
        verify(ticketDAO).findNeedingAttention(anyInt(), nullable(List.class), eq(List.of(7, 8, 9)));
    }

    /**
     * Chưa đăng nhập (hoặc session hết hạn) thì KHÔNG lọc: hiện toàn chi nhánh
     * còn hơn một trang trống mà không có gì giải thích.
     */
    @Test
    public void khongBietLaAi_thiKhongLoc() throws Exception {
        when(request.getParameter("scope")).thenReturn("mine");

        controller.doGet(request, response);

        verify(request).setAttribute("scopeFilter", "all");
        verify(contractDAO).countStatusSummary(nullable(List.class), nullable(Period.class), nullable(String.class),
                anyBoolean(), isNull());
    }

    /** Phạm vi đi cùng địa bàn và kỳ, không cái nào nuốt cái nào. */
    @Test
    public void phamVi_diCungLocTinhVaKy() throws Exception {
        dangNhap(7, "Sales");
        chonTinh("3");
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("q3");

        controller.doGet(request, response);

        verify(contractDAO).countStatusSummary(eq(List.of(3)), any(Period.class), nullable(String.class), anyBoolean(),
                eq(List.of(7)));
        verify(contractDAO, times(2)).sumInvoiceAmountInPeriod(any(Period.class), eq(List.of(3)), eq(List.of(7)));
    }

    // ------------------------------------------------------------------
    // Lọc theo kỳ (tháng/quý)
    // ------------------------------------------------------------------

    /**
     * Chọn quý thì mọi truy vấn "phát sinh trong kỳ" phải nhận đúng khoảng ngày
     * của quý đó. Doanh thu gọi hai lần: kỳ đang xem và kỳ liền trước, để tính
     * % tăng trưởng -- so với tháng trước như cũ thì con số vô nghĩa khi người
     * dùng đang xem theo quý.
     */
    @Test
    public void quarterSelected_passesThatDateRangeToEveryPeriodQuery() throws Exception {
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("q3");
        when(contractDAO.sumInvoiceAmountInPeriod(any(Period.class), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        // Quý 3/2026 = 01/07/2026 -> 30/09/2026
        verify(customerDAO).countUpToEndOfPeriod(eq(List.<Integer>of()),
                argThat(p -> "2026-07-01".equals(p.getFrom().toString())
                        && "2026-09-30".equals(p.getTo().toString())), nullable(List.class));
        verify(customerDAO).countNewInPeriod(eq(List.<Integer>of()), any(Period.class), nullable(List.class));
        verify(contractDAO).countStatusSummary(eq(List.<Integer>of()), any(Period.class), isNull(), anyBoolean(),
                nullable(List.class));
        verify(ticketDAO).countStatusSummary(eq(List.<Integer>of()), any(Period.class), nullable(List.class));
        verify(contractDAO, times(2)).sumInvoiceAmountInPeriod(any(Period.class), nullable(List.class),
                nullable(List.class));
        // Có kỳ thì không được rơi về nhánh "tháng hiện tại" nữa.
        verify(contractDAO, never()).sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(List.class),
                nullable(List.class));
        verify(request).setAttribute("periodLabel", "Quý 3/2026");
    }

    /**
     * Hai bảng cuối trang là cảnh báo tính theo hôm nay, cố ý KHÔNG theo kỳ:
     * "hợp đồng sắp hết hạn trong quý 1 năm ngoái" là câu vô nghĩa.
     */
    @Test
    public void periodSelected_doesNotNarrowTheAsOfTodayTables() throws Exception {
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("q3");
        when(contractDAO.sumInvoiceAmountInPeriod(any(Period.class), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(contractDAO).findActiveInPeriod(anyInt(), eq(List.<Integer>of()), nullable(List.class), any(Period.class));
        verify(ticketDAO).findNeedingAttention(anyInt(), eq(List.<Integer>of()), nullable(List.class));
    }

    // ------------------------------------------------------------------
    // Địa bàn của chính người đăng nhập
    // ------------------------------------------------------------------

    /**
     * Sales mở trang LẦN ĐẦU thì các tỉnh họ phụ trách đã được tích sẵn, và
     * lọc đó xuống tới mọi truy vấn.
     *
     * <p>Đây là yêu cầu gốc: "sale login thì phải lọc theo tỉnh thành của họ
     * luôn". Không có ca này thì một lần đổi thứ tự trong doGet -- đọc địa bàn
     * sau khi đã gọi DAO -- là tính năng chết lặng mà mọi test khác vẫn xanh.
     */
    @Test
    public void sales_macDinhLocTheoDiaBanCuaMinh() throws Exception {
        dangNhap(7, "Sales");
        when(employeeDAO.findProvincesCoveredBy(7))
                .thenReturn(List.of(new Province(3, "Thanh pho Ha Noi"), new Province(5, "Tinh Quang Ninh")));

        controller.doGet(request, response);

        verify(contractDAO).countStatusSummary(eq(List.of(3, 5)), nullable(Period.class), nullable(String.class),
                anyBoolean(), eq(List.of(7)));
        verify(customerDAO).countUpToEndOfPeriod(eq(List.of(3, 5)), nullable(Period.class), eq(List.of(7)));
        verify(ticketDAO).countOverdueOrDueSoon(eq(List.of(3, 5)), eq(List.of(7)));
        verify(request).setAttribute("provinceFilters", List.of(3, 5));
        // Cờ để màn hình nói "địa bàn bạn phụ trách" thay vì "2 tỉnh đang chọn".
        verify(request).setAttribute("provinceFilterIsMine", true);
    }

    /** Màn hình phải đọc được TÊN các tỉnh đó để in ra dưới lời chào. */
    @Test
    public void sales_manHinhNhanDuocDanhSachTinhPhuTrach() throws Exception {
        dangNhap(7, "Sales");
        List<Province> mine = List.of(new Province(3, "Thanh pho Ha Noi"));
        when(employeeDAO.findProvincesCoveredBy(7)).thenReturn(mine);

        controller.doGet(request, response);

        verify(request).setAttribute("myProvinces", mine);
    }

    /**
     * Bỏ tích HẾT là xem toàn chi nhánh -- và phải giữ được ý đó.
     *
     * <p>Bỏ hết thì form không gửi lên tham số provinceId nào, giống hệt lần
     * đầu mở trang. Không phân biệt hai ca này thì người dùng bỏ tích xong
     * trang tự tích lại, và không cách nào thoát khỏi địa bàn của mình.
     */
    @Test
    public void boTichHet_thiXemToanChiNhanh() throws Exception {
        dangNhap(7, "Sales");
        when(employeeDAO.findProvincesCoveredBy(7)).thenReturn(List.of(new Province(3, "Thanh pho Ha Noi")));
        when(request.getParameter("provinceSet")).thenReturn("1");
        when(request.getParameterValues("provinceId")).thenReturn(null);

        controller.doGet(request, response);

        verify(contractDAO).countStatusSummary(eq(List.<Integer>of()), nullable(Period.class), nullable(String.class),
                anyBoolean(), eq(List.of(7)));
        verify(request).setAttribute("provinceFilters", List.of());
    }

    /**
     * Người dùng tích tay một tỉnh NGOÀI địa bàn của mình thì nghe theo họ, và
     * cờ "đang ở mặc định" phải tắt -- nếu không màn hình nói dối là đang xem
     * địa bàn của bạn.
     */
    @Test
    public void tichTinhKhac_thiKhongConLaMacDinh() throws Exception {
        dangNhap(7, "Sales");
        when(employeeDAO.findProvincesCoveredBy(7)).thenReturn(List.of(new Province(3, "Thanh pho Ha Noi")));
        chonTinh("9");

        controller.doGet(request, response);

        verify(request).setAttribute("provinceFilters", List.of(9));
        verify(request).setAttribute("provinceFilterIsMine", false);
    }

    /**
     * Chưa được giao tỉnh nào thì KHÔNG siết theo địa bàn -- cùng nguyên tắc
     * với ListScope của hai màn hình danh sách: chưa xếp vào thì chưa bị siết.
     * Siết ngược lại là người mới vào mở trang ra thấy mọi ô bằng 0.
     */
    @Test
    public void chuaDuocGiaoTinh_thiKhongSietDiaBan() throws Exception {
        dangNhap(7, "Sales");
        when(employeeDAO.findProvincesCoveredBy(7)).thenReturn(List.of());

        controller.doGet(request, response);

        verify(contractDAO).countStatusSummary(eq(List.<Integer>of()), nullable(Period.class), nullable(String.class),
                anyBoolean(), eq(List.of(7)));
    }

    /** Tham số rác trên URL không được làm trang vỡ -- coi như không lọc. */
    @Test
    public void invalidProvinceParam_fallsBackToNationwide() throws Exception {
        chonTinh("khong-phai-so");
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(List.class), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(contractDAO).countStatusSummary(eq(List.<Integer>of()), isNull(), isNull(), anyBoolean(), nullable(List.class));
        verify(request).setAttribute("provinceFilters", List.of());
    }
}

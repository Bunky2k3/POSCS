package poscs.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.Period;
import poscs.common.AccessControl;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.Province;
import poscs.model.User;

/**
 * Tổng hợp số liệu cho trang tổng quan (dashboard.jsp).
 *
 * BA bộ lọc, đều do người dùng tự chọn chứ KHÔNG phải phạm vi quyền -- ai cũng
 * xem được toàn chi nhánh, đúng như PERMISSIONS.md quy định:
 * "provinceId" thu hẹp về một tỉnh, "year"+"period" thu hẹp về một tháng/quý,
 * và "scope" thu hẹp về phần việc của chính người đang đăng nhập.
 *
 * Mặc định của "scope" KHÁC nhau theo vai trò: nhân viên mở trang ra thấy việc
 * của mình trước (đó là thứ họ vào đây để xem), Admin thấy toàn chi nhánh (họ
 * không "phụ trách" hợp đồng nào, lọc theo tên họ thì trang trống trơn). Cả hai
 * đổi qua lại được bằng ô chọn, nên đây là mặc định chứ không phải giới hạn.
 *
 * "Của tôi" gồm CẢ CẤP DƯỚI trực tiếp: trưởng nhóm mở trang ra phải thấy phần
 * việc của nhóm mình, không phải mỗi mấy hợp đồng đứng tên riêng họ. Xem
 * EmployeeDAO.findTeamUserIds.
 *
 * MỌI thứ về phiếu hỗ trợ đã ra khỏi trang này theo yêu cầu người dùng
 * (2026-09-19): biểu đồ trạng thái, ô KPI và bảng "Phiếu cần xử lý". Nên
 * controller cũng thôi gọi ba truy vấn phiếu -- để lại thì trang trả tiền cho
 * dữ liệu không ai đọc. Hàm DAO thì còn nguyên: màn hình phiếu hỗ trợ và
 * NotificationScheduler vẫn dùng chúng.
 *
 * Bảng hợp đồng thì ĐI THEO KỲ đang chọn (mặc định tháng đang chạy), khác
 * bản trước -- lúc đó nó là "sắp hết hạn tính tới hôm nay" nên cố ý đứng
 * ngoài bộ lọc kỳ.
 */
@WebServlet(name = "DashboardController", urlPatterns = {"/dashboard"})
public class DashboardController extends HttpServlet {

    /**
     * Số dòng tối đa của bảng hợp đồng. 8 chứ không phải 5 như bảng "sắp hết
     * hạn" cũ: bảng giờ liệt kê mọi hợp đồng trong kỳ nên tập thường lớn hơn,
     * mà cắt ở 5 thì một nhân viên có 8 hợp đồng lại thấy thiếu -- đúng thứ
     * vừa sửa. Quá số này thì đã có link "Xem tất cả".
     */
    private static final int CONTRACTS_IN_WINDOW_LIMIT = 8;

    /** Số dòng tối đa của bảng khách hàng -- bằng bảng hợp đồng để hai thẻ cân nhau. */
    private static final int CUSTOMERS_IN_SCOPE_LIMIT = 8;
    private static final String[] WEEKDAY_VI = {
        "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"
    };

    /** Giá trị của tham số "scope" trên URL. */
    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_ALL = "all";

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final ContractDAO contractDAO = new ContractDAO();
    private final AddressDAO addressDAO = new AddressDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        LocalDate today = LocalDate.now();

        request.setAttribute("provinceList", addressDAO.findBranchProvinces());

        // Bộ lọc kỳ (tháng/quý). Mỗi ô lấy mốc ngày riêng của loại dữ liệu đó:
        // khách theo ngày tham gia, hợp đồng theo ngày ký, doanh thu theo ngày
        // thanh toán, phiếu theo ngày tạo -- không có một "ngày" chung cho cả
        // trang. Không chọn kỳ thì trang giữ nguyên nghĩa cũ (tháng hiện tại).
        Period period = Period.parse(request.getParameter("year"), request.getParameter("period"));
        ContractController.setPeriodAttributes(request, period);

        // ===== Phạm vi: của tôi hay toàn chi nhánh =====
        User me = AccessControl.currentUser(request);
        String scopeParam = request.getParameter("scope");
        // Không có tham số trên URL = lần đầu mở trang -> lấy mặc định theo vai
        // trò. Có tham số thì nghe theo người dùng, kể cả khi họ chọn ngược lại
        // mặc định của mình.
        boolean mine = scopeParam == null
                ? (me != null && !AccessControl.isAdmin(request))
                : SCOPE_MINE.equals(scopeParam);
        // "Của tôi" mà không biết tôi là ai thì không lọc được gì -- để rơi về
        // toàn chi nhánh còn hơn hiện một trang trống không giải thích nổi.
        List<Integer> ownerIds = mine && me != null
                ? employeeDAO.findTeamUserIds(me.getUserId())
                : null;
        mine = ownerIds != null;
        request.setAttribute("scopeFilter", mine ? SCOPE_MINE : SCOPE_ALL);
        // Số người trong phạm vi: 1 là "chỉ mình tôi", >1 nghĩa là đang gộp cả
        // cấp dưới -- màn hình nói rõ ra, nếu không thì con số của trưởng nhóm
        // trông như sai so với những gì họ tự làm.
        request.setAttribute("teamSize", ownerIds == null ? 0 : ownerIds.size());

        // ===== Bộ lọc địa bàn: NHIỀU tỉnh, mặc định là địa bàn của chính mình =====
        //
        // Lọc áp cho TẤT CẢ số liệu trên trang, không riêng vài ô -- nửa lọc nửa
        // không thì KPI "15 hợp đồng" nằm cạnh bảng chỉ có 4 dòng, không biết
        // tin số nào.
        //
        // Ghép với phạm vi người bằng HOẶC, GIỐNG ListScope của hai màn hình
        // danh sách: việc tôi đứng tên, cộng việc nằm trong địa bàn tôi giữ.
        //
        // Bản đầu ghép bằng VÀ (người dùng chốt thế), và nó sai ngay trên dữ
        // liệu thật: sales4 mất 3 hợp đồng họ ĐỨNG TÊN vì khách ở Hưng Yên --
        // tỉnh của người khác -- cộng 6 hợp đồng trong địa bàn họ giữ mà người
        // khác đứng tên. Dashboard nói "1 hợp đồng đang hiệu lực" trong khi
        // danh sách hợp đồng của chính họ liệt kê 10 dòng. Đừng đổi lại VÀ.
        List<Province> myProvinces = me == null ? null : employeeDAO.findProvincesCoveredBy(me.getUserId());
        if (myProvinces == null) {
            myProvinces = List.of();
        }
        request.setAttribute("myProvinces", myProvinces);
        List<Integer> provinceFilters = resolveProvinceFilters(request, myProvinces);
        request.setAttribute("provinceFilters", provinceFilters);
        // Đang tích đúng bằng địa bàn của mình = đang ở mặc định. Màn hình đổi
        // câu chữ theo nó ("địa bàn bạn phụ trách" thay vì "địa bàn đang chọn").
        request.setAttribute("provinceFilterIsMine",
                !provinceFilters.isEmpty()
                        && provinceFilters.size() == myProvinces.size()
                        && provinceFilters.containsAll(provinceIdsOf(myProvinces)));

        // ===== KPI: khách hàng =====
        // Tổng khách hàng là số LUỸ KẾ tới hết kỳ, không phải số phát sinh
        // trong kỳ -- nếu không thì ô này trùng nghĩa với ô "khách hàng mới"
        // ngay bên dưới nó.
        request.setAttribute("totalCustomers", customerDAO.countUpToEndOfPeriod(provinceFilters, period, ownerIds));
        request.setAttribute("newCustomersThisMonth",
                customerDAO.countNewInPeriod(provinceFilters, period, ownerIds));

        // ===== KPI: hợp đồng =====
        // direction = null: dải KPI này đếm CẢ hai chiều, cố ý. Trang tổng quan
        // là bức tranh chung, và đếm hợp đồng hai chiều chung một ô chỉ là rộng
        // chứ không sai. Khác hẳn doanh thu ngay dưới -- cộng tiền thu vào với
        // tiền trả ra thì con số vô nghĩa, nên chỗ đó lọc 'Bán' (xem V21).
        Map<String, Integer> contractStatusSummary = contractDAO.countStatusSummary(provinceFilters, period, null,
                false, ownerIds);
        request.setAttribute("contractStatusSummary", contractStatusSummary);

        // ===== KPI: doanh thu =====
        BigDecimal revenueThisMonth;
        BigDecimal revenueLastMonth;
        if (period != null) {
            revenueThisMonth = contractDAO.sumInvoiceAmountInPeriod(period, provinceFilters, ownerIds);
            revenueLastMonth = contractDAO.sumInvoiceAmountInPeriod(period.previous(), provinceFilters, ownerIds);
        } else {
            revenueThisMonth = contractDAO.sumInvoiceAmountByMonth(
                    today.getYear(), today.getMonthValue(), provinceFilters, ownerIds);
            LocalDate lastMonth = today.minusMonths(1);
            revenueLastMonth = contractDAO.sumInvoiceAmountByMonth(
                    lastMonth.getYear(), lastMonth.getMonthValue(), provinceFilters, ownerIds);
        }
        request.setAttribute("revenueThisMonth", revenueThisMonth);
        if (revenueLastMonth.compareTo(BigDecimal.ZERO) > 0) {
            double trendPercent = revenueThisMonth.subtract(revenueLastMonth)
                    .divide(revenueLastMonth, 4, java.math.RoundingMode.HALF_UP)
                    .doubleValue() * 100;
            request.setAttribute("revenueTrendPercent", Math.round(trendPercent * 10) / 10.0);
        }

        request.setAttribute("currentMonthNumber", today.getMonthValue());

        // ===== Badge ngày hôm nay =====
        Calendar cal = Calendar.getInstance();
        String weekday = WEEKDAY_VI[cal.get(Calendar.DAY_OF_WEEK) - 1];
        request.setAttribute("todayLabel", weekday + ", " + String.format("%02d/%02d/%04d",
                today.getDayOfMonth(), today.getMonthValue(), today.getYear()));

        // ===== Bảng hợp đồng trong kỳ =====
        //
        // Trước đây bảng này chỉ lấy hợp đồng hết hạn trong 30 ngày. Hệ quả đo
        // được trên dữ liệu thật: sales4 có 8 hợp đồng đang chạy mà bảng hiện
        // đúng 1 dòng -- đúng theo định nghĩa "sắp hết hạn" nhưng người dùng
        // đọc ra là trang hỏng. Giờ bảng lấy MỌI hợp đồng còn hiệu lực trong
        // kỳ, không lọc trạng thái; cột trạng thái tự nói ra từng cái ở đâu.
        //
        // Không chọn kỳ thì cửa sổ là THÁNG ĐANG CHẠY, không phải "mọi thời
        // điểm": bảng này để trả lời "tháng này tôi đang cầm những gì", mà đổ
        // ra cả hợp đồng của ba năm trước thì câu đó hết nghĩa. Chọn kỳ thì
        // nghe theo kỳ.
        Period contractWindow = period != null ? period : currentMonth(today);
        request.setAttribute("contractWindowLabel", contractWindow.getLabel());
        List<Contract> windowContracts =
                contractDAO.findActiveInPeriod(CONTRACTS_IN_WINDOW_LIMIT, provinceFilters, ownerIds, contractWindow);
        Map<Integer, BigDecimal> contractValues = new HashMap<>();
        for (Contract c : windowContracts) {
            // Giá trị theo ĐIỀU KHOẢN, đã cộng các phụ lục đã ký -- không phải
            // tổng các kỳ thanh toán như trước V27. Hai thứ đó khác nhau: hợp
            // đồng chưa lập kỳ nào hiện ra 0 đồng, và hợp đồng vừa được phụ lục
            // bổ sung thì vẫn hiện con số cũ.
            contractValues.put(c.getContractId(), c.getCurrentValue());
        }
        request.setAttribute("windowContracts", windowContracts);
        request.setAttribute("contractValues", contractValues);

        // ===== Bảng khách hàng =====
        // Cùng phạm vi và cùng mốc thời gian với ô KPI "Tổng khách hàng" ngay
        // trên nó -- xem CustomerDAO.findInScope.
        request.setAttribute("scopeCustomers",
                customerDAO.findInScope(CUSTOMERS_IN_SCOPE_LIMIT, provinceFilters, ownerIds, period));

        request.getRequestDispatcher("/dashboard.jsp").forward(request, response);
    }

    /**
     * Các tỉnh đang được tích trên thanh lọc.
     *
     * <p>Không có tham số {@code provinceSet} = lần đầu mở trang (hoặc mở từ
     * một link cũ) -> lấy MẶC ĐỊNH là địa bàn của chính mình. Có tham số thì
     * nghe theo người dùng, kể cả khi họ bỏ tích hết -- đó là cách duy nhất để
     * phân biệt "chưa chọn gì" với "đã bỏ tích hết để xem toàn chi nhánh". Hai
     * thứ đó gửi lên giống hệt nhau nếu chỉ nhìn {@code provinceId}.
     *
     * <p>Cùng lối với tham số "scope" ngay bên trên: mặc định theo vai, nhưng
     * người dùng chọn ngược lại thì nghe theo họ.
     *
     * <p>Id lạ trên URL bị bỏ qua chứ không làm vỡ trang; id không thuộc 18
     * tỉnh địa bàn thì đơn giản là không khớp bản ghi nào, không cần chặn.
     */
    private static List<Integer> resolveProvinceFilters(HttpServletRequest request, List<Province> myProvinces) {
        if (request.getParameter("provinceSet") == null) {
            return provinceIdsOf(myProvinces);
        }
        String[] raw = request.getParameterValues("provinceId");
        if (raw == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (String value : raw) {
            Integer id = parseIntOrNull(value);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static List<Integer> provinceIdsOf(List<Province> provinces) {
        List<Integer> ids = new ArrayList<>();
        for (Province p : provinces) {
            ids.add(p.getProvinceId());
        }
        return ids;
    }

    /** Tháng đang chạy, quy về một {@link Period} để dùng chung một đường lọc. */
    private static Period currentMonth(LocalDate today) {
        return Period.parse(String.valueOf(today.getYear()), "m" + today.getMonthValue());
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

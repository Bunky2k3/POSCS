package poscs.controller;

import java.io.IOException;
import java.sql.Date;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Address;
import poscs.model.Enterprise;

/**
 * Controller cho toàn bộ chức năng khách hàng (enterprises). Điều hướng
 * theo tham số "action" -- role nào được thao tác gì xem PERMISSIONS.md,
 * enforce bằng AccessControl.requireFullAccess ở đầu mỗi hàm handleCreate/
 * handleUpdate/handleDelete (Kỹ thuật/CSKH chỉ View only trên Customer).
 */
@WebServlet(name = "CustomerController", urlPatterns = {"/customer"})
public class CustomerController extends HttpServlet {

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/sale/listcustomer.jsp";
    private static final String DETAIL_VIEW = "/jsp/sale/viewcustomerdetail.jsp";
    private static final String CREATE_VIEW = "/jsp/sale/addnewcustomer.jsp";
    private static final String UPDATE_VIEW = "/jsp/sale/updatecustomer.jsp";

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();
    private final ContractDAO contractDAO = new ContractDAO();
    private final TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            action = "list";
        }
        switch (action) {
            case "view":
                showDetail(request, response);
                break;
            case "new":
                showCreateForm(request, response);
                break;
            case "edit":
                showEditForm(request, response);
                break;
            case "list":
            default:
                showList(request, response);
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            action = "";
        }
        switch (action) {
            case "create":
                handleCreate(request, response);
                break;
            case "update":
                handleUpdate(request, response);
                break;
            case "delete":
                handleDelete(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/customer");
        }
    }

    // ------------------------------------------------------------------
    // GET actions
    // ------------------------------------------------------------------

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int page = parseIntOrDefault(request.getParameter("page"), 1);
        if (page < 1) {
            page = 1;
        }
        String keyword = request.getParameter("keyword");
        String typeFilter = request.getParameter("type");
        Integer assigneeFilter = parseIntOrNull(request.getParameter("assigneeId"));

        List<Enterprise> customerList = customerDAO.findAll(page, PAGE_SIZE, keyword, typeFilter, assigneeFilter);
        int totalCount = customerDAO.countAll(keyword, typeFilter, assigneeFilter);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        request.setAttribute("customerList", customerList);
        request.setAttribute("userList", employeeDAO.findAllActive());
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", keyword);
        request.setAttribute("typeFilter", typeFilter);
        request.setAttribute("assigneeFilter", assigneeFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Enterprise customer = id != null ? customerDAO.findById(id) : null;
        if (customer == null) {
            // MSG-021: khách hàng không tồn tại
            response.sendRedirect(request.getContextPath() + "/customer?error=notfound");
            return;
        }

        request.setAttribute("customer", customer);
        request.setAttribute("contactList", customerDAO.findContactsByEnterpriseId(id));
        request.setAttribute("contractList", contractDAO.findByEnterpriseId(id));
        request.setAttribute("ticketList", ticketDAO.findByEnterpriseId(id));

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("userList", employeeDAO.findAllActive());
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Enterprise customer = id != null ? customerDAO.findById(id) : null;
        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/customer?error=notfound");
            return;
        }

        request.setAttribute("customer", customer);
        request.setAttribute("userList", employeeDAO.findAllActive());
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Enterprise e = new Enterprise();
        e.setEnterpriseName(request.getParameter("customerName"));
        e.setCustomerType(request.getParameter("customerType"));
        e.setCustomerGroup(request.getParameter("customerGroup"));
        e.setTaxCode(request.getParameter("taxCode"));
        e.setPhone(request.getParameter("phone"));
        e.setEmail(emptyToNull(request.getParameter("email")));
        e.setWebsite(emptyToNull(request.getParameter("website")));
        e.setStatus("Active");
        e.setJoinDate(parseDateOrNull(request.getParameter("joinDate")));

        Integer accountOwnerId = parseIntOrNull(request.getParameter("accountOwnerId"));
        if (accountOwnerId != null) {
            e.setAccountOwnerId(accountOwnerId);
        }

        setAddressFromRequest(e, request);

        if (!isValidCommonFields(e) || isBlank(e.getTaxCode())) {
            response.sendRedirect(request.getContextPath() + "/customer?action=new&error=invalid");
            return;
        }

        e.setEnterpriseCode(customerDAO.generateNextEnterpriseCode());
        int newId = customerDAO.insert(e);
        if (newId <= 0) {
            response.sendRedirect(request.getContextPath() + "/customer?action=new&error=create_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + newId);
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("customerId"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/customer?error=notfound");
            return;
        }

        Enterprise e = new Enterprise();
        e.setEnterpriseId(id);
        e.setEnterpriseName(request.getParameter("customerName"));
        e.setCustomerType(request.getParameter("customerType"));
        e.setCustomerGroup(request.getParameter("customerGroup"));
        e.setPhone(request.getParameter("phone"));
        e.setEmail(emptyToNull(request.getParameter("email")));
        e.setWebsite(emptyToNull(request.getParameter("website")));
        e.setJoinDate(parseDateOrNull(request.getParameter("joinDate")));

        Integer accountOwnerId = parseIntOrNull(request.getParameter("accountOwnerId"));
        if (accountOwnerId != null) {
            e.setAccountOwnerId(accountOwnerId);
        }

        setAddressFromRequest(e, request);

        if (!isValidCommonFields(e)) {
            response.sendRedirect(request.getContextPath() + "/customer?action=edit&id=" + id + "&error=invalid");
            return;
        }

        boolean ok = customerDAO.update(e);
        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/customer?action=edit&id=" + id + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/customer");
            return;
        }

        // BR-41: không cho xoá khách hàng còn hợp đồng đang hiệu lực
        if (customerDAO.hasActiveContracts(id)) {
            response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + "&error=has_active_contracts");
            return;
        }

        customerDAO.softDelete(id);
        response.sendRedirect(request.getContextPath() + "/customer");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setAddressFromRequest(Enterprise e, HttpServletRequest request) {
        Integer districtId = parseIntOrNull(request.getParameter("districtId"));
        String addressDetail = emptyToNull(request.getParameter("addressDetail"));
        if (districtId != null && addressDetail != null) {
            Address address = new Address();
            address.setStreetAndLocalName(addressDetail);
            address.setDistrictId(districtId);
            e.setAddress(address);
        }
    }

    /**
     * Trường bắt buộc + BR-09 (định dạng SĐT) + BR-10 (định dạng email) + ngày tham gia
     * không ở tương lai. Khớp với validate phía client ở addnewcustomer.jsp/updatecustomer.jsp
     * -- trước đây chỉ có ở client nên có thể bị bypass bằng cách POST thẳng.
     *
     * Email bắt buộc phải nhập (không chỉ đúng định dạng khi có) vì cột
     * enterprises.email trong DB là NOT NULL + UNIQUE (xem db/schema.sql) --
     * để trống sẽ làm INSERT/UPDATE thất bại ở tầng DB thay vì báo lỗi rõ
     * ràng "invalid" ngay tại đây.
     */
    private boolean isValidCommonFields(Enterprise e) {
        if (isBlank(e.getEnterpriseName()) || isBlank(e.getCustomerType()) || isBlank(e.getCustomerGroup())) {
            return false;
        }
        if (e.getAccountOwnerId() <= 0) {
            return false;
        }
        if (!isValidPhone(e.getPhone())) {
            return false;
        }
        if (isBlank(e.getEmail()) || !isValidEmail(e.getEmail())) {
            return false;
        }
        return e.getJoinDate() == null || !e.getJoinDate().toLocalDate().isAfter(java.time.LocalDate.now());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** BR-09: chấp nhận cả số di động lẫn số bàn Việt Nam (VD: 024 3822 1234). */
    private boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        return phone.replaceAll("[\\s.-]", "").matches("^(0|\\+84)[0-9]{9,10}$");
    }

    /** BR-10 */
    private boolean isValidEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        Integer parsed = parseIntOrNull(value);
        return parsed != null ? parsed : defaultValue;
    }

    private Date parseDateOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Date.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}

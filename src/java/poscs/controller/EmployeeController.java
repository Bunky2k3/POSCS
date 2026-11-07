package poscs.controller;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.Logs;
import poscs.common.QueryStrings;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.User;
import poscs.service.EmployeeService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;
import poscs.validator.EmployeeValidator;

/**
 * Controller quản lý nhân viên (FE-02 / UC-24..29, BR-12, BR-14, BR-22..24, BR-26).
 * Tầng Presentation thuần: kiểm quyền qua AccessControl + PermissionService,
 * đọc tham số HTTP và ủy quyền toàn bộ nghiệp vụ cho EmployeeService.
 */
@WebServlet(name = "EmployeeController", urlPatterns = {"/employee"})
public class EmployeeController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeController.class);
    private static final int PAGE_SIZE = EmployeeService.DEFAULT_PAGE_SIZE;
    private static final String LIST_VIEW = "/jsp/admin/listEmployee.jsp", DETAIL_VIEW = "/jsp/admin/viewdetailEmployee.jsp";
    private static final String CREATE_VIEW = "/jsp/admin/addEmployee.jsp", UPDATE_VIEW = "/jsp/admin/updateEmployee.jsp";
    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter STRICT_VN_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();
    private PermissionService permissionService;
    private EmployeeService employeeService;

    public EmployeeController() {}
    public EmployeeController(EmployeeService employeeService, PermissionService permissionService, AddressDAO addressDAO) {
        this.employeeService = employeeService;
        this.permissionService = permissionService;
        this.addressDAO = addressDAO;
    }

    private PermissionService getPermissionService() { return permissionService != null ? permissionService : (permissionService = new PermissionService(employeeDAO)); }
    private EmployeeService getEmployeeService() {
        return employeeService != null ? employeeService : (employeeService = new EmployeeService(employeeDAO, addressDAO, getPermissionService(), new EmployeeValidator()));
    }

    private boolean checkAccess(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // MSG-022: Chỉ Admin mới có quyền quản lý nhân viên (UC-24..29, BR-22)
        if (!AccessControl.requireFullAccess(req, resp, AccessControl.Resource.EMPLOYEE)) return false;
        if (getPermissionService().canManageEmployees(AccessControl.currentUser(req))) return true;
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!checkAccess(req, resp)) return;
        switch (req.getParameter("action") != null ? req.getParameter("action") : "list") {
            case "view" -> showDetail(req, resp);
            case "new" -> forwardForm(req, resp, null, null, CREATE_VIEW);
            case "edit" -> showEditForm(req, resp);
            default -> showList(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!checkAccess(req, resp)) return;
        switch (req.getParameter("action") != null ? req.getParameter("action") : "") {
            case "create" -> handleCreate(req, resp);
            case "update" -> handleUpdate(req, resp);
            case "toggleStatus" -> handleDetailAction(req, resp, false);
            case "sendAccount" -> handleDetailAction(req, resp, true);
            default -> resp.sendRedirect(req.getContextPath() + "/employee");
        }
    }

    private void showList(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // UC-24, BR-12 (10 dòng/trang), BR-14/BR-22/BR-23 (tìm kiếm, lọc, danh sách trống)
        Integer pageParam = parseIntOrNull(req.getParameter("page"));
        int page = Math.max(1, pageParam != null ? pageParam : 1);
        String keyword = req.getParameter("keyword"), status = req.getParameter("status");
        Integer roleId = parseIntOrNull(req.getParameter("roleId"));
        User actor = AccessControl.currentUser(req);
        EmployeeService svc = getEmployeeService();
        int totalCount = svc.countEmployees(keyword, status, roleId, actor);
        req.setAttribute("employeeList", svc.findEmployees(page, PAGE_SIZE, keyword, status, roleId, actor));
        req.setAttribute("roleList", svc.findAllRoles());
        req.setAttribute("currentPage", page);
        req.setAttribute("totalPages", Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE)));
        req.setAttribute("totalCount", totalCount);
        req.setAttribute("keyword", keyword);
        req.setAttribute("keywordParam", QueryStrings.param(keyword));
        req.setAttribute("statusFilter", status);
        req.setAttribute("roleFilter", roleId);
        req.getRequestDispatcher(LIST_VIEW).forward(req, resp);
    }

    private void showDetail(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // UC-25, BR-24: Hiển thị thông tin cá nhân, công việc và địa bàn
        User emp = loadEmployeeOrRedirect(req, resp);
        if (emp == null) return;
        EmployeeService svc = getEmployeeService();
        req.setAttribute("employee", emp);
        req.setAttribute("dateOfBirthText", emp.getDateOfBirth() != null ? emp.getDateOfBirth().toLocalDate().format(DATE_DISPLAY_FORMAT) : null);
        req.setAttribute("hireDateText", emp.getHireDate() != null ? emp.getHireDate().toLocalDate().format(DATE_DISPLAY_FORMAT) : null);
        req.setAttribute("assignedProvinces", svc.findProvincesOf(emp.getUserId()));
        req.setAttribute("managedProvinces", svc.findProvincesManagedBy(emp.getUserId()));
        req.getRequestDispatcher(DETAIL_VIEW).forward(req, resp);
    }

    private void showEditForm(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User emp = loadEmployeeOrRedirect(req, resp);
        if (emp != null) { req.setAttribute("employee", emp); forwardForm(req, resp, emp.getUserId(), emp.getRoleId(), UPDATE_VIEW); }
    }

    private User loadEmployeeOrRedirect(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer id = parseIntOrNull(req.getParameter("id"));
        User emp = id != null ? getEmployeeService().findEmployeeDetail(id, AccessControl.currentUser(req)) : null;
        if (emp == null) resp.sendRedirect(req.getContextPath() + "/employee?error=" + EmployeeService.ERR_NOT_FOUND);
        return emp;
    }

    private void forwardForm(HttpServletRequest req, HttpServletResponse resp,
            Integer editingId, Integer editingRoleId, String view) throws ServletException, IOException {
        EmployeeService svc = getEmployeeService();
        req.setAttribute("allProvinceAssignments", svc.branchProvinceAssignments(editingId));
        req.setAttribute("roleList", svc.assignableRoles(editingRoleId));
        req.setAttribute("departmentList", svc.findAllDepartments());
        req.setAttribute("provinceList", addressDAO.findAllProvinces());
        req.getRequestDispatcher(view).forward(req, resp);
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ServiceResult<Integer> res = getEmployeeService().createEmployee(buildUserFromRequest(req, new User()),
                parseIntList(req.getParameterValues("provinceIds")), AccessControl.currentUser(req));
        if (res.isSuccess()) { redirectAfterSave(req, resp, res.getData(), res.getMessage()); return; }
        LOG.warn("Tao nhan vien that bai (actor={}, err={})", Logs.actor(req), resolveErrorCode(res));
        resp.sendRedirect(req.getContextPath() + "/employee?action=new&error=" + resolveErrorCode(res));
    }

    private void handleUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer id = requireUserId(req, resp, "userId");
        if (id == null) return;
        User u = buildUserFromRequest(req, new User());
        u.setUserId(id);
        ServiceResult<Integer> res = getEmployeeService().updateEmployee(
                u, parseIntList(req.getParameterValues("provinceIds")), AccessControl.currentUser(req));
        if (!res.isSuccess()) redirectOnActionError(req, resp, id, "edit", resolveErrorCode(res));
        else redirectAfterSave(req, resp, id, res.getMessage());
    }

    private void handleDetailAction(HttpServletRequest req, HttpServletResponse resp, boolean sendAccount) throws IOException {
        // UC-28 (gửi tài khoản) & UC-29, BR-26 (khóa/mở khóa nhân viên)
        Integer id = requireUserId(req, resp, "id");
        if (id == null) return;
        User actor = AccessControl.currentUser(req);
        ServiceResult<?> res = sendAccount ? getEmployeeService().sendAccountInformation(id, actor)
                                           : getEmployeeService().toggleEmployeeStatus(id, actor);
        if (!res.isSuccess()) redirectOnActionError(req, resp, id, "view", resolveErrorCode(res));
        else resp.sendRedirect(req.getContextPath() + "/employee?action=view&id=" + id + (sendAccount ? "&sent=1" : ""));
    }

    private Integer requireUserId(HttpServletRequest req, HttpServletResponse resp, String param) throws IOException {
        Integer id = parseIntOrNull(req.getParameter(param));
        if (id == null) resp.sendRedirect(req.getContextPath() + "/employee?error=" + EmployeeService.ERR_NOT_FOUND);
        return id;
    }

    private void redirectAfterSave(HttpServletRequest req, HttpServletResponse resp, int userId, String msg) throws IOException {
        if (!EmployeeService.ERR_PROVINCE_NOT_SAVED.equals(msg)) {
            resp.sendRedirect(req.getContextPath() + "/employee?action=view&id=" + userId);
        } else {
            LOG.warn("Luu nhan vien xong nhung khong luu duoc dia ban (actor={}, userId={})", Logs.actor(req), userId);
            resp.sendRedirect(req.getContextPath() + "/employee?action=edit&id=" + userId + "&error=" + msg);
        }
    }

    private void redirectOnActionError(HttpServletRequest req, HttpServletResponse resp, int userId, String act, String err) throws IOException {
        if (EmployeeService.ERR_NOT_FOUND.equals(err)) {
            resp.sendRedirect(req.getContextPath() + "/employee?error=" + EmployeeService.ERR_NOT_FOUND);
        } else {
            LOG.warn("Thao tac nhan vien that bai (actor={}, userId={}, err={})", Logs.actor(req), userId, err);
            resp.sendRedirect(req.getContextPath() + "/employee?action=" + act + "&id=" + userId + "&error=" + err);
        }
    }

    private String resolveErrorCode(ServiceResult<?> res) {
        return (res.hasFieldErrors() || res.getMessage() == null) ? EmployeeService.ERR_INVALID : res.getMessage();
    }

    private User buildUserFromRequest(HttpServletRequest req, User u) {
        u.setLastName(emptyToNull(req.getParameter("lastName")));
        u.setMiddleName(emptyToNull(req.getParameter("middleName")));
        u.setFirstName(emptyToNull(req.getParameter("firstName")));
        u.setGender(emptyToNull(req.getParameter("gender")));
        String dobRaw = emptyToNull(req.getParameter("dateOfBirth"));
        if (dobRaw != null) {
            Date dob = parseDateOrNull(dobRaw);
            u.setDateOfBirth(dob != null ? dob : Date.valueOf(LocalDate.MAX));
        }
        u.setCitizenId(emptyToNull(req.getParameter("citizenId")));
        u.setPhone(emptyToNull(req.getParameter("phone")));
        u.setPersonalEmail(emptyToNull(req.getParameter("personalEmail")));
        u.setHireDate(parseDateOrNull(req.getParameter("hireDate")));
        Integer roleId = parseIntOrNull(req.getParameter("roleId")), deptId = parseIntOrNull(req.getParameter("departmentId"));
        if (roleId != null) u.setRoleId(roleId);
        if (deptId != null) u.setDepartmentId(deptId);
        Integer districtId = parseIntOrNull(req.getParameter("districtId"));
        String detail = emptyToNull(req.getParameter("addressDetail"));
        if (districtId != null) u.setAddress(new Address(0, detail != null ? detail : "", districtId));
        return u;
    }

    private List<Integer> parseIntList(String[] values) {
        List<Integer> result = new ArrayList<>();
        if (values != null) for (String v : values) { Integer p = parseIntOrNull(v); if (p != null) result.add(p); }
        return result;
    }

    private Integer parseIntOrNull(String value) {
        String trimmed = emptyToNull(value);
        try { return trimmed != null ? Integer.parseInt(trimmed) : null; } catch (NumberFormatException ex) { return null; }
    }

    private Date parseDateOrNull(String value) {
        String trimmed = emptyToNull(value);
        try { return trimmed != null ? Date.valueOf(LocalDate.parse(trimmed, trimmed.contains("/") ? STRICT_VN_DATE : DateTimeFormatter.ISO_LOCAL_DATE)) : null; }
        catch (DateTimeParseException ex) { return null; }
    }

    private String emptyToNull(String value) { return (value == null || value.trim().isEmpty()) ? null : value.trim(); }
}

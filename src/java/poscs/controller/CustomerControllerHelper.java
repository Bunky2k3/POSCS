package poscs.controller;

import java.sql.Date;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.User;
import poscs.service.PermissionService;

/**
 * Lớp tiện ích hỗ trợ cho các controller phân hệ Khách hàng / Nhà cung cấp.
 * Gom các hằng số giao diện, chuyển đổi kiểu, trích xuất form và phân giải URL.
 */
public final class CustomerControllerHelper {

    private CustomerControllerHelper() {
    }

    public static final int PAGE_SIZE = 10;
    public static final String SALES_ROLE = "Sales";
    public static final String LOGO_SUBFOLDER = "enterprise_logos";

    public static final String LIST_VIEW = "/jsp/sale/listcustomer.jsp";
    public static final String DETAIL_VIEW = "/jsp/sale/viewcustomerdetail.jsp";
    public static final String CREATE_VIEW = "/jsp/sale/addnewcustomer.jsp";
    public static final String UPDATE_VIEW = "/jsp/sale/updatecustomer.jsp";

    /**
     * Đổi tham số kind trên URL thành vai trong CSDL (Khách mua / Nhà cung cấp).
     */
    public static String roleFromKind(String kind) {
        return "supplier".equals(kind) ? PermissionService.ROLE_SUPPLIER : PermissionService.ROLE_BUYER;
    }

    /**
     * Về danh sách kèm error=notfound, giữ đúng chiều đối tác người dùng vừa đứng.
     */
    public static String notFoundUrl(HttpServletRequest request) {
        return request.getContextPath() + "/customer?error=notfound"
                + (PermissionService.ROLE_SUPPLIER.equals(roleFromKind(request.getParameter("kind"))) ? "&kind=supplier" : "");
    }

    public static Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static int parseIntOrDefault(String value, int defaultValue) {
        Integer parsed = parseIntOrNull(value);
        return parsed != null ? parsed : defaultValue;
    }

    public static Date parseDateOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Date.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Khởi tạo PermissionService nguyên bản tuân thủ quy tắc bảo mật fail-closed và thời gian thực.
     */
    public static PermissionService createPermissionService(EmployeeDAO employeeDAO) {
        return new PermissionService(employeeDAO);
    }

    /**
     * Trích xuất thông tin đối tác từ form tạo mới.
     */
    public static Enterprise extractCreateEnterprise(HttpServletRequest request) {
        Enterprise e = extractCommonEnterprise(request);
        e.setTaxCode(request.getParameter("taxCode"));
        e.setStatus("Active");
        return e;
    }

    /**
     * Trích xuất thông tin đối tác từ form cập nhật.
     */
    public static Enterprise extractUpdateEnterprise(HttpServletRequest request, int id) {
        Enterprise e = extractCommonEnterprise(request);
        e.setEnterpriseId(id);
        return e;
    }

    private static Enterprise extractCommonEnterprise(HttpServletRequest request) {
        Enterprise e = new Enterprise();
        e.setEnterpriseName(request.getParameter("customerName"));
        e.setCustomerType(request.getParameter("customerType"));
        e.setCustomerGroup(request.getParameter("customerGroup"));
        e.setPhone(request.getParameter("phone"));
        e.setEmail(emptyToNull(request.getParameter("email")));
        e.setWebsite(emptyToNull(request.getParameter("website")));
        e.setJoinDate(parseDateOrNull(request.getParameter("joinDate")));
        return e;
    }

    /**
     * Lấy danh sách tỉnh Sales trực tiếp phụ trách, sắp xếp theo tên ngắn.
     */
    public static List<Province> territoryOf(User user, EmployeeDAO employeeDAO, PermissionService permissionService) {
        if (user == null || !permissionService.isSales(user)) {
            return List.of();
        }
        List<Province> provinces = new ArrayList<>(employeeDAO.findProvincesOf(user.getUserId()));
        provinces.sort(Comparator.comparing(Province::getShortName, Collator.getInstance(Locale.forLanguageTag("vi"))));
        return provinces;
    }

    /**
     * Xác định vai của trang sửa dựa trên kind và vai thực tế của đối tác.
     */
    public static String editRoleOf(String kind, List<String> roles) {
        String asked = "supplier".equals(kind) ? PermissionService.ROLE_SUPPLIER : "buyer".equals(kind) ? PermissionService.ROLE_BUYER : null;
        if (asked != null && roles.contains(asked)) {
            return asked;
        }
        return PermissionService.isSupplierOnly(roles) ? PermissionService.ROLE_SUPPLIER : PermissionService.ROLE_BUYER;
    }

    /**
     * Bổ sung tỉnh hiện tại vào danh sách tỉnh nếu chưa có.
     */
    public static List<Province> withProvince(List<Province> provinces, Integer provinceId, AddressDAO addressDAO) {
        if (provinceId == null || provinces.stream().anyMatch(p -> p.getProvinceId() == provinceId)) {
            return provinces;
        }
        List<Province> result = new ArrayList<>(provinces);
        addressDAO.findAllProvinces().stream().filter(p -> p.getProvinceId() == provinceId).findFirst().ifPresent(result::add);
        return result;
    }

    public static List<String> withCurrentType(List<String> types, String current) {
        if (isBlank(current) || types.contains(current)) {
            return types;
        }
        List<String> result = new ArrayList<>(types);
        result.add(current);
        return result;
    }

    public static String detailUrl(HttpServletRequest request, int id) {
        return request.getContextPath() + "/customer?action=view&id=" + id;
    }

    public static void redirectWithError(HttpServletRequest request, HttpServletResponse response,
            String fallbackUrl, String error) throws java.io.IOException {
        if ("notfound".equals(error)) {
            response.sendRedirect(notFoundUrl(request));
            return;
        }
        String sep = fallbackUrl.contains("?") ? "&" : "?";
        response.sendRedirect(fallbackUrl + sep + "error=" + error);
    }

    public static void populateCreateForm(HttpServletRequest request, User currentUser,
            EmployeeDAO employeeDAO, AddressDAO addressDAO, PermissionService permissionService) {
        String role = roleFromKind(request.getParameter("kind"));
        boolean supplier = PermissionService.ROLE_SUPPLIER.equals(role);
        request.setAttribute("kind", supplier ? "supplier" : "buyer");
        request.setAttribute("customerTypeOptions", poscs.service.CustomerService.customerTypesFor(role));
        request.setAttribute("userList", employeeDAO.findActiveByRole(SALES_ROLE));

        if (supplier) {
            request.setAttribute("provinceList", addressDAO.findAllProvinces());
            if (permissionService.isSales(currentUser)) {
                request.setAttribute("lockedOwner", currentUser);
            }
        } else {
            List<Province> myProvinces = territoryOf(currentUser, employeeDAO, permissionService);
            if (!myProvinces.isEmpty()) {
                request.setAttribute("provinceList", myProvinces);
                request.setAttribute("lockedOwner", currentUser);
            } else {
                request.setAttribute("provinceList", addressDAO.findBranchProvinces());
                request.setAttribute("territoryAssignments", employeeDAO.findAllAssignments());
            }
        }
        request.setAttribute("managerOf", employeeDAO.findManagerMap());
    }

    public static void populateEditForm(HttpServletRequest request, User currentUser, Enterprise customer,
            List<String> roles, EmployeeDAO employeeDAO, AddressDAO addressDAO, PermissionService permissionService) {
        request.setAttribute("customer", customer);
        String pageRole = editRoleOf(request.getParameter("kind"), roles);
        request.setAttribute("kind", PermissionService.ROLE_SUPPLIER.equals(pageRole) ? "supplier" : "buyer");
        request.setAttribute("userList", employeeDAO.findActiveByRole(
                SALES_ROLE, customer.getAccountOwnerId(), customer.getSupportOwnerId()));
        Integer currentProvinceId = PermissionService.provinceIdOf(customer);
        boolean sales = permissionService.isSales(currentUser);
        if (sales) {
            request.setAttribute("lockedOwner", customer.getAccountOwner());
        }
        setupEditProvinces(request, currentUser, currentProvinceId, roles, sales, employeeDAO, addressDAO, permissionService);
        request.setAttribute("customerRoles", roles);
        request.setAttribute("customerTypeOptions", withCurrentType(
                poscs.service.CustomerService.customerTypesFor(pageRole), customer.getCustomerType()));
        request.setAttribute("managerOf", employeeDAO.findManagerMap());
    }

    private static void setupEditProvinces(HttpServletRequest request, User currentUser, Integer currentProvinceId,
            List<String> roles, boolean sales, EmployeeDAO employeeDAO, AddressDAO addressDAO, PermissionService permissionService) {
        if (PermissionService.isSupplierOnly(roles)) {
            request.setAttribute("provinceList", addressDAO.findAllProvinces());
        } else {
            List<Province> myProvinces = territoryOf(currentUser, employeeDAO, permissionService);
            if (myProvinces.isEmpty()) {
                request.setAttribute("provinceList", addressDAO.findBranchProvincesIncluding(currentProvinceId));
            } else {
                request.setAttribute("provinceList", withProvince(myProvinces, currentProvinceId, addressDAO));
            }
            if (!sales) {
                request.setAttribute("territoryAssignments", employeeDAO.findAllAssignments());
            }
        }
    }
}

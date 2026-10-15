package poscs.controller;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.common.ListScope;
import poscs.common.QueryStrings;
import poscs.dao.AddressDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Enterprise;
import poscs.model.User;
import poscs.service.CustomerService;
import poscs.service.PermissionService;
import poscs.validator.CustomerValidator;

/**
 * Controller cho danh sách khách hàng và nhà cung cấp (UC-08).
 * Đáp ứng các quy tắc:
 * - BR-12: Phân trang danh sách với kích thước mặc định 10.
 * - BR-13: Đầy đủ các cột hiển thị (STT, Tên+Email, Loại, SĐT, Địa bàn, Phụ trách, Thao tác).
 * - BR-14: Trạng thái trống khi không có dữ liệu.
 * - Giữ nguyên bộ lọc và từ khóa tìm kiếm khi phân trang.
 * - Phân quyền đọc và ghi dựa trên PermissionService.
 */
@WebServlet(name = "CustomerListController", urlPatterns = {"/customer/list"})
public class CustomerListController extends HttpServlet {

    private CustomerDAO customerDAO = new CustomerDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();
    private CustomerService customerService;
    private PermissionService permissionService;

    public CustomerListController() {
    }

    public CustomerListController(CustomerDAO customerDAO, EmployeeDAO employeeDAO, AddressDAO addressDAO,
            CustomerService customerService, PermissionService permissionService) {
        this.customerDAO = customerDAO;
        this.employeeDAO = employeeDAO;
        this.addressDAO = addressDAO;
        this.customerService = customerService;
        this.permissionService = permissionService;
    }

    CustomerService getCustomerService() {
        if (customerService != null) {
            return customerService;
        }
        return new CustomerService(customerDAO, employeeDAO, addressDAO, new CustomerValidator());
    }

    void setCustomerService(CustomerService customerService) {
        this.customerService = customerService;
    }

    PermissionService getPermissionService() {
        if (permissionService != null) {
            return permissionService;
        }
        return CustomerControllerHelper.createPermissionService(employeeDAO);
    }

    void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    void setCustomerDAO(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    void setAddressDAO(AddressDAO addressDAO) {
        this.addressDAO = addressDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canViewCustomers(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        FilterParams fp = extractFilters(request, currentUser);
        int pageSize = CustomerControllerHelper.PAGE_SIZE;
        List<Enterprise> customerList = getCustomerService().findAll(fp.page, pageSize, fp.keyword, fp.typeFilter,
                fp.assigneeFilter, fp.provinceFilters, false, fp.roleFilter, fp.scope);
        int totalCount = getCustomerService().countAll(fp.keyword, fp.typeFilter, fp.assigneeFilter, fp.provinceFilters,
                fp.roleFilter, fp.scope);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));

        bindListAttributes(request, currentUser, fp, customerList, totalCount, totalPages);
        request.getRequestDispatcher(CustomerControllerHelper.LIST_VIEW).forward(request, response);
    }

    private static class FilterParams {
        int page;
        String keyword;
        String typeFilter;
        Integer assigneeFilter;
        List<Integer> provinceFilters;
        String roleFilter;
        String kind;
        ListScope scope;
        String viewMode;
    }

    private FilterParams extractFilters(HttpServletRequest request, User currentUser) {
        FilterParams fp = new FilterParams();
        fp.page = CustomerControllerHelper.parseIntOrDefault(request.getParameter("page"), 1);
        if (fp.page < 1) {
            fp.page = 1;
        }
        fp.keyword = request.getParameter("keyword");
        fp.typeFilter = request.getParameter("type");
        fp.assigneeFilter = CustomerControllerHelper.parseIntOrNull(request.getParameter("assigneeId"));
        fp.roleFilter = CustomerControllerHelper.roleFromKind(request.getParameter("kind"));
        fp.kind = PermissionService.ROLE_SUPPLIER.equals(fp.roleFilter) ? "supplier" : "buyer";

        List<Integer> provs = ContractController.provinceFiltersOf(request);
        fp.provinceFilters = PermissionService.ROLE_SUPPLIER.equals(fp.roleFilter) ? List.of() : provs;

        fp.viewMode = AccessControl.defaultView(request);
        fp.scope = getPermissionService().getCustomerListScope(currentUser, fp.viewMode);
        return fp;
    }

    private void bindListAttributes(HttpServletRequest request, User currentUser, FilterParams fp,
            List<Enterprise> customerList, int totalCount, int totalPages) {
        request.setAttribute("canManage", getPermissionService().canManageCustomers(currentUser));
        request.setAttribute("viewFilter", fp.viewMode);
        request.setAttribute("viewNarrowed", fp.scope.isNarrowed());
        request.setAttribute("viewProvinceCount", fp.scope.getProvinceIds().size());
        request.setAttribute("viewToggleUrl", QueryStrings.with(request, "view",
                AccessControl.VIEW_MINE.equals(fp.viewMode) ? AccessControl.VIEW_ALL : AccessControl.VIEW_MINE));

        request.setAttribute("customerList", customerList);
        request.setAttribute("lockedIds", computeLockedIds(currentUser, customerList, fp.roleFilter));
        request.setAttribute("userList", employeeDAO.findActiveByRole(CustomerControllerHelper.SALES_ROLE));
        request.setAttribute("provinceList", addressDAO.findBranchProvinces());
        request.setAttribute("currentPage", fp.page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("pageSize", CustomerControllerHelper.PAGE_SIZE);
        request.setAttribute("keyword", fp.keyword);
        request.setAttribute("keywordParam", QueryStrings.param(fp.keyword));
        request.setAttribute("typeFilter", fp.typeFilter);
        request.setAttribute("assigneeFilter", fp.assigneeFilter);
        request.setAttribute("provinceFilters", fp.provinceFilters);
        request.setAttribute("provinceQuery", ContractController.provinceQuery(fp.provinceFilters));
        request.setAttribute("myProvinces", currentUser != null
                ? employeeDAO.findProvincesCoveredBy(currentUser.getUserId()) : List.of());
        request.setAttribute("roleFilter", fp.roleFilter);
        request.setAttribute("kind", fp.kind);
        request.setAttribute("customerTypeOptions", CustomerService.customerTypesFor(fp.roleFilter));
        request.setAttribute("showProvinceFilter", !PermissionService.ROLE_SUPPLIER.equals(fp.roleFilter));
    }

    private Set<Integer> computeLockedIds(User currentUser, List<Enterprise> customers, String listRole) {
        if (!getPermissionService().isSales(currentUser)) {
            return Set.of();
        }
        Set<Integer> locked = new HashSet<>();
        for (Enterprise c : customers) {
            List<String> roles = PermissionService.ROLE_BUYER.equals(listRole)
                    ? List.of(PermissionService.ROLE_BUYER)
                    : customerDAO.findRolesOf(c.getEnterpriseId());
            if (!getPermissionService().isCustomerInScope(currentUser, c, roles)) {
                locked.add(c.getEnterpriseId());
            }
        }
        return locked;
    }
}

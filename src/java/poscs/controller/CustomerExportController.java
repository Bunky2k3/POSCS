package poscs.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.ListScope;
import poscs.dao.AddressDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.User;
import poscs.service.CustomerService;
import poscs.service.PermissionService;
import poscs.validator.CustomerValidator;

/**
 * Controller xuất danh sách khách hàng và nhà cung cấp ra Excel (UC-37).
 * Giữ nguyên bộ lọc, từ khóa tìm kiếm, chiều đối tác và phạm vi truy cập (ListScope).
 */
@WebServlet(name = "CustomerExportController", urlPatterns = {"/customer/export"})
public class CustomerExportController extends HttpServlet {

    private CustomerDAO customerDAO = new CustomerDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();

    private CustomerService customerService;
    private PermissionService permissionService;

    public CustomerExportController() {
    }

    public CustomerExportController(CustomerDAO customerDAO, EmployeeDAO employeeDAO, AddressDAO addressDAO,
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
        if (!getPermissionService().canExportCustomers(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        String keyword = request.getParameter("keyword");
        String typeFilter = request.getParameter("type");
        Integer assigneeFilter = CustomerControllerHelper.parseIntOrNull(request.getParameter("assigneeId"));
        List<Integer> provinceFilters = ContractController.provinceFiltersOf(request);
        String roleFilter = CustomerControllerHelper.roleFromKind(request.getParameter("kind"));
        if (PermissionService.ROLE_SUPPLIER.equals(roleFilter)) {
            provinceFilters = List.of();
        }

        ListScope scope = getPermissionService().getCustomerListScope(currentUser, AccessControl.defaultView(request));

        String prefix = getCustomerService().getExportFileNamePrefix(roleFilter);
        ExcelUtil.prepareAttachmentHeaders(response, prefix);
        try (OutputStream out = response.getOutputStream()) {
            getCustomerService().exportCustomersToExcel(out, keyword, typeFilter, assigneeFilter,
                    provinceFilters, roleFilter, scope);
        }
    }
}

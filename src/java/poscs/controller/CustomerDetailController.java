package poscs.controller;

import java.io.IOException;
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
import poscs.model.Enterprise;
import poscs.model.User;
import poscs.service.CustomerEvaluationService;
import poscs.service.CustomerService;
import poscs.service.PermissionService;
import poscs.validator.CustomerValidator;

/**
 * Controller cho chi tiết khách hàng và nhà cung cấp (UC-09).
 * Hiển thị thông tin đối tác, danh sách liên hệ, hợp đồng, phiếu hỗ trợ,
 * lịch sử đánh giá vòng đời và kiểm soát quyền ghi theo địa bàn/cây tổ chức.
 */
@WebServlet(name = "CustomerDetailController", urlPatterns = {"/customer/detail"})
public class CustomerDetailController extends HttpServlet {

    private CustomerDAO customerDAO = new CustomerDAO();
    private ContractDAO contractDAO = new ContractDAO();
    private TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();

    private CustomerService customerService;
    private CustomerEvaluationService customerEvaluationService;
    private PermissionService permissionService;

    public CustomerDetailController() {
    }

    public CustomerDetailController(CustomerDAO customerDAO, ContractDAO contractDAO,
            TechnicalSupportTicketDAO ticketDAO, EmployeeDAO employeeDAO, AddressDAO addressDAO,
            CustomerService customerService, CustomerEvaluationService customerEvaluationService,
            PermissionService permissionService) {
        this.customerDAO = customerDAO;
        this.contractDAO = contractDAO;
        this.ticketDAO = ticketDAO;
        this.employeeDAO = employeeDAO;
        this.addressDAO = addressDAO;
        this.customerService = customerService;
        this.customerEvaluationService = customerEvaluationService;
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

    CustomerEvaluationService getCustomerEvaluationService() {
        if (customerEvaluationService != null) {
            return customerEvaluationService;
        }
        return new CustomerEvaluationService(customerDAO, getCustomerService());
    }

    void setCustomerEvaluationService(CustomerEvaluationService customerEvaluationService) {
        this.customerEvaluationService = customerEvaluationService;
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

    void setContractDAO(ContractDAO contractDAO) {
        this.contractDAO = contractDAO;
    }

    void setTicketDAO(TechnicalSupportTicketDAO ticketDAO) {
        this.ticketDAO = ticketDAO;
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

        Integer id = CustomerControllerHelper.parseIntOrNull(request.getParameter("id"));
        Enterprise customer = id != null ? getCustomerService().findById(id) : null;
        if (customer == null) {
            response.sendRedirect(CustomerControllerHelper.notFoundUrl(request));
            return;
        }

        request.setAttribute("customer", customer);
        request.setAttribute("contactList", customerDAO.findContactsByEnterpriseId(id));
        request.setAttribute("contractList", contractDAO.findByEnterpriseId(id));
        request.setAttribute("ticketList", ticketDAO.findByEnterpriseId(id));
        request.setAttribute("lifecycleEventList", getCustomerEvaluationService().getLifecycleEvents(id));

        List<String> roles = getCustomerService().findRolesOf(id);
        request.setAttribute("customerRoles", roles);

        boolean canManage = getPermissionService().canManageCustomers(currentUser);
        boolean outside = getPermissionService().isSales(currentUser)
                && !getPermissionService().isCustomerInScope(currentUser, customer, roles);

        request.setAttribute("canManage", canManage);
        request.setAttribute("canWrite", canManage && !outside);
        request.setAttribute("outOfScope", canManage && outside);

        request.getRequestDispatcher(CustomerControllerHelper.DETAIL_VIEW).forward(request, response);
    }
}

package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
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
import poscs.service.CustomerEvaluationService;
import poscs.service.CustomerService;
import poscs.service.PermissionService;
import poscs.validator.CustomerValidator;

/**
 * Controller trung tâm cho phân hệ Khách hàng / Nhà cung cấp (/customer).
 * Đóng vai trò điều phối (Front Controller / Facade) ủy quyền thực thi cho các controller chuyên biệt:
 * - {@link CustomerListController}: Xem danh sách, phân trang, lọc (UC-08, BR-12, BR-13, BR-14).
 * - {@link CustomerDetailController}: Xem chi tiết, dòng thời gian và quyền truy cập (UC-09).
 * - {@link CustomerActionController}: Thao tác Thêm/Sửa/Xóa/Đánh giá (UC-30, UC-31, UC-32, UC-38, BR-34).
 * - {@link CustomerExportController}: Xuất danh sách Excel (UC-37).
 */
@WebServlet(name = "CustomerController", urlPatterns = {"/customer"})
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 10 * 1024 * 1024, fileSizeThreshold = 1024 * 1024)
public class CustomerController extends HttpServlet {

    private CustomerDAO customerDAO = new CustomerDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();
    private ContractDAO contractDAO = new ContractDAO();
    private TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private CustomerValidator customerValidator = new CustomerValidator();

    private CustomerService customerService;
    private CustomerEvaluationService customerEvaluationService;
    private PermissionService permissionService;

    private CustomerListController listController;
    private CustomerDetailController detailController;
    private CustomerActionController actionController;
    private CustomerExportController exportController;

    CustomerService getCustomerService() {
        if (customerService != null) {
            return customerService;
        }
        return new CustomerService(customerDAO, employeeDAO, addressDAO, customerValidator);
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

    CustomerListController getListController() {
        if (listController != null) {
            return listController;
        }
        return new CustomerListController(customerDAO, employeeDAO, addressDAO, getCustomerService(), getPermissionService());
    }

    void setListController(CustomerListController listController) {
        this.listController = listController;
    }

    CustomerDetailController getDetailController() {
        if (detailController != null) {
            return detailController;
        }
        return new CustomerDetailController(customerDAO, contractDAO, ticketDAO, employeeDAO, addressDAO,
                getCustomerService(), getCustomerEvaluationService(), getPermissionService());
    }

    void setDetailController(CustomerDetailController detailController) {
        this.detailController = detailController;
    }

    CustomerActionController getActionController() {
        if (actionController != null) {
            return actionController;
        }
        return new CustomerActionController(customerDAO, employeeDAO, addressDAO,
                getCustomerService(), getCustomerEvaluationService(), getPermissionService());
    }

    void setActionController(CustomerActionController actionController) {
        this.actionController = actionController;
    }

    CustomerExportController getExportController() {
        if (exportController != null) {
            return exportController;
        }
        return new CustomerExportController(customerDAO, employeeDAO, addressDAO, getCustomerService(), getPermissionService());
    }

    void setExportController(CustomerExportController exportController) {
        this.exportController = exportController;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("canManage",
                getPermissionService().canManageCustomers(AccessControl.currentUser(request)));
        String action = request.getParameter("action");
        if (action == null || "list".equals(action)) {
            getListController().doGet(request, response);
        } else if ("view".equals(action)) {
            getDetailController().doGet(request, response);
        } else if ("new".equals(action) || "edit".equals(action)) {
            getActionController().doGet(request, response);
        } else if ("exportExcel".equals(action)) {
            getExportController().doGet(request, response);
        } else {
            getListController().doGet(request, response);
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
            case "update":
            case "delete":
            case "evaluate":
                getActionController().doPost(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/customer");
                break;
        }
    }
}

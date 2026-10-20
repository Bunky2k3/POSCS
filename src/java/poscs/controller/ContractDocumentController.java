package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.Logs;
import poscs.dao.ContractDAO;
import poscs.dao.ContractDocumentDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractDocument;
import poscs.model.User;
import poscs.service.ContractDocumentService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

/**
 * Controller chuyên trách xử lý tài liệu đính kèm hợp đồng (Task 4.16 - [UC-52]):
 * - Gắn đường dẫn tài liệu vào hợp đồng (không upload file).
 * - Huỷ mềm tài liệu kèm lý do bắt buộc.
 * - Phân quyền RBAC qua PermissionService (Admin, Sales quản lý vùng).
 */
@WebServlet(name = "ContractDocumentController", urlPatterns = {"/contract/document", "/contract/documents"})
public class ContractDocumentController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ContractDocumentController.class);

    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private ContractDocumentService documentService;
    private PermissionService permissionService;

    public ContractDocumentController() {
    }

    public ContractDocumentController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
                                      PermissionService permissionService) {
        this(contractDAO, employeeDAO, null, permissionService);
    }

    public ContractDocumentController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
                                      ContractDocumentService documentService,
                                      PermissionService permissionService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.documentService = documentService;
        this.permissionService = permissionService;
    }

    public ContractDAO getContractDAO() {
        return contractDAO;
    }

    public void setContractDAO(ContractDAO contractDAO) {
        this.contractDAO = contractDAO;
    }

    public EmployeeDAO getEmployeeDAO() {
        return employeeDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public PermissionService getPermissionService() {
        if (permissionService == null) {
            this.permissionService = new PermissionService(employeeDAO);
        }
        return permissionService;
    }

    public void setPermissionService(PermissionService ps) {
        this.permissionService = ps;
    }

    public ContractDocumentService getDocumentService() {
        if (documentService == null) {
            ContractDocumentDAO docDao = (contractDAO != null && contractDAO.getDocumentDAO() != null)
                    ? contractDAO.getDocumentDAO() : new ContractDocumentDAO();
            this.documentService = new ContractDocumentService(docDao, contractDAO, getPermissionService());
        }
        return documentService;
    }

    public void setDocumentService(ContractDocumentService ds) {
        this.documentService = ds;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = AccessControl.currentUser(request);
        if (!getPermissionService().canViewContracts(user)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer contractId = parseContractId(request);
        if (contractId == null) {
            response.sendRedirect(request.getContextPath() + "/contract");
            return;
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        String action = request.getParameter("action");
        String target = "view".equals(action) ? "/contract?action=view&id=" : "/contract?action=edit&id=";
        response.sendRedirect(request.getContextPath() + target + contractId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            action = "";
        }
        switch (action) {
            case "addDocument":
            case "create":
            case "add":
                handleAddDocument(request, response);
                break;
            case "voidDocument":
            case "void":
            case "delete":
            case "remove":
                handleVoidDocument(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/contract");
                break;
        }
    }

    public void handleAddDocument(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        if (contractId == null || contractId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        String back = "/contract?action=edit&id=" + contractId;
        ContractDocument doc = new ContractDocument();
        doc.setContractId(contractId);
        doc.setDocType(ContractControllerHelper.emptyToNull(request.getParameter("docType")));
        doc.setTitle(ContractControllerHelper.emptyToNull(request.getParameter("docTitle")));
        doc.setFileUrl(ContractControllerHelper.emptyToNull(request.getParameter("fileUrl")));
        doc.setNote(ContractControllerHelper.emptyToNull(request.getParameter("docNote")));

        ServiceResult<ContractDocument> res = getDocumentService().addDocument(contractId, doc, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, res.getMessage(), "Them tai lieu that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + back);
    }

    public void handleVoidDocument(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        Integer documentId = ContractControllerHelper.parseIntOrNull(request.getParameter("documentId"));
        if (contractId == null || contractId <= 0 || documentId == null || documentId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        String reason = ContractControllerHelper.emptyToNull(request.getParameter("reason"));
        ServiceResult<Void> res = getDocumentService().voidDocument(contractId, documentId, reason, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, res.getMessage(), "Huy tai lieu that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    private Integer parseContractId(HttpServletRequest request) {
        Integer id = ContractControllerHelper.parseIntOrNull(request.getParameter("contractId"));
        return id != null ? id : ContractControllerHelper.parseIntOrNull(request.getParameter("id"));
    }

    private void handleServiceError(HttpServletRequest request, HttpServletResponse response,
                                    int contractId, String errorMessage, String logPrefix) throws IOException {
        if (ContractDocumentService.ERR_FORBIDDEN.equals(errorMessage)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        if (contractId <= 0 || ContractDocumentService.ERR_NOT_FOUND.equals(errorMessage)) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        LOG.warn("{} (actor={}, contractId={}, err={})",
                logPrefix, Logs.actor(request), contractId, errorMessage);
        String errParam;
        if (ContractDocumentService.ERR_REASON_REQUIRED.equals(errorMessage)) {
            errParam = "document_reason_required";
        } else if (ContractDocumentService.ERR_INVALID.equals(errorMessage)) {
            errParam = "document_invalid";
        } else {
            errParam = "document_failed";
        }
        response.sendRedirect(request.getContextPath()
                + "/contract?action=edit&id=" + contractId + "&error=" + errParam);
    }
}

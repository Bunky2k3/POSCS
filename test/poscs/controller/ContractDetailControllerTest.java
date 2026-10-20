package poscs.controller;

import java.math.BigDecimal;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractDocument;
import poscs.model.ContractHandover;
import poscs.model.ContractHistory;
import poscs.model.Department;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.PermissionService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link ContractDetailController} (UC-11, UC-45, BR-18).
 */
public class ContractDetailControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ContractDetailController controller;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        permissionService = mock(PermissionService.class);

        controller = new ContractDetailController(contractDAO, employeeDAO, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(ContractControllerHelper.DETAIL_VIEW)).thenReturn(dispatcher);
        when(permissionService.canViewContracts(any())).thenReturn(true);

        loginAs("Sales", 2); // dept 2
    }

    private User loginAs(String roleName, Integer deptId) {
        User user = new User();
        user.setUserId(99);
        user.setFirstName("Van A");
        user.setLastName("Nguyen");
        user.setRole(new Role(1, roleName));
        if (deptId != null) {
            user.setDepartmentId(deptId);
            Department d = new Department();
            d.setDepartmentId(deptId);
            user.setDepartment(d);
        }
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(employeeDAO.findById(99)).thenReturn(user);
        return user;
    }

    @Test
    public void view_missingId_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("id")).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void view_contractNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("id")).thenReturn("123");
        when(contractDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void view_contractNotFoundWithKindBuy_preservesKindBuy() throws Exception {
        when(request.getParameter("id")).thenReturn("123");
        when(request.getParameter("kind")).thenReturn("buy");
        when(contractDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound&kind=buy");
    }

    @Test
    public void view_contractFound_forwardsToDetailViewAndBindsWorkspace() throws Exception {
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        contract.setProgressStatus(ContractDAO.PROGRESS_SIGNED);
        when(contractDAO.findById(5)).thenReturn(contract);

        ContractHistory hCreated = new ContractHistory();
        hCreated.setEventType(ContractHistory.EVENT_CREATED);
        ContractHistory hSigned = new ContractHistory();
        hSigned.setEventType(ContractHistory.EVENT_SIGNED);
        when(contractDAO.findHistoryByContractId(5)).thenReturn(List.of(hCreated, hSigned)); // UC-45

        controller.doGet(request, response);

        verify(request).setAttribute("contract", contract);
        verify(request).setAttribute(eq("contractHistory"), anyList()); // UC-45
        verify(request).setAttribute("createdEvent", hCreated);
        verify(request).setAttribute("signedEvent", hSigned);
        verify(request).setAttribute(eq("canManage"), anyBoolean());

        // Detail view must not set mutation flags
        for (String flag : new String[]{"canVoid", "canSign", "canClose", "canEditProducts", "canEditPayments"}) {
            verify(request, never()).setAttribute(eq(flag), any());
        }

        verify(dispatcher).forward(request, response);
    }

    @Test
    public void view_handoverPermissions_setAccordingToDepartment() throws Exception {
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        when(contractDAO.findById(5)).thenReturn(contract);

        ContractHandover ho1 = new ContractHandover();
        ho1.setHandoverId(11);
        ho1.setDepartmentId(2); // user's dept
        ho1.setDoneAt(null); // pending

        ContractHandover ho2 = new ContractHandover();
        ho2.setHandoverId(12);
        ho2.setDepartmentId(3); // other dept
        ho2.setDoneAt(null); // pending

        when(contractDAO.findHandoversOf(5)).thenReturn(List.of(ho1, ho2));

        controller.doGet(request, response);

        verify(request).setAttribute("canCompleteHandover_11", true);
        verify(request).setAttribute("canCompleteHandover_12", false);
        verify(request).setAttribute("hasPendingHandover", true);
    }

    @Test
    public void view_linkedBuyMargin_calculatedForSellContract() throws Exception {
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        contract.setDirection("Bán");
        contract.setContractValue(new BigDecimal("1000000000"));
        when(contractDAO.findById(5)).thenReturn(contract);
        when(contractDAO.sumLinkedBuyValue(5)).thenReturn(new BigDecimal("600000000"));

        controller.doGet(request, response);

        verify(request).setAttribute("linkIsSellSide", true);
        verify(request).setAttribute("linkedInputValue", new BigDecimal("600000000"));
        verify(request).setAttribute("linkedMargin", new BigDecimal("400000000"));
    }

    @Test
    public void view_buySideContract_marginNotCalculated() throws Exception {
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        contract.setDirection("Mua");
        when(contractDAO.findById(5)).thenReturn(contract);

        controller.doGet(request, response);

        verify(request).setAttribute("linkIsSellSide", false);
        verify(request, never()).setAttribute(eq("linkedMargin"), any());
        verify(contractDAO, never()).sumLinkedBuyValue(anyInt());
    }

    @Test
    public void view_primaryDocumentUrl_boundFromSignedContractDocument() throws Exception {
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        when(contractDAO.findById(5)).thenReturn(contract);

        ContractDocument doc = new ContractDocument();
        doc.setDocumentId(1);
        doc.setContractId(5);
        doc.setDocType(ContractDocument.TYPE_SIGNED_CONTRACT);
        doc.setFileUrl("https://poscs.example.com/contract-05.pdf");
        when(contractDAO.findDocumentsOf(5)).thenReturn(List.of(doc));

        controller.doGet(request, response);

        verify(request).setAttribute("primaryDocumentUrl", "https://poscs.example.com/contract-05.pdf");
    }

    @Test
    public void view_clusterMismatchWarning_boundWhenValuesDiffer() throws Exception {
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        contract.setContractValue(new BigDecimal("100000000"));
        when(contractDAO.findById(5)).thenReturn(contract);
        when(contractDAO.sumScheduledPaymentsForCluster(5)).thenReturn(new BigDecimal("80000000"));

        controller.doGet(request, response);

        verify(request).setAttribute("paymentMismatch", true);
    }

    @Test
    public void view_gettersAndSettersAndConstructors() {
        ContractDetailController cdc = new ContractDetailController();
        assertNotNull(cdc.getContractDAO());
        assertNotNull(cdc.getEmployeeDAO());
        assertNotNull(cdc.getPermissionService());

        cdc.setContractDAO(contractDAO);
        cdc.setEmployeeDAO(employeeDAO);
        cdc.setPermissionService(permissionService);

        assertSame(contractDAO, cdc.getContractDAO());
        assertSame(employeeDAO, cdc.getEmployeeDAO());
        assertSame(permissionService, cdc.getPermissionService());
    }

    @Test
    public void view_userCannotViewContracts_returns403WithMsg022() throws Exception {
        when(permissionService.canViewContracts(any())).thenReturn(false);
        controller.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }
}

package poscs.controller;

import java.lang.reflect.Field;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.CustomerLifecycleEventDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.Enterprise;
import poscs.model.RelationshipRating;
import poscs.model.Role;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho CustomerController -- tập trung vào các quy tắc
 * nghiệp vụ chạy trước khi chạm DB (BR-41 không xoá KH còn hợp đồng, BR-09/
 * BR-10 định dạng SĐT/email, phân quyền Full access CUSTOMER) chứ không test
 * lại CustomerDAO/JDBC. DAO được mock và inject thẳng vào field private
 * (không có constructor injection trong code gốc) bằng reflection.
 *
 * JUnit 4 (không phải 5) vì build-impl.xml của project (NetBeans Web
 * Application, Ant) chỉ nối sẵn Ant <junit> task cổ điển -- task này chỉ
 * nhận diện được org.junit.Test (JUnit 3/4), không chạy được JUnit 5 Jupiter.
 */
public class CustomerControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private CustomerController controller;
    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private ContractDAO contractDAO;
    private TechnicalSupportTicketDAO ticketDAO;
    private CustomerLifecycleEventDAO lifecycleEventDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new CustomerController();

        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        contractDAO = mock(ContractDAO.class);
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        lifecycleEventDAO = mock(CustomerLifecycleEventDAO.class);

        setField(controller, "customerDAO", customerDAO);
        setField(controller, "employeeDAO", employeeDAO);
        setField(controller, "addressDAO", addressDAO);
        setField(controller, "contractDAO", contractDAO);
        setField(controller, "ticketDAO", ticketDAO);
        setField(controller, "lifecycleEventDAO", lifecycleEventDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        loginAs("Sales"); // role được Full access trên CUSTOMER, xem PERMISSIONS.md
    }

    private void loginAs(String roleName) {
        User user = new User();
        user.setUserId(99);
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ------------------------------------------------------------------
    // GET ?action=view
    // ------------------------------------------------------------------

    @Test
    public void view_customerNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("123");
        when(customerDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
    }

    @Test
    public void view_customerFound_forwardsToDetailView() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        Enterprise enterprise = new Enterprise();
        enterprise.setEnterpriseId(5);
        when(customerDAO.findById(5)).thenReturn(enterprise);

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/viewcustomerdetail.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute("customer", enterprise);
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    // ------------------------------------------------------------------
    // POST ?action=delete (BR-41)
    // ------------------------------------------------------------------

    @Test
    public void delete_withoutFullAccess_returns403AndNeverTouchesCustomer() throws Exception {
        loginAs("Kỹ thuật"); // không có Full access trên CUSTOMER
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(customerDAO, never()).hasActiveContracts(anyInt());
        verify(customerDAO, never()).softDelete(anyInt());
    }

    @Test
    public void delete_customerHasActiveContracts_blocksDeletion() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(customerDAO.hasActiveContracts(7)).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=7&error=has_active_contracts");
    }

    @Test
    public void delete_customerHasNoActiveContracts_softDeletesAndRedirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(customerDAO.hasActiveContracts(7)).thenReturn(false);

        controller.doPost(request, response);

        verify(customerDAO).softDelete(7);
        verify(response).sendRedirect(CONTEXT_PATH + "/customer");
    }

    // ------------------------------------------------------------------
    // POST ?action=evaluate
    // ------------------------------------------------------------------

    @Test
    public void evaluate_invalidRating_redirectsWithErrorAndRecordsNothing() throws Exception {
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("3");
        when(request.getParameter("rating")).thenReturn("NOT_A_REAL_RATING");
        when(customerDAO.findById(3)).thenReturn(new Enterprise());

        controller.doPost(request, response);

        verify(customerDAO, never()).updateRelationshipRating(anyInt(), any());
        verify(lifecycleEventDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=3&error=invalid_rating");
    }

    @Test
    public void evaluate_validRating_updatesRatingAndRecordsLifecycleEvent() throws Exception {
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("3");
        when(request.getParameter("rating")).thenReturn("GOOD");
        when(request.getParameter("description")).thenReturn("Khách hàng thân thiết");
        when(customerDAO.findById(3)).thenReturn(new Enterprise());

        controller.doPost(request, response);

        verify(customerDAO).updateRelationshipRating(3, RelationshipRating.GOOD);
        verify(lifecycleEventDAO).insert(argThat((CustomerLifecycleEvent event) ->
                event.getEnterpriseId() == 3
                        && event.getRelationshipRating() == RelationshipRating.GOOD
                        && !event.isAutoGenerated()
                        && event.getRecordedBy() == 99));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=3&evaluated=1");
    }

    // ------------------------------------------------------------------
    // POST ?action=create (BR-09 SĐT, BR-10 email)
    // ------------------------------------------------------------------

    private void stubValidCreateFields() {
        when(request.getParameter("customerName")).thenReturn("Cong ty ABC");
        when(request.getParameter("customerType")).thenReturn("Nhà mạng viễn thông");
        when(request.getParameter("customerGroup")).thenReturn("VIP");
        when(request.getParameter("taxCode")).thenReturn("0101234567");
        when(request.getParameter("phone")).thenReturn("0912345678");
        when(request.getParameter("email")).thenReturn("abc@example.com");
        when(request.getParameter("accountOwnerId")).thenReturn("9");
    }

    @Test
    public void create_invalidPhone_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("phone")).thenReturn("not-a-phone"); // vi phạm BR-09

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid");
    }

    @Test
    public void create_invalidEmail_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("email")).thenReturn("not-an-email"); // vi phạm BR-10

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid");
    }

    @Test
    public void create_validFields_insertsAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(42);

        controller.doPost(request, response);

        verify(customerDAO).insert(any(Enterprise.class));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=42");
    }

    @Test
    public void create_insertFails_redirectsWithCreateFailedError() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(0);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=create_failed");
    }
}

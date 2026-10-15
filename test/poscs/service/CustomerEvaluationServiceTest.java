package poscs.service;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.CustomerDAO;
import poscs.dao.CustomerLifecycleDAO;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.Enterprise;
import poscs.model.RelationshipRating;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho CustomerEvaluationService (Task 2.2).
 * Kiểm thử nghiệp vụ đánh giá vòng đời khách hàng:
 * - Parsing chuỗi rating sang RelationshipRating enum
 * - Kiểm tra quyền hạn phạm vi địa bàn của Sales khi đánh giá
 * - Ghi nhận sự kiện đánh giá vào CustomerLifecycleEvent
 * - Lấy lịch sử sự kiện vòng đời
 */
public class CustomerEvaluationServiceTest {

    private CustomerDAO customerDAO;
    private CustomerService customerService;
    private CustomerEvaluationService service;

    private User salesUser;
    private User managerUser;

    @Before
    public void setUp() {
        customerDAO = mock(CustomerDAO.class);
        customerService = mock(CustomerService.class);
        service = new CustomerEvaluationService(customerDAO, customerService);

        salesUser = new User();
        salesUser.setUserId(10);
        salesUser.setRole(new Role(1, "Sales"));

        managerUser = new User();
        managerUser.setUserId(1);
        managerUser.setRole(new Role(2, "Sales Manager"));
    }

    @Test
    public void testParseRatingOrNull() {
        assertNull(service.parseRatingOrNull(null));
        assertNull(service.parseRatingOrNull(""));
        assertNull(service.parseRatingOrNull("   "));
        assertNull(service.parseRatingOrNull("INVALID_RATING"));

        assertEquals(RelationshipRating.GOOD, service.parseRatingOrNull("GOOD"));
        assertEquals(RelationshipRating.NEEDS_REVIEW, service.parseRatingOrNull("NEEDS_REVIEW"));
        assertEquals(RelationshipRating.BAD, service.parseRatingOrNull("BAD"));
        assertEquals(RelationshipRating.AT_RISK, service.parseRatingOrNull("AT_RISK"));
        assertEquals(RelationshipRating.GOOD, service.parseRatingOrNull("  GOOD  "));

        // Fallback tieng Viet trong CSDL
        assertEquals(RelationshipRating.GOOD, service.parseRatingOrNull("Tốt"));
        assertEquals(RelationshipRating.NEEDS_REVIEW, service.parseRatingOrNull("Cần theo dõi"));
        assertEquals(RelationshipRating.BAD, service.parseRatingOrNull("Xấu"));
        assertEquals(RelationshipRating.AT_RISK, service.parseRatingOrNull("Có nguy cơ rời bỏ"));
        assertEquals(RelationshipRating.GOOD, service.parseRatingOrNull("  Tốt  "));
    }

    @Test
    public void testEvaluateCustomer_InvalidRatingString() {
        ServiceResult<CustomerLifecycleEvent> result = service.evaluateCustomer(1, "INVALID", "ghi chu", salesUser, true);
        assertFalse(result.isSuccess());
        assertEquals("invalid_rating", result.getMessage());
        verify(customerDAO, never()).recordEvaluation(any());
    }

    @Test
    public void testEvaluateCustomer_GuardClauses() {
        // null actor
        ServiceResult<CustomerLifecycleEvent> r1 = service.evaluateCustomer(1, RelationshipRating.GOOD, "ghi chu", null, false);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        // actor.userId <= 0
        User invalidUser = new User();
        invalidUser.setUserId(0);
        ServiceResult<CustomerLifecycleEvent> r2 = service.evaluateCustomer(1, RelationshipRating.GOOD, "ghi chu", invalidUser, false);
        assertFalse(r2.isSuccess());
        assertEquals("invalid", r2.getMessage());

        // customerId <= 0
        ServiceResult<CustomerLifecycleEvent> r3 = service.evaluateCustomer(0, RelationshipRating.GOOD, "ghi chu", salesUser, false);
        assertFalse(r3.isSuccess());
        assertEquals("invalid", r3.getMessage());

        ServiceResult<CustomerLifecycleEvent> r4 = service.evaluateCustomer(-1, RelationshipRating.GOOD, "ghi chu", salesUser, false);
        assertFalse(r4.isSuccess());
        assertEquals("invalid", r4.getMessage());
    }

    @Test
    public void testEvaluateCustomer_CustomerNotFound() {
        when(customerDAO.findById(99)).thenReturn(null);

        ServiceResult<CustomerLifecycleEvent> result = service.evaluateCustomer(99, RelationshipRating.GOOD, "ghi chu", salesUser, true);
        assertFalse(result.isSuccess());
        assertEquals("notfound", result.getMessage());
        verify(customerDAO, never()).recordEvaluation(any());
    }

    @Test
    public void testEvaluateCustomer_OutsideSalesScope() {
        Enterprise customer = new Enterprise();
        customer.setEnterpriseId(1);
        when(customerDAO.findById(1)).thenReturn(customer);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(customerService.isOutsideSalesScope(customer, List.of("Khách mua"), salesUser, true)).thenReturn(true);

        ServiceResult<CustomerLifecycleEvent> result = service.evaluateCustomer(1, RelationshipRating.GOOD, "ghi chu", salesUser, true);
        assertFalse(result.isSuccess());
        assertEquals("not_your_customer", result.getMessage());
        verify(customerDAO, never()).recordEvaluation(any());
    }

    @Test
    public void testEvaluateCustomer_Success() {
        Enterprise customer = new Enterprise();
        customer.setEnterpriseId(1);
        when(customerDAO.findById(1)).thenReturn(customer);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(customerService.isOutsideSalesScope(customer, List.of("Khách mua"), salesUser, true)).thenReturn(false);
        when(customerDAO.recordEvaluation(any(CustomerLifecycleEvent.class))).thenReturn(true);

        ServiceResult<CustomerLifecycleEvent> result = service.evaluateCustomer(1, "GOOD", "Khách hàng thân thiết", salesUser, true);

        assertTrue(result.isSuccess());
        assertEquals("evaluated", result.getMessage());
        assertNotNull(result.getData());
        CustomerLifecycleEvent event = result.getData();
        assertEquals(1, event.getEnterpriseId());
        assertEquals(RelationshipRating.GOOD, event.getRelationshipRating());
        assertEquals("Khách hàng thân thiết", event.getDescription());
        assertEquals(salesUser.getUserId(), event.getRecordedBy());
        assertFalse(event.isAutoGenerated());
        assertNotNull(event.getEventDate());

        verify(customerDAO).recordEvaluation(event);
    }

    @Test
    public void testEvaluateCustomer_RecordFailed() {
        Enterprise customer = new Enterprise();
        customer.setEnterpriseId(1);
        when(customerDAO.findById(1)).thenReturn(customer);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(customerService.isOutsideSalesScope(customer, List.of("Khách mua"), salesUser, true)).thenReturn(false);
        when(customerDAO.recordEvaluation(any(CustomerLifecycleEvent.class))).thenReturn(false);

        ServiceResult<CustomerLifecycleEvent> result = service.evaluateCustomer(1, RelationshipRating.BAD, "Kem", salesUser, true);

        assertFalse(result.isSuccess());
        assertEquals("evaluate_failed", result.getMessage());
    }

    @Test
    public void testGetLifecycleEvents() {
        assertEquals(List.of(), service.getLifecycleEvents(0));
        assertEquals(List.of(), service.getLifecycleEvents(-5));

        CustomerLifecycleEvent e1 = new CustomerLifecycleEvent();
        e1.setEventId(101);
        e1.setEnterpriseId(1);
        when(customerDAO.findLifecycleEventsByEnterpriseId(1)).thenReturn(List.of(e1));

        List<CustomerLifecycleEvent> events = service.getLifecycleEvents(1);
        assertEquals(1, events.size());
        assertEquals(101, events.get(0).getEventId());
    }

    @Test
    public void testEvaluateCustomer_WithInjectedCustomerLifecycleDAO_Success() {
        CustomerLifecycleDAO lifecycleDAO = mock(CustomerLifecycleDAO.class);
        CustomerEvaluationService customService = new CustomerEvaluationService(customerDAO, lifecycleDAO, customerService);

        Enterprise customer = new Enterprise();
        customer.setEnterpriseId(1);
        when(customerDAO.findById(1)).thenReturn(customer);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(customerService.isOutsideSalesScope(customer, List.of("Khách mua"), salesUser, true)).thenReturn(false);
        when(lifecycleDAO.recordEvaluation(any(CustomerLifecycleEvent.class))).thenReturn(true);

        ServiceResult<CustomerLifecycleEvent> result = customService.evaluateCustomer(1, "GOOD", "Khách VIP", salesUser, true);

        assertTrue(result.isSuccess());
        assertEquals("evaluated", result.getMessage());
        verify(lifecycleDAO).recordEvaluation(any(CustomerLifecycleEvent.class));
        verify(customerDAO, never()).recordEvaluation(any());
        assertSame(lifecycleDAO, customService.getCustomerLifecycleDAO());
    }

    @Test
    public void testEvaluateCustomer_WithInjectedCustomerLifecycleDAO_RecordFailed() {
        CustomerLifecycleDAO lifecycleDAO = mock(CustomerLifecycleDAO.class);
        CustomerEvaluationService customService = new CustomerEvaluationService(customerDAO, lifecycleDAO, customerService);

        Enterprise customer = new Enterprise();
        customer.setEnterpriseId(1);
        when(customerDAO.findById(1)).thenReturn(customer);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(customerService.isOutsideSalesScope(customer, List.of("Khách mua"), salesUser, true)).thenReturn(false);
        when(lifecycleDAO.recordEvaluation(any(CustomerLifecycleEvent.class))).thenReturn(false);

        ServiceResult<CustomerLifecycleEvent> result = customService.evaluateCustomer(1, RelationshipRating.AT_RISK, "Nguy co", salesUser, true);

        assertFalse(result.isSuccess());
        assertEquals("evaluate_failed", result.getMessage());
        verify(lifecycleDAO).recordEvaluation(any(CustomerLifecycleEvent.class));
    }

    @Test
    public void testGetLifecycleEvents_WithInjectedCustomerLifecycleDAO() {
        CustomerLifecycleDAO lifecycleDAO = mock(CustomerLifecycleDAO.class);
        CustomerEvaluationService customService = new CustomerEvaluationService(customerDAO, lifecycleDAO, customerService);

        CustomerLifecycleEvent ev = new CustomerLifecycleEvent();
        ev.setEventId(202);
        when(lifecycleDAO.findLifecycleEventsByEnterpriseId(5)).thenReturn(List.of(ev));

        List<CustomerLifecycleEvent> events = customService.getLifecycleEvents(5);
        assertEquals(1, events.size());
        assertEquals(202, events.get(0).getEventId());
        verify(lifecycleDAO).findLifecycleEventsByEnterpriseId(5);
        verify(customerDAO, never()).findLifecycleEventsByEnterpriseId(anyInt());
    }
}

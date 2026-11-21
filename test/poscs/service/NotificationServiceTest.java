package poscs.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.dao.ContractDAO;
import poscs.dao.NotificationDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.Notification;
import poscs.model.TechnicalRequest;
import poscs.validator.ContractValidator;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link NotificationService}:
 * - Tự sinh đúng 2 loại thông báo (HĐ sắp hết hạn ≤ 30 ngày; phiếu SLA ≤ 24 giờ),
 *   idempotent theo cặp (người nhận, đối tượng), không tạo trùng khi chạy lại.
 * - UC-14, UC-15, BR-20, BR-21: xem danh sách không tự đánh dấu đã đọc, đánh dấu
 *   đọc/mở thông báo đúng chủ sở hữu.
 */
public class NotificationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private NotificationDAO notificationDAO;
    private ContractDAO contractDAO;
    private TechnicalSupportTicketDAO ticketDAO;
    private NotificationService service;

    @Before
    public void setUp() {
        notificationDAO = mock(NotificationDAO.class);
        contractDAO = mock(ContractDAO.class);
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        service = new NotificationService(notificationDAO, contractDAO, ticketDAO, FIXED_CLOCK);
    }

    private Contract contract(int id, String code, int ownerId, LocalDate endDate) {
        Contract c = new Contract();
        c.setContractId(id);
        c.setContractCode(code);
        c.setOwnerId(ownerId);
        c.setEndDate(endDate != null ? Date.valueOf(endDate) : null);
        return c;
    }

    private TechnicalRequest ticket(int id, String code, int techId, LocalDateTime slaDeadline, String status) {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketId(id);
        t.setTicketCode(code);
        t.setAssignedTechnicianId(techId);
        t.setSlaDeadline(slaDeadline != null ? Timestamp.valueOf(slaDeadline) : null);
        t.setStatus(status);
        return t;
    }

    // ------------------------------------------------------------------
    // Hợp đồng sắp hết hạn (BR-17: ≤ 30 ngày)
    // ------------------------------------------------------------------

    @Test
    public void generateContractExpiring_validContractWithin30Days_insertsNotification() {
        Contract c = contract(15, "01/2026/HDKT", 7, TODAY.plusDays(10));
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Collections.singletonList(c));
        when(notificationDAO.existsForUserAndRef(7, NotificationService.REF_CONTRACT_EXPIRING, 15)).thenReturn(false);
        when(notificationDAO.insert(eq(7), anyString(), eq(NotificationService.REF_CONTRACT_EXPIRING), eq(15)))
                .thenReturn(true);

        int created = service.generateContractExpiringNotifications();

        assertEquals(1, created);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationDAO).insert(eq(7), titleCaptor.capture(),
                eq(NotificationService.REF_CONTRACT_EXPIRING), eq(15));
        assertEquals("Hợp đồng 01/2026/HDKT sắp hết hạn (còn 10 ngày).", titleCaptor.getValue());
    }

    @Test
    public void thresholds_matchSpecAndContractValidator() {
        assertEquals(ContractValidator.SOON_THRESHOLD_DAYS, NotificationService.CONTRACT_EXPIRING_DAYS);
        assertEquals(24, NotificationService.TICKET_SLA_HOURS);
    }

    @Test
    public void generateContractExpiring_nullEndDate_skipped() {
        Contract noEndDate = contract(15, "01/2026/HDKT", 7, null);
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Collections.singletonList(noEndDate));

        int created = service.generateContractExpiringNotifications();

        assertEquals(0, created);
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void generateContractExpiring_boundary0And30Days_createsBoth_31AndNegativeDays_skips() {
        Contract day0 = contract(1, "HD-0", 7, TODAY);
        Contract day30 = contract(2, "HD-30", 7, TODAY.plusDays(30));
        Contract day31 = contract(3, "HD-31", 7, TODAY.plusDays(31));
        Contract expired = contract(4, "HD-EXP", 7, TODAY.minusDays(1));
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE))
                .thenReturn(Arrays.asList(day0, day30, day31, expired));
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt())).thenReturn(false);
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        int created = service.generateContractExpiringNotifications();

        assertEquals(2, created);
        verify(notificationDAO).insert(eq(7), anyString(), eq(NotificationService.REF_CONTRACT_EXPIRING), eq(1));
        verify(notificationDAO).insert(eq(7), anyString(), eq(NotificationService.REF_CONTRACT_EXPIRING), eq(2));
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), eq(3));
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), eq(4));
    }

    @Test
    public void generateContractExpiring_alreadyNotified_doesNotDuplicate() {
        Contract c = contract(15, "01/2026/HDKT", 7, TODAY.plusDays(5));
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Collections.singletonList(c));
        when(notificationDAO.existsForUserAndRef(7, NotificationService.REF_CONTRACT_EXPIRING, 15)).thenReturn(true);

        int created = service.generateContractExpiringNotifications();

        assertEquals(0, created);
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), anyInt());
    }

    @Test
    public void generateContractExpiring_ineligibleContracts_skipped() {
        Contract noOwner = contract(1, "HD-1", 0, TODAY.plusDays(5));
        Contract invalidId = contract(0, "HD-0", 7, TODAY.plusDays(5));
        Contract deleted = contract(2, "HD-2", 7, TODAY.plusDays(5));
        deleted.setDeleted(true);
        Contract amendment = contract(3, "HD-3", 7, TODAY.plusDays(5));
        amendment.setParentContractId(99);
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE))
                .thenReturn(Arrays.asList(null, noOwner, invalidId, deleted, amendment));

        int created = service.generateContractExpiringNotifications();

        assertEquals(0, created);
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void generateContractExpiring_nullList_returnsZero() {
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(null);
        assertEquals(0, service.generateContractExpiringNotifications());
    }

    @Test
    public void generateContractExpiring_blankAndLongCode_handledSafelyWithin150Chars() {
        Contract blankCode = contract(11, "   ", 7, TODAY.plusDays(3));
        String veryLongCode = "HD-" + "X".repeat(200);
        Contract longCode = contract(12, veryLongCode, 7, TODAY.plusDays(3));
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Arrays.asList(blankCode, longCode));
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt())).thenReturn(false);
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        int created = service.generateContractExpiringNotifications();

        assertEquals(2, created);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationDAO, times(2)).insert(eq(7), titleCaptor.capture(),
                eq(NotificationService.REF_CONTRACT_EXPIRING), anyInt());
        assertTrue(titleCaptor.getAllValues().get(0).contains("#11"));
        assertEquals(NotificationService.TITLE_MAX_LENGTH, titleCaptor.getAllValues().get(1).length());
    }

    // ------------------------------------------------------------------
    // Phiếu hỗ trợ sắp/đã quá hạn SLA (≤ 24 giờ)
    // ------------------------------------------------------------------

    @Test
    public void generateTicketSla_dueWithin24Hours_insertsNotification() {
        TechnicalRequest t = ticket(9, "REQ-2026-009", 12, NOW.plusHours(12), "Đang xử lý");
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Collections.singletonList(t));
        when(notificationDAO.existsForUserAndRef(12, NotificationService.REF_TICKET_SLA, 9)).thenReturn(false);
        when(notificationDAO.insert(eq(12), anyString(), eq(NotificationService.REF_TICKET_SLA), eq(9)))
                .thenReturn(true);

        int created = service.generateTicketSlaNotifications();

        assertEquals(1, created);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationDAO).insert(eq(12), titleCaptor.capture(),
                eq(NotificationService.REF_TICKET_SLA), eq(9));
        assertEquals("Phiếu REQ-2026-009 sắp/đã quá hạn xử lý SLA.", titleCaptor.getValue());
    }

    @Test
    public void generateTicketSla_nullSla_skipped() {
        TechnicalRequest noSla = ticket(9, "REQ-2026-009", 12, null, "Đang xử lý");
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Collections.singletonList(noSla));

        int created = service.generateTicketSlaNotifications();

        assertEquals(0, created);
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void generateTicketSla_boundary24HoursAndOverdue_creates_over24Hours_skips() {
        TechnicalRequest exact24h = ticket(1, "TK-1", 12, NOW.plusHours(24), "Mới tiếp nhận");
        TechnicalRequest overdue = ticket(2, "TK-2", 12, NOW.minusHours(2), "Đang xử lý");
        TechnicalRequest over24h = ticket(3, "TK-3", 12, NOW.plusHours(24).plusSeconds(1), "Đang xử lý");
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Arrays.asList(exact24h, overdue, over24h));
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt())).thenReturn(false);
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        int created = service.generateTicketSlaNotifications();

        assertEquals(2, created);
        verify(notificationDAO).insert(eq(12), anyString(), eq(NotificationService.REF_TICKET_SLA), eq(1));
        verify(notificationDAO).insert(eq(12), anyString(), eq(NotificationService.REF_TICKET_SLA), eq(2));
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), eq(3));
    }

    @Test
    public void generateTicketSla_alreadyNotified_doesNotDuplicate() {
        TechnicalRequest t = ticket(9, "REQ-2026-009", 12, NOW.plusHours(4), "Đang xử lý");
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Collections.singletonList(t));
        when(notificationDAO.existsForUserAndRef(12, NotificationService.REF_TICKET_SLA, 9)).thenReturn(true);

        int created = service.generateTicketSlaNotifications();

        assertEquals(0, created);
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), anyInt());
    }

    @Test
    public void generateTicketSla_ineligibleTickets_skipped() {
        TechnicalRequest noTech = ticket(1, "TK-1", 0, NOW.plusHours(2), "Đang xử lý");
        TechnicalRequest invalidId = ticket(0, "TK-0", 12, NOW.plusHours(2), "Đang xử lý");
        TechnicalRequest deleted = ticket(2, "TK-2", 12, NOW.plusHours(2), "Đang xử lý");
        deleted.setDeleted(true);
        TechnicalRequest closed = ticket(3, "TK-3", 12, NOW.plusHours(2),
                TechnicalSupportTicketDAO.STATUS_CLOSED);
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Arrays.asList(null, noTech, invalidId, deleted, closed));

        int created = service.generateTicketSlaNotifications();

        assertEquals(0, created);
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void generateScheduledNotifications_runsBothTypesAndReturnsTotal() {
        Contract c = contract(15, "01/2026/HDKT", 7, TODAY.plusDays(10));
        TechnicalRequest t = ticket(9, "REQ-2026-009", 12, NOW.plusHours(6), "Đang xử lý");
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Collections.singletonList(c));
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Collections.singletonList(t));
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt())).thenReturn(false);
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        int total = service.generateScheduledNotifications();

        assertEquals(2, total);
        verify(notificationDAO).insert(eq(7), anyString(), eq(NotificationService.REF_CONTRACT_EXPIRING), eq(15));
        verify(notificationDAO).insert(eq(12), anyString(), eq(NotificationService.REF_TICKET_SLA), eq(9));
    }

    @Test
    public void generateScheduledNotifications_runTwice_isIdempotentAndDoesNotDuplicate() {
        Contract c = contract(15, "01/2026/HDKT", 7, TODAY.plusDays(10));
        TechnicalRequest t = ticket(9, "REQ-2026-009", 12, NOW.plusHours(6), "Đang xử lý");
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Collections.singletonList(c));
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Collections.singletonList(t));

        Set<String> insertedKeys = new HashSet<>();
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt()))
                .thenAnswer(inv -> insertedKeys.contains(
                        inv.getArgument(0) + ":" + inv.getArgument(1) + ":" + inv.getArgument(2)));
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> insertedKeys.add(
                        inv.getArgument(0) + ":" + inv.getArgument(2) + ":" + inv.getArgument(3)));

        int run1 = service.generateScheduledNotifications();
        int run2 = service.generateScheduledNotifications();

        assertEquals(2, run1);
        assertEquals(0, run2);
        assertEquals(2, insertedKeys.size());
    }

    @Test
    public void generate_whenInsertFails_doesNotIncrementCreatedCount() {
        Contract c = contract(15, "01/2026/HDKT", 7, TODAY.plusDays(10));
        TechnicalRequest t = ticket(9, "REQ-2026-009", 12, NOW.plusHours(6), "Đang xử lý");
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Collections.singletonList(c));
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Collections.singletonList(t));
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt())).thenReturn(false);
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(false);

        assertEquals(0, service.generateContractExpiringNotifications());
        assertEquals(0, service.generateTicketSlaNotifications());
    }

    @Test
    public void generate_emptyAndNullLists_returnZero() {
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Collections.emptyList());
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(null);
        assertEquals(0, service.generateScheduledNotifications());

        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Collections.emptyList());
        assertEquals(0, service.generateTicketSlaNotifications());
    }

    @Test
    public void generate_distinctRecipients_sendsToEachAssignedOwnerAndTechnician() {
        Contract c1 = contract(15, "HD-15", 7, TODAY.plusDays(5));
        Contract c2 = contract(16, "HD-16", 8, TODAY.plusDays(5));
        TechnicalRequest t1 = ticket(9, "TK-9", 12, NOW.plusHours(4), "Đang xử lý");
        TechnicalRequest t2 = ticket(10, "TK-10", 13, NOW.plusHours(4), "Đang xử lý");
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Arrays.asList(c1, c2));
        when(ticketDAO.findOverdueOrDueSoon()).thenReturn(Arrays.asList(t1, t2));
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt())).thenReturn(false);
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        int total = service.generateScheduledNotifications();

        assertEquals(4, total);
        verify(notificationDAO).insert(eq(7), anyString(), eq(NotificationService.REF_CONTRACT_EXPIRING), eq(15));
        verify(notificationDAO).insert(eq(8), anyString(), eq(NotificationService.REF_CONTRACT_EXPIRING), eq(16));
        verify(notificationDAO).insert(eq(12), anyString(), eq(NotificationService.REF_TICKET_SLA), eq(9));
        verify(notificationDAO).insert(eq(13), anyString(), eq(NotificationService.REF_TICKET_SLA), eq(10));
    }

    // ------------------------------------------------------------------
    // UC-14, UC-15, BR-20, BR-21
    // ------------------------------------------------------------------

    @Test
    public void findAllByUser_doesNotMarkAsRead_andHandlesInvalidUser() {
        Notification n = new Notification();
        n.setNotificationId(1);
        when(notificationDAO.findAllByUser(7)).thenReturn(Collections.singletonList(n));

        List<Notification> list = service.findAllByUser(7);

        assertEquals(1, list.size());
        // BR-21: mở danh sách KHÔNG tự đánh dấu đã đọc
        verify(notificationDAO, never()).markAsRead(anyInt(), anyInt());
        verify(notificationDAO, never()).markAllAsRead(anyInt());

        assertTrue(service.findAllByUser(0).isEmpty());
        verify(notificationDAO, never()).findAllByUser(0);
    }

    @Test
    public void findRecentAndFindAll_preserveDaoOrderAndDoNotMarkAsRead() {
        Notification newer = new Notification();
        newer.setNotificationId(20);
        Notification older = new Notification();
        older.setNotificationId(10);
        when(notificationDAO.findRecentByUser(7, 5)).thenReturn(Arrays.asList(newer, older));
        when(notificationDAO.findAllByUser(7)).thenReturn(Arrays.asList(newer, older));

        List<Notification> recent = service.findRecentByUser(7, 5);
        List<Notification> all = service.findAllByUser(7);

        assertEquals(Arrays.asList(newer, older), recent);
        assertEquals(Arrays.asList(newer, older), all);
        verify(notificationDAO, never()).markAsRead(anyInt(), anyInt());
        verify(notificationDAO, never()).markAllAsRead(anyInt());
    }

    @Test
    public void findRecentByUser_validatesInputsAndDoesNotMarkAsRead() {
        when(notificationDAO.findRecentByUser(7, 5)).thenReturn(null);
        assertTrue(service.findRecentByUser(7, 5).isEmpty());
        assertTrue(service.findRecentByUser(0, 5).isEmpty());
        assertTrue(service.findRecentByUser(7, 0).isEmpty());
        verify(notificationDAO, never()).markAsRead(anyInt(), anyInt());
    }

    @Test
    public void countUnread_validatesUserId() {
        when(notificationDAO.countUnread(7)).thenReturn(3);
        assertEquals(3, service.countUnread(7));
        assertEquals(0, service.countUnread(0));
        verify(notificationDAO, never()).countUnread(0);
    }

    @Test
    public void markAsReadAndMarkAllAsRead_validateIds() {
        when(notificationDAO.markAsRead(42, 7)).thenReturn(true);
        when(notificationDAO.markAllAsRead(7)).thenReturn(4);

        assertTrue(service.markAsRead(42, 7));
        assertFalse(service.markAsRead(0, 7));
        assertFalse(service.markAsRead(42, 0));

        assertEquals(4, service.markAllAsRead(7));
        assertEquals(0, service.markAllAsRead(0));
    }

    @Test
    public void openNotification_owned_marksReadAndReturns_otherUser_returnsNullWithoutMarking() {
        Notification n = new Notification();
        n.setNotificationId(42);
        n.setUserId(7);
        when(notificationDAO.findByIdForUser(42, 7)).thenReturn(n);
        when(notificationDAO.findByIdForUser(99, 7)).thenReturn(null);

        Notification opened = service.openNotification(42, 7);
        assertSame(n, opened);
        verify(notificationDAO).markAsRead(42, 7);

        assertNull(service.openNotification(99, 7));
        verify(notificationDAO, never()).markAsRead(99, 7);
        assertNull(service.openNotification(42, 8));
        verify(notificationDAO, never()).markAsRead(42, 8);
        assertNull(service.openNotification(0, 7));
    }

    /**
     * Task 10.2 (BR-58): contract_code giờ để NULL được. Lịch nhắc chỉ lấy hợp
     * đồng đã ký (luôn có mã), nhưng dòng dữ liệu NULL lọt vào cũng không được
     * in chữ "null" vào tiêu đề thông báo.
     */
    @Test
    public void br58_generateContractExpiring_nullCode_neverPrintsNull() {
        Contract nullCode = contract(13, null, 7, TODAY.plusDays(3));
        when(contractDAO.findExpiringSoon(Integer.MAX_VALUE)).thenReturn(Arrays.asList(nullCode));
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt())).thenReturn(false);
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        assertEquals(1, service.generateContractExpiringNotifications());

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationDAO).insert(eq(7), titleCaptor.capture(),
                eq(NotificationService.REF_CONTRACT_EXPIRING), eq(13));
        assertFalse(titleCaptor.getValue(), titleCaptor.getValue().contains("null"));
        assertTrue(titleCaptor.getValue(), titleCaptor.getValue().contains("#13"));
    }

    // ------------------------------------------------------------------
    // Task 10.5 / BR-62: nhắc ký hợp đồng đã giao hàng quá 15 ngày
    // ------------------------------------------------------------------

    /** Hôm nay − 15 ngày: mốc lịch nhắc truyền xuống DAO (BR-62 "ngày giao ≤ hôm nay − 15 ngày"). */
    private static final Date CUTOFF = Date.valueOf(TODAY.minusDays(15));

    private Contract delivered(int id, String title, int ownerId, LocalDate deliveredOn) {
        Contract c = new Contract();
        c.setContractId(id);
        c.setTitle(title);
        c.setOwnerId(ownerId);
        c.setProgressStatus(ContractDAO.PROGRESS_DRAFT);
        c.setDeliveryStage(Contract.DELIVERY_DELIVERED);
        c.setDeliveredDate(deliveredOn != null ? Date.valueOf(deliveredOn) : null);
        return c;
    }

    @Test
    public void unsignedDelivery_constantsMatchSpec() {
        assertEquals(15, NotificationService.UNSIGNED_DELIVERY_DAYS);
        // ref_type riêng, khác loại sắp hết hạn, vừa cột varchar(30).
        assertNotEquals(NotificationService.REF_CONTRACT_EXPIRING, NotificationService.REF_CONTRACT_UNSIGNED_DELIVERY);
        assertTrue(NotificationService.REF_CONTRACT_UNSIGNED_DELIVERY.length() <= 30);
    }

    @Test
    public void unsignedDelivery_exactly15Days_createsOneNotificationForOwnerWithMsg065() {
        Contract c = delivered(31, "Cung cấp thiết bị Quảng Trị", 7, TODAY.minusDays(15));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        assertEquals(1, service.generateUnsignedDeliveryNotifications());

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationDAO).insert(eq(7), title.capture(),
                eq(NotificationService.REF_CONTRACT_UNSIGNED_DELIVERY), eq(31));
        assertEquals("Hợp đồng Cung cấp thiết bị Quảng Trị đã giao hàng 15 ngày nhưng chưa ký.", title.getValue());
    }

    @Test
    public void unsignedDelivery_16DaysAgo_createsNotificationSaying16Days() {
        Contract c = delivered(32, "HĐ giao 16 ngày", 8, TODAY.minusDays(16));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        assertEquals(1, service.generateUnsignedDeliveryNotifications());

        verify(notificationDAO).insert(8, "Hợp đồng HĐ giao 16 ngày đã giao hàng 16 ngày nhưng chưa ký.",
                NotificationService.REF_CONTRACT_UNSIGNED_DELIVERY, 32);
    }

    @Test
    public void unsignedDelivery_only14Days_noNotification() {
        // Câu truy vấn đã lọc theo mốc; nếu nó lỡ trả về bản 14 ngày thì service vẫn không nhắc.
        Contract c = delivered(33, "HĐ mới giao", 7, TODAY.minusDays(14));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));

        assertEquals(0, service.generateUnsignedDeliveryNotifications());

        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void unsignedDelivery_asksDaoForCutoffTodayMinus15() {
        service.generateUnsignedDeliveryNotifications();

        verify(contractDAO).findDeliveredUnsigned(Date.valueOf(LocalDate.of(2026, 10, 30)));
    }

    @Test
    public void unsignedDelivery_alreadyNotified_notCreatedAgain() {
        Contract c = delivered(34, "HĐ đã nhắc", 7, TODAY.minusDays(20));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));
        when(notificationDAO.existsForUserAndRef(7, NotificationService.REF_CONTRACT_UNSIGNED_DELIVERY, 34))
                .thenReturn(true);

        assertEquals(0, service.generateUnsignedDeliveryNotifications());

        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void unsignedDelivery_runTwice_insertsOnlyOnce() {
        Contract c = delivered(35, "HĐ chạy hai lượt", 7, TODAY.minusDays(20));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));
        Set<String> sent = new HashSet<>();
        when(notificationDAO.existsForUserAndRef(anyInt(), anyString(), anyInt()))
                .thenAnswer(inv -> sent.contains(inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2)));
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenAnswer(inv -> {
            sent.add(inv.getArgument(0) + "|" + inv.getArgument(2) + "|" + inv.getArgument(3));
            return true;
        });

        assertEquals(1, service.generateUnsignedDeliveryNotifications());
        assertEquals(0, service.generateUnsignedDeliveryNotifications());

        verify(notificationDAO, times(1)).insert(anyInt(), anyString(), anyString(), anyInt());
    }

    @Test
    public void unsignedDelivery_signedContract_noNotification() {
        Contract signed = delivered(36, "HĐ đã ký", 7, TODAY.minusDays(30));
        signed.setProgressStatus(ContractDAO.PROGRESS_SIGNED);
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(signed));

        assertEquals(0, service.generateUnsignedDeliveryNotifications());

        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void unsignedDelivery_voidedDeliveryNote_notCounted() {
        // Biên bản giao nhận đã huỷ không tính (BR-61): DAO đọc ra bản Nháp không còn bước
        // giao hàng và không có ngày giao -- không được nhắc.
        Contract voided = delivered(37, "HĐ huỷ biên bản", 7, null);
        voided.setDeliveryStage(null);
        Contract preparing = delivered(38, "HĐ chỉ có phiếu", 7, null);
        preparing.setDeliveryStage(Contract.DELIVERY_PREPARING);
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(voided, preparing));

        assertEquals(0, service.generateUnsignedDeliveryNotifications());

        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void unsignedDelivery_invalidRows_skipped() {
        Contract noOwner = delivered(39, "HĐ không người phụ trách", 0, TODAY.minusDays(20));
        Contract deleted = delivered(40, "HĐ đã huỷ bản ghi", 7, TODAY.minusDays(20));
        deleted.setDeleted(true);
        Contract noId = delivered(0, "HĐ không id", 7, TODAY.minusDays(20));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(Arrays.asList(null, noOwner, deleted, noId));

        assertEquals(0, service.generateUnsignedDeliveryNotifications());

        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), any());
    }

    @Test
    public void unsignedDelivery_daoReturnsNullOrEmpty_returnsZero() {
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(null);
        assertEquals(0, service.generateUnsignedDeliveryNotifications());

        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(Collections.emptyList());
        assertEquals(0, service.generateUnsignedDeliveryNotifications());
    }

    @Test
    public void unsignedDelivery_insertFails_notCounted() {
        Contract c = delivered(41, "HĐ ghi lỗi", 7, TODAY.minusDays(20));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(false);

        assertEquals(0, service.generateUnsignedDeliveryNotifications());
    }

    @Test
    public void unsignedDelivery_longTitle_isShortenedButSentenceStaysWhole() {
        String longTitle = "Cung cấp lắp đặt thiết bị truyền dẫn quang ".repeat(6).trim();
        Contract c = delivered(42, longTitle, 7, TODAY.minusDays(15));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        service.generateUnsignedDeliveryNotifications();

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationDAO).insert(eq(7), title.capture(), anyString(), eq(42));
        assertTrue(title.getValue().length() <= NotificationService.TITLE_MAX_LENGTH);
        assertTrue(title.getValue(), title.getValue().startsWith("Hợp đồng Cung cấp lắp đặt"));
        assertTrue(title.getValue(), title.getValue().endsWith("… đã giao hàng 15 ngày nhưng chưa ký."));
    }

    @Test
    public void unsignedDelivery_blankTitle_fallsBackToDisplayCode() {
        Contract c = delivered(43, "  ", 7, TODAY.minusDays(15));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        service.generateUnsignedDeliveryNotifications();

        verify(notificationDAO).insert(7, "Hợp đồng " + Contract.NO_CODE_LABEL + " đã giao hàng 15 ngày nhưng chưa ký.",
                NotificationService.REF_CONTRACT_UNSIGNED_DELIVERY, 43);
    }

    @Test
    public void generateScheduled_includesUnsignedDeliveryReminders() {
        Contract c = delivered(44, "HĐ lịch chạy", 7, TODAY.minusDays(15));
        when(contractDAO.findDeliveredUnsigned(CUTOFF)).thenReturn(List.of(c));
        when(notificationDAO.insert(anyInt(), anyString(), anyString(), anyInt())).thenReturn(true);

        assertEquals(1, service.generateScheduledNotifications());

        verify(notificationDAO).insert(eq(7), anyString(), eq(NotificationService.REF_CONTRACT_UNSIGNED_DELIVERY), eq(44));
    }
}

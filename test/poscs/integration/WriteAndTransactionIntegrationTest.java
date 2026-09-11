package poscs.integration;

import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.NotificationDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Address;
import poscs.model.Enterprise;
import poscs.model.TechnicalRequest;
import poscs.model.TechnicalRequestHistory;
import poscs.model.User;

import static org.junit.Assert.*;

/**
 * Các hành vi GHI mà test mock không thể kiểm được: ràng buộc UNIQUE/khoá
 * ngoại do CSDL áp, transaction có rollback thật hay không, và việc sinh mã
 * tự tăng có đụng nhau không.
 *
 * Mock luôn trả về "thành công" cho executeUpdate(), nên mọi quy tắc do chính
 * CSDL enforce đều vô hình với chúng.
 *
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class WriteAndTransactionIntegrationTest {

    private static final CustomerDAO customerDAO = new CustomerDAO();
    private static final EmployeeDAO employeeDAO = new EmployeeDAO();
    private static final NotificationDAO notificationDAO = new NotificationDAO();
    private static final TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();

    @BeforeClass
    public static void prepareSchema() throws Exception {
        IntegrationDb.assumeAvailable();
        IntegrationDb.resetSchema();
    }

    @Before
    public void freshData() throws Exception {
        IntegrationDb.assumeAvailable();
        IntegrationDb.clearBusinessData();
        Fixtures.seedAddressAndUser();
        Fixtures.seedEnterprise();
    }

    // ------------------------------------------------------------------
    // Transaction: địa chỉ và bản ghi chính phải cùng sống hoặc cùng chết
    // ------------------------------------------------------------------

    @Test
    public void customerInsert_writesEnterpriseAndAddressTogether() throws Exception {
        IntegrationDb.assumeAvailable();
        int addressesBefore = IntegrationDb.count("addresses", null);

        Enterprise e = newEnterprise("KH-9001", "0100009001", "kh9001@example.com", "0900009001");
        Address a = new Address();
        a.setStreetAndLocalName("Số 9 Đường Mới");
        a.setDistrictId(Fixtures.WARD_ID);
        e.setAddress(a);

        int newId = customerDAO.insert(e);

        assertTrue("insert phải trả về id mới", newId > 0);
        assertEquals("phải thêm đúng 1 dòng addresses",
                addressesBefore + 1, IntegrationDb.count("addresses", null));
        assertNotNull("enterprise mới phải trỏ tới address vừa tạo",
                IntegrationDb.scalar("SELECT address_id FROM enterprises WHERE enterprise_id = " + newId));
    }

    @Test
    public void customerInsert_duplicateTaxCode_rollsBackTheAddressToo() throws Exception {
        IntegrationDb.assumeAvailable();
        // tax_code có UNIQUE KEY. Mock không biết điều đó nên luôn báo thành công.
        int addressesBefore = IntegrationDb.count("addresses", null);

        Enterprise e = newEnterprise("KH-9002", "0100000001" /* trùng với fixture */,
                "kh9002@example.com", "0900009002");
        Address a = new Address();
        a.setStreetAndLocalName("Số 9 Đường Trùng Mã Số Thuế");
        a.setDistrictId(Fixtures.WARD_ID);
        e.setAddress(a);

        int newId = customerDAO.insert(e);

        assertTrue("Trùng mã số thuế thì phải thất bại", newId <= 0);
        assertEquals("Địa chỉ đã chèn trước đó PHẢI được rollback, không để lại dòng mồ côi",
                addressesBefore, IntegrationDb.count("addresses", null));
    }

    @Test
    public void customerUpdate_reusesTheSameAddressRowInsteadOfCreatingNewOnes() throws Exception {
        IntegrationDb.assumeAvailable();
        int addressesBefore = IntegrationDb.count("addresses", null);

        Enterprise e = customerDAO.findById(Fixtures.ENTERPRISE_ID);
        for (int i = 1; i <= 3; i++) {
            Address a = new Address();
            a.setStreetAndLocalName("Số " + i + " Đường Sửa Nhiều Lần");
            a.setDistrictId(Fixtures.WARD_ID);
            e.setAddress(a);
            assertTrue(customerDAO.update(e));
        }

        assertEquals("Sửa địa chỉ 3 lần phải ghi đè cùng 1 dòng, không tạo dòng mới mỗi lần",
                addressesBefore, IntegrationDb.count("addresses", null));
        assertEquals("Số 3 Đường Sửa Nhiều Lần",
                IntegrationDb.scalar("SELECT street_and_local_name FROM addresses WHERE address_id = "
                        + Fixtures.ADDRESS_ID));
    }

    @Test
    public void employeeUpdateProfile_duplicatePhone_rollsBackEverything() throws Exception {
        IntegrationDb.assumeAvailable();
        // Thêm người thứ hai để có số điện thoại mà đụng vào.
        IntegrationDb.exec(
            "INSERT INTO users (user_id, username, email, password_hash, role_id, last_name, first_name, "
            + "gender, date_of_birth, citizen_id, phone, department_id, hire_date) VALUES "
            + "(2, 'itsales', 'itsales@poscs.test', '" + Fixtures.PASSWORD_HASH + "', 2, N'Trần', N'Kinh Doanh', "
            + "N'Nam', '1992-02-02', '000000000002', '0900000002', 1, '2024-01-01')");

        String nameBefore = IntegrationDb.scalar(
                "SELECT first_name FROM users WHERE user_id = " + Fixtures.USER_ID);

        User u = new User();
        u.setUserId(Fixtures.USER_ID);
        u.setLastName("Nguyễn");
        u.setFirstName("Tên Mới");
        u.setGender("Nam");
        u.setDateOfBirth(java.sql.Date.valueOf("1990-01-01"));
        u.setCitizenId(Fixtures.CITIZEN_ID);
        u.setPhone("0900000002"); // trùng với người vừa thêm
        u.setPersonalEmail("moi@gmail.com");
        Address a = new Address();
        a.setStreetAndLocalName("Địa chỉ đổi kèm");
        a.setDistrictId(Fixtures.WARD_ID);
        u.setAddress(a);

        assertFalse("Trùng số điện thoại phải thất bại", employeeDAO.updateProfile(u));
        assertEquals("Tên KHÔNG được đổi khi cả transaction đã rollback",
                nameBefore,
                IntegrationDb.scalar("SELECT first_name FROM users WHERE user_id = " + Fixtures.USER_ID));
    }

    // ------------------------------------------------------------------
    // Sinh mã tự tăng
    // ------------------------------------------------------------------

    @Test
    public void generatedCodes_areUniqueAndAdvanceAfterEachInsert() throws Exception {
        IntegrationDb.assumeAvailable();

        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 5; i++) {
            String code = customerDAO.generateNextEnterpriseCode();
            assertTrue("Mã khách hàng bị trùng: " + code, codes.add(code));

            Enterprise e = newEnterprise(code, "01000090" + (10 + i),
                    "kh" + i + "@example.com", "09000090" + (10 + i));
            assertTrue(customerDAO.insert(e) > 0);
        }
        assertEquals(6, IntegrationDb.count("enterprises", "is_deleted = 0"));
    }

    // ------------------------------------------------------------------
    // Thông báo: idempotent theo (user, ref_type, ref_id)
    // ------------------------------------------------------------------

    @Test
    public void notification_isNotDuplicatedForTheSameReference() throws Exception {
        IntegrationDb.assumeAvailable();
        // NotificationScheduler chạy lại mỗi giờ và mỗi lần khởi động lại
        // server; không idempotent là người dùng ngập thông báo trùng.
        assertFalse(notificationDAO.existsForUserAndRef(Fixtures.USER_ID, "contract_expiring", 1));

        assertTrue(notificationDAO.insert(Fixtures.USER_ID, "Hợp đồng sắp hết hạn", "contract_expiring", 1));
        assertTrue(notificationDAO.existsForUserAndRef(Fixtures.USER_ID, "contract_expiring", 1));

        assertEquals(1, IntegrationDb.count("notifications", "user_id = " + Fixtures.USER_ID));
        assertFalse("Khác ref_type thì là thông báo khác",
                notificationDAO.existsForUserAndRef(Fixtures.USER_ID, "ticket_sla", 1));
    }

    @Test
    public void markAllAsRead_onlyTouchesTheGivenUser() throws Exception {
        IntegrationDb.assumeAvailable();
        IntegrationDb.exec(
            "INSERT INTO users (user_id, username, email, password_hash, role_id, last_name, first_name, "
            + "gender, date_of_birth, citizen_id, phone, department_id, hire_date) VALUES "
            + "(2, 'itsales', 'itsales@poscs.test', '" + Fixtures.PASSWORD_HASH + "', 2, N'Trần', N'Kinh Doanh', "
            + "N'Nam', '1992-02-02', '000000000002', '0900000002', 1, '2024-01-01')");
        notificationDAO.insert(Fixtures.USER_ID, "Của người 1", "contract_expiring", 1);
        notificationDAO.insert(2, "Của người 2", "contract_expiring", 1);

        notificationDAO.markAllAsRead(Fixtures.USER_ID);

        assertEquals(0, notificationDAO.countUnread(Fixtures.USER_ID));
        assertEquals("Không được đọc hộ thông báo của người khác", 1, notificationDAO.countUnread(2));
    }

    // ------------------------------------------------------------------
    // Xoá mềm phiếu hỗ trợ
    // ------------------------------------------------------------------

    @Test
    public void ticketSoftDelete_hidesRowFromListAndCount() throws Exception {
        IntegrationDb.assumeAvailable();
        Fixtures.seedContract();
        Fixtures.seedTicket();

        assertEquals(1, ticketDAO.countAll(null, null, null));
        assertTrue(ticketDAO.softDelete(Fixtures.TICKET_ID));

        assertEquals(0, ticketDAO.countAll(null, null, null));
        assertEquals(0, ticketDAO.findAll(1, 50, null, null, null).size());
        assertNull(ticketDAO.findById(Fixtures.TICKET_ID));
        assertEquals(1, IntegrationDb.count("technicalrequests", "is_deleted = 1"));
    }

    // ------------------------------------------------------------------
    // Lịch sử đổi trạng thái phiếu hỗ trợ
    // ------------------------------------------------------------------

    /**
     * Chỉ CSDL thật mới trả lời được: câu INSERT có đúng tên cột không, khoá
     * ngoại changed_by có thoả không, changed_at có được tự điền không. Mock
     * trả "thành công" cho mọi executeUpdate nên mù với cả ba.
     */
    @Test
    public void ticketStatusChange_writesHistoryRowReadableBack() throws Exception {
        IntegrationDb.assumeAvailable();
        Fixtures.seedContract();
        Fixtures.seedTicket(); // đang ở trạng thái "Đang xử lý"

        TechnicalRequest t = ticketDAO.findById(Fixtures.TICKET_ID);
        t.setStatus(TechnicalSupportTicketDAO.STATUS_CLOSED);
        t.setResolutionSummary("Đã thay bộ nguồn");
        assertTrue(ticketDAO.update(t, Fixtures.USER_ID, "Khách xác nhận hoạt động lại"));

        List<TechnicalRequestHistory> history = ticketDAO.findHistoryByTicketId(Fixtures.TICKET_ID);
        assertEquals(1, history.size());
        TechnicalRequestHistory h = history.get(0);
        assertEquals("Đang xử lý", h.getFromStatus());
        assertEquals(TechnicalSupportTicketDAO.STATUS_CLOSED, h.getToStatus());
        assertEquals(Fixtures.USER_ID, h.getChangedBy());
        assertEquals("Khách xác nhận hoạt động lại", h.getInternalNote());
        assertNotNull("changed_at phải do CSDL tự điền", h.getChangedAt());
        assertNotNull("Phải join được tên người đổi", h.getChangedByUser().getFullName());
    }

    @Test
    public void ticketUpdateWithoutStatusChange_writesNoHistoryRow() throws Exception {
        IntegrationDb.assumeAvailable();
        Fixtures.seedContract();
        Fixtures.seedTicket();

        TechnicalRequest t = ticketDAO.findById(Fixtures.TICKET_ID);
        t.setDescription("Mô tả đã sửa lại, trạng thái giữ nguyên");
        assertTrue(ticketDAO.update(t, Fixtures.USER_ID, "ghi chú này không đi kèm bước chuyển nào"));

        assertEquals(0, IntegrationDb.count("technicalrequesthistory", null));
    }

    /**
     * Xoá phiếu phải kéo theo lịch sử của nó (ON DELETE CASCADE trong schema).
     * Ở đây xoá CỨNG bằng SQL, không phải softDelete, để kiểm đúng ràng buộc đó.
     */
    @Test
    public void ticketHardDelete_cascadesToHistory() throws Exception {
        IntegrationDb.assumeAvailable();
        Fixtures.seedContract();
        Fixtures.seedTicket();

        TechnicalRequest t = ticketDAO.findById(Fixtures.TICKET_ID);
        t.setStatus(TechnicalSupportTicketDAO.STATUS_CLOSED);
        assertTrue(ticketDAO.update(t, Fixtures.USER_ID, null));
        assertEquals(1, IntegrationDb.count("technicalrequesthistory", null));

        IntegrationDb.exec("DELETE FROM technicalrequests WHERE ticket_id = " + Fixtures.TICKET_ID);

        assertEquals(0, IntegrationDb.count("technicalrequesthistory", null));
    }

    private Enterprise newEnterprise(String code, String taxCode, String email, String phone) {
        Enterprise e = new Enterprise();
        e.setEnterpriseCode(code);
        e.setEnterpriseName("Công ty Thử Nghiệm " + code);
        e.setCustomerType("Đại lý phân phối");
        e.setCustomerGroup("Thường");
        e.setTaxCode(taxCode);
        e.setEmail(email);
        e.setPhone(phone);
        e.setAccountOwnerId(Fixtures.USER_ID);
        e.setStatus("Active");
        return e;
    }
}

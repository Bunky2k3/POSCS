package poscs.integration;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.NotificationDAO;
import poscs.dao.ProductDAO;
import poscs.dao.TechnicalSupportTicketDAO;

import static org.junit.Assert.*;

/**
 * Chạy MỌI truy vấn đọc của mọi DAO trên MySQL thật, chỉ để chắc chắn câu SQL
 * thực thi được.
 *
 * Đây là lưới an toàn rẻ nhất và bắt được nhiều lỗi nhất: test mock không hề
 * gửi câu SQL nào tới CSDL, nên sai tên cột, sai cú pháp, hay lệch schema đều
 * lọt qua toàn bộ 340 test hiện có rồi mới nổ lúc chạy thật. Ở đây chỉ cần gọi
 * hàm mà không ném exception là đủ -- nội dung trả về do các lớp IT khác kiểm.
 *
 * Lưu ý: DAO bắt SQLException rồi trả list rỗng / null thay vì ném ra, nên
 * test này KHÔNG thể chỉ dựa vào "không ném exception". Phải kiểm chứng bằng
 * dữ liệu: dựng sẵn 1 bản ghi rồi khẳng định đọc lại thấy nó. Câu SQL hỏng sẽ
 * trả rỗng và test đỏ.
 *
 * JUnit 4 -- xem CustomerControllerTest. Tên PHẢI kết thúc bằng "Test":
 * build-impl.xml (do NetBeans sinh) hardcode testincludes="**{@literal /}*Test.java",
 * nên lớp đặt tên kiểu "...IT" sẽ biên dịch được nhưng KHÔNG bao giờ chạy.
 * Sửa build-impl.xml thì lần sau mở IDE nó sinh đè lại, nên đặt tên theo mẫu
 * là cách duy nhất bền.
 */
public class DaoSchemaIntegrationTest {

    private static final CustomerDAO customerDAO = new CustomerDAO();
    private static final ContractDAO contractDAO = new ContractDAO();
    private static final ProductDAO productDAO = new ProductDAO();
    private static final TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private static final EmployeeDAO employeeDAO = new EmployeeDAO();
    private static final NotificationDAO notificationDAO = new NotificationDAO();
    private static final AddressDAO addressDAO = new AddressDAO();

    @BeforeClass
    public static void prepare() throws Exception {
        IntegrationDb.assumeAvailable();
        IntegrationDb.resetSchema();
        IntegrationDb.clearBusinessData();
        Fixtures.seedMinimal();
    }

    // ------------------------------------------------------------------
    // Khách hàng
    // ------------------------------------------------------------------

    @Test
    public void customerDao_readQueriesRunAndReturnSeededRow() {
        IntegrationDb.assumeAvailable();

        assertEquals(1, customerDAO.findAll(1, 50, null, null, null).size());
        assertEquals(1, customerDAO.countAll(null, null, null));
        assertNotNull("findById phải đọc được khách hàng vừa seed",
                customerDAO.findById(Fixtures.ENTERPRISE_ID));
        // Lọc theo từ khoá đi qua nhánh appendFilters khác hẳn nhánh không lọc.
        assertEquals(1, customerDAO.findAll(1, 50, "Sông Hồng", null, null).size());
        assertEquals(0, customerDAO.findAll(1, 50, "không-có-thật", null, null).size());
        assertNotNull(customerDAO.generateNextEnterpriseCode());
    }

    // ------------------------------------------------------------------
    // Hợp đồng
    // ------------------------------------------------------------------

    @Test
    public void contractDao_readQueriesRunAndReturnSeededRow() {
        IntegrationDb.assumeAvailable();

        assertEquals(1, contractDAO.findAll(1, 50, null, null, null).size());
        assertEquals(1, contractDAO.countAll(null, null, null));
        assertNotNull(contractDAO.findById(Fixtures.CONTRACT_ID));
        assertEquals(1, contractDAO.findByEnterpriseId(Fixtures.ENTERPRISE_ID).size());
        assertNotNull(contractDAO.findProductsByContractId(Fixtures.CONTRACT_ID));
        assertNotNull(contractDAO.generateNextContractCode());
        assertNotNull(contractDAO.countStatusSummary());
        assertNotNull(contractDAO.findExpiringSoon(10));
    }

    // ------------------------------------------------------------------
    // Sản phẩm
    // ------------------------------------------------------------------

    @Test
    public void productDao_readQueriesRunAndReturnSeededRow() {
        IntegrationDb.assumeAvailable();

        assertEquals(1, productDAO.findAll(1, 50, null, null).size());
        assertEquals(1, productDAO.countAll(null, null));
        assertNotNull(productDAO.findById(Fixtures.PRODUCT_ID));
        assertNotNull(productDAO.findImagesByProductId(Fixtures.PRODUCT_ID));
        assertNotNull(productDAO.findCataloguesByProductId(Fixtures.PRODUCT_ID));
        assertNotNull(productDAO.generateNextProductCode());
        assertFalse("Danh mục sản phẩm là dữ liệu tra cứu, phải có sẵn từ schema",
                productDAO.findAllCategories().isEmpty());
    }

    // ------------------------------------------------------------------
    // Phiếu hỗ trợ
    // ------------------------------------------------------------------

    @Test
    public void ticketDao_readQueriesRunAndReturnSeededRow() {
        IntegrationDb.assumeAvailable();

        assertEquals(1, ticketDAO.findAll(1, 50, null, null, null).size());
        assertEquals(1, ticketDAO.countAll(null, null, null));
        assertNotNull(ticketDAO.findById(Fixtures.TICKET_ID));
        assertNotNull(ticketDAO.generateNextTicketCode());
        assertNotNull(ticketDAO.countStatusSummary());
        assertNotNull(ticketDAO.findOverdueOrDueSoon());
        ticketDAO.countOverdueOrDueSoon();
        ticketDAO.canDelete(Fixtures.TICKET_ID);
    }

    // ------------------------------------------------------------------
    // Nhân viên / thông báo / địa giới
    // ------------------------------------------------------------------

    @Test
    public void employeeDao_readQueriesRunAndReturnSeededRow() {
        IntegrationDb.assumeAvailable();

        assertNotNull("Truy vấn đăng nhập phải đọc được user vừa seed",
                employeeDAO.findByUsernameOrEmail(Fixtures.USERNAME));
        assertNotNull(employeeDAO.findById(Fixtures.USER_ID));
        assertNotNull(employeeDAO.findProfileById(Fixtures.USER_ID));
        assertEquals(1, employeeDAO.findAll(1, 50, null, null, null).size());
        assertEquals(1, employeeDAO.countAll(null, null, null));
        assertFalse(employeeDAO.findAllActive().isEmpty());
        assertFalse(employeeDAO.findAllRoles().isEmpty());
        assertTrue(employeeDAO.existsByPhone(Fixtures.PHONE, null));
        assertFalse("Loại trừ chính mình thì không được coi là trùng",
                employeeDAO.existsByPhone(Fixtures.PHONE, Fixtures.USER_ID));
        assertTrue(employeeDAO.existsByCitizenId(Fixtures.CITIZEN_ID, null));
    }

    @Test
    public void notificationDao_readQueriesRun() {
        IntegrationDb.assumeAvailable();

        assertNotNull(notificationDAO.findRecentByUser(Fixtures.USER_ID, 5));
        assertNotNull(notificationDAO.findAllByUser(Fixtures.USER_ID));
        notificationDAO.countUnread(Fixtures.USER_ID);
        assertFalse(notificationDAO.existsForUserAndRef(Fixtures.USER_ID, "contract_expiring", 999));
    }

    @Test
    public void addressDao_lookupTablesAreSeededBySchema() {
        IntegrationDb.assumeAvailable();

        List<?> provinces = addressDAO.findAllProvinces();
        assertFalse("Tỉnh/thành là dữ liệu tra cứu, phải có sẵn từ schema", provinces.isEmpty());
        assertFalse("Xã/phường của tỉnh đầu tiên phải đọc được",
                addressDAO.findWardsByProvinceId(Fixtures.PROVINCE_ID).isEmpty());
    }
}

package poscs.integration;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.model.Contract;

import static org.junit.Assert.*;

/**
 * Trạng thái hợp đồng được tính ở HAI NƠI bằng hai ngôn ngữ khác nhau:
 *
 * <ul>
 *   <li>{@code ContractDAO.computeStatus()} -- Java, dùng khi map từng dòng
 *       đọc lên để hiển thị;</li>
 *   <li>{@code ContractDAO.STATUS_CASE_SQL} -- biểu thức CASE trong SQL, dùng
 *       khi LỌC theo trạng thái và khi đếm tổng quan.</li>
 * </ul>
 *
 * Hai bản sao của cùng một quy tắc là chỗ dễ lệch nhau nhất trong cả dự án, và
 * test mock KHÔNG THỂ phát hiện: nó không chạy SQL nên nhánh CASE không bao giờ
 * được thực thi. Hậu quả của một lần lệch rất khó truy: lọc "Sắp hết hạn" trả
 * về một hợp đồng, nhưng chính dòng đó lại hiển thị nhãn "Đang hiệu lực".
 *
 * Lớp này dựng hợp đồng ở đúng bốn trạng thái rồi đối chiếu hai đường tính.
 *
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class ContractStatusIntegrationTest {

    private static final ContractDAO contractDAO = new ContractDAO();

    private static final String DRAFT = "Chưa hiệu lực";
    private static final String ACTIVE = "Đang hiệu lực";
    private static final String SOON = "Sắp hết hạn";
    private static final String EXPIRED = "Đã hết hạn";

    @BeforeClass
    public static void prepareSchema() throws Exception {
        IntegrationDb.assumeAvailable();
        IntegrationDb.resetSchema();
    }

    @Before
    public void seedFourContracts() throws Exception {
        IntegrationDb.assumeAvailable();
        IntegrationDb.clearBusinessData();
        Fixtures.seedAddressAndUser();
        Fixtures.seedEnterprise();

        // Mốc ngày chọn quanh ngưỡng SOON_THRESHOLD_DAYS = 30 để chạm đúng
        // từng nhánh, kể cả nhánh biên.
        insertContract(1, "HD-0001", 10, 40);    // hiệu lực sau 10 ngày -> Chưa hiệu lực
        insertContract(2, "HD-0002", -30, 180);  // đang chạy, còn 180 ngày -> Đang hiệu lực
        insertContract(3, "HD-0003", -30, 5);    // còn 5 ngày -> Sắp hết hạn
        insertContract(4, "HD-0004", -90, -1);   // kết thúc hôm qua -> Đã hết hạn
    }

    /**
     * @param effectiveOffsetDays số ngày so với hôm nay của ngày hiệu lực (âm = quá khứ)
     * @param endOffsetDays       số ngày so với hôm nay của ngày kết thúc
     */
    private void insertContract(int id, String code, int effectiveOffsetDays, int endOffsetDays)
            throws Exception {
        IntegrationDb.exec(
            "INSERT INTO contracts (contract_id, contract_code, title, contract_type, signing_date, "
            + "effective_date, end_date, enterprise_id, owner_id) VALUES ("
            + id + ", '" + code + "', N'Hợp đồng " + code + "', N'Bán hàng', "
            + "DATE_ADD(CURDATE(), INTERVAL " + (effectiveOffsetDays - 5) + " DAY), "
            + "DATE_ADD(CURDATE(), INTERVAL " + effectiveOffsetDays + " DAY), "
            + "DATE_ADD(CURDATE(), INTERVAL " + endOffsetDays + " DAY), "
            + Fixtures.ENTERPRISE_ID + ", " + Fixtures.USER_ID + ")");
    }

    private String statusOf(String code) {
        for (Contract c : contractDAO.findAll(1, 50, null, null, null)) {
            if (code.equals(c.getContractCode())) {
                return c.getStatus();
            }
        }
        return null;
    }

    @Test
    public void javaComputedStatus_matchesTheFourExpectedBuckets() {
        IntegrationDb.assumeAvailable();

        assertEquals(DRAFT, statusOf("HD-0001"));
        assertEquals(ACTIVE, statusOf("HD-0002"));
        assertEquals(SOON, statusOf("HD-0003"));
        assertEquals(EXPIRED, statusOf("HD-0004"));
    }

    @Test
    public void sqlFilter_agreesWithJavaComputedStatusForEveryBucket() {
        IntegrationDb.assumeAvailable();

        // Đây là phép đối chiếu quan trọng nhất của lớp này: lọc bằng SQL phải
        // trả về đúng những hợp đồng mà Java gán cùng nhãn đó.
        for (String status : new String[]{DRAFT, ACTIVE, SOON, EXPIRED}) {
            List<Contract> filtered = contractDAO.findAll(1, 50, null, status, null);
            assertEquals("Lọc SQL theo '" + status + "' phải trả đúng 1 hợp đồng",
                    1, filtered.size());
            assertEquals("Hợp đồng lọt qua bộ lọc SQL '" + status + "' nhưng Java lại gán nhãn khác"
                    + " -- computeStatus() và STATUS_CASE_SQL đã lệch nhau",
                    status, filtered.get(0).getStatus());
        }
    }

    @Test
    public void countAll_matchesFindAllForEveryStatusFilter() {
        IntegrationDb.assumeAvailable();

        // countAll dùng cho phân trang. Lệch với findAll là người dùng thấy
        // "4 hợp đồng" nhưng bảng chỉ có 3 dòng, hoặc có nút sang trang 2 trống.
        for (String status : new String[]{null, DRAFT, ACTIVE, SOON, EXPIRED}) {
            assertEquals("countAll và findAll phải khớp với bộ lọc " + status,
                    contractDAO.findAll(1, 50, null, status, null).size(),
                    contractDAO.countAll(null, status, null));
        }
    }

    @Test
    public void statusSummary_countsEachBucketExactlyOnce() {
        IntegrationDb.assumeAvailable();

        Map<String, Integer> summary = contractDAO.countStatusSummary();
        assertEquals(Integer.valueOf(1), summary.get(ACTIVE));
        assertEquals(Integer.valueOf(1), summary.get(SOON));
        assertEquals(Integer.valueOf(1), summary.get(EXPIRED));
    }

    @Test
    public void findExpiringSoon_returnsOnlyTheContractInTheSoonBucket() {
        IntegrationDb.assumeAvailable();

        List<Contract> expiring = contractDAO.findExpiringSoon(10);
        assertEquals(1, expiring.size());
        assertEquals("HD-0003", expiring.get(0).getContractCode());
    }

    @Test
    public void canDelete_onlyAllowsContractsThatHaveNotTakenEffect() {
        IntegrationDb.assumeAvailable();

        // BR-46. Quy tắc này đọc trực tiếp effective_date/end_date bằng SQL,
        // nên chỉ chạy thật mới kiểm được.
        assertTrue("Hợp đồng chưa hiệu lực thì được xoá", contractDAO.canDelete(1));
        assertFalse("Hợp đồng đang hiệu lực thì không", contractDAO.canDelete(2));
        assertFalse("Hợp đồng sắp hết hạn thì không", contractDAO.canDelete(3));
        assertFalse("Hợp đồng đã hết hạn thì không", contractDAO.canDelete(4));
    }

    @Test
    public void softDelete_removesRowFromEveryQueryButKeepsItInTheTable() throws Exception {
        IntegrationDb.assumeAvailable();

        assertTrue(contractDAO.softDelete(1));

        assertEquals("Xoá mềm phải biến mất khỏi danh sách",
                3, contractDAO.findAll(1, 50, null, null, null).size());
        assertEquals("...và khỏi bộ đếm phân trang", 3, contractDAO.countAll(null, null, null));
        assertNull("...và khỏi findById", contractDAO.findById(1));
        assertEquals("...nhưng dòng vẫn còn trong bảng, chỉ đánh dấu is_deleted",
                1, IntegrationDb.count("contracts", "contract_id = 1 AND is_deleted = 1"));
    }
}

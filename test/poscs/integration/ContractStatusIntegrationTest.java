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
            // progress_status ghi thẳng 'Đã ký': bốn hợp đồng này đều có ngày ký,
            // chúng đại diện cho dữ liệu đã tồn tại trước V24. Để rơi về mặc
            // định 'Nháp' thì trục tiến độ mâu thuẫn với chính signing_date.
            "INSERT INTO contracts (contract_id, contract_code, title, contract_type, progress_status, signing_date, "
            + "effective_date, end_date, enterprise_id, owner_id) VALUES ("
            + id + ", '" + code + "', N'Hợp đồng " + code + "', N'Bán hàng', N'Đã ký', "
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
    public void voidRecord_removesRowFromEveryQueryButKeepsItInTheTable() throws Exception {
        IntegrationDb.assumeAvailable();

        assertTrue(contractDAO.voidRecord(1, Fixtures.USER_ID, "Nhập nhầm khi thử nghiệm"));

        assertEquals("Bản ghi bị huỷ phải biến mất khỏi danh sách",
                3, contractDAO.findAll(1, 50, null, null, null).size());
        assertEquals("...và khỏi bộ đếm phân trang", 3, contractDAO.countAll(null, null, null));
        assertNull("...và khỏi findById", contractDAO.findById(1));
        assertEquals("...nhưng dòng vẫn còn trong bảng, chỉ đánh dấu is_deleted",
                1, IntegrationDb.count("contracts", "contract_id = 1 AND is_deleted = 1"));
    }

    /**
     * Không còn điều kiện xoá theo trạng thái lịch (BR-46 cũ): hợp đồng nào
     * cũng đã ký, nên huỷ được hay không là câu hỏi về QUYỀN, chặn ở
     * ContractController chứ không ở DAO. Thứ DAO vẫn phải chặn là huỷ không lý
     * do, và huỷ lại một bản ghi đã huỷ.
     */
    @Test
    public void voidRecord_refusesBlankReasonAndDoubleVoid() throws Exception {
        IntegrationDb.assumeAvailable();

        assertFalse("Huỷ không lý do thì không được ghi gì",
                contractDAO.voidRecord(2, Fixtures.USER_ID, "  "));
        assertEquals(0, IntegrationDb.count("contracts", "contract_id = 2 AND is_deleted = 1"));

        assertTrue(contractDAO.voidRecord(2, Fixtures.USER_ID, "Nhập trùng"));
        assertFalse("Huỷ lần hai không được sinh thêm dòng nhật ký",
                contractDAO.voidRecord(2, Fixtures.USER_ID, "Huỷ lại lần nữa"));
        assertEquals(1, IntegrationDb.count("contract_history", "contract_id = 2"));
    }

    /**
     * Nhật ký phải sống lâu hơn thứ nó nói về: contract_history cố ý KHÔNG có
     * ON DELETE CASCADE, khác technicalrequesthistory. Kiểm ở đây vì ràng buộc
     * khoá ngoại chỉ tồn tại trong CSDL thật -- test mock không chạy SQL nên
     * không bao giờ thấy.
     */
    @Test
    public void voidRecord_keepsTheHistoryRowAfterTheContractIsGone() throws Exception {
        IntegrationDb.assumeAvailable();

        assertTrue(contractDAO.voidRecord(3, Fixtures.USER_ID, "Khách huỷ đơn"));

        assertEquals("Nhật ký vẫn tra được kể cả khi hợp đồng đã khuất khỏi mọi truy vấn",
                1, contractDAO.findHistoryByContractId(3).size());
        assertEquals("Lý do người dùng nhập phải nằm ở cột note, không lẫn vào detail",
                1, IntegrationDb.count("contract_history",
                        "contract_id = 3 AND note = 'Khách huỷ đơn'"));
    }

    // ------------------------------------------------------------------
    // Trục tiến độ -- chỉ CSDL thật mới kiểm được, vì luật nằm ở SELECT ...
    // FOR UPDATE bên trong transaction.
    // ------------------------------------------------------------------

    /**
     * Vòng đời đủ một vòng: nháp → ký → thanh lý, rồi đóng băng.
     *
     * <p>Mọi hợp đồng gieo trong lớp này đều đã ở 'Đã ký' (insertContract ghi
     * thẳng vào bảng), nên bước nháp→ký kiểm trên một bản ghi tạo qua DAO.
     */
    @Test
    public void progress_runsThroughTheWholeLifecycleThenFreezes() throws Exception {
        IntegrationDb.assumeAvailable();

        Contract draft = new Contract();
        draft.setContractCode("HD-NHAP");
        draft.setTitle("Hợp đồng đang soạn");
        draft.setContractType("Bán hàng");
        draft.setDirection("Bán");
        draft.setEffectiveDate(java.sql.Date.valueOf(java.time.LocalDate.now()));
        draft.setEndDate(java.sql.Date.valueOf(java.time.LocalDate.now().plusYears(1)));
        draft.setEnterpriseId(Fixtures.ENTERPRISE_ID);
        draft.setOwnerId(Fixtures.USER_ID);
        int id = contractDAO.insert(draft, Fixtures.USER_ID);
        assertTrue(id > 0);

        // Tạo KHÔNG còn đồng nghĩa với ký -- đó là cách duy nhất diễn đạt được
        // luật KH "nhân viên không tự ký hợp đồng được".
        assertEquals("Hợp đồng mới phải là bản nháp",
                ContractDAO.PROGRESS_DRAFT, contractDAO.findById(id).getProgressStatus());
        assertNull("Nháp thì chưa có ngày ký", contractDAO.findById(id).getSigningDate());

        assertFalse("Nháp không nhảy thẳng sang thanh lý được",
                contractDAO.changeProgressStatus(id, ContractDAO.PROGRESS_LIQUIDATED, Fixtures.USER_ID, "bỏ qua bước ký"));

        assertTrue(contractDAO.changeProgressStatus(id, ContractDAO.PROGRESS_SIGNED, Fixtures.USER_ID, null));
        Contract signed = contractDAO.findById(id);
        assertEquals(ContractDAO.PROGRESS_SIGNED, signed.getProgressStatus());
        assertNotNull("Bấm Ký phải đóng dấu ngày ký", signed.getSigningDate());

        assertTrue(contractDAO.changeProgressStatus(id, ContractDAO.PROGRESS_LIQUIDATED,
                Fixtures.USER_ID, "Biên bản thanh lý số 07"));

        // Luật KH: sau thanh lý không được thay đổi, KỂ CẢ CẤP CAO.
        assertFalse("Đã thanh lý thì không đi đâu nữa",
                contractDAO.changeProgressStatus(id, ContractDAO.PROGRESS_TERMINATED, Fixtures.USER_ID, "đổi ý"));
        Contract frozen = contractDAO.findById(id);
        frozen.setTitle("Sửa lén sau thanh lý");
        assertFalse("Đã thanh lý thì sửa nội dung cũng phải bị chặn",
                contractDAO.update(frozen, Fixtures.USER_ID));
        assertEquals("Hợp đồng đang soạn", contractDAO.findById(id).getTitle());

        // Ba mốc vòng đời + dòng Khởi tạo lúc tạo nháp = 4 dòng nhật ký, và hai
        // mốc thành công phải mang cả from lẫn to.
        List<poscs.model.ContractHistory> history = contractDAO.findHistoryByContractId(id);
        assertEquals(3, history.size());
        assertTrue("Mốc vòng đời phải có from/to, khác dòng sửa đổi",
                history.get(0).isStatusChange());
        assertEquals(ContractDAO.PROGRESS_LIQUIDATED, history.get(0).getToStatus());
        assertEquals(ContractDAO.PROGRESS_SIGNED, history.get(0).getFromStatus());
    }

    /**
     * Hai trục lệch nhau: hợp đồng hết hạn theo LỊCH mà theo TIẾN ĐỘ vẫn "Đã
     * ký" nghĩa là hết hạn nhưng chưa thanh lý. Đó là hàng đợi việc còn tồn, và
     * là lý do hai trục không được gộp làm một.
     */
    @Test
    public void progress_andCalendarAxesDisagreeOnPurpose() {
        IntegrationDb.assumeAvailable();

        Contract expired = contractDAO.findById(4); // -90 → -1 ngày, đã hết hạn
        assertEquals(EXPIRED, expired.getStatus());
        assertEquals(ContractDAO.PROGRESS_SIGNED, expired.getProgressStatus());

        assertEquals("Lọc hai trục cùng lúc phải ra đúng hợp đồng hết hạn mà chưa thanh lý",
                1, contractDAO.countAll(null, EXPIRED, null, null, null, null,
                        ContractDAO.PROGRESS_SIGNED));
    }

    // ------------------------------------------------------------------
    // Kỳ thanh toán -- bảng contract_payments cuối cùng cũng có đường GHI
    // ------------------------------------------------------------------

    private poscs.model.ContractPayment payment(String amount, java.time.LocalDate due) {
        poscs.model.ContractPayment p = new poscs.model.ContractPayment();
        p.setInvoiceAmount(new java.math.BigDecimal(amount));
        p.setDueDate(java.sql.Date.valueOf(due));
        return p;
    }

    @Test
    public void payments_areWrittenReadBackAndLogged() throws Exception {
        IntegrationDb.assumeAvailable();

        assertTrue(contractDAO.insertPayment(2, payment("450000000", java.time.LocalDate.now()), Fixtures.USER_ID));
        assertTrue(contractDAO.insertPayment(2, payment("550000000",
                java.time.LocalDate.now().plusMonths(3)), Fixtures.USER_ID));

        List<poscs.model.ContractPayment> rows = contractDAO.findPaymentsByContractId(2);
        assertEquals(2, rows.size());
        assertEquals("Kỳ đến hạn sớm nhất phải đứng trước",
                new java.math.BigDecimal("450000000.00"), rows.get(0).getInvoiceAmount());
        assertNull("Mới lập thì chưa thu", rows.get(0).getPaidDate());

        // Mỗi thao tác đều để lại dấu vết, cùng dòng thời gian với mọi thay
        // đổi khác của hợp đồng.
        assertEquals(2, IntegrationDb.count("contract_history",
                "contract_id = 2 AND event_type = N'Thêm kỳ thanh toán'"));
    }

    @Test
    public void payments_rejectNonPositiveAmountAndMissingDueDate() {
        IntegrationDb.assumeAvailable();

        assertFalse(contractDAO.insertPayment(2, payment("0", java.time.LocalDate.now()), Fixtures.USER_ID));
        assertFalse(contractDAO.insertPayment(2, payment("-1000", java.time.LocalDate.now()), Fixtures.USER_ID));

        poscs.model.ContractPayment noDue = new poscs.model.ContractPayment();
        noDue.setInvoiceAmount(new java.math.BigDecimal("1000"));
        assertFalse(contractDAO.insertPayment(2, noDue, Fixtures.USER_ID));

        assertEquals(0, contractDAO.findPaymentsByContractId(2).size());
    }

    @Test
    public void markPaid_isIdempotentAndFeedsTheRevenueSum() throws Exception {
        IntegrationDb.assumeAvailable();

        assertTrue(contractDAO.insertPayment(2, payment("450000000", java.time.LocalDate.now()), Fixtures.USER_ID));
        int paymentId = contractDAO.findPaymentsByContractId(2).get(0).getPaymentId();

        assertTrue(contractDAO.markPaymentPaid(paymentId, 2,
                java.sql.Date.valueOf(java.time.LocalDate.now()), Fixtures.USER_ID));
        // Ghi nhận lần hai không được sinh thêm dòng nhật ký nói về việc chẳng
        // thay đổi gì.
        assertFalse(contractDAO.markPaymentPaid(paymentId, 2,
                java.sql.Date.valueOf(java.time.LocalDate.now()), Fixtures.USER_ID));
        assertEquals(1, IntegrationDb.count("contract_history",
                "contract_id = 2 AND event_type = N'Ghi nhận đã thu'"));

        // Đây là điều khiến cả phần này đáng làm: trước đó sumInvoiceAmount*
        // đọc một bảng không màn hình nào ghi vào được.
        assertEquals(new java.math.BigDecimal("450000000.00"),
                contractDAO.sumInvoiceAmountByContractId(2));
    }

    @Test
    public void payments_scheduleIsLockedAfterLiquidationButMoneyCanStillArrive() {
        IntegrationDb.assumeAvailable();

        assertTrue(contractDAO.insertPayment(2, payment("450000000",
                java.time.LocalDate.now().plusYears(1)), Fixtures.USER_ID));
        int paymentId = contractDAO.findPaymentsByContractId(2).get(0).getPaymentId();

        assertTrue(contractDAO.changeProgressStatus(2, ContractDAO.PROGRESS_LIQUIDATED,
                Fixtures.USER_ID, "Biên bản thanh lý số 09"));

        assertFalse("Đóng băng thì không lập thêm kỳ",
                contractDAO.insertPayment(2, payment("1000000", java.time.LocalDate.now()), Fixtures.USER_ID));
        assertFalse("...cũng không xoá kỳ đã lập",
                contractDAO.deletePayment(paymentId, 2, Fixtures.USER_ID));

        // Nhưng tiền bảo hành giữ lại thường chỉ về sau thanh lý cả năm --
        // đóng băng nói về NỘI DUNG hợp đồng, không nói về việc tiền đã vào
        // tài khoản hay chưa.
        assertTrue("Ghi nhận tiền về vẫn phải được sau thanh lý",
                contractDAO.markPaymentPaid(paymentId, 2,
                        java.sql.Date.valueOf(java.time.LocalDate.now()), Fixtures.USER_ID));
    }

    /**
     * Đường ghi đầy đủ của một lần sửa: giá trị cũ đọc trong transaction, câu
     * mô tả dựng sẵn, và bấm Lưu mà không đổi gì thì KHÔNG sinh dòng nào.
     */
    @Test
    public void update_writesOneHistoryRowDescribingWhatActuallyChanged() throws Exception {
        IntegrationDb.assumeAvailable();

        Contract c = contractDAO.findById(2);
        c.setTitle("Tiêu đề đã sửa");
        assertTrue(contractDAO.update(c, Fixtures.USER_ID));

        List<poscs.model.ContractHistory> history = contractDAO.findHistoryByContractId(2);
        assertEquals(1, history.size());
        assertTrue("Nhật ký phải nói rõ đổi gì thành gì, không chỉ 'đã sửa'",
                history.get(0).getDetail().contains("Tiêu đề đã sửa"));

        // Lưu lại y nguyên: không có gì đổi thì dòng thời gian không được dài
        // thêm, nếu không những lần sửa thật sẽ bị chôn giữa các dòng rỗng.
        assertTrue(contractDAO.update(contractDAO.findById(2), Fixtures.USER_ID));
        assertEquals(1, contractDAO.findHistoryByContractId(2).size());
    }
}

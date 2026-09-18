package poscs.integration;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.model.Contract;
import poscs.model.ContractHandover;
import poscs.model.ContractLink;

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
                contractDAO.sumScheduledPaymentsForCluster(2));
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
     * Đường ghi đầy đủ của một lần sửa BẢN NHÁP: giá trị cũ đọc trong
     * transaction, câu mô tả dựng sẵn, và bấm Lưu mà không đổi gì thì KHÔNG
     * sinh dòng nào.
     *
     * <p>Phải chạy trên bản nháp: bốn hợp đồng gieo trong lớp này đều ở 'Đã
     * ký', mà từ V29 hợp đồng đã ký không nhận sửa tiêu đề nữa.
     */
    @Test
    public void update_onDraft_writesOneHistoryRowDescribingWhatActuallyChanged() throws Exception {
        IntegrationDb.assumeAvailable();

        int id = insertDraftThroughDao("HD-NHAP-SUA");
        int rowsAfterCreate = contractDAO.findHistoryByContractId(id).size();

        Contract c = contractDAO.findById(id);
        c.setTitle("Tiêu đề đã sửa");
        assertTrue(contractDAO.update(c, Fixtures.USER_ID));

        List<poscs.model.ContractHistory> history = contractDAO.findHistoryByContractId(id);
        assertEquals(rowsAfterCreate + 1, history.size());
        assertTrue("Nhật ký phải nói rõ đổi gì thành gì, không chỉ 'đã sửa'",
                history.get(0).getDetail().contains("Tiêu đề đã sửa"));

        // Lưu lại y nguyên: không có gì đổi thì dòng thời gian không được dài
        // thêm, nếu không những lần sửa thật sẽ bị chôn giữa các dòng rỗng.
        assertTrue(contractDAO.update(contractDAO.findById(id), Fixtures.USER_ID));
        assertEquals(rowsAfterCreate + 1, contractDAO.findHistoryByContractId(id).size());
    }

    /**
     * Hợp đồng ĐÃ KÝ: điều khoản khoá, chỉ người phụ trách và link bản PDF đi
     * xuống CSDL.
     *
     * <p>Kiểm trên CSDL thật chứ không chỉ trên mock, vì thứ đang kiểm là
     * "câu UPDATE nào thật sự chạy" -- mock chỉ chứng minh được DAO chọn đúng
     * chuỗi SQL, không chứng minh được chuỗi đó làm gì với dữ liệu.
     */
    @Test
    public void update_onSignedContract_ignoresTermsButTakesTheAdminFields() throws Exception {
        IntegrationDb.assumeAvailable();

        Contract before = contractDAO.findById(2);
        assertEquals(ContractDAO.PROGRESS_SIGNED, before.getProgressStatus());

        Contract edit = contractDAO.findById(2);
        edit.setTitle("Tiêu đề bị đổi lén");
        edit.setContractCode("HD-DOI-LEN");
        edit.setAttachmentUrl("https://drive.google.com/file/d/abc/view");
        assertTrue(contractDAO.update(edit, Fixtures.USER_ID));

        Contract after = contractDAO.findById(2);
        assertEquals("Tiêu đề của hợp đồng đã ký không được đổi",
                before.getTitle(), after.getTitle());
        assertEquals("Mã của hợp đồng đã ký không được đổi",
                before.getContractCode(), after.getContractCode());
        assertEquals("Link bản PDF thì vẫn phải dán được -- nó thường chỉ có SAU khi ký",
                "https://drive.google.com/file/d/abc/view", after.getAttachmentUrl());

        assertEquals("Nhật ký chỉ được kể về thứ thật sự đã ghi", 1,
                IntegrationDb.count("contract_history",
                        "contract_id = 2 AND event_type = N'Sửa thông tin' AND detail LIKE '%Link đính kèm%'"));
        assertEquals(0, IntegrationDb.count("contract_history",
                "contract_id = 2 AND detail LIKE '%Tiêu đề%'"));
    }

    /**
     * Chữa sai sót nhập liệu: đường duy nhất chạm được vào điều khoản của hợp
     * đồng đã ký, và nó bắt buộc kèm lý do.
     */
    @Test
    public void correct_rewritesTermsAndRecordsTheReason() throws Exception {
        IntegrationDb.assumeAvailable();

        Contract fix = contractDAO.findById(2);
        java.sql.Date signedOn = fix.getSigningDate();
        fix.setContractCode("HD-0002-DUNG");
        fix.setSigningDate(java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(3)));

        assertFalse("Thiếu lý do thì không ghi gì",
                contractDAO.correct(contractDAO.findById(2), Fixtures.USER_ID, "  "));
        assertTrue(contractDAO.correct(fix, Fixtures.USER_ID, "Gõ nhầm mã, đối chiếu bản giấy"));

        Contract after = contractDAO.findById(2);
        assertEquals("HD-0002-DUNG", after.getContractCode());
        assertEquals("Ngày ký là dấu của hành động đã xảy ra, không sửa được kể cả ở đây",
                signedOn.toString(), after.getSigningDate().toString());
        assertEquals(1, IntegrationDb.count("contract_history",
                "contract_id = 2 AND event_type = N'Sửa sai sót' "
                + "AND note = N'Gõ nhầm mã, đối chiếu bản giấy'"));
    }

    // ------------------------------------------------------------------
    // Phụ lục (V29)
    // ------------------------------------------------------------------

    /**
     * Vòng đầy đủ của một phụ lục trên CSDL thật: lập, ghi vết lên cả hai hợp
     * đồng, khoá hợp đồng cha khỏi việc huỷ bản ghi, và chặn phụ lục tầng hai.
     */
    @Test
    public void amendment_fullRoundTripAgainstRealSchema() throws Exception {
        IntegrationDb.assumeAvailable();

        Contract child = new Contract();
        child.setParentContractId(2);
        child.setContractCode("HD-0002/PL01");
        child.setTitle("Phụ lục 01 — gia hạn");
        child.setContractType("Bán hàng");
        // Cố ý sai: phải bị ghi đè bằng giá trị của hợp đồng cha.
        child.setDirection("Mua");
        child.setEnterpriseId(Fixtures.ENTERPRISE_ID);
        child.setOwnerId(Fixtures.USER_ID);
        child.setEffectiveDate(java.sql.Date.valueOf(java.time.LocalDate.now()));
        child.setEndDate(java.sql.Date.valueOf(java.time.LocalDate.now().plusYears(1)));

        int childId = contractDAO.insert(child, Fixtures.USER_ID);
        assertTrue("Lập phụ lục cho hợp đồng đã ký phải được", childId > 0);

        Contract saved = contractDAO.findById(childId);
        assertEquals(Integer.valueOf(2), saved.getParentContractId());
        assertEquals("Mã hợp đồng cha phải join ra được", "HD-0002", saved.getParentContractCode());
        assertEquals("Chiều lấy từ CHA, không từ thứ bên gọi truyền vào", "Bán", saved.getDirection());
        assertEquals("Phụ lục cũng ra đời ở trạng thái nháp",
                ContractDAO.PROGRESS_DRAFT, saved.getProgressStatus());

        assertEquals("Hợp đồng cha phải có dòng 'Lập phụ lục'", 1,
                IntegrationDb.count("contract_history",
                        "contract_id = 2 AND event_type = N'Lập phụ lục'"));

        assertEquals(1, contractDAO.findAmendmentsByParentId(2).size());
        assertEquals("Hợp đồng cha đếm được phụ lục của nó",
                1, contractDAO.findById(2).getAmendmentCount());

        // Còn phụ lục thì không huỷ được bản ghi cha -- khoá ngoại không bắt
        // được vì hợp đồng xoá MỀM.
        assertFalse(contractDAO.voidRecord(2, Fixtures.USER_ID, "thử huỷ"));
        assertEquals(0, IntegrationDb.count("contracts", "contract_id = 2 AND is_deleted = 1"));

        // MỘT TẦNG: ký phụ lục rồi thử treo phụ lục lên chính nó.
        assertTrue(contractDAO.changeProgressStatus(childId, ContractDAO.PROGRESS_SIGNED,
                Fixtures.USER_ID, null));
        Contract grandchild = new Contract();
        grandchild.setParentContractId(childId);
        grandchild.setContractCode("HD-0002/PL01/PL01");
        grandchild.setTitle("Phụ lục của phụ lục");
        grandchild.setContractType("Bán hàng");
        grandchild.setEnterpriseId(Fixtures.ENTERPRISE_ID);
        grandchild.setOwnerId(Fixtures.USER_ID);
        assertEquals(ContractDAO.INVALID_PARENT, contractDAO.insert(grandchild, Fixtures.USER_ID));
    }

    /** Hợp đồng chưa ký và hợp đồng đã đóng băng đều không nhận phụ lục. */
    @Test
    public void amendment_refusedOnDraftAndOnFrozenParent() throws Exception {
        IntegrationDb.assumeAvailable();

        int draftId = insertDraftThroughDao("HD-NHAP-PL");
        assertEquals("Bản nháp sửa thẳng được, phụ lục ở đó chỉ là đường vòng",
                ContractDAO.INVALID_PARENT,
                contractDAO.insert(amendmentOf(draftId, "HD-NHAP-PL/PL01"), Fixtures.USER_ID));

        assertTrue(contractDAO.changeProgressStatus(3, ContractDAO.PROGRESS_LIQUIDATED,
                Fixtures.USER_ID, "Biên bản thanh lý số 07"));
        assertEquals("Sau thanh lý thì phát sinh là hợp đồng MỚI, không phải phụ lục",
                ContractDAO.INVALID_PARENT,
                contractDAO.insert(amendmentOf(3, "HD-0003/PL01"), Fixtures.USER_ID));
    }

    /**
     * Phụ lục gia hạn KHÔNG kéo theo thời hạn của hợp đồng gốc (chốt
     * 2026-09-17): bản ghi cha là thứ ghi trên tờ giấy đã ký.
     */
    @Test
    public void amendment_doesNotExtendTheParentCalendarStatus() throws Exception {
        IntegrationDb.assumeAvailable();

        // HD-0004 kết thúc hôm qua -> Đã hết hạn theo lịch.
        assertEquals(EXPIRED, statusOf("HD-0004"));

        Contract extension = amendmentOf(4, "HD-0004/PL01");
        extension.setEffectiveDate(java.sql.Date.valueOf(java.time.LocalDate.now()));
        extension.setEndDate(java.sql.Date.valueOf(java.time.LocalDate.now().plusYears(1)));
        int id = contractDAO.insert(extension, Fixtures.USER_ID);
        assertTrue(id > 0);
        assertTrue(contractDAO.changeProgressStatus(id, ContractDAO.PROGRESS_SIGNED, Fixtures.USER_ID, null));

        assertEquals("Hợp đồng gốc giữ nguyên trạng thái theo lịch của chính nó",
                EXPIRED, statusOf("HD-0004"));
        assertEquals("Còn phụ lục thì đang hiệu lực", ACTIVE, statusOf("HD-0004/PL01"));
    }

    /**
     * Giá trị hợp đồng CÓ cộng dồn theo phụ lục -- ngược với trục lịch ở test
     * ngay trên. Phép cộng nằm trong một câu SQL con của SELECT_BASE, nên test
     * mock không chạm tới được: ở đó không có SQL nào được thực thi.
     *
     * <p>Ba điều kiện của phép cộng đều được đo ở đây, vì mỗi cái hỏng một kiểu
     * riêng: phụ lục NHÁP chưa được tính (chưa ai ký mà tiền đã đổi), phụ lục
     * ĐÃ KÝ được tính, và phụ lục GIẢM TRỪ phải trừ đi chứ không cộng trị tuyệt
     * đối.
     */
    @Test
    public void amendmentValues_addUpOnTheParentOnlyAfterSigning() throws Exception {
        IntegrationDb.assumeAvailable();

        IntegrationDb.exec("UPDATE contracts SET contract_value = 1000000000 WHERE contract_id = 2");
        assertEquals("Chưa có phụ lục thì giá trị hiện hành là chính nó",
                0, new java.math.BigDecimal("1000000000.00")
                        .compareTo(contractDAO.findById(2).getCurrentValue()));

        // Có thời hạn thì mới ký được -- amendmentOf() để trống hai mốc đó.
        Contract addition = withTerm(amendmentOf(2, "HD-0002/PL01"));
        addition.setContractValue(new java.math.BigDecimal("250000000"));
        int additionId = contractDAO.insert(addition, Fixtures.USER_ID);
        assertTrue(additionId > 0);

        assertEquals("Phụ lục còn NHÁP thì chưa đổi được giá trị hợp đồng",
                0, new java.math.BigDecimal("1000000000.00")
                        .compareTo(contractDAO.findById(2).getCurrentValue()));
        assertEquals("...nhưng phải thấy được là có cái đang treo",
                0, new java.math.BigDecimal("250000000.00")
                        .compareTo(contractDAO.findById(2).getAmendmentValuePending()));

        assertTrue(contractDAO.changeProgressStatus(additionId, ContractDAO.PROGRESS_SIGNED,
                Fixtures.USER_ID, null));
        assertEquals("Ký xong thì cộng vào", 0, new java.math.BigDecimal("1250000000.00")
                .compareTo(contractDAO.findById(2).getCurrentValue()));
        assertEquals("Bản ghi cha giữ nguyên con số đã ký", 0,
                new java.math.BigDecimal("1000000000.00")
                        .compareTo(contractDAO.findById(2).getContractValue()));
        assertEquals("Ký phụ lục có giá trị để lại vết trên hợp đồng CHA", 1,
                IntegrationDb.count("contract_history",
                        "contract_id = 2 AND event_type = N'Điều chỉnh giá trị'"));

        // Giảm trừ: phải TRỪ đi, không phải cộng trị tuyệt đối.
        Contract reduction = withTerm(amendmentOf(2, "HD-0002/PL02"));
        reduction.setContractValue(new java.math.BigDecimal("-50000000"));
        int reductionId = contractDAO.insert(reduction, Fixtures.USER_ID);
        assertTrue(reductionId > 0);
        assertTrue(contractDAO.changeProgressStatus(reductionId, ContractDAO.PROGRESS_SIGNED,
                Fixtures.USER_ID, null));
        assertEquals("1 tỷ + 250tr - 50tr", 0, new java.math.BigDecimal("1200000000.00")
                .compareTo(contractDAO.findById(2).getCurrentValue()));

        // Huỷ bản ghi một phụ lục đã ký rút luôn phần tiền của nó ra.
        assertTrue(contractDAO.voidRecord(reductionId, Fixtures.USER_ID, "lập nhầm"));
        assertEquals("Huỷ phụ lục giảm trừ thì giá trị quay lại", 0,
                new java.math.BigDecimal("1250000000.00")
                        .compareTo(contractDAO.findById(2).getCurrentValue()));
        // Ba dòng: ký PL01, ký PL02, rồi huỷ PL02. Mỗi lần giá trị hợp đồng đổi
        // là một dòng -- kể cả lần đổi ngược trở lại.
        assertEquals("Và việc đó cũng phải có vết trên hợp đồng cha", 3,
                IntegrationDb.count("contract_history",
                        "contract_id = 2 AND event_type = N'Điều chỉnh giá trị'"));
    }

    /**
     * Phụ lục giảm trừ quá số tiền còn lại bị từ chối ngay trong transaction.
     * Chặn ở DAO chứ không chỉ ở form: giá trị hợp đồng âm không có nghĩa, và
     * lọt vào thì nó âm thầm trừ đi trong mọi phép cộng về sau.
     */
    @Test
    public void amendmentValue_reducingBelowZero_isRefusedByTheDao() throws Exception {
        IntegrationDb.assumeAvailable();

        IntegrationDb.exec("UPDATE contracts SET contract_value = 100000000 WHERE contract_id = 2");

        Contract tooMuch = amendmentOf(2, "HD-0002/PL01");
        tooMuch.setContractValue(new java.math.BigDecimal("-150000000"));
        assertEquals(ContractDAO.INVALID_VALUE, contractDAO.insert(tooMuch, Fixtures.USER_ID));
        assertEquals("Bị từ chối thì không để lại bản ghi nào",
                0, IntegrationDb.count("contracts", "contract_code = 'HD-0002/PL01'"));
    }

    /**
     * Tổng các kỳ thanh toán đối chiếu theo CẢ CỤM: kỳ lập trên phụ lục cũng
     * phải vào tổng, nếu không thì cảnh báo "lệch giá trị hợp đồng" nổ ở mọi
     * hợp đồng có phụ lục.
     */
    @Test
    public void scheduledPayments_areSummedAcrossTheWholeCluster() throws Exception {
        IntegrationDb.assumeAvailable();

        Contract addition = amendmentOf(2, "HD-0002/PL01");
        addition.setContractValue(new java.math.BigDecimal("250000000"));
        int additionId = contractDAO.insert(addition, Fixtures.USER_ID);
        assertTrue(additionId > 0);

        assertTrue(contractDAO.insertPayment(2, payment("1000000000", java.time.LocalDate.now()),
                Fixtures.USER_ID));
        assertTrue(contractDAO.insertPayment(additionId, payment("250000000", java.time.LocalDate.now()),
                Fixtures.USER_ID));

        assertEquals("Kỳ của phụ lục cũng là tiền của hợp đồng này",
                new java.math.BigDecimal("1250000000.00"),
                contractDAO.sumScheduledPaymentsForCluster(2));
    }

    /**
     * Dải KPI phải đếm đúng tập mà danh sách đang liệt kê: cùng chiều, cùng
     * phạm vi phụ lục. Bốn con số cộng lại phải bằng tổng ở thanh phân trang --
     * đó là phép kiểm mà người dùng làm bằng mắt mỗi lần mở trang.
     */
    @Test
    public void statusSummary_demCungPhamViVoiDanhSach() throws Exception {
        IntegrationDb.assumeAvailable();

        assertTrue(contractDAO.insert(amendmentOf(2, "HD-0002/PL01"), Fixtures.USER_ID) > 0);

        Map<String, Integer> all = contractDAO.countStatusSummary(null, null, N_BAN, false);
        int allRows = contractDAO.findAll(1, 50, null, null, null, null, false, null, N_BAN, null, false).size();
        assertEquals("KPI phải cộng lại bằng tổng danh sách", allRows, tong(all));

        Map<String, Integer> roots = contractDAO.countStatusSummary(null, null, N_BAN, true);
        int rootRows = contractDAO.findAll(1, 50, null, null, null, null, false, null, N_BAN, null, true).size();
        assertEquals("Chọn 'chỉ hợp đồng gốc' thì KPI cũng phải bỏ phụ lục ra", rootRows, tong(roots));
        assertEquals("...và chênh nhau đúng một phụ lục", tong(all) - 1, tong(roots));

        // Chiều MUA: bộ dữ liệu gieo ở đây toàn hợp đồng bán, nên KPI phải ra 0.
        assertEquals("KPI của chiều không có hợp đồng nào phải là 0, không phải tổng của chiều kia",
                0, tong(contractDAO.countStatusSummary(null, null, "Mua", false)));
    }

    private static final String N_BAN = "Bán";

    private static int tong(Map<String, Integer> summary) {
        return summary.values().stream().mapToInt(Integer::intValue).sum();
    }

    // ------------------------------------------------------------------
    // Liên kết bán <-> mua (V31)
    // ------------------------------------------------------------------

    /**
     * Vòng đầy đủ của "đầu ra kéo theo đầu vào" trên CSDL thật: nối, đọc được
     * từ CẢ HAI phía, cộng đúng giá trị đầu vào, và gỡ ra thì sạch.
     *
     * <p>Phép cộng và câu đọc hai chiều nằm trong SQL (CASE trong mệnh đề JOIN,
     * subquery cộng phụ lục), nên test mock không chạm tới được.
     */
    @Test
    public void contractLinks_noiDocVaGoTrenCsdlThat() throws Exception {
        IntegrationDb.assumeAvailable();

        IntegrationDb.exec("UPDATE contracts SET contract_value = 1000000000 WHERE contract_id = 2");
        int buyId = insertBuyContract("HD-MUA-0001", "600000000");

        assertEquals(ContractDAO.LINK_OK, contractDAO.linkContracts(2, buyId, "mua cáp đợt 1", Fixtures.USER_ID));

        // Đọc từ phía BÁN ra đơn mua, từ phía MUA ra hợp đồng bán -- cùng một
        // câu truy vấn, đó là điểm của nó.
        List<ContractLink> fromSell = contractDAO.findLinksOf(2);
        assertEquals(1, fromSell.size());
        assertEquals("HD-MUA-0001", fromSell.get(0).getOther().getContractCode());
        List<ContractLink> fromBuy = contractDAO.findLinksOf(buyId);
        assertEquals(1, fromBuy.size());
        assertEquals("HD-0002", fromBuy.get(0).getOther().getContractCode());

        assertEquals("Giá trị đầu vào cộng đúng", 0,
                new java.math.BigDecimal("600000000.00").compareTo(contractDAO.sumLinkedBuyValue(2)));

        assertEquals("Nối để lại vết ở CẢ HAI hợp đồng", 1,
                IntegrationDb.count("contract_history",
                        "contract_id = 2 AND event_type = N'Nối hợp đồng'"));
        assertEquals(1, IntegrationDb.count("contract_history",
                "contract_id = " + buyId + " AND event_type = N'Nối hợp đồng'"));

        // Nối lại đúng cặp đó -> chặn ở ràng buộc UNIQUE của CSDL.
        assertEquals(ContractDAO.LINK_DUPLICATE,
                contractDAO.linkContracts(2, buyId, null, Fixtures.USER_ID));

        int linkId = fromSell.get(0).getLinkId();
        assertTrue(contractDAO.unlinkContracts(linkId, Fixtures.USER_ID));
        assertTrue(contractDAO.findLinksOf(2).isEmpty());
        assertEquals("Gỡ xong thì đầu vào về 0", 0,
                java.math.BigDecimal.ZERO.compareTo(contractDAO.sumLinkedBuyValue(2)));
    }

    /**
     * Một đơn mua gom phục vụ NHIỀU hợp đồng bán -- quan hệ nhiều-nhiều, đúng
     * hình dạng khách hàng mô tả. Giá trị đầu vào của mỗi hợp đồng bán tính
     * riêng, không chia đôi: hệ thống không biết chia thế nào, và đoán hộ thì
     * ra một con số không ai đối chiếu được với chứng từ.
     */
    @Test
    public void contractLinks_motDonMuaPhucVuNhieuHopDongBan() throws Exception {
        IntegrationDb.assumeAvailable();

        int buyId = insertBuyContract("HD-MUA-0002", "500000000");
        assertEquals(ContractDAO.LINK_OK, contractDAO.linkContracts(2, buyId, null, Fixtures.USER_ID));
        assertEquals(ContractDAO.LINK_OK, contractDAO.linkContracts(3, buyId, null, Fixtures.USER_ID));

        assertEquals(2, contractDAO.findLinksOf(buyId).size());
        assertEquals(0, new java.math.BigDecimal("500000000.00").compareTo(contractDAO.sumLinkedBuyValue(2)));
        assertEquals(0, new java.math.BigDecimal("500000000.00").compareTo(contractDAO.sumLinkedBuyValue(3)));

        // Nhìn từ phía BÁN, đơn mua này phải tự khai là đơn dùng chung: giá trị
        // của nó tính trọn vẹn vào cả hai hợp đồng, nên không nói ra thì
        // "chênh lệch thô" âm ở đó bị đọc thành lỗ.
        assertTrue("đơn mua gom phải được đánh dấu", contractDAO.findLinksOf(2).get(0).isShared());
        assertEquals(1, contractDAO.findLinksOf(2).get(0).getSharedCount());
        // Nhìn từ phía MUA thì mỗi hợp đồng bán chỉ của riêng nó -- không có
        // chuyện "hợp đồng bán dùng chung".
        assertFalse(contractDAO.findLinksOf(buyId).get(0).isShared());
    }

    /** Giá trị đầu vào cộng cả PHỤ LỤC đã ký của đơn mua, như mọi chỗ khác. */
    @Test
    public void contractLinks_giaTriDauVaoGomCaPhuLucCuaDonMua() throws Exception {
        IntegrationDb.assumeAvailable();

        int buyId = insertBuyContract("HD-MUA-0003", "400000000");
        assertEquals(ContractDAO.LINK_OK, contractDAO.linkContracts(2, buyId, null, Fixtures.USER_ID));

        // Ký đơn mua trước: phụ lục chỉ treo được vào hợp đồng ĐÃ KÝ.
        assertTrue(contractDAO.changeProgressStatus(buyId, ContractDAO.PROGRESS_SIGNED,
                Fixtures.USER_ID, null));

        Contract addition = withTerm(amendmentOf(buyId, "HD-MUA-0003/PL01"));
        addition.setContractValue(new java.math.BigDecimal("150000000"));
        int additionId = contractDAO.insert(addition, Fixtures.USER_ID);
        assertTrue(additionId > 0);
        assertEquals("Phụ lục chưa ký thì chưa tính", 0,
                new java.math.BigDecimal("400000000.00").compareTo(contractDAO.sumLinkedBuyValue(2)));

        assertTrue(contractDAO.changeProgressStatus(additionId, ContractDAO.PROGRESS_SIGNED,
                Fixtures.USER_ID, null));
        assertEquals("Ký xong thì cộng vào đầu vào", 0,
                new java.math.BigDecimal("550000000.00").compareTo(contractDAO.sumLinkedBuyValue(2)));
    }

    /** Sai chiều, tự nối chính mình, hay nối vào phụ lục đều bị từ chối. */
    @Test
    public void contractLinks_tuChoiCapKhongHopLe() throws Exception {
        IntegrationDb.assumeAvailable();

        int buyId = insertBuyContract("HD-MUA-0004", "300000000");

        assertEquals("Hai hợp đồng cùng chiều bán",
                ContractDAO.LINK_INVALID, contractDAO.linkContracts(2, 3, null, Fixtures.USER_ID));
        assertEquals("Tự nối chính mình",
                ContractDAO.LINK_INVALID, contractDAO.linkContracts(2, 2, null, Fixtures.USER_ID));
        assertEquals("Đảo ngược vai trò bán/mua",
                ContractDAO.LINK_INVALID, contractDAO.linkContracts(buyId, 2, null, Fixtures.USER_ID));

        Contract amendment = withTerm(amendmentOf(2, "HD-0002/PL-LINK"));
        int amendmentId = contractDAO.insert(amendment, Fixtures.USER_ID);
        assertTrue(amendmentId > 0);
        assertEquals("Phụ lục không nối được: đầu vào phục vụ cả hợp đồng gốc",
                ContractDAO.LINK_INVALID, contractDAO.linkContracts(amendmentId, buyId, null, Fixtures.USER_ID));

        assertEquals("Bị từ chối thì không để lại liên kết nào",
                0, IntegrationDb.count("contract_links", "1 = 1"));
    }

    /** Hợp đồng mua dựng qua DAO -- bốn hợp đồng gieo sẵn đều là chiều bán. */
    private int insertBuyContract(String code, String value) {
        Contract buy = new Contract();
        buy.setContractCode(code);
        buy.setTitle("Đơn mua " + code);
        buy.setContractType("Mua vật tư");
        buy.setDirection("Mua");
        buy.setEffectiveDate(java.sql.Date.valueOf(java.time.LocalDate.now()));
        buy.setEndDate(java.sql.Date.valueOf(java.time.LocalDate.now().plusYears(1)));
        buy.setEnterpriseId(Fixtures.ENTERPRISE_ID);
        buy.setOwnerId(Fixtures.USER_ID);
        buy.setContractValue(new java.math.BigDecimal(value));
        int id = contractDAO.insert(buy, Fixtures.USER_ID);
        assertTrue(id > 0);
        return id;
    }

    // ------------------------------------------------------------------
    // Bàn giao phòng ban (V32)
    // ------------------------------------------------------------------

    /**
     * Vòng đầy đủ của một lượt bàn giao trên CSDL thật: giao cho hai phòng cùng
     * lúc, mỗi phòng tự đóng chặng, và mỗi bước để lại vết trong nhật ký.
     */
    @Test
    public void handover_giaoHaiPhongCungLucVaTungPhongDongChang() throws Exception {
        IntegrationDb.assumeAvailable();

        int ketToan = departmentId("Kế toán");
        int duAn = departmentId("Dự án");

        assertEquals(2, contractDAO.handOverToDepartments(2, List.of(ketToan, duAn),
                "soạn xong, nhờ kiểm", Fixtures.USER_ID));

        List<ContractHandover> handovers = contractDAO.findHandoversOf(2);
        assertEquals(2, handovers.size());
        assertTrue("vừa giao thì cả hai còn đang chờ",
                handovers.stream().allMatch(ContractHandover::isPending));
        assertEquals("Mỗi lượt giao một dòng nhật ký", 2,
                IntegrationDb.count("contract_history",
                        "contract_id = 2 AND event_type = N'Bàn giao phòng ban'"));

        // Đóng chặng của Kế toán; Dự án vẫn còn giữ -- đó là "điểm nghẽn" mà
        // màn hình theo dõi phải chỉ ra được.
        ContractHandover accounting = handovers.stream()
                .filter(h -> h.getDepartmentId() == ketToan).findFirst().orElseThrow();
        assertTrue(contractDAO.completeHandover(accounting.getHandoverId(), Fixtures.USER_ID,
                "đã lập lịch thu hai kỳ"));

        List<ContractHandover> pending = contractDAO.findPendingHandovers(null);
        assertEquals(1, pending.size());
        assertEquals("Dự án", pending.get(0).getDepartmentName());
        assertEquals("Đóng chặng cũng là một dòng nhật ký", 3,
                IntegrationDb.count("contract_history",
                        "contract_id = 2 AND event_type = N'Bàn giao phòng ban'"));
    }

    /** Ghi chú lúc đóng chặng là BẮT BUỘC -- đóng mà không ai nói đã làm gì thì vô nghĩa. */
    @Test
    public void handover_dongChangKhongCoGhiChu_biTuChoi() throws Exception {
        IntegrationDb.assumeAvailable();

        int ketToan = departmentId("Kế toán");
        assertEquals(1, contractDAO.handOverToDepartments(2, List.of(ketToan), null, Fixtures.USER_ID));
        int handoverId = contractDAO.findHandoversOf(2).get(0).getHandoverId();

        assertFalse(contractDAO.completeHandover(handoverId, Fixtures.USER_ID, "   "));
        assertFalse(contractDAO.completeHandover(handoverId, Fixtures.USER_ID, null));
        assertTrue("bị từ chối thì chặng vẫn đang mở",
                contractDAO.findHandoversOf(2).get(0).isPending());
    }

    /**
     * Phòng đang còn giữ thì KHÔNG giao lại lượt mới: hai chặng mở của cùng một
     * phòng thì câu "đang chờ bao lâu" không có câu trả lời. Đóng xong rồi giao
     * lại (bị trả về sửa) thì được, và mỗi lượt là một dòng riêng.
     */
    @Test
    public void handover_giaoLaiKhiPhongConGiu_biTuChoi() throws Exception {
        IntegrationDb.assumeAvailable();

        int ketToan = departmentId("Kế toán");
        assertEquals(1, contractDAO.handOverToDepartments(2, List.of(ketToan), null, Fixtures.USER_ID));
        assertEquals(ContractDAO.HANDOVER_ALREADY_PENDING,
                contractDAO.handOverToDepartments(2, List.of(ketToan), null, Fixtures.USER_ID));

        int handoverId = contractDAO.findHandoversOf(2).get(0).getHandoverId();
        assertTrue(contractDAO.completeHandover(handoverId, Fixtures.USER_ID, "xong đợt một"));
        assertEquals("Đóng xong thì giao lại được", 1,
                contractDAO.handOverToDepartments(2, List.of(ketToan), "sửa xong, gửi lại", Fixtures.USER_ID));
        assertEquals("Mỗi lượt là một dòng riêng để đếm được số lần làm lại",
                2, contractDAO.findHandoversOf(2).size());
    }

    /** Hai phòng của luồng phải tồn tại sau V32 -- không có thì không bàn giao cho ai được. */
    @Test
    public void handover_haiPhongMoiPhaiCoTrongDanhMuc() throws Exception {
        IntegrationDb.assumeAvailable();

        assertTrue(departmentId("Kế toán") > 0);
        assertTrue(departmentId("Dự án") > 0);
    }

    private int departmentId(String name) throws Exception {
        return Integer.parseInt(IntegrationDb.scalar(
                "SELECT department_id FROM departments WHERE department_name = N'" + name + "'"));
    }

    /** Ô lọc "Chỉ hợp đồng gốc": danh sách và bộ đếm phân trang phải đi cặp. */
    @Test
    public void rootsOnlyFilter_keepsListAndCountInStep() throws Exception {
        IntegrationDb.assumeAvailable();

        assertTrue(contractDAO.insert(amendmentOf(2, "HD-0002/PL01"), Fixtures.USER_ID) > 0);

        int allRows = contractDAO.findAll(1, 50, null, null, null, null, false, null, null, null, false).size();
        int rootRows = contractDAO.findAll(1, 50, null, null, null, null, false, null, null, null, true).size();
        assertEquals("Lọc hợp đồng gốc phải bỏ đúng một phụ lục", allRows - 1, rootRows);
        assertEquals(rootRows, contractDAO.countAll(null, null, null, null, null, null, null, true));
        assertEquals(allRows, contractDAO.countAll(null, null, null, null, null, null, null, false));
    }

    /** Bản nháp tạo qua DAO -- bốn hợp đồng gieo sẵn đều đã ở 'Đã ký'. */
    private int insertDraftThroughDao(String code) {
        Contract draft = new Contract();
        draft.setContractCode(code);
        draft.setTitle("Hợp đồng " + code);
        draft.setContractType("Bán hàng");
        draft.setDirection("Bán");
        draft.setEffectiveDate(java.sql.Date.valueOf(java.time.LocalDate.now()));
        draft.setEndDate(java.sql.Date.valueOf(java.time.LocalDate.now().plusYears(1)));
        draft.setEnterpriseId(Fixtures.ENTERPRISE_ID);
        draft.setOwnerId(Fixtures.USER_ID);
        int id = contractDAO.insert(draft, Fixtures.USER_ID);
        assertTrue(id > 0);
        return id;
    }

    /** Gắn thời hạn cho một phụ lục -- thiếu nó thì changeProgressStatus từ chối ký. */
    private static Contract withTerm(Contract c) {
        c.setEffectiveDate(java.sql.Date.valueOf(java.time.LocalDate.now()));
        c.setEndDate(java.sql.Date.valueOf(java.time.LocalDate.now().plusYears(1)));
        return c;
    }

    private static Contract amendmentOf(int parentId, String code) {
        Contract c = new Contract();
        c.setParentContractId(parentId);
        c.setContractCode(code);
        c.setTitle("Phụ lục " + code);
        c.setContractType("Bán hàng");
        c.setEnterpriseId(Fixtures.ENTERPRISE_ID);
        c.setOwnerId(Fixtures.USER_ID);
        return c;
    }
}

package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import poscs.model.Contract;
import poscs.model.ContractProduct;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho ContractDAO. Trọng tâm là hai chỗ có logic thật sự chứ không chỉ
 * đọc-ghi thẳng:
 *
 * <ul>
 *   <li>{@code insertProducts} tự quản transaction (tắt autocommit, commit hoặc
 *       rollback cả batch). Bên gọi -- ContractController.handleImportPdf --
 *       dựa vào giá trị false để báo với người dùng "chưa ghi gì vào CSDL", nên
 *       nếu rollback không chạy thì thông báo đó thành nói dối, hợp đồng còn
 *       lại một nửa số hạng mục.</li>
 *   <li>{@code insert} thử lại khi contract_code bị trùng UNIQUE KEY (hai
 *       request tạo hợp đồng gần đồng thời đọc được cùng một "mã lớn nhất").</li>
 * </ul>
 *
 * Ép lỗi đúng thời điểm (batch hỏng giữa chừng, commit hỏng, setAutoCommit hỏng
 * sau khi đã commit) là thứ gần như không dựng lại được trên CSDL thật -- đây
 * chính là chỗ mock JDBC làm được việc mà integration test không làm nổi.
 * Ngược lại, xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng gì.
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class ContractDAOTest {

    private final ContractDAO dao = new ContractDAO();

    /** user_id giả của người đang thao tác -- mọi đường ghi đều gắn kèm vào nhật ký. */
    private static final int ACTOR = 9;

    private static ContractProduct item(int productId, int quantity) {
        ContractProduct p = new ContractProduct();
        p.setProductId(productId);
        // Có sẵn tên thì DAO dựng được câu nhật ký ngay, không phải quay lại
        // bảng products tra thêm -- giống đường mà ContractController đi khi
        // người dùng bấm "Thêm sản phẩm".
        p.setProductName("Thiết bị " + productId);
        p.setQuantity(quantity);
        p.setUnit("cái");
        p.setNotes(null);
        return p;
    }

    /**
     * PreparedStatement dùng chung cho các test insertProducts: trả sẵn một
     * dòng trạng thái tiến độ cho lần kiểm "hợp đồng đã đóng băng chưa" mà
     * ContractDAO chạy trước khi ghi. Không có nó thì executeQuery() trả null
     * và test chết vì NPE ở chỗ chẳng liên quan gì tới thứ nó đang kiểm.
     */
    private static PreparedStatement statementForActiveContract() throws SQLException {
        ResultSet progress = singleRow(row("progress_status", ContractDAO.PROGRESS_SIGNED));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(progress);
        return ps;
    }

    private static Contract contract(String code) {
        Contract c = new Contract();
        c.setContractCode(code);
        c.setTitle("Hợp đồng thử");
        c.setContractType("Cung cấp thiết bị");
        c.setSigningDate(Date.valueOf(LocalDate.now()));
        c.setEffectiveDate(Date.valueOf(LocalDate.now().minusDays(1)));
        c.setEndDate(Date.valueOf(LocalDate.now().plusYears(1)));
        c.setEnterpriseId(3);
        c.setOwnerId(5);
        return c;
    }

    // ------------------------------------------------------------------
    // insertProducts -- "tất cả hoặc không gì cả"
    // ------------------------------------------------------------------

    @Test
    public void insertProducts_emptyList_returnsTrueWithoutTouchingDatabase() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertTrue(dao.insertProducts(1, new ArrayList<>(), ACTOR));

            // Hợp đồng không có hạng mục nào là hợp lệ -- không được mở kết nối
            // chỉ để chạy một batch rỗng.
            db.verify(DBContext::getConnection, never());
        }
    }

    @Test
    public void insertProducts_allRowsSucceed_commitsAndRestoresAutoCommit() throws Exception {
        PreparedStatement ps = statementForActiveContract();
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.insertProducts(1, Arrays.asList(item(10, 2), item(11, 5)), ACTOR));

            InOrder inOrder = inOrder(conn, ps);
            inOrder.verify(conn).setAutoCommit(false);
            inOrder.verify(ps).executeBatch();
            inOrder.verify(conn).commit();
            inOrder.verify(conn).setAutoCommit(true);
            verify(ps, times(2)).addBatch();
            verify(conn, never()).rollback();
        }
    }

    @Test
    public void insertProducts_batchFails_rollsBackAndReturnsFalse() throws Exception {
        PreparedStatement ps = statementForActiveContract();
        when(ps.executeBatch()).thenThrow(new SQLException("khoá ngoại product_id không tồn tại"));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.insertProducts(1, Arrays.asList(item(10, 2), item(11, 5)), ACTOR));

            // Không rollback thì các dòng trước chỗ hỏng vẫn nằm lại trong CSDL,
            // trong khi hàm báo false -- hợp đồng có một nửa hạng mục.
            verify(conn).rollback();
            verify(conn, never()).commit();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void insertProducts_commitFails_rollsBackAndReturnsFalse() throws Exception {
        PreparedStatement ps = statementForActiveContract();
        Connection conn = connectionReturning(ps);
        doThrow(new SQLException("mất kết nối lúc commit")).when(conn).commit();

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.insertProducts(1, Arrays.asList(item(10, 2)), ACTOR));

            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void insertProducts_autoCommitResetFailsAfterCommit_stillReportsSuccess() throws Exception {
        PreparedStatement ps = statementForActiveContract();
        Connection conn = connectionReturning(ps);
        // commit() đã thành công, chỉ khâu dọn dẹp sau đó hỏng.
        doThrow(new SQLException("kết nối chết khi trả autocommit")).when(conn).setAutoCommit(true);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Dữ liệu đã nằm trong CSDL rồi. Báo false ở đây là nói dối theo
            // hướng nguy hiểm: bên gọi sẽ bảo người dùng "chưa ghi gì" và họ
            // nhập lại, tạo ra hạng mục trùng.
            assertTrue("Lỗi dọn dẹp sau commit không được biến thành thất bại",
                    dao.insertProducts(1, Arrays.asList(item(10, 2)), ACTOR));
            verify(conn, never()).rollback();
        }
    }

    @Test
    public void insertProducts_rollbackAlsoFails_stillReturnsFalseWithoutThrowing() throws Exception {
        PreparedStatement ps = statementForActiveContract();
        when(ps.executeBatch()).thenThrow(new SQLException("batch hỏng"));
        Connection conn = connectionReturning(ps);
        doThrow(new SQLException("rollback cũng hỏng")).when(conn).rollback();

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Lỗi chồng lỗi vẫn phải thoát ra bằng giá trị trả về, không được
            // ném ngoại lệ lên tận servlet thành trang 500.
            assertFalse(dao.insertProducts(1, Arrays.asList(item(10, 2)), ACTOR));
        }
    }

    @Test
    public void insertProducts_bindsEveryItemOntoTheSameStatement() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        // Chỉ câu INSERT hạng mục mới đi vào ps. Hai câu còn lại của cùng lời
        // gọi -- kiểm trạng thái tiến độ và ghi nhật ký -- cũng bind contract_id
        // vào tham số 1, nên dùng chung một mock thì times(2) bên dưới đếm
        // thành 4 và test thất bại vì thứ nó không hề nói tới.
        Connection conn = connectionRoutingOn("contractproducts", ps, statementForActiveContract());

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.insertProducts(77, Arrays.asList(item(10, 2), item(11, 5)), ACTOR);

            verify(ps, times(2)).setInt(1, 77); // contract_id lặp lại cho từng dòng
            verify(ps).setInt(2, 10);
            verify(ps).setInt(3, 2);
            verify(ps).setInt(2, 11);
            verify(ps).setInt(3, 5);
        }
    }

    // ------------------------------------------------------------------
    // Nhật ký -- ghi cùng transaction với thay đổi
    // ------------------------------------------------------------------

    @Test
    public void insertProducts_writesHistoryRowInsideTheSameTransaction() throws Exception {
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement itemPs = statementForActiveContract();
        Connection conn = connectionRoutingOn("contract_history", historyPs, itemPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.insertProducts(7, Arrays.asList(item(10, 2)), ACTOR));

            // Dòng nhật ký phải nằm TRƯỚC commit. Ghi sau commit thì hàng hoá đã
            // vào CSDL trong khi nhật ký còn có thể hỏng -- đúng cái lỗ mà bảng
            // này sinh ra để bịt.
            InOrder inOrder = inOrder(itemPs, historyPs, conn);
            inOrder.verify(itemPs).executeBatch();
            inOrder.verify(historyPs).executeUpdate();
            inOrder.verify(conn).commit();

            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(historyPs).setString(eq(3), detail.capture());
            assertTrue("Nhật ký phải nói rõ đã thêm hàng hoá gì, không chỉ là 'có thay đổi'",
                    detail.getValue().contains("Thiết bị 10"));
            verify(historyPs).setInt(4, ACTOR);
        }
    }

    @Test
    public void insertProducts_historyFails_rollsBackTheProductRowsToo() throws Exception {
        PreparedStatement historyPs = mock(PreparedStatement.class);
        when(historyPs.executeUpdate()).thenThrow(new SQLException("contract_history hỏng"));
        PreparedStatement itemPs = statementForActiveContract();
        Connection conn = connectionRoutingOn("contract_history", historyPs, itemPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Hàng hoá vào được mà nhật ký ghi hụt là trường hợp tệ nhất: thay
            // đổi có thật nhưng không truy được ai làm. Thà không ghi gì cả.
            assertFalse(dao.insertProducts(7, Arrays.asList(item(10, 2)), ACTOR));
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void deleteProductLine_readsTheLineBeforeDeletingIt() throws Exception {
        PreparedStatement historyPs = mock(PreparedStatement.class);
        // singleRow() tự chạy vài lệnh when() bên trong, nên phải lấy ra biến
        // trước; gọi lồng trong when(...) sẽ ném UnfinishedStubbingException.
        //
        // HAI lần executeQuery, theo đúng thứ tự DAO chạy: kiểm hợp đồng đã
        // đóng băng chưa, rồi mới đọc dòng hàng hoá sắp xoá. Trả cùng một
        // ResultSet cho cả hai thì con trỏ của lần đầu đã chạy hết, lần sau đọc
        // ra rỗng.
        ResultSet progress = singleRow(row("progress_status", ContractDAO.PROGRESS_SIGNED));
        ResultSet line = singleRow(row("product_name", "Modem quang GPON", "quantity", 5, "unit", "cái"));
        PreparedStatement linePs = mock(PreparedStatement.class);
        when(linePs.executeQuery()).thenReturn(progress, line);
        when(linePs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, linePs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.deleteProductLine(3, 7, ACTOR));

            // DELETE là xoá cứng: đọc sau lệnh xoá thì không còn gì để đọc, và
            // dòng nhật ký sẽ chỉ nói được "đã gỡ một thứ gì đó".
            InOrder inOrder = inOrder(linePs);
            inOrder.verify(linePs).executeQuery();
            inOrder.verify(linePs).executeUpdate();

            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(historyPs).setString(eq(3), detail.capture());
            assertTrue(detail.getValue().contains("Modem quang GPON"));
            assertTrue(detail.getValue().contains("5"));
        }
    }

    @Test
    public void deleteProductLine_lineNotInThisContract_deletesNothing() throws Exception {
        ResultSet noLine = emptyResultSet();
        PreparedStatement linePs = mock(PreparedStatement.class);
        when(linePs.executeQuery()).thenReturn(noLine);
        Connection conn = connectionReturning(linePs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Không tra ra dòng nào thì phải dừng hẳn, đừng chạy DELETE với cặp
            // id do người gọi đưa và hy vọng mệnh đề WHERE đỡ hộ.
            assertFalse(dao.deleteProductLine(3, 7, ACTOR));
            verify(linePs, never()).executeUpdate();
            verify(conn).rollback();
        }
    }

    // ------------------------------------------------------------------
    // Trục tiến độ
    // ------------------------------------------------------------------

    @Test
    public void changeProgressStatus_unknownTarget_refusesWithoutTouchingDatabase() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertFalse(dao.changeProgressStatus(5, "Đang đàm phán", ACTOR, null));
            db.verify(DBContext::getConnection, never());
        }
    }

    @Test
    public void changeProgressStatus_liquidateWithoutNote_refusesWithoutTouchingDatabase() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            // Thanh lý và chấm dứt sớm đóng băng hợp đồng vĩnh viễn, không có
            // đường quay lại -- không được phép xảy ra mà không ai biết vì sao.
            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_LIQUIDATED, ACTOR, "  "));
            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_TERMINATED, ACTOR, null));
            db.verify(DBContext::getConnection, never());
        }
    }

    @Test
    public void changeProgressStatus_illegalStep_rollsBackAndWritesNothing() throws Exception {
        ResultSet current = singleRow(row("progress_status", ContractDAO.PROGRESS_LIQUIDATED));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(current);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Đã thanh lý thì không đi đâu được nữa -- luật KH: sau thanh lý
            // không được thay đổi, kể cả cấp cao.
            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_TERMINATED, ACTOR, "đổi ý"));
            verify(ps, never()).executeUpdate();
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void changeProgressStatus_signing_stampsSigningDateAndLogsBothEnds() throws Exception {
        ResultSet current = singleRow(row("progress_status", ContractDAO.PROGRESS_DRAFT));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.changeProgressStatus(5, ContractDAO.PROGRESS_SIGNED, ACTOR, null));

            // Ngày ký là ngày hành động xảy ra, do CSDL đóng dấu -- không phải
            // thứ người dùng gõ vào ô rồi sửa lại sau.
            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sql.capture());
            assertTrue("Bước ký phải đóng dấu signing_date",
                    sql.getAllValues().stream().anyMatch(q -> q.contains("signing_date = CURDATE()")));

            // Dòng nhật ký của MỐC vòng đời mang cả hai đầu, khác dòng sửa đổi.
            verify(historyPs).setString(4, ContractDAO.PROGRESS_DRAFT);
            verify(historyPs).setString(5, ContractDAO.PROGRESS_SIGNED);
            verify(conn).commit();
        }
    }

    @Test
    public void changeProgressStatus_liquidating_doesNotTouchSigningDate() throws Exception {
        ResultSet current = singleRow(row("progress_status", ContractDAO.PROGRESS_SIGNED));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.changeProgressStatus(5, ContractDAO.PROGRESS_LIQUIDATED, ACTOR, " Biên bản 12 "));

            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sql.capture());
            assertFalse("Chỉ bước KÝ mới đóng dấu ngày ký",
                    sql.getAllValues().stream().anyMatch(q -> q.contains("signing_date = CURDATE()")));
            verify(historyPs).setString(7, "Biên bản 12");
        }
    }

    /**
     * Contract.isDraft()/isFrozen() so chuỗi bằng hằng riêng của model, vì model
     * không phụ thuộc ngược lên DAO. Hai bên lệch nhau một dấu là nút bấm trên
     * JSP biến mất mà không ai hiểu vì sao -- nên canh ở đây.
     */
    @Test
    public void progressConstants_matchTheOnesOnTheModel() {
        Contract c = new Contract();

        c.setProgressStatus(ContractDAO.PROGRESS_DRAFT);
        assertTrue(c.isDraft());
        assertFalse(c.isFrozen());

        c.setProgressStatus(ContractDAO.PROGRESS_SIGNED);
        assertFalse(c.isDraft());
        assertFalse(c.isFrozen());

        c.setProgressStatus(ContractDAO.PROGRESS_LIQUIDATED);
        assertTrue(c.isFrozen());

        c.setProgressStatus(ContractDAO.PROGRESS_TERMINATED);
        assertTrue(c.isFrozen());
    }

    @Test
    public void voidRecord_blankReason_refusesWithoutTouchingDatabase() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            // Lý do là toàn bộ giá trị của thao tác này: một bản ghi biến khỏi
            // danh sách mà không nói vì sao thì sau không phân biệt được nhập
            // nhầm với xoá để che.
            assertFalse(dao.voidRecord(7, ACTOR, "   "));
            assertFalse(dao.voidRecord(7, ACTOR, null));
            db.verify(DBContext::getConnection, never());
        }
    }

    @Test
    public void voidRecord_storesTheReasonInNoteNotInDetail() throws Exception {
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement updatePs = mock(PreparedStatement.class);
        when(updatePs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, updatePs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.voidRecord(7, ACTOR, "  Nhập trùng với HD-0042  "));

            // detail do hệ thống sinh, note là chữ người dùng gõ. Trộn hai thứ
            // vào một cột là mất khả năng phân biệt máy ghi hay người khai.
            verify(historyPs).setString(5, "Nhập trùng với HD-0042");
            verify(conn).commit();
        }
    }

    // ------------------------------------------------------------------
    // insert -- thử lại khi trùng contract_code
    // ------------------------------------------------------------------

    @Test
    public void insert_returnsGeneratedContractId() throws Exception {
        // singleRow() tự chạy vài lệnh when() bên trong, nên phải lấy ra biến
        // trước; gọi lồng trong when(...) sẽ ném UnfinishedStubbingException.
        ResultSet keys = singleRow(row("id", 123));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(123, dao.insert(contract("HD-0001"), ACTOR));
            verify(conn).prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS));
        }
    }

    @Test
    public void insert_noRowAffected_returnsMinusOne() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insert(contract("HD-0001"), ACTOR));
        }
    }

    @Test
    public void insert_duplicateContractCode_regeneratesCodeAndRetries() throws Exception {
        ResultSet keys = singleRow(row("id", 55));
        PreparedStatement insertPs = mock(PreparedStatement.class);
        when(insertPs.executeUpdate())
                .thenThrow(duplicateKeyError("contract_code")) // lần 1: đụng UNIQUE KEY
                .thenReturn(1);                                // lần 2: mã mới, thành công
        when(insertPs.getGeneratedKeys()).thenReturn(keys);

        // generateNextContractCode() chạy xen giữa 2 lần thử, đọc mã lớn nhất hiện có.
        PreparedStatement codePs = statementReturning(singleRow(row("contract_code", "HD-0007")));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString(), anyInt())).thenReturn(insertPs);
        when(conn.prepareStatement(anyString())).thenReturn(codePs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Contract c = contract("HD-0007");
            assertEquals(55, dao.insert(c, ACTOR));

            // Mã phải được sinh lại chứ không thử lại y nguyên mã cũ -- lặp lại
            // cùng một mã sẽ trùng mãi cho tới khi hết lượt thử.
            assertEquals("HD-0008", c.getContractCode());
            verify(insertPs, times(2)).executeUpdate();
        }
    }

    @Test
    public void insert_nonDuplicateSqlError_failsImmediatelyWithoutRetrying() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenThrow(new SQLException("khoá ngoại enterprise_id sai", "23000", 1452));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insert(contract("HD-0001"), ACTOR));

            // Sinh lại mã không cứu được lỗi này -- thử lại 5 lần chỉ tổ chậm.
            verify(ps, times(1)).executeUpdate();
        }
    }

    @Test
    public void insert_duplicateCodeEveryAttempt_givesUpAfterMaxAttempts() throws Exception {
        PreparedStatement insertPs = mock(PreparedStatement.class);
        when(insertPs.executeUpdate()).thenThrow(duplicateKeyError("contract_code"));
        PreparedStatement codePs = statementReturning(singleRow(row("contract_code", "HD-0007")));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString(), anyInt())).thenReturn(insertPs);
        when(conn.prepareStatement(anyString())).thenReturn(codePs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insert(contract("HD-0007"), ACTOR));

            // Vòng lặp phải dừng, không quay vô hạn khi mã mới vẫn cứ trùng.
            verify(insertPs, times(5)).executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // computeStatus -- private, kiểm gián tiếp qua tham số bind của insert()
    // ------------------------------------------------------------------

    @Test
    public void insert_contractNotYetEffective_storesDraftStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().plusDays(10)),
                Date.valueOf(LocalDate.now().plusYears(1)));

        verify(ps).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_DRAFT);
    }

    @Test
    public void insert_contractPastEndDate_storesExpiredStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().minusYears(2)),
                Date.valueOf(LocalDate.now().minusDays(1)));

        verify(ps).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_EXPIRED);
    }

    @Test
    public void insert_contractEndingWithin30Days_storesExpiringSoonStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().minusMonths(6)),
                Date.valueOf(LocalDate.now().plusDays(10)));

        verify(ps).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_SOON);
    }

    @Test
    public void insert_contractEndingWellBeyond30Days_storesActiveStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().minusMonths(6)),
                Date.valueOf(LocalDate.now().plusDays(31)));

        verify(ps).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_ACTIVE);
    }

    @Test
    public void insert_missingDates_fallsBackToDraftStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(null, null);

        verify(ps).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_DRAFT);
    }

    // ------------------------------------------------------------------
    // Lọc/sắp xếp theo tỉnh (hợp đồng lấy địa bàn từ khách hàng đứng tên)
    // ------------------------------------------------------------------

    private static String capturedSql(Connection conn) throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sql.capture());
        return sql.getValue();
    }

    @Test
    public void findAll_withProvinceFilter_joinsFromEnterpriseToProvince() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, 3, false);

            // contracts không có cột tỉnh: phải đi qua khách hàng -> địa chỉ ->
            // xã/phường mới tới được province_id.
            String sql = capturedSql(conn);
            assertTrue(sql.contains("LEFT JOIN addresses a ON e.address_id = a.address_id"));
            assertTrue(sql.contains("d.province_id = ?"));
            verify(ps).setObject(1, 3);
        }
    }

    /** Xem ghi chú cùng tên ở CustomerDAOTest: lệch join là bộ đếm trả 0 trong im lặng. */
    @Test
    public void countAll_withProvinceFilter_joinsTablesTheFilterNeeds() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", 7)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(7, dao.countAll(null, null, null, 3));

            String sql = capturedSql(conn);
            assertTrue(sql.contains("LEFT JOIN districts d"));
            assertTrue(sql.contains("d.province_id = ?"));
        }
    }

    @Test
    public void findAll_sortByProvince_ordersByProvinceInsteadOfNewestFirst() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, true);

            assertTrue(capturedSql(conn).contains("ORDER BY p.province_name IS NULL"));
        }
    }

    /**
     * Vị trí tham số của cột status trong câu INSERT của {@code insert()}.
     *
     * <p>Bám theo THỨ TỰ CỘT của câu lệnh đó, nên thêm một cột vào giữa là
     * số này phải đổi theo -- đã dịch một lần khi thêm contract_number.
     * Tách ra hằng số để lần sau chỉ sửa một chỗ thay vì năm chỗ.
     */
    private static final int STATUS_PARAM_INDEX = 12;

    /** Chạy insert() với cặp ngày cho trước rồi trả về statement để soi tham số đã bind. */
    private PreparedStatement captureStatusFor(Date effectiveDate, Date endDate) throws Exception {
        ResultSet keys = singleRow(row("id", 1));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Contract c = contract("HD-0001");
            c.setEffectiveDate(effectiveDate);
            c.setEndDate(endDate);
            dao.insert(c, ACTOR);
        }
        return ps;
    }

    // ==================================================================
    // Kỳ thanh toán -- gộp từ ContractPaymentDAOTest
    // ==================================================================
    //
    // Doanh thu tính trên paid_date -- tiền ĐÃ THỰC THU, không phải due_date
    // (tiền đáng lẽ phải thu). Hai cột đó lệch nhau chính là chuyện trả chậm;
    // lấy nhầm due_date là KPI doanh thu tự cộng cả khoản chưa về.
    //
    // Chỗ dễ sai còn lại: trả BigDecimal.ZERO chứ không phải null khi không
    // có dòng nào hoặc khi CSDL lỗi -- dashboard chia cho giá trị tháng trước
    // để tính % tăng trưởng, null vào đó là nổ trang.

    // ------------------------------------------------------------------
    // sumInvoiceAmountByMonth
    // ------------------------------------------------------------------

    @Test
    public void sumInvoiceAmountByMonth_returnsSumAndBindsYearThenMonth() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", new BigDecimal("1500000"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(new BigDecimal("1500000"), dao.sumInvoiceAmountByMonth(2026, 9));
            verify(ps).setInt(1, 2026);
            verify(ps).setInt(2, 9);
        }
    }

    @Test
    public void sumInvoiceAmountByMonth_sqlError_returnsZeroNotNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            // Dashboard chia cho giá trị này khi tính % tăng trưởng -- null sẽ nổ.
            assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(2026, 9));
        }
    }

    // ------------------------------------------------------------------
    // sumInvoiceAmountByContractId
    // ------------------------------------------------------------------

    @Test
    public void sumInvoiceAmountByContractId_returnsSum() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", new BigDecimal("42000"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(new BigDecimal("42000"), dao.sumInvoiceAmountByContractId(11));
            verify(ps).setInt(1, 11);
        }
    }

    @Test
    public void sumInvoiceAmountByContractId_sqlError_returnsZero() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByContractId(11));
        }
    }
}

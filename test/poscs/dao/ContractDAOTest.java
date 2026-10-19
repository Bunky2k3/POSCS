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
import poscs.model.ContractDocument;
import poscs.model.ContractHandover;
import poscs.model.ContractHistory;
import poscs.model.ContractLink;
import poscs.model.ContractPayment;
import poscs.model.ContractProduct;

import static org.junit.Assert.*;
import poscs.common.ListScope;
import poscs.common.Period;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho ContractDAO. Trọng tâm là hai chỗ có logic thật sự chứ không chỉ
 * đọc-ghi thẳng:
 *
 * <ul>
 *   <li>{@code insertProducts} tự quản transaction (tắt autocommit, commit hoặc
 *       rollback cả batch). Bên gọi (ContractController.handleAddProduct)
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
     * PreparedStatement dùng chung cho các test insertProducts: trả sẵn trạng
     * thái tiến độ "Nháp" cho lần kiểm mà ContractDAO chạy trước khi ghi.
     * Phải là Nháp: hàng hoá là nội dung hợp đồng nên ký xong là chốt.
     * Không có dòng này thì executeQuery() trả null và test chết vì NPE ở chỗ
     * chẳng liên quan gì tới thứ nó đang kiểm.
     */
    private static PreparedStatement statementForActiveContract() throws SQLException {
        ResultSet progress = singleRow(row("progress_status", ContractDAO.PROGRESS_DRAFT));
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
        ResultSet progress = singleRow(row("progress_status", ContractDAO.PROGRESS_DRAFT));
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
    public void changeProgressStatus_nullTarget_refusesInsteadOfThrowing() throws Exception {
        // Map.of(...).containsKey(null) ném NPE -- POST thiếu toStatus từng ra HTTP 500.
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertFalse(dao.changeProgressStatus(5, null, ACTOR, null));
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
        ResultSet current = singleRow(contractRow(ContractDAO.PROGRESS_LIQUIDATED));
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
        ResultSet current = singleRow(contractRow(ContractDAO.PROGRESS_DRAFT));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.changeProgressStatus(5, ContractDAO.PROGRESS_SIGNED, ACTOR, null));

            // Ngày ký do CSDL đóng dấu, không phải thứ người dùng gõ vào ô rồi
            // sửa lại sau -- nhưng COALESCE chứ không phải CURDATE() thẳng:
            // hợp đồng nhập từ file PDF đã mang sẵn ngày ký đọc từ bản giấy, và
            // đè lên nó là mất hẳn ngày ký thật. Chỉ cột trống mới lấy hôm nay.
            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sql.capture());
            assertTrue("Bước ký phải đóng dấu signing_date khi cột đang trống",
                    sql.getAllValues().stream()
                            .anyMatch(q -> q.contains("signing_date = COALESCE(signing_date, CURDATE())")));

            // Dòng nhật ký của MỐC vòng đời mang cả hai đầu, khác dòng sửa đổi.
            verify(historyPs).setString(4, ContractDAO.PROGRESS_DRAFT);
            verify(historyPs).setString(5, ContractDAO.PROGRESS_SIGNED);
            verify(conn).commit();
        }
    }

    @Test
    public void changeProgressStatus_liquidating_doesNotTouchSigningDate() throws Exception {
        ResultSet current = singleRow(contractRow(ContractDAO.PROGRESS_SIGNED));
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
    public void changeProgressStatus_signingWithoutATerm_isRefused() throws Exception {
        // Chỉ có trạng thái, hai mốc thời hạn để trống.
        ResultSet current = singleRow(row("progress_status", ContractDAO.PROGRESS_DRAFT));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(current);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Hợp đồng không có thời hạn thì không phải hợp đồng. Đây là thứ
            // giữ cho việc nới effective_date/end_date thành nullable ở V26 an
            // toàn: NULL chỉ tồn tại trong quãng Nháp.
            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_SIGNED, ACTOR, null));
            verify(ps, never()).executeUpdate();
            verify(conn).rollback();
        }
    }

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
    public void products_cannotBeChangedOnceTheContractIsSigned() throws Exception {
        ResultSet signed = singleRow(row("progress_status", ContractDAO.PROGRESS_SIGNED));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(signed);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Hàng hoá là NỘI DUNG hợp đồng, không phải dữ liệu quản trị nội
            // bộ: ký xong là chốt, đổi phải đi qua phụ lục. Chặn ở DAO chứ
            // không chỉ ẩn nút -- nút ẩn thì POST thẳng vào URL vẫn ghi được.
            assertFalse(dao.insertProducts(7, Arrays.asList(item(10, 2)), ACTOR));
            assertFalse(dao.deleteProductLine(3, 7, ACTOR));
            verify(ps, never()).executeBatch();
        }
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
        // voidRecord đếm phụ lục TRƯỚC khi huỷ: hợp đồng còn phụ lục thì không
        // huỷ được, nếu không phụ lục thành văn bản không tra ngược được nó sửa
        // cho cái gì. Ở đây trả 0 -- hợp đồng này chưa có phụ lục nào.
        // Dựng ResultSet TRƯỚC: singleRow() tự tạo một mock, và tạo mock ở giữa
        // một lời when(...) đang dở khiến Mockito báo "unfinished stubbing".
        ResultSet noAmendments = singleRow(row("count", 0));
        when(updatePs.executeQuery()).thenReturn(noAmendments);
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

    /**
     * Trùng mã là LỖI NHẬP LIỆU, không phải chuyện của máy.
     *
     * <p>Trước V28 mã do hệ thống sinh nên insert() tự sinh mã khác rồi thử lại
     * tối đa 5 lần. Giờ mã do người dùng gõ: tự đổi hộ nghĩa là lưu một mã khác
     * thứ họ vừa nhập mà không báo gì. Phải trả về DUPLICATE_CODE để controller
     * nói được "mã này đã có rồi".
     */
    @Test
    public void insert_duplicateContractCode_reportsItInsteadOfInventingANewOne() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenThrow(duplicateKeyError("contract_code"));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Contract c = contract("01/2026/HĐKT-POSTEF");
            assertEquals(ContractDAO.DUPLICATE_CODE, dao.insert(c, ACTOR));

            // Mã giữ NGUYÊN thứ người dùng gõ -- đổi nó đi là thay đổi dữ liệu
            // của họ sau lưng.
            assertEquals("01/2026/HĐKT-POSTEF", c.getContractCode());
            verify(ps, times(1)).executeUpdate();
            verify(conn, never()).commit();
        }
    }

    /** Lỗi trùng mã phải phân biệt được với lỗi chung, vì hai bên cần hai câu trả lời khác nhau. */
    @Test
    public void insert_duplicateCode_isDistinctFromAGenericFailure() throws Exception {
        assertNotEquals(ContractDAO.DUPLICATE_CODE, -1);
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
            assertTrue(sql.contains("d.province_id IN (?)"));
            verify(ps).setObject(1, 3);
        }
    }

    /**
     * Bảng lọc tỉnh cho tích NHIỀU tỉnh, và mỗi lần tích thêm một tỉnh phải là
     * NỚI RA chứ không phải siết lại: một câu IN chứ không phải nhiều điều kiện
     * nối bằng AND. Nối bằng AND thì hai tỉnh cho ra danh sách RỖNG -- không
     * hợp đồng nào nằm ở hai tỉnh cùng lúc -- mà màn hình không báo gì cả.
     */
    @Test
    public void findAll_withSeveralProvinces_buildsOneInClauseAndBindsEachId() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, java.util.List.of(3, 17, 4), false,
                    null, null, null, false, null, ListScope.all());

            String sql = capturedSql(conn);
            assertTrue(sql.contains("d.province_id IN (?,?,?)"));
            assertFalse("phải là MỘT câu IN, không phải nhiều điều kiện nối AND",
                    sql.contains("d.province_id IN (?) AND d.province_id"));
            verify(ps).setObject(1, 3);
            verify(ps).setObject(2, 17);
            verify(ps).setObject(3, 4);
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
            assertTrue(sql.contains("d.province_id IN (?)"));
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
     * Một dòng hợp đồng đủ cột cho {@code changeProgressStatus}, vốn đọc cả
     * bản ghi bằng SELECT ... FOR UPDATE chứ không chỉ đọc trạng thái.
     *
     * <p>Phải có đủ effective_date và end_date: từ V26 hai cột đó nullable, và
     * hợp đồng thiếu thời hạn thì KHÔNG ký được.
     */
    private static java.util.Map<String, Object> contractRow(String progressStatus) {
        return row("progress_status", progressStatus,
                "effective_date", Date.valueOf(LocalDate.now()),
                "end_date", Date.valueOf(LocalDate.now().plusYears(1)));
    }

    /**
     * Vị trí tham số của cột status trong câu INSERT của {@code insert()}.
     *
     * <p>Bám theo THỨ TỰ CỘT của câu lệnh đó, nên thêm một cột vào giữa là
     * số này phải đổi theo -- đã dịch một lần khi thêm contract_number (V25),
     * rồi dịch ngược lại khi gộp nó vào contract_code (V28), rồi dịch lần nữa
     * khi bỏ cột attachment_url (V34).
     * Tách ra hằng số để lần sau chỉ sửa một chỗ thay vì năm chỗ.
     */
    private static final int SIGNING_DATE_PARAM_INDEX = 5;
    private static final int STATUS_PARAM_INDEX = 10;
    private static final int PROGRESS_PARAM_INDEX = STATUS_PARAM_INDEX + 1;

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

    /**
     * Hợp đồng đã HUỶ BẢN GHI không được góp tiền vào doanh thu.
     *
     * <p>voidRecord xoá mềm và cố ý không đụng tới contract_payments (huỷ một
     * bản ghi nhập nhầm không được xoá dấu vết tiền đã ghi nhận), nên hai câu
     * cộng này phải tự loại ra. Thiếu điều kiện thì tiền của một hợp đồng đã
     * biến mất khỏi mọi danh sách vẫn nằm mãi trong KPI, và không màn hình nào
     * chỉ ra được nó đến từ đâu.
     */
    /**
     * Lịch nhắc chỉ nhắc hợp đồng ĐANG chạy.
     *
     * <p>Trục lịch không biết gì về trục tiến độ: chấm dứt sớm không làm
     * end_date lùi lại, nên một hợp đồng đã đóng vẫn nằm trong khoảng hiệu lực
     * và vẫn lọt vào cửa sổ 30 ngày. Thiếu điều kiện này thì người phụ trách
     * nhận "sắp hết hạn" cho hợp đồng đã chấm dứt từ lâu -- đo được trên
     * poscs_db: 09/2026/HĐKT-POSTEF, Chấm dứt sớm, hết hạn 26/05/2027.
     */
    @Test
    public void findExpiringSoon_chiNhacHopDongDangChay() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findExpiringSoon(10);
            verify(conn).prepareStatement(contains("c.progress_status = '" + ContractDAO.PROGRESS_SIGNED + "'"));
        }
    }

    @Test
    public void sumInvoiceAmountByMonth_loaiHopDongDaHuyBanGhi() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", BigDecimal.ZERO)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.sumInvoiceAmountByMonth(2026, 9);
            verify(conn).prepareStatement(contains("c.is_deleted = 0"));
        }
    }

    @Test
    public void sumInvoiceAmountInPeriod_loaiHopDongDaHuyBanGhi() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", BigDecimal.ZERO)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.sumInvoiceAmountInPeriod(Period.parse("2026", "m9"), null);
            verify(conn).prepareStatement(contains("c.is_deleted = 0"));
        }
    }

    // ------------------------------------------------------------------
    // sumScheduledPaymentsForCluster
    // ------------------------------------------------------------------

    /**
     * Cộng kỳ thanh toán của CẢ CỤM: hợp đồng gốc và các phụ lục của nó. Cùng
     * một id đi vào hai tham số -- câu lệnh hỏi "contract_id = ? HOẶC
     * parent_contract_id = ?" -- nên test canh cả hai, thiếu một cái là cụm chỉ
     * còn một nửa mà không ai biết.
     */
    @Test
    public void sumScheduledPaymentsForCluster_returnsSumOfRootAndAmendments() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", new BigDecimal("42000"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(new BigDecimal("42000"), dao.sumScheduledPaymentsForCluster(11));
            verify(ps).setInt(1, 11);
            verify(ps).setInt(2, 11);
        }
    }

    @Test
    public void sumScheduledPaymentsForCluster_sqlError_returnsZero() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            // Màn hình so con số này với giá trị hợp đồng để báo lệch; null sẽ nổ.
            assertEquals(BigDecimal.ZERO, dao.sumScheduledPaymentsForCluster(11));
        }
    }

    // ------------------------------------------------------------------
    // Phụ lục (parent_contract_id)
    // ------------------------------------------------------------------

    /**
     * Hợp đồng cha phải ĐÃ KÝ. Bản nháp thì sửa thẳng được, nên phụ lục ở đó chỉ
     * là đường vòng dựng ra hai bản ghi cho một thứ chưa ai ký.
     */
    @Test
    public void insert_amendmentOfDraftParent_rejected() throws Exception {
        assertEquals(ContractDAO.INVALID_PARENT,
                insertAmendmentAgainstParent(parentRow(ContractDAO.PROGRESS_DRAFT, null)));
    }

    /**
     * Sau thanh lý KHÔNG lập phụ lục: hợp đồng đã chấm dứt thì không còn gì để
     * sửa đổi, phát sinh lúc đó là hợp đồng mới. Luật KH: "sau thanh lý không
     * được thay đổi, kể cả cấp cao".
     */
    @Test
    public void insert_amendmentOfLiquidatedParent_rejected() throws Exception {
        assertEquals(ContractDAO.INVALID_PARENT,
                insertAmendmentAgainstParent(parentRow(ContractDAO.PROGRESS_LIQUIDATED, null)));
    }

    /**
     * MỘT TẦNG. Khoá ngoại tự trỏ cho phép chuỗi dài tuỳ ý, nên luật này chỉ
     * tồn tại ở đây -- bỏ nó đi thì "hợp đồng gốc đã bị sửa những gì" phải đi
     * lần theo một chuỗi không ai biết trước dài bao nhiêu.
     */
    @Test
    public void insert_amendmentOfAnAmendment_rejected() throws Exception {
        assertEquals(ContractDAO.INVALID_PARENT,
                insertAmendmentAgainstParent(parentRow(ContractDAO.PROGRESS_SIGNED, 42)));
    }

    /**
     * Lập phụ lục sinh HAI dòng nhật ký, ở HAI hợp đồng: "Khởi tạo" trên chính
     * phụ lục, và "Lập phụ lục" trên hợp đồng CHA.
     *
     * <p>Dòng trên cha mới là dòng quan trọng: câu hỏi "hợp đồng này về sau có
     * bị sửa gì không" được hỏi khi đang mở hợp đồng gốc, không phải khi đang
     * mở phụ lục.
     */
    @Test
    public void insert_amendment_logsOnBothContracts() throws Exception {
        ResultSet parent = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        ResultSet keys = singleRow(row("GENERATED_KEY", 77));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(parent);
        when(contractPs.executeUpdate()).thenReturn(1);
        when(contractPs.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(77, dao.insert(amendmentOf(3), ACTOR));

            // Dòng của phụ lục treo vào chính nó (77); dòng "Lập phụ lục" treo
            // vào hợp đồng cha (3). Hai id khác nhau -- đó là toàn bộ điểm.
            verify(historyPs).setInt(1, 77);
            verify(historyPs).setInt(1, 3);
            verify(historyPs).setString(2, ContractHistory.EVENT_AMENDMENT_CREATED);
            verify(conn).commit();
        }
    }

    /** Đối tác và chiều của phụ lục lấy từ CHA, không từ thứ bên gọi truyền vào. */
    @Test
    public void insert_amendment_inheritsCounterpartyAndDirectionFromParent() throws Exception {
        ResultSet parent = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        ResultSet keys = singleRow(row("GENERATED_KEY", 77));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(parent);
        when(contractPs.executeUpdate()).thenReturn(1);
        when(contractPs.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        Contract child = amendmentOf(3);
        // Thứ bên gọi truyền vào CỐ Ý sai: đối tác khác, chiều ngược lại.
        child.setEnterpriseId(999);
        child.setDirection("Bán");

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(77, dao.insert(child, ACTOR));

            verify(contractPs).setString(4, "Mua");  // chiều của cha
            verify(contractPs).setInt(8, 12);        // đối tác của cha
        }
    }

    /**
     * Hợp đồng còn phụ lục thì KHÔNG huỷ bản ghi được.
     *
     * <p>Khoá ngoại không thay được phép kiểm này: hợp đồng xoá MỀM, nên CSDL
     * không thấy có gì bị xoá cả, và phụ lục sẽ lặng lẽ trỏ về một hợp đồng đã
     * biến mất khỏi mọi danh sách.
     */
    @Test
    public void voidRecord_withLiveAmendments_refuses() throws Exception {
        ResultSet oneAmendment = singleRow(row("count", 1));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(oneAmendment);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.voidRecord(7, ACTOR, "nhập nhầm"));
            verify(contractPs, never()).executeUpdate();
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    // ------------------------------------------------------------------
    // Siết form sửa sau khi ký
    // ------------------------------------------------------------------

    /**
     * Hợp đồng ĐÃ KÝ: chỉ owner_id đi xuống CSDL.
     *
     * <p>Trước V34 còn attachment_url, mở với lý do "bản PDF đã ký thường chỉ
     * có SAU khi ký". Giấy tờ giờ nằm ở bảng riêng, thêm được ở mọi trạng thái
     * -- nên câu UPDATE này gọn lại đúng một cột.
     *
     * <p>Chặn ở DAO chứ không chỉ ẩn ô: nút ẩn thì POST thẳng vào URL vẫn ghi
     * được, mà đây là ranh giới pháp lý chứ không phải chuyện giao diện.
     */
    @Test
    public void update_signedContract_writesOnlyAdminFields() throws Exception {
        ResultSet current = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        Contract submitted = new Contract();
        submitted.setContractId(5);
        submitted.setOwnerId(88);
        // Người dùng (hoặc một POST nặn tay) cố đổi điều khoản.
        submitted.setTitle("Tiêu đề bị đổi lén");
        submitted.setContractValue(new BigDecimal("1"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.update(submitted, ACTOR));

            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sql.capture());
            String updateSql = sql.getAllValues().stream()
                    .filter(q -> q.startsWith("UPDATE contracts SET"))
                    .findFirst().orElse("");
            assertEquals("UPDATE contracts SET owner_id = ? "
                    + "WHERE contract_id = ? AND is_deleted = 0", updateSql);
        }
    }

    /** Bản NHÁP thì vẫn ghi đủ mọi cột -- siết chỉ bắt đầu từ lúc ký. */
    @Test
    public void update_draftContract_writesEveryField() throws Exception {
        ResultSet current = singleRow(parentRow(ContractDAO.PROGRESS_DRAFT, null));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        Contract submitted = new Contract();
        submitted.setContractId(5);
        submitted.setContractCode("01/2026/HĐKT-POSTEF");
        submitted.setTitle("Tiêu đề mới");
        submitted.setContractType("Cung cấp thiết bị");
        submitted.setEnterpriseId(12);
        submitted.setOwnerId(88);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.update(submitted, ACTOR));
            verify(contractPs).setString(2, "Tiêu đề mới");
        }
    }

    /**
     * Mở form sửa rồi bấm Lưu mà không đổi gì thì KHÔNG được sinh dòng nhật ký
     * -- kể cả khi giá trị hợp đồng đọc từ CSDL có scale 2 (DECIMAL(15,2)) còn
     * giá trị vừa nhập có scale 0.
     *
     * <p>So bằng chuỗi toPlainString thì "1250000000.00" khác "1250000000", nên
     * mỗi lần bấm Lưu lại đẻ ra một dòng "Giá trị hợp đồng: 1250000000.00 →
     * 1250000000" -- một thay đổi chưa hề xảy ra, đúng thứ khối chú thích ở
     * update() nói là không được có.
     */
    @Test
    public void update_sameMoneyWithDifferentScale_writesNoHistoryLine() throws Exception {
        java.util.Map<String, Object> before = parentRow(ContractDAO.PROGRESS_DRAFT, null);
        before.put("contract_value", new BigDecimal("1250000000.00"));
        ResultSet current = singleRow(before);
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Đúng thứ MoneyVnd.parseOrNull trả về khi người dùng không đụng ô tiền.
            assertTrue(dao.update(resubmitted(new BigDecimal("1250000000")), ACTOR));

            verify(historyPs, never()).executeUpdate();
            verify(conn).commit();
        }
    }

    /** Đổi thật thì vẫn ghi, và ghi bằng số có phân nhóm để đọc được. */
    @Test
    public void update_moneyChanged_logsBothSidesGrouped() throws Exception {
        java.util.Map<String, Object> before = parentRow(ContractDAO.PROGRESS_DRAFT, null);
        before.put("contract_value", new BigDecimal("1250000000.00"));
        ResultSet current = singleRow(before);
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.update(resubmitted(new BigDecimal("1500000000")), ACTOR));

            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(historyPs).setString(eq(3), detail.capture());
            assertEquals("Giá trị hợp đồng: 1.250.000.000 đ → 1.500.000.000 đ", detail.getValue());
        }
    }

    /** Đúng các trường của parentRow(), chỉ khác mỗi số tiền -- để nhật ký chỉ nói về tiền. */
    private static Contract resubmitted(BigDecimal value) {
        Contract c = new Contract();
        c.setContractId(3);
        c.setContractCode("01/2026/HĐMB-POSTEF");
        c.setTitle("Hợp đồng gốc");
        c.setContractType("Mua vật tư");
        c.setEnterpriseId(12);
        c.setOwnerId(5);
        c.setContractValue(value);
        return c;
    }

    // ------------------------------------------------------------------
    // correct() -- Admin chữa sai sót nhập liệu
    // ------------------------------------------------------------------

    /** Thiếu lý do thì không mở cả kết nối: thay đổi trên hợp đồng đã ký phải giải thích được. */
    @Test
    public void correct_withoutReason_writesNothing() throws Exception {
        Contract c = new Contract();
        c.setContractId(5);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertFalse(dao.correct(c, ACTOR, "   "));
            db.verify(DBContext::getConnection, never());
        }
    }

    /**
     * Đóng băng rồi thì Admin cũng không sửa được. Luật KH nói rõ "kể cả cấp
     * cao"; nếu Admin là ngoại lệ thì câu đó không còn nghĩa gì.
     */
    @Test
    public void correct_frozenContract_refusesEvenForAdmin() throws Exception {
        ResultSet current = singleRow(parentRow(ContractDAO.PROGRESS_LIQUIDATED, null));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(current);
        Connection conn = connectionReturning(ps);

        Contract c = new Contract();
        c.setContractId(5);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.correct(c, ACTOR, "đối chiếu bản giấy"));
            verify(ps, never()).executeUpdate();
            verify(conn).rollback();
        }
    }

    /** Bản nháp đã sửa thẳng được rồi -- đi đường này chỉ làm loãng loại sự kiện. */
    @Test
    public void correct_draftContract_refuses() throws Exception {
        ResultSet current = singleRow(parentRow(ContractDAO.PROGRESS_DRAFT, null));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(current);
        Connection conn = connectionReturning(ps);

        Contract c = new Contract();
        c.setContractId(5);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.correct(c, ACTOR, "đối chiếu bản giấy"));
            verify(ps, never()).executeUpdate();
        }
    }

    /** Lý do người dùng gõ vào cột note, và dòng mang loại riêng "Sửa sai sót". */
    @Test
    public void correct_storesReasonAndItsOwnEventType() throws Exception {
        ResultSet current = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        Contract c = new Contract();
        c.setContractId(5);
        c.setContractCode("02/2026/HĐKT-POSTEF");
        c.setTitle("Tiêu đề đúng theo bản giấy");
        c.setContractType("Cung cấp thiết bị");
        c.setEnterpriseId(12);
        c.setOwnerId(88);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.correct(c, ACTOR, "  Gõ nhầm mã, đối chiếu bản giấy  "));

            verify(historyPs).setString(2, ContractHistory.EVENT_CORRECTED);
            verify(historyPs).setString(5, "Gõ nhầm mã, đối chiếu bản giấy");
            verify(conn).commit();
        }
    }

    // ------------------------------------------------------------------
    // Helper cho nhóm test phụ lục
    // ------------------------------------------------------------------

    /** Một dòng contracts đủ để lockForUpdate dựng lại được hợp đồng cha. */
    // ------------------------------------------------------------------
    // countStatusSummary -- phạm vi đếm
    // ------------------------------------------------------------------

    /**
     * Chiều phải đi VÀO câu lệnh, không chỉ nằm trong chữ ký.
     *
     * <p>Đây là lỗi đã có thật: tham số {@code direction} được nhận rồi bỏ quên,
     * nên dải KPI ở mục Hợp đồng mua đếm cả hợp đồng bán. Tham số có mà không
     * dùng còn nguy hơn không có -- bên gọi tưởng đã lọc rồi.
     */
    @Test
    public void countStatusSummary_locTheoChieu() throws Exception {
        ResultSet rs = singleRow(row("draft_count", 1, "expired_count", 2, "soon_count", 3, "active_count", 4));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.countStatusSummary(null, null, "Mua", false);

            verify(conn).prepareStatement(sql.capture());
            assertTrue(sql.getValue(), sql.getValue().contains("c.direction = ?"));
            verify(ps).setString(1, "Mua");
        }
    }

    /** rootsOnly: bốn con số phải đếm đúng tập mà bảng bên dưới liệt kê. */
    @Test
    public void countStatusSummary_rootsOnly_boPhuLucRaKhoiPhepDem() throws Exception {
        ResultSet rs = singleRow(row("draft_count", 1, "expired_count", 2, "soon_count", 3, "active_count", 4));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.countStatusSummary(null, null, null, true);

            verify(conn).prepareStatement(sql.capture());
            assertTrue(sql.getValue(), sql.getValue().contains("c.parent_contract_id IS NULL"));
        }
    }

    /** Không lọc gì thì không thêm mệnh đề nào -- tránh câu lệnh tự thu hẹp âm thầm. */
    @Test
    public void countStatusSummary_khongLoc_khongThemMenhDe() throws Exception {
        ResultSet rs = singleRow(row("draft_count", 0, "expired_count", 0, "soon_count", 0, "active_count", 0));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.countStatusSummary(null, null, null, false);

            verify(conn).prepareStatement(sql.capture());
            assertFalse(sql.getValue().contains("c.direction"));
            assertFalse(sql.getValue().contains("parent_contract_id"));
        }
    }

    // ------------------------------------------------------------------
    // Giá trị hợp đồng khi có phụ lục
    // ------------------------------------------------------------------

    /**
     * Phụ lục giảm trừ nhiều hơn số tiền còn lại thì bị TỪ CHỐI: giá trị hợp
     * đồng âm không có nghĩa gì, và gần như luôn là gõ nhầm dấu hoặc gõ TỔNG
     * giá trị mới vào ô chênh lệch.
     *
     * <p>Mã trả về tách riêng khỏi -1: "lưu thất bại" thì người dùng không sửa
     * được gì, còn cái này thì sửa ô tiền là xong.
     */
    @Test
    public void insert_amendmentReducingBelowZero_rejected() throws Exception {
        java.util.Map<String, Object> parent = parentRow(ContractDAO.PROGRESS_SIGNED, null);
        parent.put("contract_value", new BigDecimal("1000000"));
        ResultSet parentRs = singleRow(parent);
        // Đã có một phụ lục giảm trừ 300k được ký trước đó -> còn lại 700k.
        ResultSet signedSoFar = singleRow(row("total", new BigDecimal("-300000")));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(parentRs, signedSoFar);
        Connection conn = connectionReturning(ps);

        Contract child = amendmentOf(3);
        child.setContractValue(new BigDecimal("-800000"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(ContractDAO.INVALID_VALUE, dao.insert(child, ACTOR));
            verify(ps, never()).executeUpdate();
            verify(conn).rollback();
        }
    }

    /** Phụ lục BỔ SUNG thì bao nhiêu cũng hợp lệ -- không có trần nào cả. */
    @Test
    public void insert_amendmentAddingValue_isAccepted() throws Exception {
        ResultSet parentRs = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        ResultSet keys = singleRow(row("GENERATED_KEY", 77));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(parentRs);
        when(contractPs.executeUpdate()).thenReturn(1);
        when(contractPs.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        Contract child = amendmentOf(3);
        // Hợp đồng cha chưa chốt giá (parentRow không có contract_value) mà phụ
        // lục vẫn bổ sung được: phép kiểm chỉ chặn phần ÂM.
        child.setContractValue(new BigDecimal("250000000"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(77, dao.insert(child, ACTOR));
            verify(conn).commit();
        }
    }

    /**
     * KÝ một phụ lục có giá trị sinh dòng nhật ký trên HỢP ĐỒNG CHA.
     *
     * <p>Đây là khoảnh khắc giá trị hợp đồng thật sự đổi -- trước đó phụ lục
     * mới là bản nháp ai cũng sửa được. Không có dòng này thì con số trên màn
     * hình đổi mà không chỗ nào giải thích được vì sao.
     */
    @Test
    public void changeProgressStatus_signingAmendmentWithValue_logsOnParent() throws Exception {
        java.util.Map<String, Object> amendment = parentRow(ContractDAO.PROGRESS_DRAFT, 3);
        amendment.put("contract_id", 77);
        amendment.put("contract_code", "01/2026/HĐMB-POSTEF/PL01");
        amendment.put("contract_value", new BigDecimal("250000000"));
        amendment.put("effective_date", java.sql.Date.valueOf("2026-01-01"));
        amendment.put("end_date", java.sql.Date.valueOf("2026-12-31"));
        ResultSet amendmentRs = singleRow(amendment);
        ResultSet parentValue = singleRow(row("contract_value", new BigDecimal("1500000000")));
        ResultSet signedTotal = singleRow(row("total", new BigDecimal("250000000")));

        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(amendmentRs, parentValue, signedTotal);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.changeProgressStatus(77, ContractDAO.PROGRESS_SIGNED, ACTOR, null));

            // Dòng mốc "Ký hợp đồng" nằm trên phụ lục (77); dòng giá trị nằm
            // trên hợp đồng CHA (3).
            verify(historyPs).setInt(1, 77);
            verify(historyPs).setInt(1, 3);
            verify(historyPs).setString(2, ContractHistory.EVENT_VALUE_ADJUSTED);
            verify(conn).commit();
        }
    }

    /** Phụ lục KHÔNG đổi tiền thì không sinh dòng giá trị nào trên hợp đồng cha. */
    @Test
    public void changeProgressStatus_signingAmendmentWithoutValue_doesNotLogValueOnParent() throws Exception {
        java.util.Map<String, Object> amendment = parentRow(ContractDAO.PROGRESS_DRAFT, 3);
        amendment.put("contract_id", 77);
        amendment.put("effective_date", java.sql.Date.valueOf("2026-01-01"));
        amendment.put("end_date", java.sql.Date.valueOf("2026-12-31"));
        // ResultSet dựng TRƯỚC: singleRow() tự nó stub một mock, mà gọi nó bên
        // trong when(...) là dựng mock giữa chừng một lần stubbing khác.
        ResultSet amendmentRs = singleRow(amendment);
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(amendmentRs);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.changeProgressStatus(77, ContractDAO.PROGRESS_SIGNED, ACTOR, null));

            verify(historyPs, never()).setString(2, ContractHistory.EVENT_VALUE_ADJUSTED);
        }
    }

    private static java.util.Map<String, Object> parentRow(String progressStatus, Integer ownParentId) {
        return row("contract_id", 3,
                "contract_code", "01/2026/HĐMB-POSTEF",
                "title", "Hợp đồng gốc",
                "contract_type", "Mua vật tư",
                "direction", "Mua",
                "enterprise_id", 12,
                "owner_id", 5,
                "progress_status", progressStatus,
                "parent_contract_id", ownParentId);
    }

    private static Contract amendmentOf(int parentId) {
        Contract c = new Contract();
        c.setParentContractId(parentId);
        c.setContractCode("01/2026/HĐMB-POSTEF/PL01");
        c.setTitle("Phụ lục 01 — bổ sung hạng mục");
        c.setContractType("Mua vật tư");
        c.setOwnerId(5);
        return c;
    }

    /** Chạy insert() một phụ lục trên hợp đồng cha mô tả bởi {@code parent}. */
    private int insertAmendmentAgainstParent(java.util.Map<String, Object> parent) throws Exception {
        ResultSet parentRs = singleRow(parent);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(parentRs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.insert(amendmentOf(3), ACTOR);
            // Bị từ chối thì KHÔNG được để lại gì -- kể cả dòng nhật ký.
            verify(ps, never()).executeUpdate();
            verify(conn).rollback();
            return result;
        }
    }

    // ------------------------------------------------------------------
    // findById -- ánh xạ ResultSet sang model
    // ------------------------------------------------------------------

    /**
     * Chiều mua/bán phải đi ra khỏi findById.
     *
     * <p>SELECT_BASE chọn cột direction từ #110, nhưng mapRow quên đọc nó, nên
     * findById trả về direction = null. Hệ quả không nhìn thấy ngay trên màn
     * hình chi tiết (trang đó không in chiều), mà nằm ở form SỬA: controller
     * đổ bộ loại hợp đồng và danh sách đối tác theo chiều, và
     * counterpartyRoleFor(null) rơi về 'Khách mua'. Nghĩa là mọi hợp đồng MUA
     * mở form sửa lên là thấy bộ loại của hợp đồng bán, và bấm Lưu thì
     * counterpartyMatchesDirection từ chối -- không sửa được cái nào.
     */
    @Test
    public void findById_mapsDirection() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(buyContractRow("Mua")));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals("Mua", dao.findById(7).getDirection());
        }
    }

    /**
     * Một dòng contracts đủ các cột mà mapRow đọc. Thiếu cột nào thì
     * JdbcStub trả null cho cột đó, nên test vẫn chạy -- đây chỉ là bộ tối
     * thiểu để dựng được một Contract có nghĩa.
     */
    private static java.util.Map<String, Object> buyContractRow(String direction) {
        return row("contract_id", 7,
                "contract_code", "01/2026/HĐMB-POSTEF",
                "title", "Mua sợi quang",
                "contract_type", "Mua vật tư",
                "direction", direction,
                "enterprise_id", 3,
                "owner_id", 5,
                "progress_status", "Đã ký");
    }

    // ==================================================================
    // Task 4.2: Khóa hành vi ContractDAO trước khi tách
    // [UC-10, UC-11, UC-33, UC-34, UC-35, UC-44] [BR-16, BR-17, BR-35, BR-37]
    // ==================================================================

    // ------------------------------------------------------------------
    // 1. Bộ lọc mặc định: "Của tôi" & "Còn hiệu lực trong tháng" (UC-10, BR-16)
    // ------------------------------------------------------------------

    @Test
    public void findAll_withMyScopeOwnerAndProvinces_buildsOrClauseAndBindsOwnerThenProvince() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        ListScope myScope = ListScope.of(List.of(5, 8), List.of(3, 17));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, false,
                    null, null, null, false, null, myScope);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("c.is_deleted = 0"));
            assertTrue(sql.contains("(c.owner_id IN (?,?) OR d.province_id IN (?,?))"));
            verify(ps).setObject(1, 5);
            verify(ps).setObject(2, 8);
            verify(ps).setObject(3, 3);
            verify(ps).setObject(4, 17);
            verify(ps).setObject(5, 10);
            verify(ps).setObject(6, 0);
        }
    }

    @Test
    public void findAll_withMyScopeWithoutProvinces_filtersByOwnerOnly() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        ListScope myScope = ListScope.of(List.of(5), List.of());

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, false,
                    null, null, null, false, null, myScope);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("(c.owner_id IN (?))"));
            assertFalse(sql.contains("OR d.province_id"));
            verify(ps).setObject(1, 5);
        }
    }

    @Test
    public void findAll_withActiveWindowInScope_filtersByEffectiveAndEndDateAllowingNulls() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        Period monthWindow = Period.parse("2026", "m10");
        ListScope scope = ListScope.activeIn(monthWindow);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, false,
                    null, null, null, false, null, scope);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("((c.effective_date IS NULL OR c.effective_date <= ?)"
                    + " AND (c.end_date IS NULL OR c.end_date >= ?))"));
            verify(ps).setObject(1, monthWindow.getTo());
            verify(ps).setObject(2, monthWindow.getFrom());
        }
    }

    @Test
    public void findAll_withCombinedDefaultScopeAndUserFilters_bindsAllParamsInOrder() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        Period monthWindow = Period.parse("2026", "m10");
        Period signingPeriod = Period.parse("2026", "q4");
        ListScope scope = ListScope.of(List.of(5), List.of(3)).withActiveWindow(monthWindow);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(2, 10, "POSTEF", ContractDAO.STATUS_ACTIVE, "Cung cấp thiết bị",
                    List.of(3), false, signingPeriod, ContractDAO.DIRECTION_SELL,
                    ContractDAO.PROGRESS_SIGNED, true, 4, scope);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("(c.owner_id IN (?) OR d.province_id IN (?))"));
            assertTrue(sql.contains("((c.effective_date IS NULL OR c.effective_date <= ?)"
                    + " AND (c.end_date IS NULL OR c.end_date >= ?))"));
            assertTrue(sql.contains("c.parent_contract_id IS NULL"));
            assertTrue(sql.contains("AND wh.department_id = ?"));
            assertTrue(sql.contains("(c.contract_code LIKE ? OR c.title LIKE ? OR e.enterprise_name LIKE ?)"));
            assertTrue(sql.contains("c.contract_type = ?"));
            assertTrue(sql.contains("d.province_id IN (?)"));
            assertTrue(sql.contains("c.signing_date BETWEEN ? AND ?"));
            assertTrue(sql.contains("c.progress_status = ?"));
            assertTrue(sql.contains("c.direction = ?"));

            verify(ps).setObject(1, 5);
            verify(ps).setObject(2, 3);
            verify(ps).setObject(3, monthWindow.getTo());
            verify(ps).setObject(4, monthWindow.getFrom());
            verify(ps).setObject(5, 4);
            verify(ps).setObject(6, "%POSTEF%");
            verify(ps).setObject(7, "%POSTEF%");
            verify(ps).setObject(8, "%POSTEF%");
            verify(ps).setObject(9, "Cung cấp thiết bị");
            verify(ps).setObject(10, ContractDAO.STATUS_ACTIVE);
            verify(ps).setObject(11, 3);
            verify(ps).setObject(12, signingPeriod.getFrom());
            verify(ps).setObject(13, signingPeriod.getTo());
            verify(ps).setObject(14, ContractDAO.PROGRESS_SIGNED);
            verify(ps).setObject(15, ContractDAO.DIRECTION_SELL);
            verify(ps).setObject(16, 10);
            verify(ps).setObject(17, 10);
        }
    }

    @Test
    public void countAll_withDefaultScope_appliesSameOwnerProvinceAndActiveWindowAsFindAll() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", 4)));
        Connection conn = connectionReturning(ps);
        Period monthWindow = Period.parse("2026", "m10");
        ListScope scope = ListScope.of(List.of(5, 9), List.of(3)).withActiveWindow(monthWindow);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(4, dao.countAll(null, null, null, null, null, null, null, false, null, scope));

            String sql = capturedSql(conn);
            assertTrue(sql.contains("(c.owner_id IN (?,?) OR d.province_id IN (?))"));
            assertTrue(sql.contains("((c.effective_date IS NULL OR c.effective_date <= ?)"
                    + " AND (c.end_date IS NULL OR c.end_date >= ?))"));
            verify(ps).setObject(1, 5);
            verify(ps).setObject(2, 9);
            verify(ps).setObject(3, 3);
            verify(ps).setObject(4, monthWindow.getTo());
            verify(ps).setObject(5, monthWindow.getFrom());
        }
    }

    @Test
    public void countStatusSummary_withDefaultScope_appliesOwnerProvinceAndActiveWindow() throws Exception {
        ResultSet rs = singleRow(row("draft_count", 2, "expired_count", 1, "soon_count", 3, "active_count", 6));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);
        Period monthWindow = Period.parse("2026", "m10");
        ListScope scope = ListScope.of(List.of(5), List.of(3)).withActiveWindow(monthWindow);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            java.util.Map<String, Integer> summary = dao.countStatusSummary(
                    List.of(), null, ContractDAO.DIRECTION_SELL, false, null, scope,
                    ContractDAO.WAITING_ANY_DEPARTMENT);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("(c.owner_id IN (?) OR d.province_id IN (?))"));
            assertTrue(sql.contains("((c.effective_date IS NULL OR c.effective_date <= ?)"
                    + " AND (c.end_date IS NULL OR c.end_date >= ?))"));
            assertTrue(sql.contains("EXISTS (SELECT 1 FROM contract_handovers wh"
                    + " WHERE wh.contract_id = c.contract_id AND wh.done_at IS NULL)"));
            assertEquals(Integer.valueOf(2), summary.get(ContractDAO.STATUS_DRAFT));
            assertEquals(Integer.valueOf(1), summary.get(ContractDAO.STATUS_EXPIRED));
            assertEquals(Integer.valueOf(3), summary.get(ContractDAO.STATUS_SOON));
            assertEquals(Integer.valueOf(6), summary.get(ContractDAO.STATUS_ACTIVE));
        }
    }

    @Test
    public void findActiveInPeriod_withWindowAndScope_filtersRootsAndAllowsNullDates() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        Period window = Period.parse("2026", "m10");

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findActiveInPeriod(5, List.of(3), List.of(7), window);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("c.is_deleted = 0 AND c.parent_contract_id IS NULL"));
            assertTrue(sql.contains("(c.effective_date IS NULL OR c.effective_date <= ?)"));
            assertTrue(sql.contains("(c.end_date IS NULL OR c.end_date >= ?)"));
            verify(ps).setDate(1, window.getTo());
            verify(ps).setDate(2, window.getFrom());
        }
    }

    @Test
    public void findAll_withAllScope_hasNoOwnerNorActiveWindowClause() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // UC-10: chuyển sang "Toàn chi nhánh" + "Mọi thời điểm" (ListScope.all())
            dao.findAll(1, 10, null, null, null, null, false,
                    null, null, null, false, null, ListScope.all());

            String sql = capturedSql(conn);
            assertTrue(sql.contains("c.is_deleted = 0"));
            assertFalse(sql.contains("c.effective_date IS NULL OR c.effective_date <="));
            assertFalse(sql.contains("c.end_date IS NULL OR c.end_date >="));
            assertFalse(sql.contains("c.owner_id IN"));
            verify(ps).setObject(1, 10);
            verify(ps).setObject(2, 0);
        }
    }

    @Test
    public void findAll_withCurrentMonthDefaultActiveWindow_bindsCurrentMonthBounds() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        // UC-10: cửa sổ mặc định của danh sách là tháng hiện tại (xem ContractController.currentMonth)
        LocalDate today = LocalDate.now();
        Period currentMonth = Period.parse(String.valueOf(today.getYear()), "m" + today.getMonthValue());
        ListScope defaultScope = ListScope.of(List.of(5), List.of(3)).withActiveWindow(currentMonth);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, false,
                    null, null, null, false, null, defaultScope);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("((c.effective_date IS NULL OR c.effective_date <= ?)"
                    + " AND (c.end_date IS NULL OR c.end_date >= ?))"));
            verify(ps).setObject(1, 5);
            verify(ps).setObject(2, 3);
            verify(ps).setObject(3, Date.valueOf(today.withDayOfMonth(today.lengthOfMonth())));
            verify(ps).setObject(4, Date.valueOf(today.withDayOfMonth(1)));
        }
    }

    // ------------------------------------------------------------------
    // 2. Trạng thái tự tính (BR-17, UC-10, UC-11)
    // ------------------------------------------------------------------

    /**
     * BR-17: các test biên dưới đây cùng gọi {@code LocalDate.now()} như
     * {@code ContractDAO.computeStatus}. Lưu ý: nếu chạy đúng vào khoảnh khắc
     * chuyển ngày lúc 00:00:00 giữa bước dựng tham số và bước DAO tính toán thì
     * mốc ngày có thể lệch 1 ngày.
     */
    @Test
    public void insert_boundaryExactly30DaysRemaining_storesExpiringSoonStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().minusMonths(1)),
                Date.valueOf(LocalDate.now().plusDays(30)));

        verify(ps).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_SOON);
    }

    @Test
    public void insert_endingToday_storesExpiringSoonStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().minusMonths(1)),
                Date.valueOf(LocalDate.now()));

        verify(ps).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_SOON);
    }

    @Test
    public void insert_effectiveTodayAndEndingBeyond30Days_storesActiveStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now()),
                Date.valueOf(LocalDate.now().plusDays(31)));

        verify(ps).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_ACTIVE);
    }

    @Test
    public void insert_onlyOneDatePresent_fallsBackToDraftStatus() throws Exception {
        PreparedStatement psMissingEnd = captureStatusFor(
                Date.valueOf(LocalDate.now()), null);
        verify(psMissingEnd).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_DRAFT);

        PreparedStatement psMissingEffective = captureStatusFor(
                null, Date.valueOf(LocalDate.now().plusDays(60)));
        verify(psMissingEffective).setString(STATUS_PARAM_INDEX, ContractDAO.STATUS_DRAFT);
    }

    @Test
    public void findById_recomputesStatusFromDatesIgnoringStaleDatabaseStatusAndMapsAllBR16Fields() throws Exception {
        java.util.Map<String, Object> dbRow = row(
                "contract_id", 15,
                "contract_code", "15/2026/HĐKT-POSTEF",
                "title", "Cung cấp thiết bị nguồn",
                "contract_type", "Cung cấp thiết bị",
                "direction", ContractDAO.DIRECTION_SELL,
                "signing_date", Date.valueOf(LocalDate.now().minusDays(200)),
                "effective_date", Date.valueOf(LocalDate.now().minusDays(190)),
                "end_date", Date.valueOf(LocalDate.now().minusDays(2)),
                "enterprise_id", 4,
                "owner_id", 6,
                "status", ContractDAO.STATUS_ACTIVE, // Cột status trong DB bị cũ
                "progress_status", ContractDAO.PROGRESS_SIGNED,
                "contract_value", new BigDecimal("1000000000"),
                "amendment_count", 1,
                "amendment_value_signed", new BigDecimal("200000000"),
                "amendment_value_pending", BigDecimal.ZERO,
                "enterprise_name", "Công ty Viễn thông A",
                "province_id", 3,
                "province_name", "Thành phố Hà Nội",
                "owner_last_name", "Nguyễn",
                "owner_middle_name", "Văn",
                "owner_first_name", "An");
        PreparedStatement ps = statementReturning(singleRow(dbRow));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Contract found = dao.findById(15);

            assertNotNull(found);
            // BR-17: trạng thái phải tính lại từ ngày hiệu lực/kết thúc, không đọc cột status cũ
            assertEquals(ContractDAO.STATUS_EXPIRED, found.getStatus());
            assertEquals(ContractDAO.PROGRESS_SIGNED, found.getProgressStatus());
            assertEquals(new BigDecimal("1200000000"), found.getCurrentValue());
            assertNotNull(found.getEnterprise());
            assertEquals("Công ty Viễn thông A", found.getEnterprise().getEnterpriseName());
            assertEquals("Thành phố Hà Nội",
                    found.getEnterprise().getAddress().getDistrict().getProvince().getProvinceName());
            assertNotNull(found.getOwner());
            assertEquals("Nguyễn Văn An", found.getOwner().getFullName());
        }
    }

    @Test
    public void findByEnterpriseId_recomputesStatusForEachContract() throws Exception {
        java.util.Map<String, Object> activeRow = row(
                "contract_id", 1,
                "contract_code", "01/2026/HĐKT",
                "title", "HĐ 1",
                "contract_type", "Cung cấp thiết bị",
                "direction", "Bán",
                "effective_date", Date.valueOf(LocalDate.now().minusDays(10)),
                "end_date", Date.valueOf(LocalDate.now().plusDays(20)),
                "enterprise_id", 4,
                "owner_id", 6);
        PreparedStatement ps = statementReturning(singleRow(activeRow));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<Contract> list = dao.findByEnterpriseId(4);

            assertEquals(1, list.size());
            assertEquals(ContractDAO.STATUS_SOON, list.get(0).getStatus());
        }
    }

    // ------------------------------------------------------------------
    // 3. Tạo ở Nháp (UC-33, BR-35)
    // ------------------------------------------------------------------

    @Test
    public void insert_alwaysForcesDraftProgressStatusAndLogsCreatedHistory() throws Exception {
        ResultSet keys = singleRow(row("GENERATED_KEY", 99));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeUpdate()).thenReturn(1);
        when(contractPs.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        Contract c = contract("99/2026/HĐKT-POSTEF");
        c.setDirection(ContractDAO.DIRECTION_SELL);
        // BR-35: form tạo hợp đồng (ContractController.buildContractFromRequest) không đọc
        // signing_date từ request nên c.getSigningDate() = null; khóa việc insert() bind null
        // vào cột signing_date và luôn ép progress_status = Nháp dù bên gọi truyền giá trị khác.
        c.setSigningDate(null);
        c.setProgressStatus(ContractDAO.PROGRESS_SIGNED);
        c.setEffectiveDate(null);
        c.setEndDate(null);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(99, dao.insert(c, ACTOR));

            verify(contractPs).setDate(SIGNING_DATE_PARAM_INDEX, null);
            verify(contractPs).setString(PROGRESS_PARAM_INDEX, ContractDAO.PROGRESS_DRAFT);
            verify(historyPs).setInt(1, 99);
            verify(historyPs).setString(2, ContractHistory.EVENT_CREATED);
            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(historyPs).setString(eq(3), detail.capture());
            assertTrue(detail.getValue().contains("Tạo bản nháp 99/2026/HĐKT-POSTEF"));
            assertTrue(detail.getValue().contains("chưa chốt thời hạn"));
            verify(conn).commit();
        }
    }

    @Test
    public void contractCodeExists_blankOrNull_returnsFalseWithoutTouchingDatabase() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertFalse(dao.contractCodeExists(null, 0));
            assertFalse(dao.contractCodeExists("   ", 0));
            db.verify(DBContext::getConnection, never());
        }
    }

    @Test
    public void contractCodeExists_trimsCodeAndExcludesGivenContractId() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("one", 1)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.contractCodeExists("  01/2026/HĐKT-POSTEF  ", 12));

            verify(ps).setString(1, "01/2026/HĐKT-POSTEF");
            verify(ps).setInt(2, 12);
        }
    }

    // ------------------------------------------------------------------
    // 4. Ký hợp đồng & Cập nhật theo tiến độ (UC-44, UC-35, BR-35)
    // ------------------------------------------------------------------

    @Test
    public void changeProgressStatus_signingWithOnlyOneDatePresent_isRefused() throws Exception {
        ResultSet missingEnd = singleRow(row(
                "progress_status", ContractDAO.PROGRESS_DRAFT,
                "effective_date", Date.valueOf(LocalDate.now()),
                "end_date", null));
        ResultSet missingEffective = singleRow(row(
                "progress_status", ContractDAO.PROGRESS_DRAFT,
                "effective_date", null,
                "end_date", Date.valueOf(LocalDate.now().plusYears(1))));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(missingEnd, missingEffective);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_SIGNED, ACTOR, null));
            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_SIGNED, ACTOR, null));
            verify(ps, never()).executeUpdate();
            verify(conn, times(2)).rollback();
        }
    }

    @Test
    public void changeProgressStatus_alreadySigned_cannotSignAgainOrRevertToDraft() throws Exception {
        ResultSet signed1 = singleRow(contractRow(ContractDAO.PROGRESS_SIGNED));
        ResultSet signed2 = singleRow(contractRow(ContractDAO.PROGRESS_SIGNED));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(signed1, signed2);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_SIGNED, ACTOR, null));
            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_DRAFT, ACTOR, null));
            verify(ps, never()).executeUpdate();
            verify(conn, times(2)).rollback();
        }
    }

    @Test
    public void changeProgressStatus_draftCannotJumpDirectlyToLiquidatedOrTerminated() throws Exception {
        ResultSet draft1 = singleRow(contractRow(ContractDAO.PROGRESS_DRAFT));
        ResultSet draft2 = singleRow(contractRow(ContractDAO.PROGRESS_DRAFT));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(draft1, draft2);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_LIQUIDATED, ACTOR, "Thanh lý"));
            assertFalse(dao.changeProgressStatus(5, ContractDAO.PROGRESS_TERMINATED, ACTOR, "Chấm dứt"));
            verify(ps, never()).executeUpdate();
            verify(conn, times(2)).rollback();
        }
    }

    @Test
    public void update_frozenContract_refusesBothLiquidatedAndTerminated() throws Exception {
        ResultSet liquidated = singleRow(parentRow(ContractDAO.PROGRESS_LIQUIDATED, null));
        ResultSet terminated = singleRow(parentRow(ContractDAO.PROGRESS_TERMINATED, null));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(liquidated, terminated);
        Connection conn = connectionReturning(ps);

        Contract submitted = contract("01/2026/HĐMB-POSTEF");
        submitted.setContractId(3);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.update(submitted, ACTOR));
            assertFalse(dao.update(submitted, ACTOR));
            verify(ps, never()).executeUpdate();
            verify(conn, times(2)).rollback();
        }
    }

    @Test
    public void update_signedContract_ignoresLockedFieldChangesInHistoryWhenOwnerUnchanged() throws Exception {
        ResultSet current = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        Contract submitted = new Contract();
        submitted.setContractId(3);
        submitted.setOwnerId(5); // Giữ nguyên owner_id = 5 như trong parentRow
        submitted.setTitle("Tiêu đề bị sửa nhưng đã khóa");

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.update(submitted, ACTOR));

            // BR-35 / UC-35: câu UPDATE của HĐ đã ký chỉ được chứa owner_id, không chứa title
            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sql.capture());
            assertTrue(sql.getAllValues().stream()
                    .filter(q -> q.startsWith("UPDATE contracts"))
                    .allMatch(q -> q.contains("owner_id = ?") && !q.contains("title")));
            verify(historyPs, never()).executeUpdate();
            verify(conn).commit();
        }
    }

    @Test
    public void update_signedContract_whenOwnerChanged_logsOnlyOwnerChangeAndIgnoresLockedFields() throws Exception {
        ResultSet current = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        ResultSet oldOwnerLabel = singleRow(row("label", "Nguyễn Văn Cũ"));
        ResultSet newOwnerLabel = singleRow(row("label", "Trần Thị Mới"));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(current, oldOwnerLabel, newOwnerLabel);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        Contract submitted = new Contract();
        submitted.setContractId(3);
        submitted.setOwnerId(88); // Đổi từ 5 sang 88
        submitted.setTitle("Tiêu đề gửi kèm nhưng đã khóa");

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.update(submitted, ACTOR));

            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sql.capture());
            assertTrue(sql.getAllValues().stream()
                    .filter(q -> q.startsWith("UPDATE contracts"))
                    .allMatch(q -> q.contains("owner_id = ?") && !q.contains("title")));
            // setInt(1, 88) được gọi 2 lần trên contractPs: 1 lần cho UPDATE owner_id và 1 lần cho lookupLabel(88)
            verify(contractPs, times(2)).setInt(1, 88);
            verify(contractPs).setInt(2, 3);
            verify(historyPs).setInt(1, 3);
            verify(historyPs).setString(2, ContractHistory.EVENT_UPDATED);
            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(historyPs).setString(eq(3), detail.capture());
            assertTrue(detail.getValue().contains("Người phụ trách: Nguyễn Văn Cũ → Trần Thị Mới"));
            assertFalse(detail.getValue().contains("Tiêu đề"));
            verify(conn).commit();
        }
    }

    @Test
    public void update_draftAmendmentReducingBelowZero_refusesAndRollsBack() throws Exception {
        java.util.Map<String, Object> draftAmendment = parentRow(ContractDAO.PROGRESS_DRAFT, 3);
        draftAmendment.put("contract_id", 77);
        ResultSet amendmentRs = singleRow(draftAmendment);
        ResultSet parentValueRs = singleRow(row("contract_value", new BigDecimal("500000000")));
        ResultSet signedAmendmentsRs = singleRow(row("total", BigDecimal.ZERO));

        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(amendmentRs, parentValueRs, signedAmendmentsRs);
        Connection conn = connectionReturning(ps);

        Contract submitted = amendmentOf(3);
        submitted.setContractId(77);
        submitted.setContractValue(new BigDecimal("-600000000"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.update(submitted, ACTOR));
            verify(ps, never()).executeUpdate();
            verify(conn).rollback();
        }
    }

    // ------------------------------------------------------------------
    // 5. Huỷ bản ghi (UC-34, BR-37)
    // ------------------------------------------------------------------

    @Test
    public void voidRecord_softDeletesAndWritesHistoryBeforeCommit() throws Exception {
        ResultSet noChildAmendments = singleRow(row("count", 0));
        ResultSet voidedRs = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(noChildAmendments, voidedRs);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.voidRecord(3, ACTOR, "Nhập nhầm số hợp đồng"));

            // BR-37 & rules §4: xóa mềm (is_deleted = 1), không DELETE cứng, ghi lịch sử trước commit
            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sql.capture());
            assertTrue(sql.getAllValues().stream()
                    .anyMatch(q -> q.contains("UPDATE contracts SET is_deleted = 1")));
            assertTrue(sql.getAllValues().stream()
                    .noneMatch(q -> q.startsWith("DELETE FROM contracts")));

            InOrder inOrder = inOrder(conn, contractPs, historyPs);
            inOrder.verify(conn).setAutoCommit(false);
            inOrder.verify(contractPs).executeUpdate();
            inOrder.verify(historyPs).executeUpdate();
            inOrder.verify(conn).commit();
        }
    }

    @Test
    public void voidRecord_historyFails_rollsBackAndReturnsFalse() throws Exception {
        ResultSet noChildAmendments = singleRow(row("count", 0));
        ResultSet voidedRs = singleRow(parentRow(ContractDAO.PROGRESS_SIGNED, null));
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(noChildAmendments, voidedRs);
        when(contractPs.executeUpdate()).thenReturn(1);
        when(historyPs.executeUpdate()).thenThrow(new SQLException("history table down"));
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.voidRecord(3, ACTOR, "Nhập nhầm số hợp đồng"));

            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void voidRecord_nonExistentOrAlreadyDeletedContract_returnsFalseAndWritesNoHistory() throws Exception {
        ResultSet noAmendments = singleRow(row("count", 0));
        ResultSet notFound = emptyResultSet();
        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(noAmendments, notFound);
        when(contractPs.executeUpdate()).thenReturn(0);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.voidRecord(999, ACTOR, "Nhập nhầm"));

            verify(historyPs, never()).executeUpdate();
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void voidRecord_signedAmendmentWithValue_logsNegatedValueAdjustmentOnParent() throws Exception {
        ResultSet noChildAmendments = singleRow(row("count", 0));
        java.util.Map<String, Object> signedAmendment = parentRow(ContractDAO.PROGRESS_SIGNED, 3);
        signedAmendment.put("contract_id", 77);
        signedAmendment.put("contract_code", "01/2026/HĐMB-POSTEF/PL01");
        signedAmendment.put("contract_value", new BigDecimal("250000000"));
        ResultSet voidedRs = singleRow(signedAmendment);
        ResultSet parentBaseValue = singleRow(row("contract_value", new BigDecimal("1500000000")));
        ResultSet remainingSignedTotal = singleRow(row("total", BigDecimal.ZERO));

        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(
                noChildAmendments, voidedRs, parentBaseValue, remainingSignedTotal);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.voidRecord(77, ACTOR, "Nhập nhầm phụ lục"));

            verify(historyPs).setInt(1, 77);
            verify(historyPs).setString(2, ContractHistory.EVENT_VOIDED);
            verify(historyPs).setInt(1, 3);
            verify(historyPs).setString(2, ContractHistory.EVENT_VALUE_ADJUSTED);
            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(historyPs, times(2)).setString(eq(3), detail.capture());
            assertTrue(detail.getAllValues().get(1).contains("-250.000.000 đ"));
            verify(conn).commit();
        }
    }

    @Test
    public void voidRecord_draftAmendmentWithValue_doesNotLogValueAdjustmentOnParent() throws Exception {
        ResultSet noChildAmendments = singleRow(row("count", 0));
        java.util.Map<String, Object> draftAmendment = parentRow(ContractDAO.PROGRESS_DRAFT, 3);
        draftAmendment.put("contract_id", 77);
        draftAmendment.put("contract_value", new BigDecimal("250000000"));
        ResultSet voidedRs = singleRow(draftAmendment);

        PreparedStatement historyPs = mock(PreparedStatement.class);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(noChildAmendments, voidedRs);
        when(contractPs.executeUpdate()).thenReturn(1);
        Connection conn = connectionRoutingOn("contract_history", historyPs, contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.voidRecord(77, ACTOR, "Huỷ phụ lục nháp"));

            verify(historyPs, times(1)).executeUpdate();
            verify(historyPs).setString(2, ContractHistory.EVENT_VOIDED);
            verify(historyPs, never()).setString(2, ContractHistory.EVENT_VALUE_ADJUSTED);
            verify(conn).commit();
        }
    }

    // ==================================================================
    // Tài liệu hợp đồng -- ủy quyền sang ContractDocumentDAO (Task 4.5, UC-52)
    // ==================================================================

    @Test
    public void contractDAO_documentDelegation_constructorsAndGetters() {
        ContractDAO defaultDao = new ContractDAO();
        assertNotNull(defaultDao.getDocumentDAO());

        ContractDocumentDAO docDao = new ContractDocumentDAO();
        ContractDAO customDao = new ContractDAO(docDao);
        assertSame(docDao, customDao.getDocumentDAO());
    }

    @Test
    public void contractDAO_addDocument_delegatesToContractDocumentDAO() {
        ContractDocumentDAO mockDocDAO = mock(ContractDocumentDAO.class);
        ContractDAO daoWithMock = new ContractDAO(mockDocDAO);

        ContractDocument doc = new ContractDocument();
        doc.setContractId(10);
        when(mockDocDAO.addDocument(doc, ACTOR)).thenReturn(ContractDocumentDAO.DOC_OK);

        int result = daoWithMock.addDocument(doc, ACTOR);
        assertEquals(ContractDocumentDAO.DOC_OK, result);
        verify(mockDocDAO).addDocument(doc, ACTOR);
    }

    @Test
    public void contractDAO_voidDocument_delegatesToContractDocumentDAO() {
        ContractDocumentDAO mockDocDAO = mock(ContractDocumentDAO.class);
        ContractDAO daoWithMock = new ContractDAO(mockDocDAO);

        when(mockDocDAO.voidDocument(50, ACTOR, "Lý do huỷ")).thenReturn(true);

        assertTrue(daoWithMock.voidDocument(50, ACTOR, "Lý do huỷ"));
        verify(mockDocDAO).voidDocument(50, ACTOR, "Lý do huỷ");
    }

    @Test
    public void contractDAO_findDocumentsOf_delegatesToContractDocumentDAO() {
        ContractDocumentDAO mockDocDAO = mock(ContractDocumentDAO.class);
        ContractDAO daoWithMock = new ContractDAO(mockDocDAO);

        ContractDocument d = new ContractDocument();
        d.setDocumentId(1);
        when(mockDocDAO.findDocumentsOf(10)).thenReturn(List.of(d));

        List<ContractDocument> list = daoWithMock.findDocumentsOf(10);
        assertEquals(1, list.size());
        assertSame(d, list.get(0));
        verify(mockDocDAO).findDocumentsOf(10);
    }

    // ==================================================================
    // Bàn giao phòng ban -- ủy quyền sang ContractHandoverDAO (Task 4.7, UC-48, UC-49)
    // ==================================================================

    @Test
    public void contractDAO_handoverDelegation_constructorsAndGetters() {
        ContractDAO defaultDao = new ContractDAO();
        assertNotNull(defaultDao.getHandoverDAO());

        ContractHandoverDAO handoverDao = new ContractHandoverDAO();
        ContractDAO customDao = new ContractDAO(handoverDao);
        assertSame(handoverDao, customDao.getHandoverDAO());
    }

    @Test
    public void contractDAO_handOverToDepartments_delegatesToContractHandoverDAO() {
        ContractHandoverDAO mockHandoverDAO = mock(ContractHandoverDAO.class);
        ContractDAO daoWithMock = new ContractDAO(mockHandoverDAO);

        when(mockHandoverDAO.handOverToDepartments(10, List.of(1, 2), "ghi chú", ACTOR)).thenReturn(2);

        int result = daoWithMock.handOverToDepartments(10, List.of(1, 2), "ghi chú", ACTOR);
        assertEquals(2, result);
        verify(mockHandoverDAO).handOverToDepartments(10, List.of(1, 2), "ghi chú", ACTOR);
    }

    @Test
    public void contractDAO_completeHandover_delegatesToContractHandoverDAO() {
        ContractHandoverDAO mockHandoverDAO = mock(ContractHandoverDAO.class);
        ContractDAO daoWithMock = new ContractDAO(mockHandoverDAO);

        when(mockHandoverDAO.completeHandover(50, ACTOR, "xong việc")).thenReturn(true);

        assertTrue(daoWithMock.completeHandover(50, ACTOR, "xong việc"));
        verify(mockHandoverDAO).completeHandover(50, ACTOR, "xong việc");
    }

    @Test
    public void contractDAO_findHandoversOf_delegatesToContractHandoverDAO() {
        ContractHandoverDAO mockHandoverDAO = mock(ContractHandoverDAO.class);
        ContractDAO daoWithMock = new ContractDAO(mockHandoverDAO);

        ContractHandover h = new ContractHandover();
        h.setHandoverId(1);
        when(mockHandoverDAO.findHandoversOf(10)).thenReturn(List.of(h));

        List<ContractHandover> list = daoWithMock.findHandoversOf(10);
        assertEquals(1, list.size());
        assertSame(h, list.get(0));
        verify(mockHandoverDAO).findHandoversOf(10);
    }

    @Test
    public void contractDAO_findPendingHandovers_delegatesToContractHandoverDAO() {
        ContractHandoverDAO mockHandoverDAO = mock(ContractHandoverDAO.class);
        ContractDAO daoWithMock = new ContractDAO(mockHandoverDAO);

        ContractHandover h = new ContractHandover();
        h.setHandoverId(2);
        when(mockHandoverDAO.findPendingHandovers(List.of(ACTOR))).thenReturn(List.of(h));

        List<ContractHandover> list = daoWithMock.findPendingHandovers(List.of(ACTOR));
        assertEquals(1, list.size());
        assertSame(h, list.get(0));
        verify(mockHandoverDAO).findPendingHandovers(List.of(ACTOR));
    }

    // ==================================================================
    // Task 4.9 -- CRUD hợp đồng gốc, hàng hóa, phụ lục, kiểm tra đầu vào
    // và ủy quyền sang ContractHistoryDAO / ContractPaymentDAO / ContractLinkDAO
    // ==================================================================

    @Test
    public void nullGuards_insertUpdateCorrectInsertProducts_doNotTouchDatabase() {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertEquals(-1, dao.insert(null, ACTOR));
            assertFalse(dao.update(null, ACTOR));
            assertFalse(dao.correct(null, ACTOR, "Lý do hợp lệ"));
            assertTrue(dao.insertProducts(10, null, ACTOR));
            assertFalse(dao.contractCodeExists(null, 0));
            assertFalse(dao.contractCodeExists("   ", 0));

            db.verifyNoInteractions();
        }
    }

    @Test
    public void findById_whenNotFound_returnsNull() throws Exception {
        ResultSet rs = emptyResultSet();
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNull(dao.findById(999));
            verify(ps).setInt(1, 999);
        }
    }

    @Test
    public void findById_onSqlException_returnsNull() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException("boom"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNull(dao.findById(10));
        }
    }

    @Test
    public void findByEnterpriseId_mapsContractFieldsAndStatus() throws Exception {
        ResultSet rs = singleRow(row(
                "contract_id", 15,
                "contract_code", "HD-ENT-15",
                "title", "Hợp đồng bảo trì",
                "contract_type", "Bảo trì",
                "direction", ContractDAO.DIRECTION_SELL,
                "signing_date", Date.valueOf("2026-01-10"),
                "effective_date", Date.valueOf(LocalDate.now().minusDays(10)),
                "end_date", Date.valueOf(LocalDate.now().plusDays(90)),
                "enterprise_id", 7,
                "owner_id", 3));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<Contract> list = dao.findByEnterpriseId(7);

            assertEquals(1, list.size());
            Contract c = list.get(0);
            assertEquals(15, c.getContractId());
            assertEquals("HD-ENT-15", c.getContractCode());
            assertEquals(ContractDAO.DIRECTION_SELL, c.getDirection());
            assertEquals(ContractDAO.STATUS_ACTIVE, c.getStatus());
            verify(ps).setInt(1, 7);
        }
    }

    @Test
    public void findByEnterpriseId_onSqlException_returnsEmptyList() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException("boom"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.findByEnterpriseId(7).isEmpty());
        }
    }

    @Test
    public void findProductsByContractId_mapsProductRowsAndHandlesException() throws Exception {
        ResultSet rs = singleRow(row(
                "contract_product_id", 101,
                "contract_id", 10,
                "product_id", 5,
                "product_code", "SP-05",
                "product_name", "Thiết bị định vị",
                "quantity", 12,
                "unit", "bộ",
                "notes", "Bảo hành 24 tháng"));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractProduct> items = dao.findProductsByContractId(10);

            assertEquals(1, items.size());
            ContractProduct cp = items.get(0);
            assertEquals(101, cp.getContractProductId());
            assertEquals(10, cp.getContractId());
            assertEquals(5, cp.getProductId());
            assertEquals("SP-05", cp.getProductCode());
            assertEquals("Thiết bị định vị", cp.getProductName());
            assertEquals(12, cp.getQuantity());
            assertEquals("bộ", cp.getUnit());
            assertEquals("Bảo hành 24 tháng", cp.getNotes());
        }

        Connection badConn = mock(Connection.class);
        when(badConn.prepareStatement(anyString())).thenThrow(new SQLException("boom"));
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(badConn);
            assertTrue(dao.findProductsByContractId(10).isEmpty());
        }
    }

    @Test
    public void findAmendmentsByParentId_mapsRowsAndHandlesException() throws Exception {
        ResultSet rs = singleRow(row(
                "contract_id", 21,
                "contract_code", "HD-01-PL01",
                "title", "Phụ lục gia hạn",
                "contract_type", "Phụ lục",
                "direction", ContractDAO.DIRECTION_SELL,
                "parent_contract_id", 10,
                "parent_contract_code", "HD-01",
                "progress_status", ContractDAO.PROGRESS_SIGNED));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<Contract> list = dao.findAmendmentsByParentId(10);

            assertEquals(1, list.size());
            assertEquals(21, list.get(0).getContractId());
            assertEquals(Integer.valueOf(10), list.get(0).getParentContractId());
            verify(ps).setInt(1, 10);
        }

        Connection badConn = mock(Connection.class);
        when(badConn.prepareStatement(anyString())).thenThrow(new SQLException("boom"));
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(badConn);
            assertTrue(dao.findAmendmentsByParentId(10).isEmpty());
        }
    }

    @Test
    public void contractDAO_historyPaymentAndLinkDelegation_forwardsAllCalls() {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractPaymentDAO mockPaymentDAO = mock(ContractPaymentDAO.class);
        ContractDocumentDAO mockDocDAO = mock(ContractDocumentDAO.class);
        ContractLinkDAO mockLinkDAO = mock(ContractLinkDAO.class);
        ContractHandoverDAO mockHandoverDAO = mock(ContractHandoverDAO.class);

        ContractDAO fullDao = new ContractDAO(
                mockHistoryDAO, mockPaymentDAO, mockDocDAO, mockLinkDAO, mockHandoverDAO);

        assertSame(mockHistoryDAO, fullDao.getHistoryDAO());
        assertSame(mockPaymentDAO, fullDao.getPaymentDAO());
        assertSame(mockDocDAO, fullDao.getDocumentDAO());
        assertSame(mockLinkDAO, fullDao.getLinkDAO());
        assertSame(mockHandoverDAO, fullDao.getHandoverDAO());

        ContractHistory history = new ContractHistory();
        when(mockHistoryDAO.findHistoryByContractId(10)).thenReturn(List.of(history));
        assertEquals(1, fullDao.findHistoryByContractId(10).size());
        verify(mockHistoryDAO).findHistoryByContractId(10);

        ContractPayment payment = new ContractPayment();
        Date paidDate = Date.valueOf("2026-04-15");
        Period period = mock(Period.class);
        when(mockPaymentDAO.findPaymentsByContractId(10)).thenReturn(List.of(payment));
        when(mockPaymentDAO.insertPayment(10, payment, ACTOR)).thenReturn(true);
        when(mockPaymentDAO.markPaymentPaid(1, 10, paidDate, ACTOR)).thenReturn(true);
        when(mockPaymentDAO.deletePayment(1, 10, ACTOR)).thenReturn(true);
        when(mockPaymentDAO.sumInvoiceAmountInPeriod(period, 1)).thenReturn(BigDecimal.TEN);
        when(mockPaymentDAO.sumInvoiceAmountInPeriod(period, List.of(1), List.of(ACTOR))).thenReturn(BigDecimal.ONE);
        when(mockPaymentDAO.sumInvoiceAmountByMonth(2026, 4)).thenReturn(BigDecimal.TEN);
        when(mockPaymentDAO.sumInvoiceAmountByMonth(2026, 4, 1)).thenReturn(BigDecimal.TEN);
        when(mockPaymentDAO.sumInvoiceAmountByMonth(2026, 4, List.of(1), List.of(ACTOR))).thenReturn(BigDecimal.ONE);
        when(mockPaymentDAO.sumScheduledPaymentsForCluster(10)).thenReturn(new BigDecimal("500"));

        assertEquals(1, fullDao.findPaymentsByContractId(10).size());
        assertTrue(fullDao.insertPayment(10, payment, ACTOR));
        assertTrue(fullDao.markPaymentPaid(1, 10, paidDate, ACTOR));
        assertTrue(fullDao.deletePayment(1, 10, ACTOR));
        assertEquals(BigDecimal.TEN, fullDao.sumInvoiceAmountInPeriod(period, 1));
        assertEquals(BigDecimal.ONE, fullDao.sumInvoiceAmountInPeriod(period, List.of(1), List.of(ACTOR)));
        assertEquals(BigDecimal.TEN, fullDao.sumInvoiceAmountByMonth(2026, 4));
        assertEquals(BigDecimal.TEN, fullDao.sumInvoiceAmountByMonth(2026, 4, 1));
        assertEquals(BigDecimal.ONE, fullDao.sumInvoiceAmountByMonth(2026, 4, List.of(1), List.of(ACTOR)));
        assertEquals(new BigDecimal("500"), fullDao.sumScheduledPaymentsForCluster(10));

        ContractLink link = new ContractLink();
        when(mockLinkDAO.linkContracts(10, 20, "note", ACTOR)).thenReturn(ContractDAO.LINK_OK);
        when(mockLinkDAO.unlinkContracts(5, ACTOR)).thenReturn(true);
        when(mockLinkDAO.findLinksOf(10)).thenReturn(List.of(link));
        when(mockLinkDAO.sumLinkedBuyValue(10)).thenReturn(new BigDecimal("300"));

        assertEquals(ContractDAO.LINK_OK, fullDao.linkContracts(10, 20, "note", ACTOR));
        assertTrue(fullDao.unlinkContracts(5, ACTOR));
        assertEquals(1, fullDao.findLinksOf(10).size());
        assertEquals(new BigDecimal("300"), fullDao.sumLinkedBuyValue(10));
    }

    @Test
    public void insertProducts_listWithNullElement_returnsFalseWithoutTouchingDatabase() {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertFalse(dao.insertProducts(10, Arrays.asList(item(1, 2), null), ACTOR));
            db.verifyNoInteractions();
        }
    }

    @Test
    public void findAll_pageSizeZeroOrNegative_usesDefaultPageSize() throws Exception {
        PreparedStatement psZero = statementReturning(emptyResultSet());
        Connection connZero = connectionReturning(psZero);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connZero);
            dao.findAll(2, 0, null, null, null);
            verify(psZero).setObject(1, 10);
            verify(psZero).setObject(2, 10);
        }

        PreparedStatement psNegative = statementReturning(emptyResultSet());
        Connection connNegative = connectionReturning(psNegative);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connNegative);
            dao.findAll(3, -5, null, null, null);
            verify(psNegative).setObject(1, 10);
            verify(psNegative).setObject(2, 20);
        }
    }

    @Test
    public void findAll_pageBelowOne_startsAtOffsetZero() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);
            dao.findAll(0, 15, null, null, null);
            verify(ps).setObject(1, 15);
            verify(ps).setObject(2, 0);
        }
    }

    @Test
    public void mapRow_withNullEnterpriseNullProvinceNullOwner_handlesNullableBranches() throws Exception {
        ResultSet rsAllNull = singleRow(row(
                "contract_id", 30,
                "contract_code", "HD-30",
                "enterprise_id", 5,
                "owner_id", 8));
        ResultSet rsEnterpriseWithoutProvince = singleRow(row(
                "contract_id", 31,
                "contract_code", "HD-31",
                "enterprise_id", 5,
                "enterprise_name", "Công ty A",
                "owner_id", 8,
                "owner_last_name", "Nguyễn",
                "owner_middle_name", "Văn",
                "owner_first_name", "B"));
        ResultSet rsEnterpriseWithProvince = singleRow(row(
                "contract_id", 32,
                "contract_code", "HD-32",
                "enterprise_id", 5,
                "enterprise_name", "Công ty A",
                "province_id", 1,
                "province_name", "Hà Nội",
                "owner_id", 8));

        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(rsAllNull, rsEnterpriseWithoutProvince, rsEnterpriseWithProvince);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Contract c1 = dao.findById(30);
            assertNotNull(c1);
            assertNull(c1.getEnterprise());
            assertNull(c1.getOwner());

            Contract c2 = dao.findById(31);
            assertNotNull(c2);
            assertNotNull(c2.getEnterprise());
            assertEquals("Công ty A", c2.getEnterprise().getEnterpriseName());
            assertNull(c2.getEnterprise().getAddress());
            assertNotNull(c2.getOwner());
            assertEquals("Nguyễn", c2.getOwner().getLastName());

            Contract c3 = dao.findById(32);
            assertNotNull(c3);
            assertNotNull(c3.getEnterprise());
            assertNotNull(c3.getEnterprise().getAddress());
            assertEquals("Hà Nội", c3.getEnterprise().getAddress().getDistrict().getProvince().getProvinceName());
            assertNull(c3.getOwner());
        }
    }

    @Test
    public void countStatusSummary_waitingHandoverClause_handlesAnyAndSpecificDepartment() throws Exception {
        ResultSet rsAny = singleRow(row("draft_count", 1, "expired_count", 2, "soon_count", 3, "active_count", 4));
        PreparedStatement psAny = statementReturning(rsAny);
        Connection connAny = mock(Connection.class);
        ArgumentCaptor<String> sqlAny = ArgumentCaptor.forClass(String.class);
        when(connAny.prepareStatement(sqlAny.capture())).thenReturn(psAny);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connAny);

            java.util.Map<String, Integer> summary = dao.countStatusSummary(
                    (Integer) null, null, null, false, null, ListScope.all(), ContractDAO.WAITING_ANY_DEPARTMENT);

            assertEquals(Integer.valueOf(4), summary.get(ContractDAO.STATUS_ACTIVE));
            assertTrue(sqlAny.getValue().contains("wh.done_at IS NULL)"));
            assertFalse(sqlAny.getValue().contains("wh.department_id = ?"));
            verify(psAny, never()).setInt(anyInt(), anyInt());
        }

        ResultSet rsDept = singleRow(row("draft_count", 0, "expired_count", 0, "soon_count", 1, "active_count", 2));
        PreparedStatement psDept = statementReturning(rsDept);
        Connection connDept = mock(Connection.class);
        ArgumentCaptor<String> sqlDept = ArgumentCaptor.forClass(String.class);
        when(connDept.prepareStatement(sqlDept.capture())).thenReturn(psDept);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connDept);

            dao.countStatusSummary((Integer) null, null, null, false, null, ListScope.all(), 3);

            assertTrue(sqlDept.getValue().contains("wh.department_id = ?"));
            verify(psDept).setInt(1, 3);
        }
    }
}



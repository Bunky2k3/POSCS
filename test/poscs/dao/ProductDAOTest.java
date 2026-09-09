package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho ProductDAO. Hai chỗ đáng chú ý:
 *
 * <ul>
 *   <li>{@code nextProductCodeAfter} -- hàm thuần tính toán, không chạm CSDL,
 *       tách ra riêng để nhập Excel hàng loạt chỉ phải đọc mã lớn nhất đúng 1
 *       lần rồi tăng dần. Sai ở đây làm cả lô nhập vào bị trùng mã.</li>
 *   <li>{@code isUsedInContracts} -- khi đọc lỗi thì trả <b>true</b> ("coi như
 *       đang được dùng") để chặn xoá, vì xoá nhầm sản phẩm đang nằm trong hợp
 *       đồng sẽ để lại dòng contractproducts mồ côi.</li>
 * </ul>
 *
 * Xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng gì (SQL, tên
 * cột, schema). JUnit 4 -- xem CustomerControllerTest.
 */
public class ProductDAOTest {

    private final ProductDAO dao = new ProductDAO();

    // ------------------------------------------------------------------
    // nextProductCodeAfter -- thuần tính toán, không chạm CSDL
    // ------------------------------------------------------------------

    @Test
    public void nextProductCodeAfter_null_startsAtOne() {
        assertEquals("SP-0001", dao.nextProductCodeAfter(null));
    }

    @Test
    public void nextProductCodeAfter_normalCode_incrementsAndKeepsPadding() {
        assertEquals("SP-0008", dao.nextProductCodeAfter("SP-0007"));
    }

    @Test
    public void nextProductCodeAfter_rollsOverPaddingWidth() {
        // Vượt 4 chữ số thì giữ nguyên số thật, không được cắt cụt thành "SP-0000".
        assertEquals("SP-10000", dao.nextProductCodeAfter("SP-9999"));
    }

    @Test
    public void nextProductCodeAfter_codeWithoutDigits_startsAtOne() {
        assertEquals("SP-0001", dao.nextProductCodeAfter("SP-"));
    }

    @Test
    public void nextProductCodeAfter_digitsScatteredInCode_areReadAsOneNumber() {
        // Hàm gom mọi chữ số trong chuỗi rồi +1: "SP-12-34" -> 1234 + 1.
        assertEquals("SP-1235", dao.nextProductCodeAfter("SP-12-34"));
    }

    @Test
    public void generateNextProductCode_emptyTable_returnsFirstCode() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals("SP-0001", dao.generateNextProductCode());
        }
    }

    @Test
    public void generateNextProductCode_readsLatestCodeAndIncrements() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("product_code", "SP-0026")));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals("SP-0027", dao.generateNextProductCode());
        }
    }

    @Test
    public void generateNextProductCode_sqlError_returnsNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            // null là tín hiệu "không sinh được mã" để bên gọi dừng lại, khác
            // hẳn với việc trả về "SP-0001" rồi ghi đè lên sản phẩm đầu tiên.
            assertNull(dao.generateNextProductCode());
        }
    }

    // ------------------------------------------------------------------
    // isUsedInContracts -- chặn xoá sản phẩm đang nằm trong hợp đồng
    // ------------------------------------------------------------------

    @Test
    public void isUsedInContracts_countGreaterThanZero_returnsTrue() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("c", 2)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.isUsedInContracts(10));
            verify(ps).setInt(1, 10);
        }
    }

    @Test
    public void isUsedInContracts_countZero_returnsFalse() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("c", 0)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.isUsedInContracts(10));
        }
    }

    @Test
    public void isUsedInContracts_sqlError_returnsTrueToBlockDeletion() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            // Không kiểm tra được thì phải nghiêng về chặn xoá -- cho xoá nhầm
            // sẽ để lại dòng contractproducts trỏ vào sản phẩm đã biến mất.
            assertTrue(dao.isUsedInContracts(10));
        }
    }

    // ------------------------------------------------------------------
    // Ảnh / catalogue -- xoá phải gắn với đúng sản phẩm
    // ------------------------------------------------------------------

    @Test
    public void deleteImage_scopesDeletionToOwningProduct() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.deleteImage(5, 10));

            // product_id phải nằm trong WHERE: form sửa sản phẩm gửi lên id ảnh
            // từ phía client, thiếu ràng buộc này thì sửa sản phẩm A có thể xoá
            // ảnh của sản phẩm B.
            verify(ps).setInt(1, 5);
            verify(ps).setInt(2, 10);
        }
    }

    @Test
    public void deleteImage_imageBelongsToAnotherProduct_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.deleteImage(5, 999));
        }
    }

    @Test
    public void deleteCatalogue_scopesDeletionToOwningProduct() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.deleteCatalogue(3, 10));
            verify(ps).setInt(1, 3);
            verify(ps).setInt(2, 10);
        }
    }

    @Test
    public void findPrimaryImageUrl_noImage_returnsNull() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNull(dao.findPrimaryImageUrl(10));
        }
    }

    @Test
    public void findImagesByProductId_sqlError_returnsEmptyListNotNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertNotNull(dao.findImagesByProductId(10));
            assertTrue(dao.findImagesByProductId(10).isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // softDelete
    // ------------------------------------------------------------------

    @Test
    public void softDelete_marksRowInsteadOfRemovingIt() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.softDelete(10));
            verify(ps).setInt(1, 10);
        }
    }

    @Test
    public void softDelete_sqlError_returnsFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.softDelete(10));
        }
    }

    // ------------------------------------------------------------------
    // Phân trang
    // ------------------------------------------------------------------

    @Test
    public void findAll_secondPage_offsetSkipsFirstPage() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(2, 12, null, null);

            verify(ps).setObject(1, 12);
            verify(ps).setObject(2, 12); // (2-1)*12
        }
    }

    @Test
    public void countAll_sqlError_returnsZero() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertEquals(0, dao.countAll(null, null));
        }
    }

    @Test
    public void findByCode_noMatch_returnsNull() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNull(dao.findByCode("SP-9999"));
            verify(ps).setString(1, "SP-9999");
        }
    }
}

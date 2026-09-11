package poscs.integration;

import java.sql.SQLException;

/**
 * Dữ liệu đầu vào tối thiểu cho test tích hợp: đúng MỘT bản ghi cho mỗi thực
 * thể chính, nối với nhau đúng khoá ngoại.
 *
 * Cố tình seed bằng SQL trực tiếp chứ không qua DAO: nếu dựng dữ liệu bằng
 * chính cái mình đang kiểm thì một lỗi trong DAO.insert() vừa làm hỏng dữ
 * liệu đầu vào vừa làm hỏng phép kiểm, và test vẫn xanh.
 *
 * Chỉ một bản ghi mỗi loại nên các phép khẳng định đếm được chính xác
 * ({@code assertEquals(1, ...)}) -- đủ để chứng minh câu SQL chạy và map đúng
 * cột, mà không phải bảo trì một bộ dữ liệu lớn.
 */
public final class Fixtures {

    public static final int PROVINCE_ID = 1;
    public static final int WARD_ID = 1;
    public static final int ROLE_ID = 1;
    public static final int DEPARTMENT_ID = 1;

    public static final int ADDRESS_ID = 1;
    public static final int USER_ID = 1;
    public static final int ENTERPRISE_ID = 1;
    public static final int CONTRACT_ID = 1;
    public static final int PRODUCT_ID = 1;
    public static final int TICKET_ID = 1;

    public static final String USERNAME = "itadmin";
    public static final String EMAIL = "itadmin@poscs.test";
    public static final String PHONE = "0900000001";
    public static final String CITIZEN_ID = "000000000001";

    /** BCrypt của "Admin@123" -- cố định để test đăng nhập khỏi phải hash lại mỗi lần. */
    public static final String PASSWORD_PLAIN = "Admin@123";
    public static final String PASSWORD_HASH =
            "$2a$10$EqcGGSpXeK2RWOyyHnJQx.QvO.BJP656NOJZ9Lsgud7WcgsUAjDG.";

    private Fixtures() {
    }

    public static void seedMinimal() throws SQLException {
        seedAddressAndUser();
        seedEnterprise();
        seedContract();
        seedProduct();
        seedTicket();
    }

    public static void seedAddressAndUser() throws SQLException {
        IntegrationDb.exec(
            "INSERT INTO addresses (address_id, street_and_local_name, districts_id) VALUES "
            + "(" + ADDRESS_ID + ", N'Số 1 Đường Thử Nghiệm', " + WARD_ID + ")");
        IntegrationDb.exec(
            "INSERT INTO users (user_id, username, email, password_hash, role_id, last_name, "
            + "middle_name, first_name, gender, date_of_birth, citizen_id, phone, personal_email, "
            + "address_id, department_id, hire_date) VALUES ("
            + USER_ID + ", '" + USERNAME + "', '" + EMAIL + "', '" + PASSWORD_HASH + "', " + ROLE_ID + ", "
            + "N'Nguyễn', NULL, N'Quản Trị', N'Nam', '1990-01-01', '" + CITIZEN_ID + "', '" + PHONE + "', "
            + "'it.admin@gmail.com', " + ADDRESS_ID + ", " + DEPARTMENT_ID + ", '2024-01-01')");
    }

    public static void seedEnterprise() throws SQLException {
        IntegrationDb.exec(
            "INSERT INTO enterprises (enterprise_id, enterprise_code, enterprise_name, customer_type, "
            + "customer_group, tax_code, email, phone, website, address_id, account_owner_id, "
            + "legal_representative, current_relationship_rating, join_date) VALUES ("
            + ENTERPRISE_ID + ", 'KH-0001', N'Công ty Cổ phần Viễn thông Sông Hồng', N'Đại lý phân phối', "
            + "N'Thường', '0100000001', 'songhong@example.com', '0243822001', 'https://songhong.example', "
            + ADDRESS_ID + ", " + USER_ID + ", N'Nguyễn Văn A', N'Tốt', '2025-01-10')");
    }

    /** Hợp đồng đang hiệu lực: hiệu lực đã bắt đầu, còn xa ngày kết thúc. */
    public static void seedContract() throws SQLException {
        IntegrationDb.exec(
            "INSERT INTO contracts (contract_id, contract_code, title, contract_type, signing_date, "
            + "effective_date, end_date, enterprise_id, owner_id) VALUES ("
            + CONTRACT_ID + ", 'HD-0001', N'Hợp đồng cung cấp thiết bị', N'Bán hàng', "
            + "DATE_SUB(CURDATE(), INTERVAL 60 DAY), DATE_SUB(CURDATE(), INTERVAL 30 DAY), "
            + "DATE_ADD(CURDATE(), INTERVAL 180 DAY), " + ENTERPRISE_ID + ", " + USER_ID + ")");
    }

    public static void seedProduct() throws SQLException {
        IntegrationDb.exec(
            "INSERT INTO products (product_id, product_code, product_name, category_id, description) "
            + "VALUES (" + PRODUCT_ID + ", 'SP-0001', N'Tủ nguồn trạm BTS', "
            + "(SELECT MIN(category_id) FROM productcategories), N'Mô tả thử nghiệm')");
    }

    /** Phiếu chưa đóng, có hạn SLA trong tương lai gần. */
    public static void seedTicket() throws SQLException {
        IntegrationDb.exec(
            "INSERT INTO technicalrequests (ticket_id, ticket_code, enterprise_id, contract_id, "
            + "ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, "
            + "created_by, created_date, description, is_warranty, status) VALUES ("
            + TICKET_ID + ", 'TK-0001', " + ENTERPRISE_ID + ", " + CONTRACT_ID + ", "
            + "N'Bảo hành', N'Cao', N'Điện thoại', DATE_ADD(NOW(), INTERVAL 3 DAY), "
            + USER_ID + ", " + USER_ID + ", CURDATE(), N'Thiết bị lỗi nguồn', 1, N'Đang xử lý')");
    }
}

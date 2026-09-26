/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phân hệ Nhân viên.
 * Chạy: node tools/guide/capture.mjs employee   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/employee.jsp -- sửa bên này thì soát lại bên kia. Tên ảnh
 * đánh số theo thứ tự xuất hiện trong trang hướng dẫn (Hình 1, Hình 2...).
 *
 * Chỉ Admin vào được phân hệ này, nên mọi ảnh chụp bằng tài khoản admin.
 * KHÔNG ảnh nào ghi dữ liệu: form chỉ điền ví dụ, không gửi; hai hộp xác
 * nhận (gửi tài khoản, khoá) chỉ mở ra rồi chụp.
 *
 * Dữ liệu mẫu (bản sao poscs_db, 17 tài khoản -- xem V12): sales4 (id 22,
 * Phạm Thị Ngọc Anh) cầm 5 tỉnh; sales6 (id 24) đang bị khoá. Cả 18 tỉnh
 * địa bàn chi nhánh đều đã có người cầm. Tài khoản thật của người dùng
 * (id 32) đứng đầu danh sách -- trong bản sao dùng để chụp, đổi thành nhân
 * viên mẫu (Nguyễn Văn Đức, nguyenvanduc) trước khi chụp, vì ảnh nằm công
 * khai trong repo.
 */
const view = (id) => '/employee?action=view&id=' + id;
const edit = (id) => '/employee?action=edit&id=' + id;

// Ô ngày dùng flatpickr: ô gốc bị đổi thành type=hidden, người dùng thấy ô
// "alt" chèn ngay sau nó -- điền qua API của flatpickr, khoanh ô alt.
const dateField = (id) => '#' + id + ' + input';
async function pickDate(page, id, isoDate) {
    await page.eval(`document.getElementById(${JSON.stringify(id)})._flatpickr.setDate(${JSON.stringify(isoDate)}, true)`);
}

// Mở hộp xác nhận (modal) rồi chờ nó hiện hẳn.
async function openModal(page, buttonSelector) {
    await page.click(buttonSelector);
    await page.waitFor('.modal.show');
    await page.settle(500);
}

export default {
    viewport: { width: 1600, height: 900 },
    shots: [
        // ===== 2. Xem và tìm nhân viên =====
        {
            file: '01-danh-sach.png',
            user: 'admin',
            url: '/employee',
            marks: [
                { sel: '#searchInput', n: 1 },
                { sel: '#filterRole', n: 2 },
                { sel: '#filterStatus', n: 3 },
                { sel: '.header-actions .btn-add', n: 4 },
                { sel: '.employee-grid .employee-card', n: 5 },
                { sel: '.employee-card .status-inactive', n: 6 },
                { sel: '.pagination-bar nav', n: 7 }
            ],
            clip: '.page-container'
        },

        // ===== 3. Thêm nhân viên =====
        {
            file: '02-them.png',
            user: 'admin',
            url: '/employee?action=new',
            // Điền ví dụ -- KHÔNG bấm Tạo nhân viên. Bốn ô cá nhân để trống,
            // đúng như lúc tạo thật (nhân viên tự bổ sung ở lần đăng nhập đầu).
            before: async (page) => {
                await page.select('#department', 'Kinh doanh');
                await page.select('#roleId', 'Sales');
                await pickDate(page, 'hireDate', '2026-10-01');
                await page.type('#lastName', 'Trần');
                await page.type('#middleName', 'Minh');
                await page.type('#firstName', 'Khoa');
                await page.type('#personalEmail', 'tranminhkhoa.test@example.com');
            },
            marks: [
                { sel: '.info-banner', n: 1 },
                { sel: '#department', n: 2 },
                { sel: '#roleId', n: 3 },
                { sel: dateField('hireDate'), n: 4 },
                { sel: '.emp-prov-toggle', n: 5 },
                { sel: '#lastName', n: 6 },
                { sel: '#middleName', n: 6 },
                { sel: '#firstName', n: 6 },
                // Khoanh chính bốn ô không bắt buộc chứ không khoanh dòng chú thích
                // phía trên: dòng đó nằm sát nhãn hàng dưới, khung nào cũng đè chữ.
                { sel: '#gender', n: 7 },
                { sel: dateField('dateOfBirth'), n: 7 },
                { sel: '#citizenId', n: 7 },
                { sel: '#phone', n: 7 },
                { sel: '#personalEmail', n: 8 },
                { sel: '.section-header', text: 'Địa chỉ', n: 9, pad: 9 },
                { sel: '.action-bar .btn-primary', n: 10 }
            ],
            clip: '.page-container'
        },
        {
            file: '03-dia-ban.png',
            user: 'admin',
            url: edit(22),
            before: async (page) => {
                await page.click('[data-prov-pop] [data-popover-toggle]');
            },
            // 2 là Điện Biên chứ không phải dòng ngay dưới số 1: hai khung ở hai
            // dòng liền nhau dính vào nhau.
            marks: [
                { sel: '#empProvincePanel label.prov-row.mine', n: 1 },
                { sel: '#empProvincePanel label.prov-row', text: 'Điện Biên', n: 2 },
                { sel: '#empProvinceClear', n: 3 },
                { sel: '.emp-prov-toggle', n: 4 }
            ],
            // Lề trên rộng để lấy trọn nhãn "Địa bàn phụ trách" phía trên ô.
            clip: ['.emp-prov-pop', '#empProvincePanel'],
            margin: 44
        },

        // ===== 4. Xem chi tiết =====
        {
            file: '04-chi-tiet.png',
            user: 'admin',
            url: view(22),
            marks: [
                { sel: '.profile-banner .badge-row', n: 1 },
                { sel: '.btn-outline-edit', n: 2 },
                { sel: '.field-row', text: 'Tên đăng nhập', n: 3 },
                { sel: '.field-row', text: 'Địa bàn phụ trách', n: 4 },
                { sel: '.section-header', text: 'Thông tin cá nhân', n: 5, pad: 9 },
                { sel: '.action-bar .btn-primary', n: 6 },
                { sel: '.action-bar .btn-danger-outline', n: 7 }
            ],
            clip: '.page-container'
        },

        // ===== 5. Gửi thông tin tài khoản =====
        {
            file: '05-gui-tai-khoan.png',
            user: 'admin',
            url: view(22),
            // Mở hộp xác nhận -- KHÔNG bấm Xác nhận gửi.
            before: async (page) => { await openModal(page, 'button[data-bs-target="#sendAccountModal"]'); },
            marks: [
                { sel: '.modal.show .modal-body', n: 1 },
                { sel: '.modal.show button[type=submit]', n: 2 }
            ],
            clip: '.modal.show .modal-content'
        },

        // ===== 6. Sửa thông tin =====
        {
            file: '06-sua.png',
            user: 'admin',
            url: edit(22),
            marks: [
                { sel: '#username', n: 1 },
                { sel: '#department', n: 2 },
                { sel: '#roleId', n: 3 },
                { sel: '.emp-prov-toggle', n: 4 },
                { sel: '#personalEmail', n: 5 },
                { sel: '.action-bar .btn-primary', n: 6 }
            ],
            clip: '.page-container'
        },

        // ===== 7. Khoá / mở khoá =====
        {
            file: '07-khoa.png',
            user: 'admin',
            url: view(22),
            // Mở hộp xác nhận khoá -- KHÔNG bấm Xác nhận.
            before: async (page) => { await openModal(page, '.action-bar .btn-danger-outline'); },
            marks: [
                { sel: '.modal.show .modal-body', n: 1 },
                { sel: '.modal.show button[type=submit]', n: 2 }
            ],
            clip: '.modal.show .modal-content'
        },
        {
            file: '08-mo-khoa.png',
            user: 'admin',
            url: view(24),
            marks: [
                { sel: '.profile-banner .status-inactive', n: 1 },
                { sel: '.action-bar .btn-success-outline', n: 2 }
            ],
            clip: '.page-container'
        }
    ]
};

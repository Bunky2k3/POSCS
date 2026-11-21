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
 * Dữ liệu mẫu: bộ Task 10.6 (db/schema.sql, V46), 10 tài khoản nên danh sách
 * chỉ có một trang. Người tạo sau cùng đứng đầu; cả bộ gieo cùng một lúc nên
 * thứ tự là user_id giảm dần: dangvantung (id 10) đứng đầu.
 *   - leminhhoang (id 3, Lê Minh Hoàng, Sales, Trưởng phòng Kinh doanh) cầm
 *     4 tỉnh: Hà Nội, Bắc Ninh, Hưng Yên, Ninh Bình. Dùng cho ảnh chi tiết,
 *     sửa, địa bàn, gửi tài khoản, khoá (khoá chỉ mở hộp xác nhận).
 *   - dangvantung (id 10, Sales đã nghỉ) đang bị khoá, không cầm tỉnh nào.
 *   - 15/18 tỉnh địa bàn chi nhánh đã có người cầm (4 Sales); Hà Tĩnh, Nghệ An,
 *     Thanh Hóa chưa ai cầm (id 16, 23, 32).
 * Không cần chuẩn bị gì thêm trên bản sao CSDL.
 */
const view = (id) => '/employee?action=view&id=' + id;
const edit = (id) => '/employee?action=edit&id=' + id;

const LE_MINH_HOANG = 3;   // Sales đang hoạt động, cầm 4 tỉnh
const DANG_VAN_TUNG = 10;  // Sales đã nghỉ, tài khoản bị khoá
const THANH_HOA = 32;      // tỉnh chưa ai cầm

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
            // Thẻ đầu tiên là dangvantung (đang bị khoá, số 6), nên số 5 khoanh
            // một thẻ khác chứ không lấy thẻ đầu.
            marks: [
                { sel: '#searchInput', n: 1 },
                { sel: '#filterRole', n: 2 },
                { sel: '#filterStatus', n: 3 },
                { sel: '.header-actions .btn-add', n: 4 },
                { sel: '.employee-grid .employee-card', text: 'leminhhoang', n: 5 },
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
            // Tích sẵn Thanh Hóa (tỉnh chưa ai cầm) để ô địa bàn hiện "1 tỉnh".
            before: async (page) => {
                await page.select('#department', 'Kinh doanh');
                await page.select('#roleId', 'Sales');
                await pickDate(page, 'hireDate', '2026-11-23');
                await page.eval(`(() => {
                    const b = document.querySelector('#empProvincePanel input[name="provinceIds"][value="${THANH_HOA}"]');
                    b.checked = true;
                    b.dispatchEvent(new Event('change', { bubbles: true }));
                })()`);
                await page.type('#lastName', 'Trần');
                await page.type('#middleName', 'Minh');
                await page.type('#firstName', 'Khoa');
                await page.type('#personalEmail', 'tranminhkhoa@example.com');
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
            url: edit(LE_MINH_HOANG),
            before: async (page) => {
                await page.click('[data-prov-pop] [data-popover-toggle]');
            },
            // Thứ tự dòng: Hà Nội (của người này), Hải Phòng, Bắc Ninh (của
            // người này), Cao Bằng, Điện Biên, Hà Tĩnh (trống)... Khoanh cách
            // dòng (1, 4, 6): hai khung ở hai dòng liền nhau dính vào nhau.
            marks: [
                { sel: '#empProvincePanel label.prov-row.mine', n: 1 },
                { sel: '#empProvincePanel label.prov-row', text: 'Cao Bằng', n: 2 },
                { sel: '#empProvincePanel label.prov-row', text: 'Hà Tĩnh', n: 3 },
                { sel: '#empProvinceClear', n: 4 },
                { sel: '.emp-prov-toggle', n: 5 }
            ],
            // Lề trên rộng để lấy trọn nhãn "Địa bàn phụ trách" phía trên ô.
            clip: ['.emp-prov-pop', '#empProvincePanel'],
            margin: 44
        },

        // ===== 4. Xem chi tiết =====
        {
            file: '04-chi-tiet.png',
            user: 'admin',
            url: view(LE_MINH_HOANG),
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
            url: view(LE_MINH_HOANG),
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
            url: edit(LE_MINH_HOANG),
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
            url: view(LE_MINH_HOANG),
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
            url: view(DANG_VAN_TUNG),
            marks: [
                { sel: '.profile-banner .status-inactive', n: 1 },
                { sel: '.action-bar .btn-success-outline', n: 2 }
            ],
            clip: '.page-container'
        }
    ]
};

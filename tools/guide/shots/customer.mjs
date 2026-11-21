/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phân hệ Khách hàng.
 * Chạy: node tools/guide/capture.mjs customer   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/customer.jsp -- sửa bên này thì soát lại bên kia.
 *
 * Dữ liệu mẫu: bộ của Task 10.6 (db/schema.sql). phamthutrang là Sales giữ
 * Hải Phòng, Quảng Ninh, Lạng Sơn, Cao Bằng; KH-0002 (id 2, Công ty TNHH Hạ
 * tầng mạng Duyên Hải, Hải Phòng) đứng tên chị, có người liên hệ, một hợp đồng
 * đã ký đang hiệu lực (03/2026/HĐKT/POT-DHN), một hợp đồng nháp chưa có mã
 * (hiện "Chưa có số") và phiếu TK-0001 -- nên ảnh "xoá bị chặn" chạy thật mà
 * KHÔNG xoá gì. Ba khách còn lại thuộc Sales khác: ở "Toàn chi nhánh" chúng
 * hiện dấu khoá. NCC-001 (id 5) là nhà cung cấp duy nhất, do leminhhoang phụ
 * trách -- Sales chỉ sửa được nhà cung cấp mình phụ trách, nên ảnh trang Sửa
 * nhà cung cấp đăng nhập bằng leminhhoang.
 *
 * CHUẨN BỊ BẢN SAO CSDL (chạy MỘT lần trên CSDL dựng từ db/schema.sql, trước
 * khi chụp). Bộ mẫu cố ý không có lần đánh giá xếp hạng nào; ảnh trang chi
 * tiết cần huy hiệu xếp hạng và bảng Lịch sử đánh giá, nên ghi hai lần đánh giá
 * cho KH-0002 đúng như app ghi khi bấm "Lưu đánh giá":
 *
 *   INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating,
 *       is_auto_generated, description, event_date, recorded_by) VALUES
 *    (2, 'Đánh giá xếp hạng', 'Tốt', 0, 'Ký hợp đồng thi công tuyến cáp quang, phối hợp khảo sát tốt.', '2026-05-04', 4),
 *    (2, 'Đánh giá xếp hạng', 'Cần theo dõi', 0, 'Quá hạn thanh toán sau nghiệm thu (hạn 09/10), đã gọi nhắc kế toán bên khách.', '2026-10-20', 4);
 *   UPDATE enterprises SET current_relationship_rating = 'Cần theo dõi' WHERE enterprise_id = 2;
 */
const DETAIL = '/customer?action=view&id=2&kind=buyer';

export default {
    // Rộng 1600 để bảng danh sách hiện đủ cột Phụ trách chính và Thao tác.
    viewport: { width: 1600, height: 900 },
    shots: [
        {
            // Phạm vi "Toàn chi nhánh" (view=all) chứ không phải mặc định "Của
            // tôi": phamthutrang chỉ có một khách, xem toàn chi nhánh thì thấy
            // cả dòng sửa/xoá được lẫn dòng khoá của Sales khác.
            file: '01-danh-sach.png',
            user: 'phamthutrang',
            url: '/customer?kind=buyer&view=all',
            viewport: { width: 1920, height: 900 },
            // Bảng rộng hơn khung thì cuộn ngang (có chủ ý, xem listcustomer.jsp):
            // kéo hết sang phải để cột Thao tác (số 7) lộ ra như khi người dùng kéo.
            before: async (page) => {
                await page.eval("document.querySelector('.table-responsive').scrollLeft = 100000");
                await page.settle(200);
            },
            marks: [
                { sel: '#searchInput', n: 1 },
                { sel: '#filterScope', n: 2 },
                { sel: '#filterType', n: 3 },
                { sel: '[data-prov-pop] .pop-btn', n: 4 },
                { sel: '.header-actions .btn-outline-action', n: 5 },
                { sel: '.header-actions .btn-add', n: 6 },
                { within: { sel: 'table tbody tr', text: 'Duyên Hải' }, sel: '.action-icons', n: 7 },
                { within: { sel: 'table tbody tr', text: 'Tây Bắc' }, sel: '.action-icons', n: 7 }
            ],
            // Chỉ phần nội dung trang: cả khung 1920px thu vào trang hướng dẫn thì
            // chữ trong ảnh nhỏ quá không đọc nổi.
            clip: '.page-container'
        },
        {
            file: '02-loc-tinh.png',
            user: 'phamthutrang',
            url: '/customer?kind=buyer',
            before: async (page) => {
                await page.click('[data-prov-pop] [data-popover-toggle]');
                await page.click('[data-prov-mine]');
            },
            marks: [
                { sel: '#provPanel .prov-row.mine', n: 1 },
                { sel: '[data-prov-all]', n: 2 },
                { sel: '[data-prov-none]', n: 2 },
                { sel: '[data-prov-mine]', n: 3 },
                { sel: '.prov-apply', n: 4 }
            ],
            clip: ['.filter-bar', '#provPanel']
        },
        {
            file: '03-them-khach-hang.png',
            user: 'phamthutrang',
            url: '/customer?action=new&kind=buyer',
            marks: [
                { sel: '.logo-avatar-wrap', n: 1 },
                { sel: '#customerName', n: 2 },
                { sel: '#taxCode', n: 2 },
                { sel: '#customerType', n: 3 },
                { sel: '#customerGroup', n: 3 },
                { sel: '#assignee', n: 4 },
                { sel: '#supportAssignee', n: 5 },
                { sel: '#phone', n: 6 },
                { sel: '#email', n: 6 },
                { sel: '#province', n: 7 },
                { sel: '#district', n: 7 },
                { sel: '#addressDetail', n: 7 },
                { sel: '.action-bar .btn-primary', n: 8 }
            ],
            clip: '.page-container'
        },
        {
            file: '04-them-loi.png',
            user: 'phamthutrang',
            url: '/customer?action=new&kind=buyer',
            // Bấm Tạo khi form trống: validateForm() chặn ngay trên trang và
            // hiện chữ đỏ dưới từng ô -- không có request nào đi lên server.
            before: async (page) => {
                await page.click('.action-bar .btn-primary');
            },
            clip: '.card-box'
        },
        {
            file: '05-them-nha-cung-cap.png',
            user: 'phamthutrang',
            url: '/customer?action=new&kind=supplier',
            marks: [
                { sel: '#customerType', n: 1 },
                { sel: '#assignee', n: 2 },
                { sel: '#province', n: 3 },
                { sel: '.action-bar .btn-primary', n: 4 }
            ],
            clip: '.page-container'
        },
        {
            file: '06-chi-tiet.png',
            user: 'phamthutrang',
            url: DETAIL,
            // Trang không nhớ tab, nhưng cứ bấm tab Hợp đồng cho chắc: ảnh cần
            // dòng "Chưa có số" của hợp đồng nháp.
            before: async (page) => {
                await page.click('button[data-bs-target="#tab-contracts"]');
            },
            marks: [
                { sel: '.detail-header .rating-badge', n: 1 },
                { sel: '.header-actions button[onclick="openEvaluateModal()"]', n: 2 },
                { sel: '.header-actions a.btn-edit-detail', n: 3 },
                { sel: '.header-actions .btn-delete-detail', n: 4 },
                { sel: '.section-header', text: 'Thông tin doanh nghiệp', n: 5, pad: 9 },
                { sel: '.section-header', text: 'Người liên hệ', n: 6, pad: 9 },
                { sel: '.section-header', text: 'Lịch sử đánh giá', n: 7, pad: 9 },
                { sel: 'button[data-bs-target="#tab-contracts"]', n: 8 },
                { sel: 'button[data-bs-target="#tab-tickets"]', n: 8 }
            ],
            clip: '.page-container'
        },
        {
            file: '07-danh-gia.png',
            user: 'phamthutrang',
            url: DETAIL,
            before: async (page) => {
                await page.click('.header-actions button[onclick="openEvaluateModal()"]');
                await page.settle(500); // hiệu ứng mở modal của Bootstrap
                await page.select('#ratingSelect', 'GOOD');
                await page.type('#reasonInput', 'Khách đã thanh toán đủ sau nghiệm thu, phối hợp tốt.');
            },
            marks: [
                { sel: '#ratingSelect', n: 1 },
                { sel: '#reasonInput', n: 2 },
                { sel: '.modal.show .btn-modal-primary', n: 3 }
            ],
            clip: '.modal.show .modal-content'
        },
        {
            file: '08-sua.png',
            user: 'phamthutrang',
            url: '/customer?action=edit&id=2&kind=buyer',
            marks: [
                { sel: '.page-header-row p', n: 1 },
                { sel: '.role-check-group', n: 2 },
                { sel: '#assignee', n: 3 },
                { sel: '#province', n: 4 },
                { sel: '#supportAssignee', n: 5 },
                { sel: '.action-bar .btn-primary', n: 6 }
            ],
            clip: '.page-container'
        },
        {
            // Trang Sửa nhà cung cấp: cùng khung, khác chữ. Khoanh không số --
            // các bước đã đánh số ở Hình 8.
            file: '08b-sua-nha-cung-cap.png',
            user: 'leminhhoang',
            url: '/customer?action=edit&id=5&kind=supplier',
            marks: [
                { sel: '.page-header-row h2', n: null },
                { sel: '.role-check-group', n: null },
                { sel: '#assignee', n: null }
            ],
            clip: '.page-container'
        },
        {
            file: '09-xoa.png',
            user: 'phamthutrang',
            url: DETAIL,
            before: async (page) => {
                await page.click('.header-actions .btn-delete-detail');
                await page.settle(500);
            },
            marks: [
                { sel: '.header-actions .btn-delete-detail', n: 1 },
                { sel: '#confirmDeleteBtn', n: 2 }
            ]
        },
        {
            file: '10-xoa-bi-chan.png',
            user: 'phamthutrang',
            url: DETAIL,
            // Xoá THẬT: KH-0002 còn hợp đồng 03/2026/HĐKT/POT-DHN đã ký, đang
            // trong thời hạn, nên server từ chối (BR-34) và quay lại trang chi
            // tiết kèm thông báo -- không xoá gì. Đổi khách thì phải chắc khách
            // mới cũng còn hợp đồng như vậy, nếu không ảnh này xoá thật.
            before: async (page) => {
                await page.click('.header-actions .btn-delete-detail');
                await page.settle(500);
                await page.clickAndWait('#confirmDeleteBtn');
                await page.waitFor('.toast-msg.blocked');
            },
            marks: [
                { sel: '.toast-msg.blocked', n: null }
            ]
        }
    ]
};

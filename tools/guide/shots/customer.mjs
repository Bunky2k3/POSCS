/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phân hệ Khách hàng.
 * Chạy: node tools/guide/capture.mjs customer   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/customer.jsp -- sửa bên này thì soát lại bên kia.
 *
 * Dữ liệu mẫu (bản sao poscs_db): sales4 phụ trách 5 tỉnh (Hà Nội, Bắc Ninh,
 * Lào Cai, Phú Thọ, Quảng Ninh); KH-0001 (id 1, Sông Hồng) đứng tên sales4,
 * có người liên hệ, hợp đồng đang hiệu lực, phiếu hỗ trợ và lịch sử đánh giá
 * -- nên ảnh "xoá bị chặn" chạy thật mà KHÔNG xoá gì.
 */
const DETAIL = '/customer?action=view&id=1&kind=buyer';

export default {
    // Rộng 1600 để bảng danh sách hiện đủ cột Phụ trách chính và Thao tác.
    viewport: { width: 1600, height: 900 },
    shots: [
        {
            file: '01-danh-sach.png',
            user: 'sales4',
            url: '/customer?kind=buyer',
            viewport: { width: 1920, height: 900 },
            // Bảng rộng hơn khung nên cuộn ngang (có chủ ý, xem listcustomer.jsp):
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
                { sel: 'table tbody tr:first-child td:last-child', n: 7 }
            ],
            // Chỉ phần nội dung trang: cả khung 1920px thu vào trang hướng dẫn thì
            // chữ trong ảnh nhỏ quá không đọc nổi.
            clip: '.page-container'
        },
        {
            file: '02-loc-tinh.png',
            user: 'sales4',
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
            user: 'sales4',
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
            user: 'sales4',
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
            user: 'sales4',
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
            user: 'sales4',
            url: DETAIL,
            marks: [
                { sel: '.detail-header .rating-badge', n: 1 },
                { sel: '.header-actions button[onclick="openEvaluateModal()"]', n: 2 },
                { sel: '.header-actions a.btn-edit-detail', n: 3 },
                { sel: '.header-actions .btn-delete-detail', n: 4 },
                { sel: '.section-header', text: 'Thông tin doanh nghiệp', n: 5 },
                { sel: '.section-header', text: 'Người liên hệ', n: 6 },
                { sel: '.section-header', text: 'Lịch sử đánh giá', n: 7 },
                { sel: 'button[data-bs-target="#tab-contracts"]', n: 8 },
                { sel: 'button[data-bs-target="#tab-tickets"]', n: 8 }
            ],
            clip: '.page-container'
        },
        {
            file: '07-danh-gia.png',
            user: 'sales4',
            url: DETAIL,
            before: async (page) => {
                await page.click('.header-actions button[onclick="openEvaluateModal()"]');
                await page.settle(500); // hiệu ứng mở modal của Bootstrap
                await page.select('#ratingSelect', 'GOOD');
                await page.type('#reasonInput', 'Khách thanh toán đúng hạn, phản hồi tích cực.');
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
            user: 'sales4',
            url: '/customer?action=edit&id=1',
            marks: [
                { sel: '.page-header-row p', n: 1 },
                { sel: '.role-check-group', n: 2 },
                { sel: '#assignee', n: 3 },
                { sel: '#supportAssignee', n: 4 },
                { sel: '.action-bar .btn-primary', n: 5 }
            ],
            clip: '.page-container'
        },
        {
            file: '09-xoa.png',
            user: 'sales4',
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
            user: 'sales4',
            url: DETAIL,
            // Xoá THẬT: KH-0001 còn hợp đồng đang hiệu lực nên server từ chối
            // (BR-34) và quay lại trang chi tiết kèm thông báo -- không xoá gì.
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

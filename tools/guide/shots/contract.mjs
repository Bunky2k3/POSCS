/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phân hệ Hợp đồng.
 * Chạy: node tools/guide/capture.mjs contract   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/contract.jsp -- sửa bên này thì soát lại bên kia. Tên ảnh
 * đánh số theo thứ tự xuất hiện trong trang hướng dẫn; ảnh thêm sau mang hậu
 * tố chữ (13b, 13c...) để khỏi đổi tên các ảnh có sẵn.
 *
 * KHÔNG ảnh nào ghi dữ liệu: các hộp xác nhận / hộp nhập (Ký, Thanh lý, Huỷ bản
 * ghi, Lập kỳ, Đã nộp hồ sơ, Đã thu, Thêm tờ) chỉ mở ra, điền ví dụ rồi chụp,
 * không bấm nút gửi; form cũng chỉ điền ví dụ.
 *
 * Dữ liệu mẫu: CSDL dựng từ db/schema.sql (Task 10.6, V46). Id hợp đồng là id
 * do schema.sql sinh ra theo thứ tự gieo:
 *   1  02/2026/HĐMB/POT-HTN  leminhhoang, Đã ký, đã bàn giao, kỳ Tạm ứng 80% đã
 *      thu + kỳ Thanh toán 20% đã nộp hồ sơ (Chờ thu), 1 phụ lục (id 16), nối
 *      với hợp đồng mua 2.
 *   3  04/2026/HĐMB/POT-TDT  doducanh, Đã thanh lý, đủ giấy tờ.
 *   4  03/2026/HĐKT/POT-DHN  dangvantung (đã khoá), một kỳ Quá hạn.
 *   5  05/2026/HĐMB/POT-HTN  leminhhoang, Đã ký, đang chờ phòng Tổng hợp xác
 *      nhận, một kỳ Thanh toán 30 ngày chưa nộp hồ sơ.
 *   6  (chưa có số)           phamthutrang, Nháp "Đã giao – chưa ký": Phiếu
 *      GQNCKH, Phiếu ĐCNB, Biên bản giao nhận ngày CURDATE() - 16.
 *   7  (chưa có số)           vuthihang, Nháp "Chuẩn bị hàng", có hàng hoá.
 *   8  (chưa có số)           doducanh, Nháp vừa mở hồ sơ: chưa giấy, chưa giá
 *      trị, chưa thời hạn.
 * Phòng Tổng hợp: hoangthimai (xác nhận bàn giao), buivannam (ghi nhận đã thu).
 * Dữ liệu mẫu không có phụ lục làm đổi giá trị nên không có ảnh "Giá trị hiện
 * hành" (mục 10.2 chỉ tả bằng chữ).
 *
 * CHUẨN BỊ BẢN SAO CSDL trước khi chụp (KHÔNG làm trên poscs_db): khai Giám
 * đốc chi nhánh ở trang Thông tin chi nhánh (/branch, Task 8.3) cho khớp người
 * ký trên các hợp đồng mẫu đã ký:
 *   UPDATE branch_info SET director_name = 'Nguyễn Văn Thành', phone = '0981600001',
 *          fax = '02437659999', updated_by = (SELECT user_id FROM users WHERE username = 'admin')
 *    WHERE branch_info_id = 1;
 * Chưa khai thì form tạo/sửa hiện dải MSG-060 và bốn ô người ký bên mình để
 * trống (BR-56), ảnh 04, 07, 19 không còn đúng với hướng dẫn. Người phụ trách
 * (BR-35) chỉ là ô chọn #owner khi Admin tạo hợp đồng gốc hoặc thay người đã bị
 * khoá tài khoản; còn lại là ô chỉ đọc không có id.
 *
 * Trang hợp đồng NHỚ TAB cuối cùng (sessionStorage), nên ảnh nào cần tab nào
 * thì tự bấm tab đó -- đừng dựa vào tab mặc định.
 */
const view = (id) => '/contract?action=view&id=' + id;
const edit = (id) => '/contract?action=edit&id=' + id;
const tab = (pane) => '#contractTabs [data-bs-target="#' + pane + '"]';
const paper = (type) => ({ sel: '#pane-tai-lieu .paper-item', text: type });

// Mở một tab rồi chờ hiệu ứng mờ dần của Bootstrap chạy xong.
async function openTab(page, pane) {
    await page.click(tab(pane));
    await page.settle(300);
}

// Mở hộp xác nhận (modal) rồi chờ nó hiện hẳn.
async function openModal(page, buttonSelector) {
    await page.click(buttonSelector);
    await page.waitFor('.modal.show');
    await page.settle(500);
}

// Ngày yyyy-MM-dd cách hôm nay `days` ngày (âm là trước hôm nay), theo giờ máy
// trình duyệt -- ô ngày trên trang chặn ngày sau hôm nay.
const isoDaysAgo = (days) => `(() => { const d = new Date(Date.now() - ${days} * 864e5);
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); })()`;

export default {
    viewport: { width: 1600, height: 900 },
    shots: [
        // ===== 2. Xem và tìm hợp đồng =====
        {
            file: '01-danh-sach.png',
            user: 'leminhhoang',
            url: '/contract?kind=sell',
            viewport: { width: 1920, height: 900 },
            marks: [
                { sel: '.status-strip', n: 1 },
                { sel: '#searchInput', n: 2 },
                { sel: '#filterView', n: 3 },
                { sel: '#filterProgress', n: 4 },
                { sel: 'label[for=filterScope]', n: 5 },
                { sel: 'label[for=filterWaitingAny]', n: 5 },
                { sel: '#toggleAdvanced', n: 6 },
                { sel: '.filter-bar .scope-note', n: 7 },
                { sel: '.header-actions a[href*="exportExcel"]', n: 8 },
                { sel: '.header-actions .btn-add', n: 9 }
            ],
            // Phần đầu trang tới hết thanh lọc; bảng có ảnh riêng (03). Lề rộng
            // hơn mặc định: nút Tạo hợp đồng nằm sát mép phải của hàng tiêu đề.
            clip: ['.page-header-row', '.filter-bar'],
            margin: 28
        },
        {
            file: '02-loc-them.png',
            user: 'leminhhoang',
            url: '/contract?kind=sell',
            before: async (page) => {
                await page.click('#toggleAdvanced');
                await page.click('[data-period-pop] [data-popover-toggle]');
            },
            marks: [
                { sel: '#filterType', n: 1 },
                { sel: '[data-prov-pop] .pop-btn', n: 2 },
                { sel: '[data-period-pop] .pop-btn', n: 3 },
                { sel: '#filterWaitingDept', n: 4 },
                { sel: '#filterDelivery', n: 5 }
            ],
            clip: ['.filter-bar', '#periodPanel']
        },
        {
            file: '03-doc-dong.png',
            user: 'leminhhoang',
            // Toàn chi nhánh, mọi thời điểm: trang 1 có đủ phụ lục, ba bản nháp
            // (hai bước giao hàng) và hợp đồng đang chờ phòng Tổng hợp.
            url: '/contract?kind=sell&view=all&month=all',
            viewport: { width: 1920, height: 1000 },
            before: async (page) => {
                await page.eval("document.querySelector('.table-responsive').scrollLeft = 100000");
                await page.settle(200);
            },
            // 3 và 4 khoanh CÙNG MỘT dòng (hợp đồng 6): bản nháp Chưa hiệu lực
            // mà hàng đã tới khách -- nhãn giao hàng nằm dưới tiến độ.
            marks: [
                { sel: '#contractTableBody .pl-tag', n: 1 },
                { sel: '#contractTableBody td.term-cell span', text: 'chưa ký', n: 2 },
                { within: { sel: '#contractTableBody tr', text: 'Lê Chân' }, sel: '.status-pill', n: 3 },
                { within: { sel: '#contractTableBody tr', text: 'Lê Chân' }, sel: 'td:nth-child(6)', n: 4 },
                { within: { sel: '#contractTableBody tr', text: '12 trạm' }, sel: 'td:nth-child(6) > div', n: 5 },
                { sel: '#contractTableBody tr:first-child .action-icons', n: 6 }
            ],
            clip: '.table-card'
        },

        // ===== 3. Tạo hợp đồng =====
        {
            file: '04-tao-hop-dong.png',
            user: 'leminhhoang',
            url: '/contract?action=new&kind=sell',
            marks: [
                { sel: '#contractCode', n: 1 },
                { sel: '#title', n: 2 },
                { sel: '#customer', n: 3 },
                // Sales tạo: ô Người phụ trách chỉ đọc, ghi tên chính mình (BR-35).
                { within: { sel: '#createContractForm .field-row', text: 'Người phụ trách' }, sel: 'input', n: 4 },
                { sel: '#contractType', n: 5 },
                { sel: '#createContractForm .col-12', text: 'Thông tin ký kết', n: 6 },
                { sel: '#contractValue', n: 7 },
                { sel: '.action-bar .btn-primary', n: 8 }
            ],
            clip: '.page-container'
        },

        // ===== 4. Xem chi tiết =====
        {
            file: '05-chi-tiet.png',
            user: 'phamthutrang',
            url: view(6),
            before: async (page) => { await openTab(page, 'pane-ky-thu'); },
            marks: [
                { sel: '.detail-header .status-pill', n: 1 },
                { sel: '.detail-header .progress-pill', n: 1 },
                { sel: '.detail-header .delivery-pill', n: 1 },
                { sel: '.header-actions a[href*="action=edit"]', n: 2 },
                { sel: '.lifecycle-card', n: 3 },
                { sel: '.info-card:not(.lifecycle-card)', n: 4 },
                { sel: '#contractTabs', n: 5 }
            ],
            clip: '.page-container'
        },
        {
            file: '06-nhat-ky.png',
            user: 'doducanh',
            // Hợp đồng 3 đi đủ vòng đời tới thanh lý; bước thanh lý có căn cứ (số 3).
            url: view(3),
            // Nhật ký đủ vòng đời dài gần 2000px: chỉ lấy 8 dòng mới nhất (có mốc
            // Thanh lý). Gắn id tạm cho dòng thứ 8 làm mép dưới vùng chụp.
            before: async (page) => {
                await openTab(page, 'pane-nhat-ky');
                await page.eval("document.querySelectorAll('#pane-nhat-ky .tl-item')[7].id = '__lastShown'");
            },
            marks: [
                { sel: '#pane-nhat-ky .tl-item.milestone', n: 1 },
                { sel: '#pane-nhat-ky .tl-item:not(.milestone)', n: 2 },
                { sel: '#pane-nhat-ky .tl-note', n: 3 }
            ],
            clip: ['#contractTabs', '#__lastShown'],
            // Lề hẹp: dòng thứ 9 bắt đầu ngay dưới, lề mặc định lộ nửa dòng đó.
            margin: 6
        },

        // ===== 5. Trang Quản lý =====
        {
            file: '07-quan-ly.png',
            user: 'doducanh',
            url: edit(8),
            before: async (page) => { await openTab(page, 'pane-tien-trinh'); },
            marks: [
                { sel: '#createContractForm .section-header', text: 'Thông tin chung', n: 1 },
                { sel: '#contractValue', n: 2 },
                { sel: '#effectiveDate', n: 3 },
                { sel: '#endDate', n: 3 },
                { sel: '#signDate', n: 4 },
                { sel: '#createContractForm .action-bar .btn-primary', n: 5 },
                { sel: '#contractTabs', n: 6 }
            ],
            clip: '.page-container'
        },
        {
            file: '08-hang-hoa.png',
            user: 'vuthihang',
            url: edit(7),
            // Điền sẵn một dòng ví dụ -- KHÔNG bấm Thêm.
            before: async (page) => {
                await openTab(page, 'pane-hang-hoa');
                // Một món hợp với hợp đồng tủ nguồn; không có thì lấy món đầu tiên.
                await page.eval(`(() => {
                    const s = document.querySelector('#pane-hang-hoa select[name=productId]');
                    const i = Array.from(s.options).findIndex((o) => /ắc quy/i.test(o.textContent));
                    s.selectedIndex = i > 0 ? i : 1;
                })()`);
                await page.type('#pane-hang-hoa input[name=quantity]', '8');
                await page.type('#pane-hang-hoa input[name=unit]', 'Bộ');
                await page.type('#pane-hang-hoa input[name=notes]', 'Giao đợt 2');
            },
            marks: [
                { sel: '#pane-hang-hoa select[name=productId]', n: 1 },
                { sel: '#pane-hang-hoa input[name=quantity]', n: 2 },
                { sel: '#pane-hang-hoa input[name=unit]', n: 3 },
                { sel: '#pane-hang-hoa input[name=notes]', n: 4 },
                { sel: '#pane-hang-hoa .btn-add-item', n: 5 },
                { sel: '#pane-hang-hoa .btn-remove-item', n: 6 }
            ],
            clip: ['#contractTabs', '#pane-hang-hoa']
        },

        // ===== 6. Ký =====
        {
            file: '09-buoc-tien-trinh.png',
            user: 'doducanh',
            url: edit(8),
            before: async (page) => { await openTab(page, 'pane-tien-trinh'); },
            marks: [
                { sel: '#pane-tien-trinh .btn-step.primary', n: 1 },
                { sel: '#pane-tien-trinh .btn-step.danger', n: 2 },
                { sel: '#pane-tien-trinh .locked-note', text: 'chưa có mã', n: 3 },
                { sel: '#pane-tien-trinh .locked-note', text: 'thiếu ngày', n: 4 }
            ],
            clip: ['#contractTabs', '#pane-tien-trinh']
        },
        {
            file: '10-ky.png',
            user: 'phamthutrang',
            url: edit(6),
            before: async (page) => {
                await openTab(page, 'pane-tien-trinh');
                await openModal(page, '#pane-tien-trinh .btn-step.primary');
            },
            marks: [
                { sel: '#askOk', n: 1 }
            ],
            clip: '.modal.show .modal-content'
        },
        {
            file: '11-da-ky.png',
            user: 'admin',
            url: edit(5),
            // Đã ký: mọi ô khoá, kể cả người phụ trách (BR-35); nút Lưu thay đổi
            // ẩn tới khi bật chữa sai sót, nên ảnh này không khoanh nút đó.
            marks: [
                { sel: '#createContractForm > div', n: 1 },
                { sel: '#contractCode', n: 2 },
                { sel: '#toggleCorrection', n: 3 }
            ],
            // Thẻ đầu tiên của trang = thẻ chứa form sửa (hợp đồng chưa đóng băng).
            clip: '.page-container .card-box'
        },
        {
            file: '12-chua-sai-sot.png',
            user: 'admin',
            url: edit(5),
            // Chỉ bật chế độ chữa sai sót và gõ ví dụ -- KHÔNG bấm Lưu.
            before: async (page) => {
                await page.click('#toggleCorrection');
                await page.type('#correctionReason', 'Gõ nhầm chức vụ người ký bên đối tác, đối chiếu bản hợp đồng giấy đã ký');
            },
            marks: [
                { sel: '#correctionReason', n: 1 },
                { sel: '#createContractForm .action-bar .btn-primary', n: 2 }
            ],
            clip: ['#correctionBox', '#createContractForm .action-bar']
        },

        // ===== 7. Kỳ thanh toán =====
        {
            file: '13-thanh-toan.png',
            user: 'leminhhoang',
            url: edit(1),
            // Cột thao tác rộng cố định nên bốn nút của kỳ chưa thu rớt hai dòng ở
            // mọi khổ màn hình (khung trang có bề rộng tối đa) -- đúng giao diện thật.
            before: async (page) => { await openTab(page, 'pane-ky-thu'); },
            marks: [
                { sel: '#pane-ky-thu thead', n: 1 },
                { sel: '#pane-ky-thu .pay-status.waiting', n: 2 },
                { sel: '#pane-ky-thu .js-edit-payment', n: 3 },
                { sel: '#pane-ky-thu .js-submit-payment', n: 4 },
                { sel: '#pane-ky-thu .js-mark-paid', n: 5 },
                { sel: '#pane-ky-thu button[onclick^="confirmRemovePayment"]', n: 6 },
                { sel: '#pane-ky-thu .js-add-payment', n: 7 }
            ],
            clip: ['#contractTabs', '#pane-ky-thu']
        },
        {
            file: '13b-lap-ky.png',
            user: 'phamthutrang',
            url: edit(6),
            // Lập kỳ tạm ứng 80% bằng ô "%" -- KHÔNG bấm Lưu kỳ.
            before: async (page) => {
                await openTab(page, 'pane-ky-thu');
                await openModal(page, '#pane-ky-thu .js-add-payment');
                await page.select('#payType', 'Tạm ứng');
                await page.type('#payPercent', '80');
                await page.type('#payCondition', 'Tạm ứng 80% sau khi ký hợp đồng và nhận bảo lãnh tạm ứng');
                await page.type('#payDays', '7');
                await page.settle(300);
            },
            marks: [
                { sel: '#payType', n: 1 },
                { sel: '#payPercent', n: 2 },
                { sel: '#payAmount', n: 3 },
                { sel: '#payCondition', n: 4 },
                { sel: '#payDays', n: 5 },
                { sel: '#payDue', n: 6 },
                { sel: '#payPaid', n: 7 },
                { sel: '#paymentForm .btn-modal-primary', n: 8 }
            ],
            clip: '.modal.show .modal-content'
        },
        {
            file: '13c-nop-ho-so.png',
            user: 'leminhhoang',
            // Kỳ duy nhất của hợp đồng 5 có 30 ngày phải trả: hộp hiện hạn tự tính.
            url: edit(5),
            before: async (page) => {
                await openTab(page, 'pane-ky-thu');
                await openModal(page, '#pane-ky-thu .js-submit-payment');
            },
            marks: [
                { sel: '#submitDate', n: 1 },
                { sel: '#submitAutoDue', n: 2 },
                { sel: '#submitForm .btn-modal-primary', n: 3 }
            ],
            clip: '.modal.show .modal-content'
        },
        {
            file: '13d-da-thu.png',
            user: 'leminhhoang',
            // Kỳ Thanh toán 20% (Chờ thu) của hợp đồng 1 -- KHÔNG bấm Ghi nhận.
            url: edit(1),
            before: async (page) => {
                await openTab(page, 'pane-ky-thu');
                await openModal(page, '#pane-ky-thu .js-mark-paid');
            },
            marks: [
                { sel: '#paidDate', n: 1 },
                { sel: '#paidAmount', n: 2 },
                { sel: '#paidForm .btn-modal-primary', n: 3 }
            ],
            clip: '.modal.show .modal-content'
        },
        {
            file: '14-cong-no.png',
            user: 'buivannam',
            // Tài khoản Tổng hợp ở trang chi tiết: thấy nút Ghi nhận đã thu.
            url: view(4),
            before: async (page) => { await openTab(page, 'pane-ky-thu'); },
            marks: [
                { sel: '#pane-ky-thu .row.g-2', n: 1 },
                { sel: '#pane-ky-thu .pay-status.overdue', n: 2 },
                { sel: '#pane-ky-thu .js-mark-paid', n: 3 }
            ],
            clip: ['#contractTabs', '#pane-ky-thu']
        },

        // ===== 8. Bàn giao =====
        {
            file: '15-ban-giao.png',
            user: 'leminhhoang',
            url: edit(5),
            before: async (page) => { await openTab(page, 'pane-ban-giao'); },
            marks: [
                { sel: '#pane-ban-giao table', n: 1 },
                { sel: '#pane-ban-giao form.row > .col-md-5', n: 2 },
                { sel: '#pane-ban-giao input[name=handoverNote]', n: 3 },
                { sel: '#pane-ban-giao form.row button[type=submit]', n: 4 }
            ],
            clip: ['#contractTabs', '#pane-ban-giao']
        },
        {
            file: '16-hang-doi.png',
            user: 'hoangthimai',
            url: '/contract?action=handovers',
            // Rộng 1920 để khung trang căn giữa, không dính mép thanh bên trái.
            viewport: { width: 1920, height: 900 },
            marks: [
                { sel: '#filterView', n: 1 },
                { sel: '.dept-strip a.dept-chip', text: 'Tổng hợp', n: 2 },
                { sel: '.dept-strip div.dept-chip', n: 3 },
                { sel: '.queue-table tbody tr:first-child .waited', n: 4 },
                { sel: '.queue-table tbody tr:first-child form', n: 5 }
            ],
            clip: '.page-container'
        },

        // ===== 9. Giấy tờ =====
        {
            file: '17-tai-lieu.png',
            user: 'phamthutrang',
            url: edit(6),
            before: async (page) => { await openTab(page, 'pane-tai-lieu'); },
            marks: [
                { within: paper('Phiếu GQNCKH'), sel: '.paper-head', n: 1 },
                { within: paper('Phiếu GQNCKH'), sel: '.paper-sheet-main', n: 2 },
                { within: paper('Hợp đồng đã ký'), sel: '.paper-box', n: 3 },
                { within: paper('Biên bản giao nhận'), sel: '.paper-add', n: 4 },
                { within: paper('Biên bản giao nhận'), sel: '.js-void-doc', n: 5 }
            ],
            clip: ['#contractTabs', '#pane-tai-lieu']
        },
        {
            file: '17b-them-to.png',
            user: 'phamthutrang',
            url: edit(6),
            // Thêm tờ biên bản giao nhận đợt 2 -- KHÔNG bấm Lưu tờ này.
            before: async (page) => {
                await openTab(page, 'pane-tai-lieu');
                await page.eval(`Array.from(document.querySelectorAll('#pane-tai-lieu .paper-item'))
                    .find((i) => i.textContent.includes('Biên bản giao nhận')).querySelector('.paper-add').click()`);
                await page.waitFor('.modal.show');
                await page.settle(500);
                await page.eval(`(() => { const el = document.getElementById('paperDate');
                    el.value = ${isoDaysAgo(2)}; el.dispatchEvent(new Event('input', { bubbles: true })); })()`);
                await page.type('#paperNo', '21/BBGN-POT');
                await page.type('#paperTitle', 'Biên bản giao nhận đợt 2');
                await page.type('#paperNote', 'Bản gốc lưu phòng Tổng hợp');
            },
            marks: [
                { sel: '#paperDate', n: 1 },
                { sel: '#paperNo', n: 2 },
                { sel: '#paperTitle', n: 3 },
                { sel: '#paperUrl', n: 4 },
                { sel: '#paperNote', n: 5 },
                { sel: '#paperForm .btn-modal-primary', n: 6 }
            ],
            clip: '.modal.show .modal-content'
        },

        // ===== 10. Phụ lục =====
        {
            file: '18-phu-luc.png',
            user: 'leminhhoang',
            url: edit(1),
            before: async (page) => { await openTab(page, 'pane-phu-luc'); },
            marks: [
                { sel: '#pane-phu-luc table', n: 1 },
                { sel: '#pane-phu-luc a[href*="newAmendment"]', n: 2 }
            ],
            clip: ['#contractTabs', '#pane-phu-luc']
        },
        {
            file: '19-lap-phu-luc.png',
            user: 'leminhhoang',
            url: '/contract?action=newAmendment&parentId=1',
            // Điền ví dụ -- KHÔNG bấm Lập phụ lục.
            before: async (page) => {
                await page.type('#contractCode', '02/2026/HĐMB/POT-HTN/PL02');
                await page.type('#title', 'Phụ lục 02 — bổ sung 4 bộ nguồn dự phòng');
                await page.select('#valueAdjustment', 'increase');
                await page.type('#contractValue', '96000000');
            },
            marks: [
                { sel: '.page-container .card-box', n: 1 },
                { sel: '#contractCode', n: 2 },
                { sel: '#createContractForm input[disabled]', n: 3 },
                { sel: '#valueAdjustment', n: 4 },
                { sel: '#contractValue', n: 5 },
                { sel: '.action-bar .btn-primary', n: 6 }
            ],
            clip: '.page-container'
        },

        // ===== 11. Nối bán – mua =====
        {
            file: '21-noi-ban-mua.png',
            user: 'leminhhoang',
            url: edit(1),
            before: async (page) => { await openTab(page, 'pane-noi-hd'); },
            marks: [
                { sel: '#pane-noi-hd .row.g-2', n: 1 },
                { sel: '#pane-noi-hd form[data-confirm] button', n: 2 },
                { sel: '#pane-noi-hd select[name=otherContractId]', n: 3 },
                { sel: '#pane-noi-hd input[name=linkNote]', n: 4 },
                { sel: '#pane-noi-hd form:not([data-confirm]) button[type=submit]', n: 5 }
            ],
            clip: ['#contractTabs', '#pane-noi-hd']
        },

        // ===== 12. Thanh lý =====
        {
            file: '22-thanh-ly.png',
            user: 'leminhhoang',
            url: edit(1),
            // Mở hộp Thanh lý và gõ căn cứ ví dụ -- KHÔNG bấm Xác nhận.
            before: async (page) => {
                await openTab(page, 'pane-tien-trinh');
                await openModal(page, '#pane-tien-trinh .btn-step:not(.primary):not(.warn):not(.danger)');
                await page.type('#askNote', 'Biên bản thanh lý số 05/BBTL-POT ký ngày 15/12/2026');
            },
            marks: [
                { sel: '#askNote', n: 1 },
                { sel: '#askOk', n: 2 }
            ],
            clip: '.modal.show .modal-content'
        },
        {
            file: '23-da-thanh-ly.png',
            user: 'doducanh',
            url: view(3),
            marks: [
                { sel: '.lifecycle .lc-step:nth-child(3)', n: 1 },
                { sel: '.lifecycle-card .lc-alert', n: 2 }
            ],
            clip: '.lifecycle-card'
        },

        // ===== 13. Huỷ bản ghi =====
        {
            file: '24-huy-ban-ghi.png',
            user: 'doducanh',
            url: edit(8),
            // Mở hộp Huỷ bản ghi và gõ lý do ví dụ -- KHÔNG bấm Huỷ.
            before: async (page) => {
                await openTab(page, 'pane-tien-trinh');
                await openModal(page, '#pane-tien-trinh .btn-step.danger');
                await page.type('#askNote', 'Mở trùng hồ sơ — phiếu GQNCKH này đã có hồ sơ khác');
            },
            marks: [
                { sel: '#askNote', n: 1 },
                { sel: '#askOk', n: 2 }
            ],
            clip: '.modal.show .modal-content'
        }
    ]
};

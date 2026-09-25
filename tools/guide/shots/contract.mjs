/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phân hệ Hợp đồng.
 * Chạy: node tools/guide/capture.mjs contract   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/contract.jsp -- sửa bên này thì soát lại bên kia. Tên ảnh
 * đánh số theo thứ tự xuất hiện trong trang hướng dẫn (Hình 1, Hình 2...).
 *
 * KHÔNG ảnh nào ghi dữ liệu: các hộp xác nhận (Ký, Thanh lý, Huỷ bản ghi) chỉ
 * mở ra rồi chụp, không bấm Xác nhận; form chỉ điền ví dụ, không gửi. Riêng
 * ảnh 26 có gửi file -- chính file mẫu còn trống -- và bị từ chối trước khi
 * ghi (nhập PDF có lỗi thì không ghi gì).
 *
 * Dữ liệu mẫu (bản sao poscs_db, xem V30): sales4 phụ trách 5 tỉnh và đứng tên
 * 05/2026 (id 5), 06/2026 (id 6, hai phụ lục +300tr / -120tr, kỳ đã thu + kỳ
 * chưa thu), 08/2026 (id 8, đã thanh lý, nối hai đơn mua). Các hợp đồng khác
 * dùng làm ví dụ: 07/2026 (id 7, thiếu kỳ thu so với giá trị, một phòng đã
 * xong một phòng đang chờ), 10/2026 (id 10, nháp chưa chốt gì), 11/2026 (id 11,
 * nháp đủ thời hạn), 15/2026 (id 39, đã ký nhưng chưa tới ngày hiệu lực, có
 * bản PDF, chờ ba phòng).
 *
 * Trang hợp đồng NHỚ TAB cuối cùng (sessionStorage), nên ảnh nào cần tab nào
 * thì tự bấm tab đó -- đừng dựa vào tab mặc định.
 */
import { fileURLToPath } from 'node:url';

const TEMPLATE = fileURLToPath(new URL('../../../web/WEB-INF/templates/hopdong_import_template.pdf', import.meta.url));

const view = (id) => '/contract?action=view&id=' + id;
const edit = (id) => '/contract?action=edit&id=' + id;
const tab = (pane) => '#contractTabs [data-bs-target="#' + pane + '"]';

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

export default {
    viewport: { width: 1600, height: 900 },
    shots: [
        // ===== 2. Xem và tìm hợp đồng =====
        {
            file: '01-danh-sach.png',
            user: 'sales4',
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
                { sel: '.header-actions a[href*="importForm"]', n: 9 },
                { sel: '.header-actions .btn-add', n: 10 }
            ],
            // Phần đầu trang tới hết thanh lọc; bảng có ảnh riêng (03). Lề rộng
            // hơn mặc định: nút Tạo hợp đồng nằm sát mép phải của hàng tiêu đề.
            clip: ['.page-header-row', '.filter-bar'],
            margin: 28
        },
        {
            file: '02-loc-them.png',
            user: 'sales4',
            url: '/contract?kind=sell',
            before: async (page) => {
                await page.click('#toggleAdvanced');
                await page.click('[data-period-pop] [data-popover-toggle]');
            },
            marks: [
                { sel: '#filterType', n: 1 },
                { sel: '[data-prov-pop] .pop-btn', n: 2 },
                { sel: '[data-period-pop] .pop-btn', n: 3 },
                { sel: '#filterWaitingDept', n: 4 }
            ],
            clip: ['.filter-bar', '#periodPanel']
        },
        {
            file: '03-doc-dong.png',
            user: 'sales4',
            // Toàn chi nhánh, mọi thời điểm: trang 1 có đủ phụ lục, bản nháp,
            // hợp đồng đang chờ phòng và hợp đồng hết hạn mà chưa thanh lý.
            url: '/contract?kind=sell&view=all&month=all',
            viewport: { width: 1920, height: 1000 },
            before: async (page) => {
                await page.eval("document.querySelector('.table-responsive').scrollLeft = 100000");
                await page.settle(200);
            },
            // 3 và 4 khoanh HAI nhãn của CÙNG MỘT dòng (12/2026): hết hạn theo
            // lịch mà tiến độ vẫn "Đã ký" -- ví dụ của hai trục lệch nhau.
            marks: [
                { sel: '#contractTableBody .pl-tag', n: 1 },
                { sel: '#contractTableBody td.term-cell span', text: 'chưa ký', n: 2 },
                { within: { sel: '#contractTableBody tr', text: 'Bảo trì hệ thống UPS' }, sel: '.status-pill', n: 3 },
                { within: { sel: '#contractTableBody tr', text: 'Bảo trì hệ thống UPS' }, sel: '.progress-pill', n: 4 },
                { sel: '#contractTableBody td:nth-child(6) > div', n: 5 },
                { sel: '#contractTableBody tr:first-child .action-icons', n: 6 }
            ],
            clip: '.table-card'
        },

        // ===== 3. Tạo hợp đồng =====
        {
            file: '04-tao-hop-dong.png',
            user: 'sales4',
            url: '/contract?action=new&kind=sell',
            marks: [
                { sel: '#contractCode', n: 1 },
                { sel: '#title', n: 2 },
                { sel: '#customer', n: 3 },
                { sel: '#owner', n: 4 },
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
            user: 'sales4',
            url: view(39),
            before: async (page) => { await openTab(page, 'pane-ky-thu'); },
            marks: [
                { sel: '.detail-header .status-pill', n: 1 },
                { sel: '.detail-header .progress-pill', n: 1 },
                { sel: '.header-actions a[href^="http"]', n: 2 },
                { sel: '.header-actions a[href*="exportPdf"]', n: 3 },
                { sel: '.header-actions a[href*="action=edit"]', n: 4 },
                { sel: '.lifecycle-card', n: 5 },
                { sel: '.info-card:not(.lifecycle-card)', n: 6 },
                { sel: '#contractTabs', n: 7 }
            ],
            clip: '.page-container'
        },
        {
            file: '06-nhat-ky.png',
            user: 'sales4',
            // 08/2026 (đã thanh lý), KHÔNG phải 15/2026 như ảnh 05: nhật ký của
            // 15/2026 là dữ liệu gõ thử bằng tay (giá trị gõ nhầm 98 tỷ, đơn vị
            // "bộbộ"). Của 08/2026 là dữ liệu gieo theo đúng các dòng ContractDAO
            // tự ghi (V30), đi đủ vòng đời, và bước thanh lý có căn cứ (số 3).
            url: view(8),
            before: async (page) => { await openTab(page, 'pane-nhat-ky'); },
            marks: [
                { sel: '#pane-nhat-ky .tl-item.milestone', n: 1 },
                { sel: '#pane-nhat-ky .tl-item:not(.milestone)', n: 2 },
                { sel: '#pane-nhat-ky .tl-note', n: 3 }
            ],
            clip: ['#contractTabs', '#pane-nhat-ky']
        },

        // ===== 5. Trang Quản lý =====
        {
            file: '07-quan-ly.png',
            user: 'sales4',
            url: edit(10),
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
            user: 'sales4',
            url: edit(11),
            // Điền sẵn một dòng ví dụ -- KHÔNG bấm Thêm.
            before: async (page) => {
                await openTab(page, 'pane-hang-hoa');
                // Một món hợp với hợp đồng cáp quang; không có thì lấy món đầu tiên.
                await page.eval(`(() => {
                    const s = document.querySelector('#pane-hang-hoa select[name=productId]');
                    const i = Array.from(s.options).findIndex((o) => o.textContent.includes('Măng xông'));
                    s.selectedIndex = i > 0 ? i : 1;
                })()`);
                await page.type('#pane-hang-hoa input[name=quantity]', '20');
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
            user: 'sales4',
            url: edit(10),
            before: async (page) => { await openTab(page, 'pane-tien-trinh'); },
            marks: [
                { sel: '#pane-tien-trinh .btn-step.primary', n: 1 },
                { sel: '#pane-tien-trinh .btn-step.danger', n: 2 },
                { sel: '#pane-tien-trinh .locked-note', n: 3 }
            ],
            clip: ['#contractTabs', '#pane-tien-trinh']
        },
        {
            file: '10-ky.png',
            user: 'sales4',
            url: edit(11),
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
            url: edit(6),
            marks: [
                { sel: '#createContractForm > div', n: 1 },
                { sel: '#owner', n: 2 },
                { sel: '#contractCode', n: 3 },
                { sel: '#toggleCorrection', n: 4 },
                { sel: '#createContractForm .action-bar .btn-primary', n: 5 }
            ],
            // Thẻ đầu tiên của trang = thẻ chứa form sửa.
            clip: '.page-container .card-box'
        },
        {
            file: '12-chua-sai-sot.png',
            user: 'admin',
            url: edit(6),
            // Chỉ bật chế độ chữa sai sót và gõ ví dụ -- KHÔNG bấm Lưu.
            before: async (page) => {
                await page.click('#toggleCorrection');
                await page.type('#correctionReason', 'Mã hợp đồng gõ nhầm 60 thành 06, đối chiếu bản giấy ngày 11/04/2026');
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
            user: 'sales4',
            url: edit(6),
            // Điền sẵn một kỳ ví dụ -- KHÔNG bấm Lập kỳ.
            before: async (page) => {
                await openTab(page, 'pane-ky-thu');
                await page.type('#invoiceAmount', '300000000');
                await page.type('#pane-ky-thu input[name=dueDate]', '2026-12-15');
            },
            marks: [
                { sel: '#invoiceAmount', n: 1 },
                { sel: '#pane-ky-thu input[name=dueDate]', n: 2 },
                { sel: '#pane-ky-thu input[name=paidDate]', n: 3 },
                { sel: '#pane-ky-thu .btn-add-item', n: 4 },
                { sel: '#pane-ky-thu button[onclick^="markPaid"]', n: 5 },
                { sel: '#pane-ky-thu button[onclick^="confirmRemovePayment"]', n: 6 }
            ],
            clip: ['#contractTabs', '#pane-ky-thu']
        },
        {
            file: '14-cong-no.png',
            user: 'sales4',
            url: view(7),
            before: async (page) => { await openTab(page, 'pane-ky-thu'); },
            marks: [
                { sel: '#pane-ky-thu .row.g-2', n: 1 },
                { sel: '#pane-ky-thu .lc-alert', n: 2 },
                { sel: '#pane-ky-thu table', n: 3 }
            ],
            clip: ['#contractTabs', '#pane-ky-thu']
        },

        // ===== 8. Bàn giao =====
        {
            file: '15-ban-giao.png',
            user: 'sales4',
            url: edit(7),
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
            user: 'admin',
            url: '/contract?action=handovers',
            // Rộng 1920 để khung trang căn giữa, không dính mép thanh bên trái.
            viewport: { width: 1920, height: 900 },
            marks: [
                { sel: '#filterView', n: 1 },
                { sel: '.dept-strip a.dept-chip', text: 'Kế toán', n: 2 },
                { sel: '.dept-strip div.dept-chip', n: 3 },
                { sel: '.queue-table tbody tr:first-child .waited', n: 4 },
                { sel: '.queue-table tbody tr:first-child form', n: 5 }
            ],
            clip: '.page-container'
        },

        // ===== 9. Tài liệu =====
        {
            file: '17-tai-lieu.png',
            user: 'sales4',
            url: edit(39),
            before: async (page) => { await openTab(page, 'pane-tai-lieu'); },
            marks: [
                { sel: '#pane-tai-lieu select[name=docType]', n: 1 },
                { sel: '#pane-tai-lieu input[name=docTitle]', n: 2 },
                { sel: '#pane-tai-lieu input[name=fileUrl]', n: 3 },
                { sel: '#pane-tai-lieu input[name=docNote]', n: 4 },
                { sel: '#pane-tai-lieu form button[type=submit]', n: 5 },
                { sel: '#pane-tai-lieu a[target=_blank]', n: 6 },
                { sel: '#pane-tai-lieu .js-void-doc', n: 7 }
            ],
            clip: ['#contractTabs', '#pane-tai-lieu']
        },

        // ===== 10. Phụ lục =====
        {
            file: '18-phu-luc.png',
            user: 'sales4',
            url: edit(6),
            before: async (page) => { await openTab(page, 'pane-phu-luc'); },
            marks: [
                { sel: '#pane-phu-luc table', n: 1 },
                { sel: '#pane-phu-luc a[href*="newAmendment"]', n: 2 }
            ],
            clip: ['#contractTabs', '#pane-phu-luc']
        },
        {
            file: '19-lap-phu-luc.png',
            user: 'sales4',
            url: '/contract?action=newAmendment&parentId=6',
            // Điền ví dụ -- KHÔNG bấm Lập phụ lục.
            before: async (page) => {
                await page.type('#contractCode', '06/2026/HĐKT-POSTEF/PL03');
                await page.type('#title', 'Phụ lục 03 — bổ sung 2 km tuyến nhánh Cẩm Phả');
                await page.select('#valueAdjustment', 'increase');
                await page.type('#contractValue', '150000000');
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
        {
            file: '20-gia-tri-hien-hanh.png',
            user: 'sales4',
            url: view(6),
            marks: [
                { sel: '.info-card .field-row', text: 'Giá trị hợp đồng', n: 1 },
                { sel: '.info-card .field-row', text: 'Giá trị hiện hành', n: 2 }
            ],
            clip: '.info-card:not(.lifecycle-card)'
        },

        // ===== 11. Nối bán – mua =====
        {
            file: '21-noi-ban-mua.png',
            user: 'sales4',
            url: edit(5),
            before: async (page) => { await openTab(page, 'pane-noi-hd'); },
            marks: [
                { sel: '#pane-noi-hd .row.g-2', n: 1 },
                { sel: '#pane-noi-hd td > div', text: 'Đơn mua gom', n: 2 },
                { sel: '#pane-noi-hd form[data-confirm] button', n: 3 },
                { sel: '#pane-noi-hd select[name=otherContractId]', n: 4 },
                { sel: '#pane-noi-hd input[name=linkNote]', n: 5 },
                { sel: '#pane-noi-hd form:not([data-confirm]) button[type=submit]', n: 6 }
            ],
            clip: ['#contractTabs', '#pane-noi-hd']
        },

        // ===== 12. Thanh lý =====
        {
            file: '22-thanh-ly.png',
            user: 'sales4',
            url: edit(6),
            // Mở hộp Thanh lý và gõ căn cứ ví dụ -- KHÔNG bấm Xác nhận.
            before: async (page) => {
                await openTab(page, 'pane-tien-trinh');
                await openModal(page, '#pane-tien-trinh .btn-step:not(.primary):not(.warn):not(.danger)');
                await page.type('#askNote', 'Biên bản thanh lý số 07/2026/BBTL ký ngày 24/09/2026');
            },
            marks: [
                { sel: '#askNote', n: 1 },
                { sel: '#askOk', n: 2 }
            ],
            clip: '.modal.show .modal-content'
        },
        {
            file: '23-da-thanh-ly.png',
            user: 'sales4',
            url: view(8),
            marks: [
                { sel: '.lifecycle .lc-step:nth-child(3)', n: 1 },
                { sel: '.lifecycle-card .lc-alert', n: 2 }
            ],
            clip: '.lifecycle-card'
        },

        // ===== 13. Huỷ bản ghi =====
        {
            file: '24-huy-ban-ghi.png',
            user: 'sales4',
            url: edit(10),
            // Mở hộp Huỷ bản ghi và gõ lý do ví dụ -- KHÔNG bấm Huỷ.
            before: async (page) => {
                await openTab(page, 'pane-tien-trinh');
                await openModal(page, '#pane-tien-trinh .btn-step.danger');
                await page.type('#askNote', 'Tạo trùng — bản đúng là 11/2026/HĐKT-POSTEF');
            },
            marks: [
                { sel: '#askNote', n: 1 },
                { sel: '#askOk', n: 2 }
            ],
            clip: '.modal.show .modal-content'
        },

        // ===== 14. Nhập PDF =====
        {
            file: '25-nhap-pdf.png',
            user: 'sales4',
            url: '/contract?action=importForm',
            marks: [
                { sel: '.template-link', n: 1 },
                { sel: 'input[type=file]', n: 2 },
                { sel: '.btn-submit-import', n: 3 }
            ],
            clip: '.page-container'
        },
        {
            file: '26-nhap-pdf-loi.png',
            user: 'sales4',
            url: '/contract?action=importForm',
            // Gửi THẬT chính file mẫu còn trống: server liệt kê lỗi và không
            // ghi gì (nhập PDF có lỗi là không ghi phần nào).
            before: async (page) => {
                await page.upload('input[type=file]', TEMPLATE);
                await page.clickAndWait('.btn-submit-import');
                await page.waitFor('.error-list');
            },
            marks: [
                { sel: '.error-list', n: null }
            ],
            clip: '.page-container'
        }
    ]
};

/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phân hệ Phiếu hỗ trợ kỹ thuật.
 * Chạy: node tools/guide/capture.mjs ticket   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/ticket.jsp -- sửa bên này thì soát lại bên kia. Tên ảnh đánh
 * số theo thứ tự xuất hiện trong trang hướng dẫn (Hình 1, Hình 2...).
 *
 * KHÔNG ảnh nào ghi dữ liệu: form chỉ điền ví dụ, không gửi; hộp xác nhận xoá
 * chỉ mở ra rồi chụp. Riêng ảnh 09 gửi THẬT lệnh xoá một phiếu đang xử lý --
 * server từ chối (TechnicalSupportTicketDAO.canDelete) nên không xoá gì, chỉ
 * hiện thông báo.
 *
 * Dữ liệu mẫu: CSDL dựng từ db/schema.sql (Task 10.6), KHÔNG cần chuẩn bị gì
 * thêm. Chỉ có 3 phiếu, nên danh sách ít dòng:
 *   - TK-0001 (id 1): KH-0002 Duyên Hải, hợp đồng 03/2026/HĐKT/POT-DHN, Đã
 *     đóng, đủ nguyên nhân -> phương hướng -> kết quả, 3 dòng lịch sử. Người
 *     tạo và người xử lý: leminhhoang (Lê Minh Hoàng). Dùng cho ảnh chi tiết.
 *   - TK-0002 (id 2): KH-0001 Hồ Tây, hợp đồng 02/2026/HĐMB/POT-HTN, Đang xử
 *     lý, Khẩn cấp, hạn SLA 22/11/2026 09:00 (nhãn "Sắp tới hạn" khi chụp
 *     trong vòng 24 giờ trước mốc đó -- chụp muộn hơn thì thành "Quá hạn
 *     SLA", trang hướng dẫn tả cả hai), đã có nguyên nhân và phương hướng,
 *     chưa có kết quả. Người xử lý: leminhhoang. Không xoá được -> ảnh 09.
 *   - TK-0003 (id 3): KH-0001, hợp đồng 05/2026/HĐMB/POT-HTN, Tư vấn, Mới
 *     tiếp nhận, chưa đánh giá. Người tạo và người xử lý: phamthutrang
 *     (Phạm Thu Trang). Xoá được -> ảnh 08; form sửa trống phần đánh giá ->
 *     ảnh 06.
 * KH-0001 có ba hợp đồng, một là phụ lục (02/2026/HĐMB/POT-HTN/PL01).
 * Không đăng nhập tranquocbao (agent khác có thể đang tạm sửa hồ sơ đó).
 */
const view = (id) => '/ticket?action=view&id=' + id;
const edit = (id) => '/ticket?action=edit&id=' + id;
const row = (code) => ({ sel: '#ticketTableBody tr', text: code });

// Ô chọn hợp đồng chỉ mở sau khi AJAX nạp xong danh sách của khách hàng.
async function contractsLoaded(page) {
    await page.waitFor('#contractLoaded[value="1"]');
    await page.settle(200);
}

// Chọn khách hàng trong hộp chọn, như người dùng bấm vào một dòng của nó.
async function pickCustomer(page, code) {
    await page.eval(`Array.from(document.querySelectorAll('#customerPickerList .picker-item'))
        .find((i) => i.textContent.includes(${JSON.stringify(code)})).click()`);
    await contractsLoaded(page);
}

export default {
    viewport: { width: 1600, height: 900 },
    shots: [
        // ===== 2. Xem và tìm phiếu =====
        {
            file: '01-danh-sach.png',
            user: 'leminhhoang',
            url: '/ticket',
            viewport: { width: 1920, height: 900 },
            marks: [
                { sel: '.status-strip', n: 1 },
                { sel: '#searchInput', n: 2 },
                { sel: '#filterStatus', n: 3 },
                { sel: '#filterPriority', n: 4 },
                { sel: '#filterAssignee', n: 5 },
                { sel: '#filterYear', n: 6 },
                { sel: '#filterPeriod', n: 6 },
                { sel: '.header-actions a[href*="exportExcel"]', n: 7 },
                { sel: '.header-actions .btn-add', n: 8 }
            ],
            // Đầu trang tới hết thanh lọc; bảng có ảnh riêng (02). Lề rộng hơn
            // mặc định: nút Tạo phiếu nằm sát mép phải của hàng tiêu đề.
            clip: ['.page-header-row', '.filter-bar'],
            margin: 28
        },
        {
            file: '02-doc-dong.png',
            user: 'leminhhoang',
            url: '/ticket',
            viewport: { width: 1920, height: 1000 },
            // Bảng rộng hơn khung nên cuộn ngang (có chủ ý, xem listTicket.jsp):
            // kéo hết sang phải để cột Thao tác lộ ra như khi người dùng kéo.
            before: async (page) => {
                await page.eval("document.querySelector('.table-responsive').scrollLeft = 100000");
                await page.settle(200);
            },
            // 3 và 5 cùng một dòng (TK-0002): đang xử lý, sắp tới hạn SLA, và
            // vì đang xử lý nên nút xoá mờ. 1 và 4 ở dòng trên, 2 ở dòng dưới
            // -- khác cột với 3 để hai khung không dính nhau.
            marks: [
                { within: row('TK-0003'), sel: 'td:nth-child(2)', n: 1 },
                { within: row('TK-0001'), sel: '.cell-2line', n: 2 },
                { within: row('TK-0002'), sel: 'td.status-cell', n: 3 },
                { within: row('TK-0003'), sel: 'td:nth-child(6)', n: 4 },
                { within: row('TK-0002'), sel: '.action-icons', n: 5 }
            ],
            clip: '.table-card'
        },

        // ===== 3. Tạo phiếu =====
        {
            file: '03-tao-phieu.png',
            user: 'leminhhoang',
            url: '/ticket?action=new',
            // Điền ví dụ -- KHÔNG bấm Tạo phiếu hỗ trợ.
            before: async (page) => {
                await pickCustomer(page, 'KH-0001');
                await page.eval(`Array.from(document.querySelectorAll('#contractPickerList .picker-item'))
                    .find((i) => i.dataset.name === '02/2026/HĐMB/POT-HTN').click()`);
                await page.select('#ticketType', 'Bảo hành');
                await page.select('#priority', 'Cao');
                await page.select('#receptionChannel', 'Điện thoại');
                await page.type('#slaDeadline', '2026-11-25T17:00');
                await page.select('#technician', 'Lê Minh Hoàng');
                await page.type('#description', 'UPS số 1 tại trung tâm điều hành mạng báo ắc quy yếu, thời gian lưu điện chỉ còn khoảng 5 phút khi cắt điện lưới.');
            },
            marks: [
                { sel: '#customerPickerField', n: 1 },
                { sel: '#contractPickerField', n: 2 },
                { sel: '#ticketType', n: 3 },
                { sel: '#priority', n: 4 },
                { sel: '#receptionChannel', n: 5 },
                { sel: '#slaDeadline', n: 6 },
                { sel: '#technician', n: 7 },
                { sel: '.form-check', n: 8 },
                { sel: '#description', n: 9 },
                { sel: '.action-bar .btn-primary', n: 10 }
            ],
            clip: '.page-container'
        },
        {
            file: '04-chon-hop-dong.png',
            user: 'leminhhoang',
            url: '/ticket?action=new',
            before: async (page) => {
                await pickCustomer(page, 'KH-0001');
                await page.click('#contractPickerField');
                await page.waitFor('.modal.show');
                await page.settle(500);
            },
            // 3 là 02/2026 (UPS EATON) -- đúng hợp đồng chọn ở Hình 3, và không
            // nằm sát dòng số 2 (hai khung liền nhau dính vào nhau). Dòng phụ
            // lục PL01 ở giữa.
            marks: [
                { sel: '#contractSearchInput', n: 1 },
                { sel: '#contractPickerList .picker-item', text: 'Không gắn hợp đồng', n: 2 },
                { sel: '#contractPickerList .picker-item', text: 'Cung cấp UPS EATON', n: 3 }
            ],
            clip: '.modal.show .modal-content'
        },

        // ===== 4. Xem chi tiết =====
        {
            file: '05-chi-tiet.png',
            user: 'leminhhoang',
            url: view(1),
            marks: [
                { sel: '.detail-header .doc-info', n: 1 },
                { sel: '.header-actions a[href*="exportPdf"]', n: 2 },
                { sel: '.header-actions a[href*="action=edit"]', n: 3 },
                { sel: '.header-actions button.btn-delete-detail', n: 4 },
                // Tiêu đề khối không có lề trái: nới khung để viền không đè chữ.
                { sel: '.section-header', text: 'Thông tin chung', n: 5, pad: 9 },
                { sel: '.section-header', text: 'Người tạo phiếu ghi nhận', n: 6, pad: 9 },
                { sel: '.section-header', text: 'Người xử lý đánh giá', n: 7, pad: 9 },
                { sel: '.section-header', text: 'Lịch sử xử lý', n: 8, pad: 9 }
            ],
            clip: '.page-container'
        },

        // ===== 5. Sửa phiếu =====
        {
            file: '06-sua.png',
            user: 'phamthutrang',
            url: edit(3),
            before: contractsLoaded,
            marks: [
                { sel: '.section-header', text: 'Thông tin chung', n: 1, pad: 9 },
                { sel: '#technician', n: 2 },
                { sel: '#description', n: 3 },
                { sel: '.section-header', text: 'Người xử lý đánh giá', n: 4, pad: 9 },
                { sel: '#internalNote', n: 5 },
                { sel: '.action-bar .btn-primary', n: 6 }
            ],
            clip: '.page-container'
        },

        // ===== 8. Xoá phiếu =====
        {
            file: '08-xoa.png',
            user: 'phamthutrang',
            url: view(3),
            // Mở hộp xác nhận -- KHÔNG bấm Xoá phiếu. Chụp nguyên khung nhìn để
            // thấy nút Xóa nằm đâu, như ảnh xoá của hướng dẫn Khách hàng.
            before: async (page) => {
                await page.click('.header-actions button.btn-delete-detail');
                await page.waitFor('.modal.show');
                await page.settle(500);
            },
            marks: [
                { sel: '.header-actions button.btn-delete-detail', n: 1 },
                { sel: '#deleteOk', n: 2 }
            ]
        },

        // ===== 6. Ghi kết quả xử lý (người xử lý) =====
        {
            file: '07-cap-nhat-xu-ly.png',
            user: 'leminhhoang',
            url: edit(2),
            // Điền ví dụ đóng phiếu -- KHÔNG bấm Lưu thay đổi. Chỉ chụp từ khối
            // "Người xử lý đánh giá" xuống nút Lưu: phần trên đã có ở Hình 6.
            // Gắn id tạm cho tiêu đề khối để làm mốc vùng chụp (clip chỉ nhận
            // selector, không lọc theo chữ).
            before: async (page) => {
                await contractsLoaded(page);
                await page.eval(`Array.from(document.querySelectorAll('.section-header'))
                    .find((e) => e.textContent.includes('Người xử lý đánh giá')).id = '__guideHandlerSec'`);
                await page.select('#status', 'Đã đóng');
                await page.type('#resolutionSummary', 'Đã thay quạt làm mát mô-đun công suất, chạy thử tải 2 giờ ổn định; UPS số 2 trở lại chế độ online.');
                await page.type('#internalNote', 'Khách đã nghiệm thu tại trung tâm điều hành mạng, đồng ý đóng phiếu.');
            },
            marks: [
                { sel: '#status', n: 1 },
                { sel: '#causeCategory', n: 2 },
                { sel: '#rootCause', n: 3 },
                { sel: '#handlingPlan', n: 4 },
                { sel: '#resolutionSummary', n: 5 },
                { sel: '#internalNote', n: 6 },
                { sel: '.action-bar .btn-primary', n: 7 }
            ],
            clip: ['#__guideHandlerSec', '.action-bar'],
            margin: 24
        },
        {
            file: '09-xoa-bi-chan.png',
            user: 'leminhhoang',
            url: view(2),
            // Gửi THẬT lệnh xoá TK-0002 (đang xử lý): nút Xóa đã mờ, nên đi qua
            // form ẩn của trang -- đúng cái xảy ra khi phiếu vừa bị người khác
            // chuyển sang Đang xử lý. Server từ chối, không xoá gì.
            before: async (page) => {
                const loaded = page.cdp.once('Page.loadEventFired');
                await page.eval("document.getElementById('deleteFormId').value = 2; document.getElementById('deleteForm').submit();");
                await loaded;
                await page.settle();
                await page.waitFor('.alert-danger');
            },
            marks: [
                { sel: '.alert-danger', n: null }
            ],
            clip: ['.back-link-top', '.detail-header']
        }
    ]
};

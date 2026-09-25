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
 * server từ chối (canDelete) nên không xoá gì, chỉ hiện thông báo.
 *
 * Dữ liệu mẫu (bản sao poscs_db, 16 phiếu gieo ở V15): sales4 (Phạm Thị Ngọc
 * Anh, Sales) cho các ảnh của người tạo phiếu; kythuat3 (Hoàng Thị Lan) là kỹ
 * thuật viên được giao TK-0003 (id 3, Đang xử lý, Khẩn cấp, đã có nguyên nhân
 * và phương hướng nhưng chưa có kết quả). Các phiếu khác dùng làm ví dụ:
 * TK-0001 (id 1, đã đóng, đủ nguyên nhân -> kết quả, 3 dòng lịch sử), TK-0008
 * (id 8, Mới tiếp nhận -- xoá được), TK-0009 (id 9, Mới tiếp nhận, chưa đánh
 * giá), TK-0014 (không gắn hợp đồng), TK-0015 (id 15, Đang xử lý, quá hạn SLA
 * -- không xoá được). KH-0001 (Sông Hồng) có ba hợp đồng, một là phụ lục.
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
            user: 'sales4',
            url: '/ticket',
            viewport: { width: 1920, height: 900 },
            marks: [
                { sel: '.status-strip', n: 1 },
                { sel: '#searchInput', n: 2 },
                { sel: '#filterStatus', n: 3 },
                { sel: '#filterPriority', n: 4 },
                { sel: '#filterYear', n: 5 },
                { sel: '#filterPeriod', n: 5 },
                { sel: '.header-actions a[href*="exportExcel"]', n: 6 },
                { sel: '.header-actions .btn-add', n: 7 }
            ],
            // Đầu trang tới hết thanh lọc; bảng có ảnh riêng (02). Lề rộng hơn
            // mặc định: nút Tạo phiếu nằm sát mép phải của hàng tiêu đề.
            clip: ['.page-header-row', '.filter-bar'],
            margin: 28
        },
        {
            file: '02-doc-dong.png',
            user: 'sales4',
            url: '/ticket',
            viewport: { width: 1920, height: 1000 },
            // Bảng rộng hơn khung nên cuộn ngang (có chủ ý, xem listTicket.jsp):
            // kéo hết sang phải để cột Thao tác lộ ra như khi người dùng kéo.
            before: async (page) => {
                await page.eval("document.querySelector('.table-responsive').scrollLeft = 100000");
                await page.settle(200);
            },
            // 3 và 5 cùng một dòng (TK-0015): đang xử lý, quá hạn SLA, và vì
            // đang xử lý nên nút xoá mờ.
            marks: [
                { within: row('TK-0016'), sel: 'td:nth-child(2)', n: 1 },
                { within: row('TK-0014'), sel: '.cell-2line', n: 2 },
                { within: row('TK-0015'), sel: 'td.status-cell', n: 3 },
                { within: row('TK-0016'), sel: 'td:nth-child(6)', n: 4 },
                { within: row('TK-0015'), sel: '.action-icons', n: 5 }
            ],
            clip: '.table-card'
        },

        // ===== 3. Tạo phiếu =====
        {
            file: '03-tao-phieu.png',
            user: 'sales4',
            url: '/ticket?action=new',
            // Điền ví dụ -- KHÔNG bấm Tạo phiếu hỗ trợ.
            before: async (page) => {
                await pickCustomer(page, 'KH-0001');
                await page.eval(`Array.from(document.querySelectorAll('#contractPickerList .picker-item'))
                    .find((i) => i.dataset.name === '01/2026/HĐKT-POSTEF').click()`);
                await page.select('#ticketType', 'Bảo hành');
                await page.select('#priority', 'Cao');
                await page.select('#receptionChannel', 'Điện thoại');
                await page.type('#slaDeadline', '2026-09-26T17:00');
                await page.select('#technician', 'Vũ Đình Nam');
                await page.type('#description', 'Hai tủ ắc quy lithium tại trạm BTS Thanh Xuân báo lỗi BMS, không nhận sạc từ tối qua.');
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
            user: 'sales4',
            url: '/ticket?action=new',
            before: async (page) => {
                await pickCustomer(page, 'KH-0001');
                await page.click('#contractPickerField');
                await page.waitFor('.modal.show');
                await page.settle(500);
            },
            // 3 là 01/2026 -- đúng hợp đồng chọn ở Hình 3, và không nằm sát dòng
            // số 2 (hai khung liền nhau dính vào nhau). Dòng phụ lục PL01 ở giữa.
            marks: [
                { sel: '#contractSearchInput', n: 1 },
                { sel: '#contractPickerList .picker-item', text: 'Không gắn hợp đồng', n: 2 },
                { sel: '#contractPickerList .picker-item', text: 'Cung cấp ắc quy lithium', n: 3 }
            ],
            clip: '.modal.show .modal-content'
        },

        // ===== 4. Xem chi tiết =====
        {
            file: '05-chi-tiet.png',
            user: 'sales4',
            url: view(1),
            marks: [
                { sel: '.detail-header .doc-info', n: 1 },
                { sel: '.header-actions a[href*="exportPdf"]', n: 2 },
                { sel: '.header-actions a[href*="action=edit"]', n: 3 },
                { sel: '.header-actions button.btn-delete-detail', n: 4 },
                // Tiêu đề khối không có lề trái: nới khung để viền không đè chữ.
                { sel: '.section-header', text: 'Thông tin chung', n: 5, pad: 9 },
                { sel: '.section-header', text: 'Người tạo phiếu ghi nhận', n: 6, pad: 9 },
                { sel: '.section-header', text: 'Nhân viên kỹ thuật xử lý', n: 7, pad: 9 },
                { sel: '.section-header', text: 'Lịch sử xử lý', n: 8, pad: 9 }
            ],
            clip: '.page-container'
        },

        // ===== 5. Sửa phiếu (Admin, Sales) =====
        {
            file: '06-sua.png',
            user: 'sales4',
            url: edit(9),
            before: contractsLoaded,
            marks: [
                { sel: '.section-header', text: 'Thông tin chung', n: 1, pad: 9 },
                { sel: '#description', n: 2 },
                { sel: '.section-header', text: 'Nhân viên kỹ thuật xử lý', n: 3, pad: 9 },
                { sel: '#internalNote', n: 4 },
                { sel: '.action-bar .btn-primary', n: 5 }
            ],
            clip: '.page-container'
        },

        // ===== 6. Cập nhật xử lý (kỹ thuật viên) =====
        {
            file: '07-cap-nhat-xu-ly.png',
            user: 'kythuat3',
            url: edit(3),
            // Điền ví dụ đóng phiếu -- KHÔNG bấm Lưu thay đổi.
            before: async (page) => {
                await contractsLoaded(page);
                await page.select('#status', 'Đã đóng');
                await page.type('#resolutionSummary', 'Đã lắp lại gioăng đúng chiều tại 12 điểm, thử kín nước đạt; 12 thiết bị CPE kết nối lại bình thường.');
                await page.type('#internalNote', 'Khách đã nghiệm thu tại hiện trường, đồng ý đóng phiếu.');
            },
            // Nhãn "Chỉ xem" cạnh tiêu đề Thông tin chung không khoanh riêng: số
            // khoanh đè mất biểu tượng khoá của nó. Trang hướng dẫn nhắc bằng lời.
            marks: [
                { sel: '.lock-note', n: 1 },
                { sel: '#status', n: 2 },
                { sel: '#causeCategory', n: 3 },
                { sel: '#rootCause', n: 4 },
                { sel: '#handlingPlan', n: 5 },
                { sel: '#resolutionSummary', n: 6 },
                { sel: '#internalNote', n: 7 },
                { sel: '.action-bar .btn-primary', n: 8 }
            ],
            clip: '.page-container'
        },

        // ===== 8. Xoá phiếu =====
        {
            file: '08-xoa.png',
            user: 'sales4',
            url: view(8),
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
        {
            file: '09-xoa-bi-chan.png',
            user: 'sales4',
            url: view(15),
            // Gửi THẬT lệnh xoá TK-0015 (đang xử lý): nút Xóa đã mờ, nên đi qua
            // form ẩn của trang -- đúng cái xảy ra khi phiếu vừa bị người khác
            // chuyển sang Đang xử lý. Server từ chối, không xoá gì.
            before: async (page) => {
                const loaded = page.cdp.once('Page.loadEventFired');
                await page.eval("document.getElementById('deleteFormId').value = 15; document.getElementById('deleteForm').submit();");
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

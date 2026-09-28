/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phân hệ Sản phẩm.
 * Chạy: node tools/guide/capture.mjs product   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/product.jsp -- sửa bên này thì soát lại bên kia. Tên ảnh
 * đánh số theo thứ tự xuất hiện trong trang hướng dẫn (Hình 1, Hình 2...).
 *
 * Chụp bằng kythuat1: vai Kỹ thuật (cùng Admin) có toàn quyền trên sản phẩm,
 * nên thấy đủ các nút Thêm / Sửa / Xoá.
 *
 * KHÔNG ảnh nào ghi dữ liệu: form Thêm chỉ điền ví dụ (ảnh và catalogue lấy
 * từ tools/guide/samples/), không bấm Tạo; hộp xác nhận xoá chỉ mở ra. Ảnh
 * 07 bấm Xoá THẬT trên một sản phẩm đang có hợp đồng -- server từ chối và
 * quay lại trang chi tiết kèm thông báo, không xoá gì.
 *
 * Dữ liệu mẫu (bản sao poscs_db, 89 sản phẩm, 33 danh mục): SP-0006 "Nguồn
 * POSTEF" (id 6) có 3 catalogue và nằm trong 3 hợp đồng bán; SP-0066
 * "Ambient Light Sensor" (id 66) chưa có hợp đồng nào; danh mục 30 là
 * CNTT & IOT › LoRa › Sensors (12 sản phẩm).
 */
import { fileURLToPath } from 'node:url';

const SAMPLE_IMAGE = fileURLToPath(new URL('../samples/cam-bien-th01.png', import.meta.url));
const SAMPLE_CATALOGUE = fileURLToPath(new URL('../samples/catalogue-th01.pdf', import.meta.url));

const view = (id) => '/product?action=view&id=' + id;
const edit = (id) => '/product?action=edit&id=' + id;

// Chọn tệp cho ô nhiều tệp của form sản phẩm. DOM.setFileInputFiles có bắn
// sự kiện change hay không tuỳ bản Chrome -- trang dựng ô xem trước trong
// sự kiện đó, nên chỉ tự bắn khi chưa thấy ô xem trước nào (bắn hai lần là
// tệp bị thêm hai lần).
async function chooseFile(page, inputId, previewSelector, file) {
    await page.upload('#' + inputId, file);
    await page.settle(400);
    if (!await page.eval(`document.querySelectorAll(${JSON.stringify(previewSelector)}).length`)) {
        await page.eval(`document.getElementById(${JSON.stringify(inputId)}).dispatchEvent(new Event('change'))`);
        await page.settle(400);
    }
}

export default {
    viewport: { width: 1600, height: 900 },
    shots: [
        // ===== 2. Xem và tìm sản phẩm =====
        {
            file: '01-danh-sach.png',
            user: 'kythuat1',
            url: '/product',
            marks: [
                { sel: '.category-panel', n: 1 },
                { sel: '.search-input-wrap', n: 2 },
                { sel: '.header-actions .btn-add', n: 3 },
                { sel: '.product-grid .product-card', n: 4 },
                // Nút của thẻ THỨ HAI: khung lồng trong khung số 4 thì đè số.
                { within: { sel: '.product-grid > .product-card:nth-child(2)' }, sel: '.action-icons', n: 5 },
                { sel: '.pagination-bar', n: 6 }
            ],
            clip: '.page-container'
        },
        {
            file: '02-loc-danh-muc.png',
            user: 'kythuat1',
            url: '/product?action=list&categoryId=30',
            marks: [
                { sel: '.cat-link.active', n: 1 },
                { within: { sel: '.cat-row', text: 'LoRa' }, sel: '.cat-toggle', n: 2 },
                { sel: '.active-filter-chip', n: 3 },
                // Dòng chữ không có lề: khung sát chữ thì đè mất chữ đầu.
                { sel: '.pagination-info', n: 4, pad: 8 }
            ],
            clip: '.catalog-layout'
        },

        // ===== 3. Thêm sản phẩm =====
        {
            file: '03-them.png',
            user: 'kythuat1',
            url: '/product?action=new',
            // Điền ví dụ -- KHÔNG bấm Tạo sản phẩm.
            before: async (page) => {
                await page.type('#productName', 'Cảm biến nhiệt độ, độ ẩm LoRa TH-01');
                await page.select('#category', '30');
                await page.type('#description', 'Cảm biến đo nhiệt độ và độ ẩm, kết nối LoRaWAN, pin 2 × AA dùng đến 3 năm. Dùng cho phòng máy, trạm BTS, kho lạnh.');
                await chooseFile(page, 'imagesInput', '#imagePreviewGrid .file-thumb', SAMPLE_IMAGE);
                await chooseFile(page, 'cataloguesInput', '#catalogueChipList .file-chip', SAMPLE_CATALOGUE);
            },
            marks: [
                // Khoanh cả khối nhãn + ô: khoanh riêng ô thì số đè chữ đầu của nhãn ngay trên.
                { sel: '.field-row', text: 'Tên sản phẩm', n: 1 },
                { sel: '.field-row', text: 'Danh mục', n: 2 },
                { sel: '.field-row', text: 'Mô tả', n: 3 },
                { sel: 'label[for=imagesInput]', n: 4 },
                { sel: '#imagePreviewGrid .file-thumb', n: 4 },
                { sel: 'label[for=cataloguesInput]', n: 5 },
                { sel: '#catalogueChipList .file-chip', n: 5 },
                { sel: '.action-bar .btn-primary', n: 6 }
            ],
            clip: '.page-container'
        },

        // ===== 4. Xem chi tiết =====
        {
            file: '04-chi-tiet.png',
            user: 'kythuat1',
            url: view(6),
            marks: [
                { sel: '.detail-header .product-info', n: 1 },
                { sel: '.header-actions .btn-edit-detail', n: 2 },
                { sel: '.header-actions .btn-delete-detail', n: 3 },
                { sel: '.image-gallery a', n: 4 },
                { sel: '.file-chip-list', n: 5 },
                { sel: '.mini-table', n: 6 }
            ],
            clip: '.page-container'
        },

        // ===== 5. Sửa sản phẩm =====
        {
            file: '05-sua.png',
            user: 'kythuat1',
            url: edit(6),
            marks: [
                { sel: '.field-row', text: 'Mã sản phẩm', n: 1 },
                { sel: '.field-row', text: 'Danh mục', n: 2 },
                { sel: '#existingImageGrid .file-thumb', n: 3 },
                { sel: 'label[for=imagesInput]', n: 4 },
                { sel: '#existingCatalogueList .file-chip', n: 5 },
                { sel: 'label[for=cataloguesInput]', n: 6 },
                { sel: '.action-bar .btn-primary', n: 7 }
            ],
            clip: '.page-container'
        },

        // ===== 6. Xoá sản phẩm =====
        {
            file: '06-xoa.png',
            user: 'kythuat1',
            url: view(66),
            // Mở hộp xác nhận -- KHÔNG bấm Xóa sản phẩm.
            before: async (page) => {
                await page.click('.header-actions .btn-delete-detail');
                await page.waitFor('.modal.show');
                await page.settle(500);
            },
            marks: [
                { sel: '.header-actions .btn-delete-detail', n: 1 },
                { sel: '#confirmDeleteBtn', n: 2 }
            ]
        },
        {
            file: '07-xoa-bi-chan.png',
            user: 'kythuat1',
            url: view(6),
            // Xoá THẬT: SP-0006 còn nằm trong hợp đồng nên server từ chối và quay
            // lại trang chi tiết kèm thông báo -- không xoá gì.
            before: async (page) => {
                await page.click('.header-actions .btn-delete-detail');
                await page.waitFor('.modal.show');
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

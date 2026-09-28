/*
 * Canh một lỗi XSS dễ tái phát trong JSP: chèn dữ liệu người dùng vào chuỗi
 * JavaScript bên trong thuộc tính sự kiện, kiểu
 *
 *     onclick="openDeleteModal(5, '${fn:escapeXml(product.productName)}')"
 *
 * fn:escapeXml chỉ an toàn cho HTML. Trình duyệt giải mã &#039; lại thành '
 * TRƯỚC khi chạy onclick, nên tên "D'Link" làm hỏng lệnh (bấm nút không có gì
 * xảy ra), còn tên như  x'); document.title='x'; ('  thì chạy luôn đoạn mã đó
 * trong phiên của người bấm. Đã chạy thật 2026-09-28 trên danh sách Sản phẩm
 * và Khách hàng. Cách đúng: đưa dữ liệu qua data-* rồi đọc this.dataset.
 *
 * CÁCH CHẠY:  node --test test/js/
 * Chỉ đọc file, không cần trình duyệt hay Tomcat.
 */

'use strict';

const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '..', '..', 'web');

// Giá trị của mọi thuộc tính on...="..." (onclick, onchange, onsubmit...).
const THUOC_TINH_SU_KIEN = /\son[a-z]+="([^"]*)"/g;
// EL đã escapeXml nằm trong chuỗi JS nháy đơn.
const CHEN_VAO_CHUOI_JS = /'\$\{fn:escapeXml\(/;

function tatCaJsp(thuMuc) {
    let ketQua = [];
    for (const muc of fs.readdirSync(thuMuc, { withFileTypes: true })) {
        const duongDan = path.join(thuMuc, muc.name);
        if (muc.isDirectory()) {
            ketQua = ketQua.concat(tatCaJsp(duongDan));
        } else if (/\.jspf?$/.test(muc.name)) {
            ketQua.push(duongDan);
        }
    }
    return ketQua;
}

/** Các chỗ vi phạm trong một nội dung JSP: [{ dong, thuocTinh }]. */
function timViPham(noiDung) {
    const viPham = [];
    for (const m of noiDung.matchAll(THUOC_TINH_SU_KIEN)) {
        if (CHEN_VAO_CHUOI_JS.test(m[1])) {
            viPham.push({
                dong: noiDung.slice(0, m.index).split('\n').length,
                thuocTinh: m[0].trim()
            });
        }
    }
    return viPham;
}

test('bộ dò bắt đúng mẫu lỗi và bỏ qua cách viết đúng', () => {
    assert.strictEqual(timViPham(
        `<button onclick="openDeleteModal(\${p.id}, '\${fn:escapeXml(p.name)}')">`).length, 1);
    assert.strictEqual(timViPham(
        `<button data-name="\${fn:escapeXml(p.name)}" onclick="openDeleteModal(\${p.id}, this.dataset.name)">`).length, 0);
    // Đường dẫn dựng từ contextPath + id số không phải dữ liệu người dùng gõ.
    assert.strictEqual(timViPham(
        `<button onclick="location.href='\${pageContext.request.contextPath}/product?id=\${p.id}'">`).length, 0);
});

test('không JSP nào chèn dữ liệu đã escapeXml vào chuỗi JS trong thuộc tính sự kiện', () => {
    const files = tatCaJsp(WEB);
    assert.ok(files.length > 20, 'không tìm thấy JSP nào -- sai đường dẫn web/?');
    const loi = [];
    for (const f of files) {
        for (const v of timViPham(fs.readFileSync(f, 'utf8'))) {
            loi.push(`${path.relative(WEB, f)}:${v.dong}  ${v.thuocTinh.slice(0, 120)}`);
        }
    }
    assert.deepStrictEqual(loi, [], 'Đưa dữ liệu qua data-* rồi đọc this.dataset:\n' + loi.join('\n'));
});

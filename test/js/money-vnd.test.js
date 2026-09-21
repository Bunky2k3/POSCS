/*
 * Test cho phần tiền VNĐ trong web/js/appshell.js -- định dạng ô nhập và đọc
 * số thành chữ.
 *
 * VÌ SAO CÓ FILE NÀY. Phần JS của dự án trước đây không có test nào, và đã trả
 * giá đúng một lần: hai lỗi lọt qua cả review lẫn CI rồi chỉ lộ ra khi mở
 * trang thật (PR #136) -- phụ lục giảm trừ mất hẳn dòng đọc thành chữ vì hàm
 * đọc số từ chối dấu trừ, và dán "980000000.00" vào ô tiền ra 98 tỷ. Lần đó có
 * một test JUnit gọi thẳng hàm đọc chữ nên vẫn xanh: test đúng hàm, sai đường
 * đi. Cả hai ca đó nằm ở cuối file này.
 *
 * CÁCH CHẠY:  node --test test/js/
 * Không cần npm, không package.json -- dùng bộ chạy test có sẵn của Node 18+.
 *
 * CÁCH NẠP. appshell.js là script cho trình duyệt, không phải module: nó chạy
 * ngay khi được nạp và đụng vào document. Nên test dựng một document giả tối
 * thiểu rồi nạp CHÍNH file đang chạy thật bằng vm, thay vì chép logic sang một
 * bản riêng cho test (bản chép sẽ trôi khỏi bản thật, và đó đúng là kiểu lỗi
 * đang muốn chặn). Ba khối IIFE trong file đều tự thoát khi không tìm thấy
 * phần tử của nó, nên document giả chỉ cần trả null / mảng rỗng.
 *
 * GIỚI HẠN. Chỉ các hàm THUẦN treo ở window.POSCS mới kiểm được ở đây. Phần
 * gắn sự kiện và phần ghi ra DOM vẫn phải mở trang thật mới biết -- test này
 * không thay thế việc đó.
 */

'use strict';

const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Cho phép trỏ sang một bản appshell.js khác để KIỂM CHỨNG chính bộ test này:
//   git show <commit cũ>:web/js/appshell.js > /tmp/cu.js
//   POSCS_APPSHELL=/tmp/cu.js node --test test/js/
// Bộ test nào không đỏ trên bản đã biết là hỏng thì nó chưa chứng minh được gì.
const DUONG_DAN = process.env.POSCS_APPSHELL
    || path.join(__dirname, '..', '..', 'web', 'js', 'appshell.js');

function napAppshell() {
    const phanTuGia = () => ({
        classList: { add() {}, remove() {}, contains: () => false, toggle() {} },
        style: {},
        addEventListener() {},
        appendChild() {},
        setAttribute() {},
        getAttribute: () => null,
        querySelector: () => null,
        querySelectorAll: () => [],
        textContent: ''
    });

    const document = {
        getElementById: () => null,
        querySelector: () => null,
        querySelectorAll: () => [],
        addEventListener() {},
        createElement: phanTuGia,
        body: phanTuGia(),
        documentElement: phanTuGia()
    };

    const sandbox = {
        document,
        localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
        console
    };
    sandbox.window = sandbox;
    sandbox.window.matchMedia = () => ({
        matches: false, addEventListener() {}, addListener() {}
    });
    sandbox.globalThis = sandbox;

    vm.createContext(sandbox);
    vm.runInContext(fs.readFileSync(DUONG_DAN, 'utf8'), sandbox, { filename: DUONG_DAN });

    assert.ok(sandbox.window.POSCS, 'appshell.js phải treo POSCS lên window');
    return sandbox.window.POSCS;
}

const POSCS = napAppshell();

// ----------------------------------------------------------------------
// docTienVND -- đọc số thành chữ
// ----------------------------------------------------------------------

test('docTienVND: các mốc dễ sai của cách đọc tiếng Việt', () => {
    const bang = [
        [0, 'Không đồng'],
        [1, 'Một đồng'],
        [10, 'Mười đồng'],
        [11, 'Mười một đồng'],
        [15, 'Mười lăm đồng'],          // "lăm", không phải "năm"
        [21, 'Hai mươi mốt đồng'],      // "mốt", không phải "một"
        [24, 'Hai mươi bốn đồng'],
        [25, 'Hai mươi lăm đồng'],
        [100, 'Một trăm đồng'],
        [101, 'Một trăm lẻ một đồng'],  // có "lẻ"
        [105, 'Một trăm lẻ năm đồng'],
        [110, 'Một trăm mười đồng'],    // không có "lẻ"
        [115, 'Một trăm mười lăm đồng']
    ];
    for (const [so, chu] of bang) {
        assert.strictEqual(POSCS.docTienVND(so), chu, 'đọc ' + so);
    }
});

test('docTienVND: nhóm rỗng ở giữa phải đọc "không trăm lẻ"', () => {
    // Bỏ "không trăm" đi thì 1.000.005 thành "một triệu năm" -- nghe ra
    // 1.000.500, tức là một con số khác hẳn.
    assert.strictEqual(POSCS.docTienVND(1000000), 'Một triệu đồng');
    assert.strictEqual(POSCS.docTienVND(1000005), 'Một triệu không trăm lẻ năm đồng');
    assert.strictEqual(POSCS.docTienVND(1000015), 'Một triệu không trăm mười lăm đồng');
});

test('docTienVND: các bậc nghìn / triệu / tỷ', () => {
    assert.strictEqual(POSCS.docTienVND(1000), 'Một nghìn đồng');
    assert.strictEqual(POSCS.docTienVND(980000000), 'Chín trăm tám mươi triệu đồng');
    assert.strictEqual(POSCS.docTienVND(1000000000), 'Một tỷ đồng');
    assert.strictEqual(POSCS.docTienVND(1350000000), 'Một tỷ ba trăm năm mươi triệu đồng');
    assert.strictEqual(POSCS.docTienVND(1000000000000), 'Một nghìn tỷ đồng');
});

test('docTienVND: số âm đọc là "Giảm trừ" chứ không phải "Âm"', () => {
    // Số âm ở đây chỉ đến từ phụ lục giảm trừ, nên đọc theo nghiệp vụ.
    assert.strictEqual(POSCS.docTienVND(-120000000), 'Giảm trừ một trăm hai mươi triệu đồng');
});

// ----------------------------------------------------------------------
// soTuChuoi -- phân biệt dấu phân nhóm với dấu thập phân
// Cùng quy tắc với MoneyVnd.parseOrNull bên Java, xem javadoc ở đó.
// ----------------------------------------------------------------------

test('soTuChuoi: chuỗi từ CSDL không bị nhân 100', () => {
    // JSP đổ DECIMAL(15,2) thẳng ra ô nhập, nên chuỗi vào mang đuôi ".00".
    assert.strictEqual(POSCS.soTuChuoi('980000000.00'), 980000000);
    assert.strictEqual(POSCS.soTuChuoi('980000000,00'), 980000000);
    assert.strictEqual(POSCS.soTuChuoi('1500000000.00'), 1500000000);
});

test('soTuChuoi: nhóm cuối đúng 3 chữ số là phân nhóm', () => {
    // "1.500" ở Việt Nam là một nghìn rưỡi, không phải một phẩy năm.
    assert.strictEqual(POSCS.soTuChuoi('1.500'), 1500);
    assert.strictEqual(POSCS.soTuChuoi('1,500'), 1500);
    assert.strictEqual(POSCS.soTuChuoi('980.000.000'), 980000000);
    assert.strictEqual(POSCS.soTuChuoi('1,500,000,000'), 1500000000);
});

test('soTuChuoi: nhóm cuối khác 3 chữ số là thập phân', () => {
    assert.strictEqual(POSCS.soTuChuoi('1.5'), 1.5);
    assert.strictEqual(POSCS.soTuChuoi('1,5'), 1.5);
});

test('soTuChuoi: có cả hai loại dấu thì dấu SAU là thập phân', () => {
    assert.strictEqual(POSCS.soTuChuoi('1.500,75'), 1500.75);
    assert.strictEqual(POSCS.soTuChuoi('1,500.75'), 1500.75);
});

test('soTuChuoi: bỏ qua khoảng trắng, kể cả non-breaking space', () => {
    // Dán từ Excel hay từ một trang web khác rất hay mang theo U+00A0.
    assert.strictEqual(POSCS.soTuChuoi(' 980 000 000 '), 980000000);
    assert.strictEqual(POSCS.soTuChuoi('980 000 000'), 980000000);
});

test('soTuChuoi: chuỗi không đọc được trả null', () => {
    for (const rac of ['', '   ', 'abc', '12abc', '1.500 đ', '.', '.5', '5.', null, undefined]) {
        assert.strictEqual(POSCS.soTuChuoi(rac), null, JSON.stringify(rac));
    }
});

test('soTuChuoi: ĐỌC ĐƯỢC SỐ ÂM (lỗi đã xảy ra: phụ lục giảm trừ)', () => {
    // Trang chi tiết đổ contract_value ra data-vnd, và phụ lục giảm trừ mang
    // giá trị âm THẬT. Bản cũ từ chối dấu trừ nên trả null, dòng chữ rỗng, và
    // CSS .money-words:empty giấu luôn -- tính năng biến mất không một dấu vết
    // ở đúng ca mà docTienVND đã viết sẵn nhánh "Giảm trừ ..." để phục vụ.
    assert.strictEqual(POSCS.soTuChuoi('-120000000.00'), -120000000);
    assert.strictEqual(POSCS.soTuChuoi('-180000000'), -180000000);
    assert.strictEqual(POSCS.soTuChuoi('-1.500.000'), -1500000);
});

// ----------------------------------------------------------------------
// giaTriSauNhap -- giá trị ô mang sau một lần gõ hoặc dán
// ----------------------------------------------------------------------

test('giaTriSauNhap: gõ từng phím thì chỉ giữ chữ số', () => {
    // Chuỗi trung gian lúc đang gõ "1.500" phải đi qua được, không bị hàm đọc
    // số cắn mất chữ số vừa gõ.
    assert.strictEqual(POSCS.giaTriSauNhap('1', false), '1');
    assert.strictEqual(POSCS.giaTriSauNhap('1.5', false), '15');
    assert.strictEqual(POSCS.giaTriSauNhap('1.50', false), '150');
    assert.strictEqual(POSCS.giaTriSauNhap('1.500', false), '1.500');
    assert.strictEqual(POSCS.giaTriSauNhap('1500000000', false), '1.500.000.000');
});

test('giaTriSauNhap: ô rỗng và số 0 đứng đầu', () => {
    assert.strictEqual(POSCS.giaTriSauNhap('', false), '');
    assert.strictEqual(POSCS.giaTriSauNhap('0', false), '0');
    assert.strictEqual(POSCS.giaTriSauNhap('007', false), '7');
});

test('giaTriSauNhap: DÁN số có đuôi thập phân KHÔNG nhân 100 (lỗi đã xảy ra)', () => {
    // Copy một con số từ bản xuất Excel hoặc từ một ô cũ còn nguyên đuôi ".00"
    // rồi dán vào. Bản cũ xoá mọi ký tự không phải chữ số, nên "980000000.00"
    // thành 98.000.000.000 ngay trên màn hình và bấm Lưu là CSDL nhận 98 tỷ --
    // đúng lỗi ×100 đã sửa ở server, nhưng xảy ra TRƯỚC khi form gửi đi nên
    // MoneyVnd.parseOrNull không còn gì để cứu.
    assert.strictEqual(POSCS.giaTriSauNhap('980000000.00', true), '980.000.000');
    assert.strictEqual(POSCS.giaTriSauNhap('1500000000,00', true), '1.500.000.000');
});

test('giaTriSauNhap: dán số đã phân nhóm thì giữ nguyên', () => {
    assert.strictEqual(POSCS.giaTriSauNhap('1.500.000.000', true), '1.500.000.000');
    assert.strictEqual(POSCS.giaTriSauNhap('98 000 000', true), '98.000.000');
});

test('giaTriSauNhap: dán chuỗi không đọc được thì lùi về giữ chữ số', () => {
    // "980.000.000 ₫" copy nguyên từ trang chi tiết: có ký hiệu tiền nên không
    // khớp quy tắc đọc số, nhưng lùi về giữ chữ số vẫn ra đúng con số.
    assert.strictEqual(POSCS.giaTriSauNhap('980.000.000 ₫', true), '980.000.000');
});

test('giaTriSauNhap: dán số âm thì ô giữ nguyên chữ số, không mang dấu trừ', () => {
    // Ô nhập tiền luôn là số dương: dấu của phụ lục nằm ở ô chọn Bổ sung/Giảm
    // trừ bên cạnh. Dán "-120000000" vào thì chỉ còn con số.
    assert.strictEqual(POSCS.giaTriSauNhap('-120000000', true), '120.000.000');
});

// ----------------------------------------------------------------------
// nhomNghin
// ----------------------------------------------------------------------

test('nhomNghin: chèn dấu chấm mỗi 3 chữ số, không phải dấu phẩy', () => {
    // Dấu chấm để khớp phần hiển thị sẵn có (toLocaleString 'vi-VN'): một màn
    // hình mà chỗ này chấm chỗ kia phẩy thì 1.500 đọc ra hai nghĩa.
    assert.strictEqual(POSCS.nhomNghin('1'), '1');
    assert.strictEqual(POSCS.nhomNghin('100'), '100');
    assert.strictEqual(POSCS.nhomNghin('1000'), '1.000');
    assert.strictEqual(POSCS.nhomNghin('980000000'), '980.000.000');
});

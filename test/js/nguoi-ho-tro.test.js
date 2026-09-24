/*
 * Test cho phần lọc ô "Người hỗ trợ" ở form khách hàng, trong
 * web/js/appshell.js.
 *
 * LUẬT (chốt với người dùng 2026-09-24): người hỗ trợ KHÔNG được là chính người
 * phụ trách chính, và KHÔNG được là cấp trên TRỰC TIẾP của người đó; trưởng
 * nhóm khác, và cấp dưới của người phụ trách, thì vẫn được. Server kiểm lại ở
 * CustomerController -- phần ở đây là lọc trên form, loại lỗi chỉ lộ ra khi
 * mở trang thật.
 *
 * CÁCH CHẠY:  node --test test/js/
 * Nạp CHÍNH appshell.js trong sandbox có document giả, y như
 * money-vnd.test.js (xem lý do đầy đủ ở đó). ganLocNguoiHoTro có đụng DOM,
 * nhưng chỉ qua ô select, option và một dòng chữ -- dựng bản giả tối thiểu
 * là kiểm được, không cần trình duyệt.
 */

'use strict';

const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Xem money-vnd.test.js: trỏ sang bản appshell.js khác để kiểm chính bộ test.
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
    sandbox.window.matchMedia = () => ({ matches: false, addEventListener() {}, addListener() {} });
    sandbox.globalThis = sandbox;

    vm.createContext(sandbox);
    vm.runInContext(fs.readFileSync(DUONG_DAN, 'utf8'), sandbox, { filename: DUONG_DAN });

    assert.ok(sandbox.window.POSCS, 'appshell.js phải treo POSCS lên window');
    return sandbox.window.POSCS;
}

const POSCS = napAppshell();

// Mảng trả về từ sandbox thuộc realm khác (Array.prototype khác), nên
// deepStrictEqual so thẳng sẽ trượt dù nội dung giống hệt -- chép ra mảng thường.
const biLoai = (chuTri, capTrenCua) => [...POSCS.nguoiHoTroBiLoai(chuTri, capTrenCua)];

// Cây mẫu: sales5 (23) là cấp trên của sales4 (22); sales2 (20) là cấp trên
// của sales1 (15) -- một nhóm KHÁC.
const CAY = { 22: 23, 15: 20 };

// ----------------------------------------------------------------------
// nguoiHoTroBiLoai -- hàm thuần
// ----------------------------------------------------------------------

test('nguoiHoTroBiLoai: chưa chọn người phụ trách thì chưa loại ai', () => {
    assert.deepStrictEqual(biLoai('', CAY), []);
});

test('nguoiHoTroBiLoai: loại chính người phụ trách và cấp trên trực tiếp của họ', () => {
    assert.deepStrictEqual(biLoai('22', CAY), ['22', '23']);
});

test('nguoiHoTroBiLoai: trưởng nhóm KHÁC không bị loại', () => {
    assert.ok(!biLoai('22', CAY).includes('20'));
});

test('nguoiHoTroBiLoai: người phụ trách là cấp trên thì cấp dưới của họ vẫn làm người hỗ trợ được', () => {
    assert.deepStrictEqual(biLoai('23', CAY), ['23']);
});

test('nguoiHoTroBiLoai: cây tổ chức còn trống thì chỉ loại chính người phụ trách', () => {
    assert.deepStrictEqual(biLoai('22', {}), ['22']);
});

test('nguoiHoTroBiLoai: id dạng số cũng ra chuỗi, để so thẳng với option.value', () => {
    assert.deepStrictEqual(biLoai(22, CAY), ['22', '23']);
});

// ----------------------------------------------------------------------
// ganLocNguoiHoTro -- gắn lên ô select giả
// ----------------------------------------------------------------------

function oSelectGia(giaTri, dangChon) {
    const nghe = {};
    const options = [''].concat(giaTri).map(v => ({
        value: v, hidden: false, disabled: false, selected: v === dangChon
    }));
    return {
        options,
        get value() {
            const o = options.find(x => x.selected);
            return o ? o.value : '';
        },
        set value(v) {
            options.forEach(o => { o.selected = o.value === v; });
        },
        addEventListener(suKien, fn) {
            (nghe[suKien] = nghe[suKien] || []).push(fn);
        },
        phat(suKien) {
            (nghe[suKien] || []).forEach(fn => fn());
        }
    };
}

const dongGoiY = () => ({ textContent: '', style: { display: 'none' } });

const dangBiLoai = oHoTro => oHoTro.options
    .filter(o => o.value && o.hidden && o.disabled)
    .map(o => o.value);

test('ganLocNguoiHoTro: lọc ngay khi gắn, không đợi người dùng đổi gì', () => {
    const chuTri = oSelectGia(['20', '22', '23'], '22');
    const hoTro = oSelectGia(['20', '22', '23'], '');

    POSCS.ganLocNguoiHoTro(chuTri, hoTro, CAY, dongGoiY());

    assert.deepStrictEqual(dangBiLoai(hoTro), ['22', '23']);
});

test('ganLocNguoiHoTro: người dùng đổi người phụ trách thì lọc lại theo người mới', () => {
    const chuTri = oSelectGia(['15', '20', '22', '23'], '22');
    const hoTro = oSelectGia(['15', '20', '22', '23'], '');
    POSCS.ganLocNguoiHoTro(chuTri, hoTro, CAY, dongGoiY());

    chuTri.value = '15';
    chuTri.phat('change');

    // 22 và 23 phải được mở lại, không bị kẹt từ lần lọc trước.
    assert.deepStrictEqual(dangBiLoai(hoTro), ['15', '20']);
});

test('ganLocNguoiHoTro: người hỗ trợ đang chọn hoá không hợp lệ thì bỏ chọn VÀ báo', () => {
    const chuTri = oSelectGia(['22', '23'], '');
    const hoTro = oSelectGia(['22', '23'], '23');
    const goiY = dongGoiY();
    const loc = POSCS.ganLocNguoiHoTro(chuTri, hoTro, CAY, goiY);
    assert.strictEqual(hoTro.value, '23', 'chưa có người phụ trách thì chưa loại ai');

    // Form khách hàng điền người phụ trách theo địa bàn bằng code -- không có
    // sự kiện change, nên phải gọi tay hàm lọc trả về.
    chuTri.value = '22';
    loc();

    assert.strictEqual(hoTro.value, '');
    assert.strictEqual(goiY.style.display, 'block');
    assert.match(goiY.textContent, /cấp trên trực tiếp/);
});

test('ganLocNguoiHoTro: người hỗ trợ hợp lệ thì giữ nguyên, không báo gì', () => {
    const chuTri = oSelectGia(['20', '22', '23'], '22');
    const hoTro = oSelectGia(['20', '22', '23'], '20'); // trưởng nhóm khác
    const goiY = dongGoiY();

    POSCS.ganLocNguoiHoTro(chuTri, hoTro, CAY, goiY);

    assert.strictEqual(hoTro.value, '20');
    assert.strictEqual(goiY.style.display, 'none');
});

test('ganLocNguoiHoTro: chọn lại người hỗ trợ thì tắt dòng báo cũ', () => {
    const chuTri = oSelectGia(['20', '22', '23'], '22');
    const hoTro = oSelectGia(['20', '22', '23'], '23');
    const goiY = dongGoiY();
    POSCS.ganLocNguoiHoTro(chuTri, hoTro, CAY, goiY);
    assert.strictEqual(goiY.style.display, 'block');

    hoTro.value = '20';
    hoTro.phat('change');

    assert.strictEqual(goiY.style.display, 'none');
});

/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phần chung (tab "Bắt đầu"):
 * đăng nhập, lần đầu đăng nhập, quên mật khẩu, thanh trên / thanh bên, trang
 * chủ, thông báo, thông tin cá nhân.
 * Chạy: node tools/guide/capture.mjs general   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/general.jsp -- sửa bên này thì soát lại bên kia.
 *
 * CHUẨN BỊ BẢN SAO CSDL trước khi chụp (KHÔNG làm trên poscs_db):
 *   - cskh1 (id 17): must_change_password = 1  -> ảnh 02, bị buộc đổi mật khẩu tạm.
 *   - sales5 (id 23): gender, date_of_birth, citizen_id, phone = NULL -> ảnh 03,
 *     bị buộc bổ sung hồ sơ.
 * Ảnh 05, 06 đi thật luồng quên mật khẩu của sales4: Tomcat thử phải chạy
 * với MAIL_* để trống (EmailUtil in "Ma OTP: ..." ra log thay vì gửi mail),
 * và biến POSCS_TOMCAT_LOG trỏ vào file log đó để kịch bản đọc mã OTP.
 * Ảnh 06 chỉ điền mật khẩu mới, KHÔNG bấm Đặt lại -- mật khẩu sales4 giữ
 * nguyên. Các form khác cũng chỉ điền ví dụ, không gửi.
 */
import { readFileSync } from 'node:fs';
import { setTimeout as sleep } from 'node:timers/promises';

const RESET_USER = 'sales4';

function otpLines() {
    const log = process.env.POSCS_TOMCAT_LOG;
    if (!log) {
        throw new Error('Đặt POSCS_TOMCAT_LOG=<log của Tomcat thử> để đọc mã OTP (xem đầu file).');
    }
    return readFileSync(log, 'utf8').match(/Ma OTP: \d{6}/g) || [];
}

// Bước 1 của luồng quên mật khẩu: gửi tên đăng nhập, chờ mã mới hiện trong log.
async function requestOtp(page) {
    const before = otpLines().length;
    await page.type('#username', RESET_USER);
    await page.clickAndWait('#forgotPasswordForm button[type=submit]');
    for (let i = 0; i < 40; i++) {
        const lines = otpLines();
        if (lines.length > before) {
            return lines[lines.length - 1].slice(-6);
        }
        await sleep(250);
    }
    throw new Error('Không thấy mã OTP mới trong log Tomcat -- MAIL_* có để trống không?');
}

// Điền 6 ô OTP như người dùng gõ từng số.
async function fillOtp(page, code) {
    await page.eval(`(() => {
        const code = ${JSON.stringify(code)};
        document.querySelectorAll('#otpInputs input').forEach((el, i) => {
            el.value = code[i];
            el.dispatchEvent(new Event('input', { bubbles: true }));
        });
    })()`);
    await page.settle(300);
}

async function openDropdown(page, toggleSelector) {
    await page.click(toggleSelector);
    await page.waitFor('.dropdown-menu.show');
    await page.settle(400);
}

export default {
    viewport: { width: 1600, height: 900 },
    shots: [
        // ===== 1. Đăng nhập =====
        {
            file: '01-dang-nhap.png',
            user: null,
            url: '/login.jsp',
            marks: [
                // Khoanh cả khối nhãn + ô: khoanh riêng ô thì số đè chữ đầu của nhãn.
                { sel: '.mb-4', text: 'Tên đăng nhập', n: 1 },
                { sel: '.mb-4', text: 'Mật khẩu', n: 2 },
                { sel: 'a[href="forgotPassword.jsp"]', n: 3 },
                { sel: '#loginForm button[type=submit]', n: 4 }
            ]
        },

        // ===== 2. Lần đầu đăng nhập =====
        {
            file: '02-doi-mat-khau-tam.png',
            user: 'cskh1',
            onboarding: true,
            url: '/changePassword.jsp?onboarding=1',
            // Điền ví dụ -- KHÔNG bấm Cập nhật mật khẩu.
            before: async (page) => {
                await page.type('#oldPassword', 'Poscs@123');
                await page.type('#newPassword', 'PhuongPOSTEF@2026');
                await page.type('#confirmPassword', 'PhuongPOSTEF@2026');
            },
            marks: [
                { sel: '.alert-error', n: 1 },
                { sel: '.form-group', text: 'Mật khẩu hiện tại', n: 2 },
                { sel: '.form-group', text: 'Mật khẩu mới', n: 3 },
                { sel: '.form-group', text: 'Xác nhận mật khẩu mới', n: 4 },
                { sel: '.hint-list', n: 5 },
                { sel: '#changePasswordForm button[type=submit]', n: 6 }
            ],
            clip: '.auth-content .container'
        },
        {
            file: '03-bo-sung-ho-so.png',
            user: 'sales5',
            url: '/updateProfile?onboarding=1',
            marks: [
                { sel: '.alert', text: 'bổ sung đầy đủ', n: 1 },
                { sel: '.field-row', text: 'Giới tính', n: 2 },
                { sel: '.field-row', text: 'Ngày sinh', n: 2 },
                { sel: '.field-row', text: 'Số CCCD/CMND', n: 2 },
                { sel: '.field-row', text: 'Số điện thoại', n: 2 },
                // Dải nhắc không kể địa chỉ, nhưng lưu vẫn bắt buộc chọn tới xã / phường.
                { sel: '.field-row', text: 'Tỉnh / Thành phố', n: 3 },
                { sel: '.field-row', text: 'Xã / Phường', n: 3 },
                { sel: '.action-bar button[type=submit]', n: 4 }
            ],
            clip: '.page-container'
        },

        // ===== 3. Quên mật khẩu =====
        {
            file: '04-quen-mat-khau.png',
            user: null,
            url: '/forgotPassword.jsp',
            before: async (page) => { await page.type('#username', RESET_USER); },
            marks: [
                { sel: '.mb-4', text: 'Tên đăng nhập', n: 1 },
                { sel: '#forgotPasswordForm button[type=submit]', n: 2 },
                { sel: '.alert-info-custom', n: 3 }
            ]
        },
        {
            file: '05-nhap-otp.png',
            user: null,
            url: '/forgotPassword.jsp',
            // Gửi tên đăng nhập thật, rồi điền một mã VÍ DỤ -- không bấm Xác nhận.
            before: async (page) => {
                await requestOtp(page);
                await fillOtp(page, '482915');
            },
            marks: [
                { sel: '#otpInputs', n: 1 },
                // Cả dòng chữ: khoanh riêng con số / link thì khung đè chữ bên cạnh.
                { sel: '.expiry-text', n: 2, pad: 6 },
                { sel: '#submitBtn', n: 3 },
                { sel: '.resend-text', n: 4, pad: 6 }
            ]
        },
        {
            file: '06-dat-lai-mat-khau.png',
            user: null,
            url: '/forgotPassword.jsp',
            // Đi thật tới bước 3 bằng mã trong log, rồi chỉ ĐIỀN mật khẩu mới.
            before: async (page) => {
                const code = await requestOtp(page);
                await fillOtp(page, code);
                await page.clickAndWait('#submitBtn');
                await page.type('#newPassword', 'NgocAnh@POSTEF26');
                await page.type('#confirmPassword', 'NgocAnh@POSTEF26');
                await page.settle(600); // thanh độ mạnh có hiệu ứng 0,3 giây
            },
            marks: [
                { sel: '.mb-4', text: 'Mật khẩu mới', n: 1 },
                { sel: '.mb-3', text: 'Xác nhận mật khẩu mới', n: 2 },
                { sel: '.hint-list', n: 3 },
                { sel: '#resetPasswordForm button[type=submit]', n: 4 }
            ]
        },

        // ===== 4. Thanh trên và thanh bên =====
        {
            file: '07-thanh-tren.png',
            user: 'sales4',
            url: '/dashboard',
            before: async (page) => { await openDropdown(page, 'img.avatar-mini'); },
            marks: [
                { sel: '#sidebarToggle', n: 1, pad: 4 },
                { sel: '#sidebar', n: 2 },
                { sel: '.bell-icon', n: 3, pad: 4 },
                { sel: '.dropdown-menu.show', n: 4 }
            ]
        },

        // ===== 5. Trang chủ =====
        {
            file: '08-trang-chu.png',
            user: 'sales4',
            url: '/dashboard',
            marks: [
                { sel: '.welcome-row', n: 1 },
                { sel: '#filterScope', n: 2 },
                { sel: 'button:has(#provToggleLabel)', n: 3 },
                { sel: 'button:has(#periodToggleLabel)', n: 4 },
                { sel: '.row.g-4.mb-4', n: 5 },
                { sel: '.table-section:has(.t-contract)', n: 6 },
                { sel: '.table-section:has(.t-customer)', n: 7 }
            ],
            clip: '.page-container'
        },
        {
            file: '09-loc-tinh.png',
            user: 'sales4',
            url: '/dashboard',
            before: async (page) => {
                await page.click('button:has(#provToggleLabel)');
                await page.settle(400);
            },
            marks: [
                { within: { sel: '#provPanel' }, sel: 'label.prov-row.mine', n: 1 },
                { within: { sel: '#provPanel' }, sel: '[data-prov-mine]', n: 2 },
                { within: { sel: '#provPanel' }, sel: '.prov-apply', n: 3 }
            ],
            clip: ['.filter-bar', '#provPanel'],
            margin: 24
        },

        // ===== 6. Thông báo =====
        {
            file: '10-chuong.png',
            // kythuat1 có sẵn thông báo phiếu SLA đúng mã hiện tại; thông báo của
            // sales4 còn từ trước lần gieo lại hợp đồng (mã HD-0009 kiểu cũ).
            user: 'kythuat1',
            url: '/dashboard',
            before: async (page) => { await openDropdown(page, '.bell-icon'); },
            marks: [
                { sel: '.bell-icon', n: 1, pad: 4 },
                { sel: '.notif-dropdown .notif-item', n: 2 },
                { sel: '.notif-dropdown a[href$="/notifications"]', n: 3 }
            ],
            clip: ['.topbar-right', '.notif-dropdown'],
            margin: 24
        },
        {
            file: '11-thong-bao.png',
            user: 'kythuat1',
            url: '/notifications',
            marks: [
                { sel: '.btn-mark-all', n: 1 },
                { sel: '.notif-row .notif-row-body', n: 2 },
                { sel: '.mark-read-link', n: 3 }
            ],
            clip: '.page-container'
        },

        // ===== 7. Thông tin cá nhân =====
        {
            file: '12-ho-so.png',
            user: 'sales4',
            url: '/viewProfile',
            marks: [
                { sel: '.section-header', text: 'Thông tin công việc', n: 1, pad: 9 },
                { sel: '.btn-outline-edit', n: 2 },
                { sel: '.section-header', text: 'Địa chỉ', n: 3, pad: 9 }
            ],
            clip: '.page-container'
        },
        {
            file: '13-sua-ho-so.png',
            user: 'sales4',
            url: '/updateProfile',
            marks: [
                { sel: '.avatar-edit-btn', n: 1, pad: 4 },
                { sel: '.section-header', text: 'Thông tin công việc', n: 2, pad: 9 },
                { sel: '.field-row', text: 'Số điện thoại', n: 3 },
                { sel: '.field-row', text: 'Email cá nhân', n: 4 },
                { sel: '.field-row', text: 'Tỉnh / Thành phố', n: 5 },
                { sel: '.action-bar button[type=submit]', n: 6 }
            ],
            clip: '.page-container'
        }
    ]
};

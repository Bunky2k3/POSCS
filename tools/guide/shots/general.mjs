/*
 * Kịch bản chụp ảnh cho Hướng dẫn sử dụng -- phần chung (tab "Bắt đầu"):
 * đăng nhập, lần đầu đăng nhập, quên mật khẩu, thanh trên / thanh bên, trang
 * chủ, thông báo, thông tin cá nhân.
 * Chạy: node tools/guide/capture.mjs general   (xem capture.mjs)
 *
 * Số khoanh đỏ trong từng ảnh PHẢI khớp với số bước trong
 * web/jsp/guide/general.jsp -- sửa bên này thì soát lại bên kia.
 *
 * DỮ LIỆU MẪU: bộ Task 10.6 (db/schema.sql, V46), KHÔNG chạy trên poscs_db.
 *   - leminhhoang (Sales, Trưởng phòng Kinh doanh; Hà Nội, Bắc Ninh, Hưng Yên,
 *     Ninh Bình): ví dụ tên đăng nhập trong bài, thanh trên, thông tin cá
 *     nhân, quên mật khẩu. Có sẵn hai thông báo thật do NotificationScheduler
 *     sinh (hợp đồng 02/2026/HĐMB/POT-HTN sắp hết hạn, phiếu TK-0002 sắp quá
 *     hạn SLA) -- dùng cho ảnh chuông và trang Thông báo. Scheduler chạy 1 phút
 *     sau khi Tomcat khởi động; chụp sớm hơn thì chưa có.
 *   - phamthutrang (Sales, Phó phòng; Hải Phòng, Quảng Ninh, Lạng Sơn, Cao
 *     Bằng): trang chủ và lọc tỉnh -- địa bàn có hợp đồng nháp "Đã giao – chưa
 *     ký" (hiện "Chưa có số"), nên ô đó khác 0.
 *   - ngothilan (Tổng hợp): must_change_password = 1 sẵn trong dữ liệu mẫu ->
 *     ảnh 02, bị buộc đổi mật khẩu tạm.
 *   - tranquocbao (Admin, PGĐ): ảnh 03, xem phần chuẩn bị dưới đây.
 *
 * CHUẨN BỊ BẢN SAO CSDL cho ảnh 03 (bị buộc bổ sung hồ sơ) -- chạy ngay trước
 * `--only 03` và TRẢ LẠI NGAY sau khi chụp (tài khoản này các phần khác cũng
 * dùng; còn NULL thì mọi lần đăng nhập của nó đều bị đưa về trang bổ sung):
 *   UPDATE users SET gender = NULL, date_of_birth = NULL, citizen_id = NULL, phone = NULL
 *    WHERE username = 'tranquocbao';
 *   -- chụp 03, rồi trả lại giá trị gốc của dữ liệu mẫu:
 *   UPDATE users SET gender = 'Nam', date_of_birth = '1977-09-02',
 *          citizen_id = '034077002816', phone = '0981600002'
 *    WHERE username = 'tranquocbao';
 * tranquocbao có email cá nhân nhưng chưa có địa chỉ, nên dải nhắc kể cả địa chỉ.
 *
 * Ảnh 05, 06 đi thật luồng quên mật khẩu của leminhhoang: Tomcat thử phải chạy
 * với MAIL_* để trống (EmailUtil in "Ma OTP: ..." ra log thay vì gửi mail),
 * và biến POSCS_TOMCAT_LOG trỏ vào file stdout của Tomcat đó để kịch bản đọc
 * mã OTP. Mã OTP chỉ nằm trong phiên, không ghi CSDL. Mỗi lượt chạy đủ bộ tốn
 * 2 trong 10 lượt xin mã / 15 phút của một IP (AuthService).
 * Ảnh 06 chỉ điền mật khẩu mới, KHÔNG bấm Đặt lại -- mật khẩu leminhhoang giữ
 * nguyên. Các form khác cũng chỉ điền ví dụ, không gửi.
 */
import { readFileSync } from 'node:fs';
import { setTimeout as sleep } from 'node:timers/promises';

const RESET_USER = 'leminhhoang';

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
            user: 'ngothilan',
            onboarding: true,
            url: '/changePassword.jsp?onboarding=1',
            // Điền ví dụ -- KHÔNG bấm Cập nhật mật khẩu.
            before: async (page) => {
                await page.type('#oldPassword', 'Poscs@123');
                await page.type('#newPassword', 'NgoLan@POSTEF26');
                await page.type('#confirmPassword', 'NgoLan@POSTEF26');
                await page.settle(600); // thanh độ mạnh có hiệu ứng
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
            // Cần phần CHUẨN BỊ ở đầu file (tranquocbao thiếu bốn ô hồ sơ).
            file: '03-bo-sung-ho-so.png',
            user: 'tranquocbao',
            url: '/updateProfile?onboarding=1',
            marks: [
                { sel: '.alert', text: 'Còn thiếu', n: 1 },
                { sel: '.field-row', text: 'Giới tính', n: 2 },
                { sel: '.field-row', text: 'Ngày sinh', n: 2 },
                { sel: '.field-row', text: 'Số CCCD/CMND', n: 2 },
                { sel: '.field-row', text: 'Số điện thoại', n: 2 },
                // Chưa có địa chỉ: dải nhắc kể cả địa chỉ.
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
                await page.type('#newPassword', 'MinhHoang@POSTEF26');
                await page.type('#confirmPassword', 'MinhHoang@POSTEF26');
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
            user: 'leminhhoang',
            url: '/dashboard',
            before: async (page) => { await openDropdown(page, 'img.avatar-mini'); },
            marks: [
                { sel: '#sidebarToggle', n: 1, pad: 4 },
                { sel: '#sidebar', n: 2 },
                { sel: '.bell-icon', n: 3, pad: 9 },
                { sel: '.dropdown-menu.show', n: 4 }
            ]
        },

        // ===== 5. Trang chủ =====
        {
            // phamthutrang: có một hợp đồng "Đã giao – chưa ký" và một hợp đồng
            // nháp "Chưa có số" trong địa bàn -- ảnh thấy đủ phần mới của Giai đoạn 10.
            file: '08-trang-chu.png',
            user: 'phamthutrang',
            url: '/dashboard',
            marks: [
                { sel: '.welcome-row', n: 1 },
                { sel: '#filterScope', n: 2 },
                { sel: 'button:has(#provToggleLabel)', n: 3 },
                { sel: 'button:has(#periodToggleLabel)', n: 4 },
                // Ba ô số liệu đầu (tổng khách, hợp đồng hiệu lực, doanh thu) chung
                // một khoanh; ô "Đã giao – chưa ký" bấm được nên khoanh riêng.
                { sel: '.row.g-4.mb-4 > .col-6:nth-child(1)', n: 5 },
                { sel: '.row.g-4.mb-4 > .col-6:nth-child(2)', n: 5 },
                { sel: '.row.g-4.mb-4 > .col-6:nth-child(3)', n: 5 },
                { sel: 'a.kpi-link', n: 6 },
                { sel: '.table-section:has(.t-contract)', n: 7 },
                { sel: '.table-section:has(.t-customer)', n: 8 }
            ],
            clip: '.page-container'
        },
        {
            file: '09-loc-tinh.png',
            user: 'phamthutrang',
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
            // leminhhoang có hai thông báo thật: hợp đồng sắp hết hạn + phiếu SLA.
            user: 'leminhhoang',
            url: '/dashboard',
            before: async (page) => { await openDropdown(page, '.bell-icon'); },
            marks: [
                { sel: '.bell-icon', n: 1, pad: 9 },
                { sel: '.notif-dropdown .notif-item', n: 2 },
                { sel: '.notif-dropdown a[href$="/notifications"]', n: 3 }
            ],
            clip: ['.topbar-right', '.notif-dropdown'],
            margin: 24
        },
        {
            file: '11-thong-bao.png',
            user: 'leminhhoang',
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
            user: 'leminhhoang',
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
            user: 'leminhhoang',
            url: '/updateProfile',
            // leminhhoang chưa có địa chỉ: chọn ví dụ tỉnh rồi chờ danh sách xã /
            // phường nạp xong mới chọn -- KHÔNG bấm Lưu thay đổi.
            before: async (page) => {
                await page.select('#province', '3'); // Hà Nội
                await page.waitFor('#district option[value="14"]');
                await page.select('#district', '14'); // Phường Cầu Giấy
            },
            marks: [
                { sel: '.avatar-edit-btn', n: 1, pad: 4 },
                { sel: '.section-header', text: 'Thông tin công việc', n: 2, pad: 9 },
                { sel: '.field-row', text: 'Số điện thoại', n: 3 },
                { sel: '.field-row', text: 'Email cá nhân', n: 4 },
                { sel: '.field-row', text: 'Tỉnh / Thành phố', n: 5 },
                { sel: '.field-row', text: 'Xã / Phường', n: 5 },
                { sel: '.action-bar button[type=submit]', n: 6 }
            ],
            clip: '.page-container'
        }
    ]
};

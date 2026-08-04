<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Đổi Mật Khẩu</title>
<style>
    :root {
        --primary-dark: #003c6e;
        --primary: #0568a6;
        --primary-light: #0f9edb;
        --primary-lighter: #6fd0ff;
        --accent: #00c2ff;
        --text-dark: #0a2540;
        --text-muted: #5c7a92;
        --danger: #e2536b;
        --success: #2fbf8f;
        --bg-card: #ffffff;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }

    body {
        min-height: 100vh;
        font-family: 'Segoe UI', Arial, sans-serif;
        background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary) 45%, var(--primary-light) 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 24px;
        position: relative;
        overflow: hidden;
    }

    /* Trang trí sóng nước phía sau */
    body::before, body::after {
        content: "";
        position: absolute;
        border-radius: 50%;
        background: rgba(255,255,255,0.06);
    }
    body::before {
        width: 500px; height: 500px;
        top: -150px; left: -150px;
    }
    body::after {
        width: 400px; height: 400px;
        bottom: -120px; right: -100px;
        background: rgba(255,255,255,0.08);
    }

    .container {
        width: 100%;
        max-width: 440px;
        background: var(--bg-card);
        border-radius: 18px;
        box-shadow: 0 20px 50px rgba(0, 40, 80, 0.35);
        overflow: hidden;
        position: relative;
        z-index: 1;
        animation: fadeUp 0.5s ease;
    }

    @keyframes fadeUp {
        from { opacity: 0; transform: translateY(20px); }
        to { opacity: 1; transform: translateY(0); }
    }

    .header {
        background: linear-gradient(120deg, var(--primary-dark), var(--primary));
        padding: 34px 30px 28px;
        text-align: center;
        color: #fff;
    }

    .header .icon-circle {
        width: 64px;
        height: 64px;
        margin: 0 auto 14px;
        border-radius: 50%;
        background: rgba(255,255,255,0.15);
        display: flex;
        align-items: center;
        justify-content: center;
        border: 2px solid rgba(255,255,255,0.3);
    }

    .header .icon-circle svg {
        width: 30px;
        height: 30px;
        fill: #fff;
    }

    .header h1 {
        font-size: 22px;
        font-weight: 600;
        letter-spacing: 0.3px;
    }

    .header p {
        margin-top: 6px;
        font-size: 13.5px;
        color: rgba(255,255,255,0.85);
    }

    .form-body {
        padding: 30px 30px 34px;
    }

    .alert {
        padding: 11px 14px;
        border-radius: 10px;
        font-size: 13.5px;
        margin-bottom: 18px;
        display: flex;
        align-items: center;
        gap: 8px;
    }
    .alert-error {
        background: #fdecef;
        color: var(--danger);
        border: 1px solid #f6c3cd;
    }
    .alert-success {
        background: #e8faf3;
        color: var(--success);
        border: 1px solid #b9ecd8;
    }

    .form-group {
        margin-bottom: 20px;
        position: relative;
    }

    .form-group label {
        display: block;
        font-size: 13.5px;
        font-weight: 600;
        color: var(--text-dark);
        margin-bottom: 7px;
    }

    .input-wrap {
        position: relative;
        display: flex;
        align-items: center;
    }

    .input-wrap .icon-left {
        position: absolute;
        left: 13px;
        width: 18px;
        height: 18px;
        fill: var(--text-muted);
        pointer-events: none;
    }

    .input-wrap input {
        width: 100%;
        padding: 12px 42px 12px 40px;
        border: 1.5px solid #d9e6ee;
        border-radius: 10px;
        font-size: 14.5px;
        color: var(--text-dark);
        outline: none;
        transition: border-color 0.2s, box-shadow 0.2s;
        background: #f7fbfd;
    }

    .input-wrap input:focus {
        border-color: var(--primary-light);
        box-shadow: 0 0 0 3px rgba(15, 158, 219, 0.15);
        background: #fff;
    }

    .toggle-eye {
        position: absolute;
        right: 12px;
        cursor: pointer;
        width: 19px;
        height: 19px;
        fill: var(--text-muted);
        user-select: none;
    }
    .toggle-eye:hover { fill: var(--primary); }

    .error-text {
        color: var(--danger);
        font-size: 12.5px;
        margin-top: 6px;
        display: none;
    }

    .strength-meter {
        margin-top: 8px;
        height: 5px;
        border-radius: 3px;
        background: #e6eef3;
        overflow: hidden;
    }
    .strength-meter-bar {
        height: 100%;
        width: 0%;
        border-radius: 3px;
        transition: width 0.3s ease, background 0.3s ease;
    }
    .strength-label {
        font-size: 11.5px;
        color: var(--text-muted);
        margin-top: 4px;
        display: block;
    }

    .btn-submit {
        width: 100%;
        padding: 13px;
        border: none;
        border-radius: 10px;
        background: linear-gradient(120deg, var(--primary), var(--primary-light));
        color: #fff;
        font-size: 15px;
        font-weight: 600;
        cursor: pointer;
        margin-top: 8px;
        letter-spacing: 0.3px;
        transition: transform 0.15s, box-shadow 0.15s, opacity 0.15s;
        box-shadow: 0 8px 18px rgba(5, 104, 166, 0.35);
    }
    .btn-submit:hover { transform: translateY(-1px); box-shadow: 0 10px 22px rgba(5, 104, 166, 0.45); }
    .btn-submit:active { transform: translateY(0); }

    .back-link {
        display: block;
        text-align: center;
        margin-top: 18px;
        font-size: 13.5px;
        color: var(--primary);
        text-decoration: none;
        font-weight: 500;
    }
    .back-link:hover { text-decoration: underline; }

    .hint-list {
        font-size: 12px;
        color: var(--text-muted);
        margin-top: 10px;
        padding-left: 18px;
        line-height: 1.6;
    }

    @media (max-width: 480px) {
        .header { padding: 28px 22px 24px; }
        .form-body { padding: 24px 22px 28px; }
    }
</style>
</head>
<body>

<div class="container">
    <div class="header">
        <div class="icon-circle">
            <svg viewBox="0 0 24 24"><path d="M12 1a5 5 0 0 0-5 5v3H6a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2h-1V6a5 5 0 0 0-5-5zm0 2a3 3 0 0 1 3 3v3H9V6a3 3 0 0 1 3-3zm0 10a2 2 0 0 1 1 3.73V19a1 1 0 1 1-2 0v-2.27A2 2 0 0 1 12 13z"/></svg>
        </div>
        <h1>Đổi Mật Khẩu</h1>
        <p>Bảo mật tài khoản của bạn với mật khẩu mới</p>
    </div>

    <div class="form-body">

        <%-- Khu vực này dùng để hiển thị thông báo lỗi/thành công khi có xử lý backend sau này --%>

        <form id="changePasswordForm" method="post" onsubmit="return validateForm();">

            <div class="form-group">
                <label for="oldPassword">Mật khẩu hiện tại</label>
                <div class="input-wrap">
                    <svg class="icon-left" viewBox="0 0 24 24"><path d="M12 17a2 2 0 0 0 2-2 2 2 0 0 0-2-2 2 2 0 0 0-2 2 2 2 0 0 0 2 2zm6-9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V6a5 5 0 0 1 10 0v2h1zM12 3a3 3 0 0 0-3 3v2h6V6a3 3 0 0 0-3-3z"/></svg>
                    <input type="password" id="oldPassword" name="oldPassword" placeholder="Nhập mật khẩu hiện tại" required>
                    <svg class="toggle-eye" data-target="oldPassword" viewBox="0 0 24 24">
                        <path d="M12 5c-7 0-11 7-11 7s4 7 11 7 11-7 11-7-4-7-11-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/>
                    </svg>
                </div>
                <span class="error-text" id="err-oldPassword">Vui lòng nhập mật khẩu hiện tại</span>
            </div>

            <div class="form-group">
                <label for="newPassword">Mật khẩu mới</label>
                <div class="input-wrap">
                    <svg class="icon-left" viewBox="0 0 24 24"><path d="M12 17a2 2 0 0 0 2-2 2 2 0 0 0-2-2 2 2 0 0 0-2 2 2 2 0 0 0 2 2zm6-9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V6a5 5 0 0 1 10 0v2h1zM12 3a3 3 0 0 0-3 3v2h6V6a3 3 0 0 0-3-3z"/></svg>
                    <input type="password" id="newPassword" name="newPassword" placeholder="Nhập mật khẩu mới" required oninput="checkStrength(this.value)">
                    <svg class="toggle-eye" data-target="newPassword" viewBox="0 0 24 24">
                        <path d="M12 5c-7 0-11 7-11 7s4 7 11 7 11-7 11-7-4-7-11-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/>
                    </svg>
                </div>
                <div class="strength-meter"><div class="strength-meter-bar" id="strengthBar"></div></div>
                <span class="strength-label" id="strengthLabel">Độ mạnh mật khẩu</span>
                <span class="error-text" id="err-newPassword">Mật khẩu mới phải có ít nhất 8 ký tự</span>
            </div>

            <div class="form-group">
                <label for="confirmPassword">Xác nhận mật khẩu mới</label>
                <div class="input-wrap">
                    <svg class="icon-left" viewBox="0 0 24 24"><path d="M12 17a2 2 0 0 0 2-2 2 2 0 0 0-2-2 2 2 0 0 0-2 2 2 2 0 0 0 2 2zm6-9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V6a5 5 0 0 1 10 0v2h1zM12 3a3 3 0 0 0-3 3v2h6V6a3 3 0 0 0-3-3z"/></svg>
                    <input type="password" id="confirmPassword" name="confirmPassword" placeholder="Nhập lại mật khẩu mới" required>
                    <svg class="toggle-eye" data-target="confirmPassword" viewBox="0 0 24 24">
                        <path d="M12 5c-7 0-11 7-11 7s4 7 11 7 11-7 11-7-4-7-11-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/>
                    </svg>
                </div>
                <span class="error-text" id="err-confirmPassword">Mật khẩu xác nhận không khớp</span>
            </div>

            <ul class="hint-list">
                <li>Tối thiểu 8 ký tự</li>
                <li>Nên có chữ hoa, chữ thường và số</li>
                <li>Không trùng với mật khẩu cũ</li>
            </ul>

            <button type="submit" class="btn-submit">Cập nhật mật khẩu</button>
        </form>

        <a href="profile.jsp" class="back-link">← Quay lại trang cá nhân</a>
    </div>
</div>

<script>
    // Toggle hiện / ẩn mật khẩu
    document.querySelectorAll('.toggle-eye').forEach(function (eye) {
        eye.addEventListener('click', function () {
            var input = document.getElementById(eye.getAttribute('data-target'));
            input.type = (input.type === 'password') ? 'text' : 'password';
        });
    });

    // Đánh giá độ mạnh mật khẩu
    function checkStrength(value) {
        var bar = document.getElementById('strengthBar');
        var label = document.getElementById('strengthLabel');
        var score = 0;

        if (value.length >= 8) score++;
        if (/[A-Z]/.test(value)) score++;
        if (/[0-9]/.test(value)) score++;
        if (/[^A-Za-z0-9]/.test(value)) score++;

        var colors = ['#e2536b', '#e2536b', '#f5a623', '#2fbf8f', '#0568a6'];
        var texts = ['Rất yếu', 'Yếu', 'Trung bình', 'Mạnh', 'Rất mạnh'];
        var percents = [15, 30, 55, 80, 100];

        if (value.length === 0) {
            bar.style.width = '0%';
            label.textContent = 'Độ mạnh mật khẩu';
            return;
        }
        bar.style.width = percents[score] + '%';
        bar.style.background = colors[score];
        label.textContent = texts[score];
    }

    // Validate form phía client trước khi submit
    function validateForm() {
        var oldPass = document.getElementById('oldPassword');
        var newPass = document.getElementById('newPassword');
        var confirmPass = document.getElementById('confirmPassword');
        var valid = true;

        document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

        if (oldPass.value.trim() === '') {
            document.getElementById('err-oldPassword').style.display = 'block';
            valid = false;
        }
        if (newPass.value.length < 8) {
            document.getElementById('err-newPassword').style.display = 'block';
            valid = false;
        }
        if (confirmPass.value !== newPass.value || confirmPass.value === '') {
            document.getElementById('err-confirmPassword').style.display = 'block';
            valid = false;
        }
        return valid;
    }
</script>

</body>
</html>

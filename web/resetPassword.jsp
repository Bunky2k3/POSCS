<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đặt lại mật khẩu - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <style>
        :root {
            --primary-dark: #003c6e;
            --primary: #0568a6;
            --primary-light: #0f9edb;
            --primary-lighter: #6fd0ff;
            --accent: #00c2ff;
            --danger: #e2536b;
            --success: #2fbf8f;
            --warning: #f5a623;
        }

        body {
            font-family: 'Inter', sans-serif;
            min-height: 100vh;
            background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary) 50%, var(--primary-light) 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 24px;
            position: relative;
            overflow: hidden;
        }

        /* Trang trí vòng tròn mờ phía sau, đồng bộ các trang khác */
        body::before, body::after {
            content: "";
            position: absolute;
            border-radius: 50%;
            background: rgba(255,255,255,0.06);
        }
        body::before { width: 480px; height: 480px; top: -140px; left: -140px; }
        body::after  { width: 380px; height: 380px; bottom: -110px; right: -90px; background: rgba(255,255,255,0.08); }

        .card-wrapper {
            width: 100%;
            max-width: 460px;
            background: #ffffff;
            border-radius: 20px;
            box-shadow: 0 20px 50px rgba(0, 40, 80, 0.35);
            overflow: hidden;
            position: relative;
            z-index: 1;
        }

        .card-header {
            background: linear-gradient(120deg, var(--primary-dark), var(--primary));
            color: #fff;
            text-align: center;
            padding: 34px 30px 28px;
        }

        .icon-circle {
            width: 62px;
            height: 62px;
            margin: 0 auto 14px;
            border-radius: 50%;
            background: rgba(255,255,255,0.15);
            border: 2px solid rgba(255,255,255,0.3);
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .icon-circle i { font-size: 1.4rem; color: #fff; }

        .card-header h3 { font-weight: 700; margin-bottom: 6px; }
        .card-header p { font-size: 0.88rem; color: rgba(255,255,255,0.85); margin: 0; }

        .card-body-custom { padding: 30px 30px 34px; }

        .form-control {
            padding: 0.75rem 1rem;
            border-radius: 12px;
            border: 1px solid #e5e7eb;
            background-color: #f9fafb;
            font-size: 0.95rem;
            transition: all 0.3s;
        }
        .form-control:focus {
            background-color: #ffffff;
            border-color: var(--primary-light);
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
        }

        .input-group-text {
            background-color: #f9fafb;
            border: 1px solid #e5e7eb;
            border-right: none;
            color: #6b7280;
            border-radius: 12px 0 0 12px;
        }
        .form-control { border-left: none; }

        .btn-toggle-pass {
            background-color: #f9fafb;
            border: 1px solid #e5e7eb;
            border-left: none;
            color: #6b7280;
            border-radius: 0 12px 12px 0;
            cursor: pointer;
        }

        .form-label {
            font-weight: 500;
            color: #374151;
            font-size: 0.9rem;
            margin-bottom: 0.5rem;
        }

        .btn-primary {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            border: none;
            border-radius: 12px;
            padding: 0.8rem;
            font-weight: 600;
            font-size: 1rem;
            transition: all 0.3s ease;
            box-shadow: 0 8px 18px rgba(5, 104, 166, 0.3);
        }
        .btn-primary:hover {
            background: linear-gradient(120deg, var(--primary-dark), var(--primary));
            transform: translateY(-2px);
            box-shadow: 0 10px 22px rgba(5, 104, 166, 0.4);
        }

        .custom-link {
            color: var(--primary);
            font-weight: 500;
            text-decoration: none;
            transition: color 0.2s;
        }
        .custom-link:hover { color: var(--primary-dark); text-decoration: underline; }

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
        .strength-label { font-size: 11.5px; color: #6b7280; margin-top: 4px; display: block; }

        .hint-list {
            font-size: 12px;
            color: #6b7280;
            margin-top: 4px;
            padding-left: 18px;
            line-height: 1.6;
        }

        .alert-info-custom {
            background-color: #f0f9ff;
            border: 1px solid #bae6fd;
            color: var(--primary-dark);
            border-radius: 12px;
            font-size: 0.85rem;
            padding: 0.9rem 1rem;
        }

        @media (max-width: 480px) {
            .card-header { padding: 28px 22px 24px; }
            .card-body-custom { padding: 24px 22px 28px; }
        }
    </style>
</head>
<body>

    <div class="card-wrapper">
        <div class="card-header">
            <div class="icon-circle"><i class="fa-solid fa-shield-halved"></i></div>
            <h3>Đặt lại mật khẩu mới</h3>
            <p>Nhập mật khẩu mới cho tài khoản <strong>nguyenvana@company.com</strong></p>
        </div>

        <div class="card-body-custom">

            <div class="alert-info-custom mb-4">
                <i class="fa-solid fa-clock me-1"></i>
                Liên kết này còn hiệu lực trong <strong id="countdown">15:00</strong> phút.
            </div>

            <form action="#" method="POST" id="resetPasswordForm" onsubmit="return validateForm();">

                <%-- Token xác thực lấy từ đường link trong email, ví dụ: resetPassword.jsp?token=xxxxx --%>
                <input type="hidden" name="token" id="token" value="">

                <div class="mb-4">
                    <label for="newPassword" class="form-label">Mật khẩu mới</label>
                    <div class="input-group">
                        <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                        <input type="password" class="form-control" id="newPassword" name="newPassword"
                               placeholder="Nhập mật khẩu mới" required oninput="checkStrength(this.value)">
                        <span class="input-group-text btn-toggle-pass" data-target="newPassword">
                            <i class="fa-regular fa-eye"></i>
                        </span>
                    </div>
                    <div class="strength-meter"><div class="strength-meter-bar" id="strengthBar"></div></div>
                    <span class="strength-label" id="strengthLabel">Độ mạnh mật khẩu</span>
                    <span class="error-text" id="err-newPassword">Mật khẩu mới phải có ít nhất 8 ký tự</span>
                </div>

                <div class="mb-3">
                    <label for="confirmPassword" class="form-label">Xác nhận mật khẩu mới</label>
                    <div class="input-group">
                        <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                        <input type="password" class="form-control" id="confirmPassword" name="confirmPassword"
                               placeholder="Nhập lại mật khẩu mới" required>
                        <span class="input-group-text btn-toggle-pass" data-target="confirmPassword">
                            <i class="fa-regular fa-eye"></i>
                        </span>
                    </div>
                    <span class="error-text" id="err-confirmPassword">Mật khẩu xác nhận không khớp</span>
                </div>

                <ul class="hint-list mb-4">
                    <li>Tối thiểu 8 ký tự</li>
                    <li>Nên có chữ hoa, chữ thường và số</li>
                    <li>Không trùng với mật khẩu cũ</li>
                </ul>

                <div class="d-grid mb-3">
                    <button type="submit" class="btn btn-primary">
                        Đặt lại mật khẩu <i class="fa-solid fa-check ms-2"></i>
                    </button>
                </div>

                <div class="text-center">
                    <a href="login.jsp" class="custom-link" style="font-size: 0.9rem;">
                        <i class="fa-solid fa-arrow-left-long me-1"></i> Quay lại đăng nhập
                    </a>
                </div>
            </form>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Lấy token từ query string trên URL (?token=xxxxx) khi người dùng click link trong email
        (function () {
            var params = new URLSearchParams(window.location.search);
            var token = params.get('token') || '';
            document.getElementById('token').value = token;
        })();

        // Toggle hiện / ẩn mật khẩu cho cả 2 ô
        document.querySelectorAll('.btn-toggle-pass').forEach(function (toggle) {
            toggle.addEventListener('click', function () {
                var input = document.getElementById(toggle.getAttribute('data-target'));
                var icon = toggle.querySelector('i');
                if (input.type === 'password') {
                    input.type = 'text';
                    icon.classList.replace('fa-eye', 'fa-eye-slash');
                } else {
                    input.type = 'password';
                    icon.classList.replace('fa-eye-slash', 'fa-eye');
                }
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
            var newPass = document.getElementById('newPassword');
            var confirmPass = document.getElementById('confirmPassword');
            var valid = true;

            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

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

        // Đếm ngược thời gian hiệu lực của liên kết (demo giao diện, 15 phút)
        (function () {
            var totalSeconds = 15 * 60;
            var el = document.getElementById('countdown');
            var timer = setInterval(function () {
                totalSeconds--;
                if (totalSeconds <= 0) {
                    clearInterval(timer);
                    el.textContent = '00:00';
                    return;
                }
                var m = Math.floor(totalSeconds / 60);
                var s = totalSeconds % 60;
                el.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
            }, 1000);
        })();
    </script>
</body>
</html>

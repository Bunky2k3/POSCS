<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Xác thực OTP - POSCS Portal</title>

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

        .otp-inputs {
            display: flex;
            justify-content: space-between;
            gap: 10px;
            margin-bottom: 8px;
        }

        .otp-inputs input {
            width: 100%;
            aspect-ratio: 1 / 1;
            text-align: center;
            font-size: 1.4rem;
            font-weight: 700;
            color: var(--primary-dark);
            border: 1.5px solid #e5e7eb;
            border-radius: 12px;
            background-color: #f9fafb;
            transition: all 0.2s;
        }

        .otp-inputs input:focus {
            outline: none;
            background-color: #ffffff;
            border-color: var(--primary-light);
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
        }

        .otp-inputs input.filled {
            border-color: var(--primary-light);
            background-color: #f0f9ff;
        }

        .error-text {
            color: var(--danger);
            font-size: 12.5px;
            margin-top: 4px;
            margin-bottom: 16px;
            display: none;
            text-align: center;
        }

        .expiry-text {
            text-align: center;
            font-size: 0.85rem;
            color: #6b7280;
            margin-bottom: 22px;
        }
        .expiry-text strong { color: var(--primary-dark); }
        .expiry-text.expired strong { color: var(--danger); }

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
        .btn-primary:disabled {
            opacity: 0.6;
            transform: none;
            cursor: not-allowed;
        }

        .custom-link {
            color: var(--primary);
            font-weight: 500;
            text-decoration: none;
            transition: color 0.2s;
        }
        .custom-link:hover { color: var(--primary-dark); text-decoration: underline; }

        .resend-text {
            text-align: center;
            font-size: 0.85rem;
            color: #6b7280;
            margin-top: 18px;
        }

        .resend-link {
            color: var(--primary);
            font-weight: 600;
            text-decoration: none;
            cursor: pointer;
        }
        .resend-link:hover { text-decoration: underline; }
        .resend-link.disabled {
            color: #9ca3af;
            cursor: not-allowed;
            pointer-events: none;
            text-decoration: none;
        }

        @media (max-width: 480px) {
            .card-header { padding: 28px 22px 24px; }
            .card-body-custom { padding: 24px 22px 28px; }
            .otp-inputs { gap: 8px; }
            .otp-inputs input { font-size: 1.2rem; }
        }
    </style>
</head>
<body>

    <div class="card-wrapper">
        <div class="card-header">
            <div class="icon-circle"><i class="fa-solid fa-shield-halved"></i></div>
            <h3>Xác thực OTP</h3>
            <p>Mã OTP gồm 6 chữ số đã được gửi đến<br><strong>ngu***.a@company.com</strong></p>
        </div>

        <div class="card-body-custom">

            <form action="VerifyOtpServlet" method="POST" id="verifyOtpForm" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">

                <c:if test="${not empty param.error}">
                    <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.85rem; border-radius: 12px;">
                        <c:choose>
                            <c:when test="${param.error == 'expired'}">Mã OTP đã hết hạn, vui lòng quay lại và yêu cầu gửi mã mới.</c:when>
                            <c:when test="${param.error == 'invalid_otp'}">Mã OTP không đúng, vui lòng thử lại.</c:when>
                            <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                        </c:choose>
                    </div>
                </c:if>

                <%-- otpCode sẽ được ghép lại từ 6 ô input bên dưới trước khi submit --%>
                <input type="hidden" name="otpCode" id="otpCode" value="">

                <div class="otp-inputs" id="otpInputs">
                    <input type="text" inputmode="numeric" maxlength="1" autofocus>
                    <input type="text" inputmode="numeric" maxlength="1">
                    <input type="text" inputmode="numeric" maxlength="1">
                    <input type="text" inputmode="numeric" maxlength="1">
                    <input type="text" inputmode="numeric" maxlength="1">
                    <input type="text" inputmode="numeric" maxlength="1">
                </div>
                <span class="error-text" id="err-otp">Vui lòng nhập đầy đủ 6 chữ số OTP</span>

                <p class="expiry-text" id="expiryText">
                    Mã có hiệu lực trong <strong id="countdown">05:00</strong>
                </p>

                <div class="d-grid mb-3">
                    <button type="submit" class="btn btn-primary" id="submitBtn">
                        Xác nhận <i class="fa-solid fa-check ms-2"></i>
                    </button>
                </div>

                <div class="text-center">
                    <a href="login.jsp" class="custom-link" style="font-size: 0.9rem;">
                        <i class="fa-solid fa-arrow-left-long me-1"></i> Quay lại đăng nhập
                    </a>
                </div>
            </form>

            <p class="resend-text">
                Không nhận được mã?
                <a class="resend-link disabled" id="resendLink" onclick="resendOtp()">Gửi lại mã (00:30)</a>
            </p>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var inputs = document.querySelectorAll('#otpInputs input');

        inputs.forEach(function (input, index) {
            input.addEventListener('input', function () {
                this.value = this.value.replace(/[^0-9]/g, '');
                if (this.value) {
                    this.classList.add('filled');
                    if (index < inputs.length - 1) {
                        inputs[index + 1].focus();
                    }
                } else {
                    this.classList.remove('filled');
                }
            });

            input.addEventListener('keydown', function (e) {
                if (e.key === 'Backspace' && !this.value && index > 0) {
                    inputs[index - 1].focus();
                    inputs[index - 1].value = '';
                    inputs[index - 1].classList.remove('filled');
                }
            });

            input.addEventListener('paste', function (e) {
                e.preventDefault();
                var pasted = (e.clipboardData || window.clipboardData).getData('text').replace(/[^0-9]/g, '');
                for (var i = 0; i < pasted.length && i < inputs.length; i++) {
                    inputs[i].value = pasted[i];
                    inputs[i].classList.add('filled');
                }
                var nextIndex = Math.min(pasted.length, inputs.length - 1);
                inputs[nextIndex].focus();
            });
        });

        function validateForm() {
            var code = '';
            var allFilled = true;
            inputs.forEach(function (input) {
                if (!input.value) allFilled = false;
                code += input.value;
            });

            document.getElementById('err-otp').style.display = 'none';

            if (!allFilled) {
                document.getElementById('err-otp').style.display = 'block';
                return false;
            }
            document.getElementById('otpCode').value = code;
            return true;
        }

        // Đếm ngược thời gian hiệu lực OTP (5 phút)
        (function () {
            var totalSeconds = 5 * 60;
            var el = document.getElementById('countdown');
            var wrapper = document.getElementById('expiryText');
            var submitBtn = document.getElementById('submitBtn');
            var timer = setInterval(function () {
                totalSeconds--;
                if (totalSeconds <= 0) {
                    clearInterval(timer);
                    el.textContent = '00:00';
                    wrapper.classList.add('expired');
                    wrapper.innerHTML = 'Mã OTP đã <strong>hết hạn</strong>, vui lòng gửi lại mã mới';
                    submitBtn.disabled = true;
                    return;
                }
                var m = Math.floor(totalSeconds / 60);
                var s = totalSeconds % 60;
                el.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
            }, 1000);
        })();

        // Đếm ngược cho phép gửi lại mã (30 giây)
        (function () {
            var resendSeconds = 30;
            var link = document.getElementById('resendLink');
            var timer = setInterval(function () {
                resendSeconds--;
                if (resendSeconds <= 0) {
                    clearInterval(timer);
                    link.textContent = 'Gửi lại mã OTP';
                    link.classList.remove('disabled');
                    return;
                }
                link.textContent = 'Gửi lại mã (' + (resendSeconds < 10 ? '0' : '') + resendSeconds + ')';
            }, 1000);
        })();

        function resendOtp() {
            if (document.getElementById('resendLink').classList.contains('disabled')) return;
            // TODO: gọi API/servlet gửi lại OTP, ví dụ: window.location.href = 'ForgotPasswordServlet?resend=true';
        }
    </script>
</body>
</html>

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true"%>
<%
    // Ghi log lỗi ra server thay vì chỉ hiển thị trang thân thiện cho người
    // dùng -- phòng trường hợp nơi ném lỗi chưa tự log (không phải mọi
    // exception đều xuất phát từ 1 khối catch có log sẵn).
    if (exception != null) {
        System.err.println("--- LOI 500 CHUA XU LY (" + request.getRequestURI() + ") ---");
        exception.printStackTrace();
    }
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đã có lỗi xảy ra - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <style>
        :root {
            --primary-dark: #003c6e;
            --primary: #0568a6;
            --primary-light: #0f9edb;
            --danger: #dc2626;
        }
        body {
            font-family: 'Inter', sans-serif;
            background: #f3f4f6;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .error-card {
            background: #ffffff;
            border-radius: 24px;
            box-shadow: 0 20px 40px rgba(0, 60, 110, 0.15);
            max-width: 480px;
            width: 100%;
            margin: 20px;
            padding: 3rem 2.5rem;
            text-align: center;
        }
        .error-icon {
            width: 72px; height: 72px;
            border-radius: 50%;
            background: linear-gradient(135deg, var(--danger), #f87171);
            color: #fff;
            display: flex; align-items: center; justify-content: center;
            margin: 0 auto 1.5rem;
            font-size: 1.75rem;
        }
        .error-code {
            font-weight: 700;
            color: var(--danger);
            letter-spacing: 0.05em;
            font-size: 0.85rem;
            text-transform: uppercase;
        }
        .btn-back {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            border: none;
            border-radius: 12px;
            padding: 0.7rem 1.5rem;
            font-weight: 600;
            color: #fff;
            text-decoration: none;
            display: inline-block;
            margin-top: 1rem;
        }
        .btn-back:hover { color: #fff; opacity: 0.92; }
    </style>
</head>
<body>
    <div class="error-card">
        <div class="error-icon"><i class="fa-solid fa-triangle-exclamation"></i></div>
        <div class="error-code">Lỗi 500</div>
        <h3 class="fw-bold mt-2 mb-2" style="color:#111827;">Đã có lỗi xảy ra</h3>
        <p class="text-muted mb-0">Hệ thống gặp sự cố khi xử lý yêu cầu này. Vui lòng thử lại sau hoặc liên hệ quản trị viên nếu tình trạng vẫn tiếp diễn.</p>
        <a href="${pageContext.request.contextPath}/dashboard" class="btn-back">
            <i class="fa-solid fa-house me-1"></i> Về trang chủ
        </a>
    </div>
</body>
</html>

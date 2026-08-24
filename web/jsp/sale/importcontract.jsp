<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute ContractController#handleImportPdf có thể thiết lập
    trước khi forward về lại chính trang này (POST xong ở lại đây, không
    redirect, để hiển thị kết quả import ngay). Vì file PDF chỉ chứa ĐÚNG 1
    hợp đồng (khác với nhập Excel nhiều dòng), nếu có bất kỳ lỗi nào thì
    không import phần nào cả -- báo hết lỗi để người dùng sửa 1 lần:
      - importErrors        : List<String> (rỗng nếu import thành công)
      - importError         : String       (lỗi chung, vd chưa chọn file/file không đúng mẫu)
      - importSuccessCode   : String       (mã hợp đồng vừa tạo, khi thành công)
      - importSuccessId     : int          (id hợp đồng vừa tạo, để link sang trang chi tiết)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Nhập PDF hợp đồng - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 780px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 24px 28px 28px; margin-bottom: 20px; }

        .template-link {
            display: inline-flex; align-items: center; gap: 8px; color: var(--primary); font-weight: 600;
            font-size: 0.88rem; text-decoration: none; background: #eaf6ff; padding: 9px 16px; border-radius: 10px;
        }
        .template-link:hover { background: #d9f0ff; color: var(--primary-dark); }

        .file-input-wrap { margin: 18px 0; }
        .file-input-wrap input[type="file"] {
            width: 100%; padding: 10px 14px; border-radius: 10px; border: 1.5px dashed #d1d5db;
            background: #f9fafb; font-size: 0.88rem;
        }

        .btn-submit-import {
            background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; border: none;
            border-radius: 10px; padding: 10px 22px; font-weight: 600; font-size: 0.9rem;
            display: inline-flex; align-items: center; gap: 8px;
        }
        .btn-submit-import:hover { color: #fff; background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }

        .result-chip { display: inline-flex; align-items: center; gap: 8px; padding: 10px 18px; border-radius: 12px; font-weight: 700; font-size: 0.95rem; margin-bottom: 16px; }
        .result-chip.ok { background: #eafff2; color: #16a34a; }
        .result-chip a { color: inherit; text-decoration: underline; }

        .error-list { list-style: none; margin: 0; padding: 0; }
        .error-list li {
            padding: 9px 12px; border-radius: 8px; background: #fdecef; color: #7a1f2b;
            font-size: 0.85rem; margin-bottom: 6px;
        }

        .alert-plain { background: #fdecef; color: var(--danger); padding: 12px 16px; border-radius: 10px; font-size: 0.88rem; margin-bottom: 16px; }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="contract" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <a href="${pageContext.request.contextPath}/contract" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <div class="page-header-row">
            <h2>Nhập PDF hợp đồng</h2>
            <p>Dùng cho hợp đồng mới chưa có trong hệ thống: tải file mẫu, điền thông tin vào các ô rồi tải lên. Mỗi lần chỉ nhập được 1 hợp đồng.</p>
        </div>

        <c:if test="${not empty importError}">
            <div class="alert-plain"><i class="fa-solid fa-triangle-exclamation"></i> ${fn:escapeXml(importError)}</div>
        </c:if>

        <c:if test="${not empty importSuccessCode}">
            <div class="result-chip ok">
                <i class="fa-solid fa-circle-check"></i> Đã tạo hợp đồng ${fn:escapeXml(importSuccessCode)} --
                <a href="${pageContext.request.contextPath}/contract?action=view&id=${importSuccessId}">xem chi tiết</a>
            </div>
        </c:if>

        <c:if test="${not empty importErrors}">
            <div class="card-box">
                <div class="result-chip" style="background:#fdecef;color:var(--danger);">
                    <i class="fa-solid fa-circle-xmark"></i> Không nhập được -- ${fn:length(importErrors)} lỗi cần sửa
                </div>
                <ul class="error-list">
                    <c:forEach var="err" items="${importErrors}">
                        <li>${fn:escapeXml(err)}</li>
                    </c:forEach>
                </ul>
            </div>
        </c:if>

        <div class="card-box">
            <a class="template-link" href="${pageContext.request.contextPath}/contract?action=downloadImportTemplate">
                <i class="fa-solid fa-file-arrow-down"></i> Tải file mẫu (.pdf)
            </a>

            <form action="${pageContext.request.contextPath}/contract" method="POST" enctype="multipart/form-data">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="importPdf">
                <div class="file-input-wrap">
                    <input type="file" name="file" accept=".pdf" required>
                </div>
                <button type="submit" class="btn-submit-import"><i class="fa-solid fa-upload"></i> Nhập dữ liệu</button>
            </form>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Trang "Thông tin chi nhánh" (UC-55, SPEC 5.8) -- chỉ Admin, do
    BranchInfoController (/branch) forward tới. Một bản ghi duy nhất: chỉ xem
    và sửa, không có nút tạo/xoá.

    Request attribute:
      - branchInfo    : poscs.model.BranchInfo -- giá trị trong 4 ô (bản đang
                        lưu, hoặc dữ liệu vừa gửi khi lưu bị lỗi)
      - fieldErrors   : Map<String,String> lỗi theo từng ô (BR-57), rỗng khi không lỗi
      - formError     : lỗi chung khi CSDL không ghi được; null khi không có
      - loadError     : MSG-018 khi không đọc được bản ghi -- khi đó không hiện form
      - savedMessage  : MSG-057, chỉ có sau khi lưu xong (redirect kèm saved=1)
      - updatedByName, updatedAtText : dòng "Cập nhật lần cuối"; không có khi
                        chưa ai sửa từ lúc gieo dữ liệu
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thông tin chi nhánh - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 900px; margin: 32px auto; padding: 0 20px 32px; }
        .form-card { background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 15px 40px rgba(0,40,80,0.12); }
        .form-banner { background: linear-gradient(120deg, var(--primary-dark), var(--primary), var(--primary-light)); padding: 30px 34px 24px; color: #fff; }
        .form-banner h3 { font-weight: 700; margin: 0 0 4px; }
        .form-banner p { margin: 0; opacity: 0.85; font-size: 0.88rem; }
        .form-body { padding: 24px 28px 28px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 0 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row label .req { color: var(--danger); }
        .field-row label .label-note { text-transform: none; font-weight: 400; letter-spacing: normal; }
        .form-control { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .form-control:focus { background-color: #ffffff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15,158,219,0.15); }
        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }
        .error-text.show { display: block; }
        .last-updated { color: #6b7280; font-size: 0.82rem; margin-top: 4px; }

        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5,104,166,0.3); }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        .toast-msg {
            position: fixed; top: 24px; right: 24px; z-index: 999;
            background: #fff; border-left: 4px solid var(--success);
            border-radius: 12px; padding: 14px 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.15);
            display: flex; align-items: center; gap: 12px; font-size: 0.88rem; color: #111827; font-weight: 500;
            transform: translateX(130%); transition: transform 0.35s ease;
        }
        .toast-msg.show { transform: translateX(0); }
        .toast-msg i { color: var(--success); font-size: 1.2rem; }

        @media (max-width: 768px) { .form-body { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="branch" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <div class="form-card">
            <div class="form-banner">
                <h3><i class="fa-solid fa-building me-2"></i>Thông tin chi nhánh</h3>
                <p>Giám đốc chi nhánh: người ký bên mình trên hợp đồng.</p>
            </div>
            <div class="form-body">

                <c:choose>
                    <c:when test="${not empty loadError}">
                        <%-- MSG-018: không đọc được bản ghi thì không hiện form, vì lưu cũng sẽ hỏng. --%>
                        <div class="alert alert-danger py-2 px-3 mb-0" style="font-size: 0.9rem; border-radius: 12px;">
                            <c:out value="${loadError}"/>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <c:if test="${not empty formError}">
                            <div class="alert alert-danger py-2 px-3 mb-4" style="font-size: 0.9rem; border-radius: 12px;">
                                <c:out value="${formError}"/>
                            </div>
                        </c:if>

                        <form id="branchInfoForm" action="${pageContext.request.contextPath}/branch" method="POST" onsubmit="return validateForm();" novalidate>
                            <input type="hidden" name="csrfToken" value="${csrfToken}">

                            <div class="section-header"><h5>Giám đốc chi nhánh</h5></div>
                            <div class="row">
                                <div class="col-md-6 field-row">
                                    <label for="directorName">Họ tên Giám đốc chi nhánh <span class="req">*</span></label>
                                    <input type="text" class="form-control" id="directorName" name="directorName" maxlength="100" value="${fn:escapeXml(branchInfo.directorName)}">
                                    <span class="error-text${not empty fieldErrors.directorName ? ' show' : ''}" id="err-directorName"><c:out value="${fieldErrors.directorName}"/></span>
                                </div>
                                <div class="col-md-6 field-row">
                                    <label for="directorPosition">Chức vụ <span class="req">*</span></label>
                                    <input type="text" class="form-control" id="directorPosition" name="directorPosition" maxlength="100" value="${fn:escapeXml(branchInfo.directorPosition)}">
                                    <span class="error-text${not empty fieldErrors.directorPosition ? ' show' : ''}" id="err-directorPosition"><c:out value="${fieldErrors.directorPosition}"/></span>
                                </div>
                                <div class="col-md-6 field-row">
                                    <label for="phone">Điện thoại <span class="label-note">(không bắt buộc)</span></label>
                                    <input type="tel" class="form-control" id="phone" name="phone" maxlength="20" value="${fn:escapeXml(branchInfo.phone)}">
                                    <span class="error-text${not empty fieldErrors.phone ? ' show' : ''}" id="err-phone"><c:out value="${fieldErrors.phone}"/></span>
                                </div>
                                <div class="col-md-6 field-row">
                                    <label for="fax">Fax <span class="label-note">(không bắt buộc)</span></label>
                                    <input type="tel" class="form-control" id="fax" name="fax" maxlength="20" value="${fn:escapeXml(branchInfo.fax)}">
                                    <span class="error-text${not empty fieldErrors.fax ? ' show' : ''}" id="err-fax"><c:out value="${fieldErrors.fax}"/></span>
                                </div>
                            </div>

                            <c:if test="${not empty updatedByName}">
                                <div class="last-updated">
                                    <i class="fa-regular fa-clock me-1"></i>Cập nhật lần cuối bởi <strong><c:out value="${updatedByName}"/></strong><c:if test="${not empty updatedAtText}"> lúc <c:out value="${updatedAtText}"/></c:if>
                                </div>
                            </c:if>

                            <div class="action-bar">
                                <a href="${pageContext.request.contextPath}/branch" class="btn-cancel">Hủy</a>
                                <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Lưu thay đổi</button>
                            </div>
                        </form>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>

        </div>
    </div>

    <%-- MSG-057 (Toast) sau khi lưu xong. --%>
    <c:if test="${not empty savedMessage}">
        <div class="toast-msg show" id="savedToast" role="status">
            <i class="fa-solid fa-circle-check"></i>
            <span><c:out value="${savedMessage}"/></span>
        </div>
    </c:if>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        (function () {
            var toast = document.getElementById('savedToast');
            if (toast) { setTimeout(function () { toast.classList.remove('show'); }, 3500); }
        })();

        // Kiểm trước theo BR-57 cho đỡ một lượt gửi; server (BranchInfoValidator)
        // vẫn kiểm lại đầy đủ. Câu báo lỗi khớp MSG-058, MSG-015, MSG-059.
        var MSG_REQUIRED = 'Vui lòng nhập họ tên và chức vụ Giám đốc chi nhánh.';
        var MSG_PHONE = 'Số điện thoại không hợp lệ.';
        var MSG_FAX = 'Số fax không hợp lệ.';

        function isValidContact(raw) {
            var value = raw.trim();
            if (!value) { return true; }
            if (value.length > 20 || !/^\+?[0-9 .()\-]+$/.test(value)) { return false; }
            var digits = value.replace(/[^0-9]/g, '').length;
            return digits >= 8 && digits <= 15;
        }

        function showError(id, message) {
            var el = document.getElementById('err-' + id);
            el.textContent = message;
            el.classList.add('show');
        }

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.classList.remove('show'); });
            ['directorName', 'directorPosition'].forEach(function (id) {
                if (!document.getElementById(id).value.trim()) { showError(id, MSG_REQUIRED); valid = false; }
            });
            if (!isValidContact(document.getElementById('phone').value)) { showError('phone', MSG_PHONE); valid = false; }
            if (!isValidContact(document.getElementById('fax').value)) { showError('fax', MSG_FAX); valid = false; }
            return valid;
        }
    </script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

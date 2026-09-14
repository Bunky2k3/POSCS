<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ChangeRequestController#showCreateForm thiết lập:
      - presetResourceType / presetIntent / presetTargetId : điền sẵn khi người
        dùng bấm "Gửi yêu cầu" từ màn Khách hàng hoặc Hợp đồng, để họ khỏi phải
        tự chọn lại thứ mà hệ thống đã biết.

    Trang này chỉ hiện với người CÓ cấp trên -- controller trả 403 cho người
    khác, vì họ tự sửa được, gửi yêu cầu cho chính mình là vô nghĩa.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Gửi yêu cầu thay đổi - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 820px; margin: 28px auto; padding: 0 20px 32px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 12px; }
        .back-link-top:hover { text-decoration: underline; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; margin-bottom: 20px; }
        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 24px 28px 28px; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row label .req { color: var(--danger); }
        .form-control, .form-select { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .form-control:focus, .form-select:focus { background-color: #fff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }
        .hint { font-size: 0.8rem; color: #6b7280; margin-top: 6px; }

        .action-bar { display: flex; gap: 12px; margin-top: 26px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 20px; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; color: #fff; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3); }
        .btn-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; }
        @media (max-width: 768px) { .card-box { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="changerequest" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <a href="${pageContext.request.contextPath}/changerequest" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <div class="page-header-row">
            <h2>Gửi yêu cầu thay đổi</h2>
            <p>Yêu cầu sẽ được gửi tới cấp trên trực tiếp của bạn để duyệt.</p>
        </div>

        <div class="card-box">
            <c:if test="${not empty param.error}">
                <div class="alert alert-danger py-2 px-3 mb-4" style="font-size: 0.9rem; border-radius: 12px;">
                    <c:choose>
                        <c:when test="${param.error == 'invalid'}">Vui lòng chọn loại dữ liệu, loại thay đổi và mô tả nội dung đề xuất. Với thay đổi "Sửa" hoặc "Xoá" thì phải có mã bản ghi cần thay đổi.</c:when>
                        <c:when test="${param.error == 'create_failed'}">Không gửi được yêu cầu. Vui lòng thử lại.</c:when>
                        <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                    </c:choose>
                </div>
            </c:if>

            <form id="changeRequestForm" action="${pageContext.request.contextPath}/changerequest" method="POST" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="create">

                <div class="row">
                    <div class="col-md-6 field-row">
                        <label for="resourceType">Dữ liệu cần thay đổi <span class="req">*</span></label>
                        <select class="form-select" id="resourceType" name="resourceType">
                            <option value="">-- Chọn --</option>
                            <option value="Khách hàng" ${presetResourceType == 'Khách hàng' ? 'selected' : ''}>Khách hàng</option>
                            <option value="Hợp đồng" ${presetResourceType == 'Hợp đồng' ? 'selected' : ''}>Hợp đồng</option>
                        </select>
                        <span class="error-text" id="err-resourceType">Vui lòng chọn loại dữ liệu.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label for="intent">Loại thay đổi <span class="req">*</span></label>
                        <select class="form-select" id="intent" name="intent">
                            <option value="">-- Chọn --</option>
                            <option value="Tạo mới" ${presetIntent == 'Tạo mới' ? 'selected' : ''}>Tạo mới</option>
                            <option value="Sửa" ${presetIntent == 'Sửa' ? 'selected' : ''}>Sửa</option>
                            <option value="Xoá" ${presetIntent == 'Xoá' ? 'selected' : ''}>Xoá</option>
                        </select>
                        <span class="error-text" id="err-intent">Vui lòng chọn loại thay đổi.</span>
                    </div>

                    <%-- Mã bản ghi chỉ có nghĩa khi Sửa/Xoá. Với "Tạo mới" thì
                         chưa có dòng nào để trỏ tới -- JS tự ẩn ô này, và phía
                         server cũng chỉ bắt buộc trong hai trường hợp kia. --%>
                    <div class="col-md-6 field-row" id="targetIdRow">
                        <label for="targetId">Mã bản ghi cần thay đổi <span class="req">*</span></label>
                        <input type="number" class="form-control" id="targetId" name="targetId" min="1"
                               value="${presetTargetId}" placeholder="Ví dụ: 12">
                        <div class="hint">Số ID hiện trên đường dẫn trang chi tiết của bản ghi đó.</div>
                        <span class="error-text" id="err-targetId">Vui lòng nhập mã bản ghi cần thay đổi.</span>
                    </div>

                    <div class="col-12 field-row">
                        <label for="proposedContent">Nội dung đề xuất <span class="req">*</span></label>
                        <textarea class="form-control" id="proposedContent" name="proposedContent" rows="5"
                                  placeholder="Ghi rõ bạn muốn thay đổi thành gì. Ví dụ: đổi số điện thoại của khách sang 0912 345 678, đổi người phụ trách sang Lê Minh Quân."></textarea>
                        <div class="hint">Cấp trên sẽ đọc phần này rồi tự thực hiện trên màn hình tương ứng, nên càng cụ thể càng đỡ phải hỏi lại.</div>
                        <span class="error-text" id="err-proposedContent">Vui lòng mô tả nội dung đề xuất.</span>
                    </div>

                    <div class="col-12 field-row">
                        <label for="reason">Lý do</label>
                        <textarea class="form-control" id="reason" name="reason" rows="2"
                                  placeholder="Vì sao cần thay đổi (không bắt buộc)"></textarea>
                    </div>
                </div>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/changerequest" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-paper-plane me-1"></i> Gửi yêu cầu</button>
                </div>
            </form>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
    <script>
        var intentEl = document.getElementById('intent');
        var targetIdRow = document.getElementById('targetIdRow');

        function toggleTargetId() {
            var creating = intentEl.value === 'Tạo mới';
            targetIdRow.style.display = creating ? 'none' : '';
            if (creating) { document.getElementById('targetId').value = ''; }
        }
        intentEl.addEventListener('change', toggleTargetId);
        toggleTargetId();

        function validateForm() {
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });
            var valid = true;

            ['resourceType', 'intent'].forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            var content = document.getElementById('proposedContent');
            if (!content.value.trim()) { document.getElementById('err-proposedContent').style.display = 'block'; valid = false; }

            // Mã bản ghi chỉ bắt buộc khi KHÔNG phải tạo mới -- giống đúng điều
            // kiện mà ChangeRequestController.isValid áp ở phía server.
            if (intentEl.value && intentEl.value !== 'Tạo mới') {
                var target = document.getElementById('targetId');
                if (!target.value) { document.getElementById('err-targetId').style.display = 'block'; valid = false; }
            }
            return valid;
        }
    </script>
</body>
</html>

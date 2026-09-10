<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ContractController#showEditForm thiết lập trước khi forward tới trang này:
      - contract     : poscs.model.Contract (hợp đồng đang sửa)
      - customerList : List<poscs.model.Enterprise>
      - userList      : List<poscs.model.User>

    Form này chỉ sửa bản ghi trong bảng contracts. Hạng mục sản phẩm/dịch vụ
    được thêm/gỡ ở trang chi tiết hợp đồng, không sửa tại đây.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cập nhật hợp đồng - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 980px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 24px 28px 28px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 24px 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row label .req { color: var(--danger); }
        .form-control, .form-select { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .form-control:focus, .form-select:focus { background-color: #ffffff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }


        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3); }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) { .card-box { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="contract" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại chi tiết hợp đồng</a>

        <div class="page-header-row">
            <h2>Cập nhật hợp đồng</h2>
            <p>Mã hợp đồng: <strong style="color:var(--primary-dark)">${fn:escapeXml(contract.contractCode)}</strong></p>
        </div>

        <div class="card-box">
            <c:if test="${not empty param.error}">
                <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                    <c:choose>
                        <c:when test="${param.error == 'invalid_drive_link'}">Link file PDF phải là địa chỉ bắt đầu bằng http:// hoặc https://. Vui lòng dán lại.</c:when>
                        <c:when test="${param.error == 'invalid'}">Thông tin hợp đồng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.</c:when>
                        <c:when test="${param.error == 'update_failed'}">Không lưu được thay đổi. Vui lòng thử lại.</c:when>
                        <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                    </c:choose>
                </div>
            </c:if>

            <form id="createContractForm" action="${pageContext.request.contextPath}/contract" method="POST" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="update">
                <input type="hidden" name="contractId" value="${contract.contractId}">

                <div class="section-header"><h5>Thông tin chung</h5></div>
                <div class="row">
                    <div class="col-12 field-row">
                        <label>Tiêu đề hợp đồng <span class="req">*</span></label>
                        <input type="text" class="form-control" id="title" name="title" value="${fn:escapeXml(contract.title)}">
                        <span class="error-text" id="err-title">Tiêu đề không được để trống.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customer" name="enterpriseId">
                            <option value="">-- Chọn khách hàng --</option>
                            <c:forEach var="customer" items="${customerList}">
                                <option value="${customer.enterpriseId}" ${customer.enterpriseId == contract.enterpriseId ? 'selected' : ''}>${fn:escapeXml(customer.enterpriseName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-customer">Vui lòng chọn khách hàng.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Loại hợp đồng <span class="req">*</span></label>
                        <select class="form-select" id="contractType" name="contractType">
                            <option value="">-- Chọn loại hợp đồng --</option>
                            <option value="Cung cấp thiết bị" ${contract.contractType == 'Cung cấp thiết bị' ? 'selected' : ''}>Cung cấp thiết bị</option>
                            <option value="Thi công lắp đặt" ${contract.contractType == 'Thi công lắp đặt' ? 'selected' : ''}>Thi công lắp đặt</option>
                            <option value="Bảo trì bảo dưỡng" ${contract.contractType == 'Bảo trì bảo dưỡng' ? 'selected' : ''}>Bảo trì bảo dưỡng</option>
                        </select>
                        <span class="error-text" id="err-contractType">Vui lòng chọn loại hợp đồng.</span>
                    </div>

                    <div class="col-md-4 field-row">
                        <label>Ngày ký <span class="req">*</span></label>
                        <input type="date" class="form-control" id="signDate" name="signDate" value="<fmt:formatDate value="${contract.signingDate}" pattern="yyyy-MM-dd"/>">
                        <span class="error-text" id="err-dates">Ngày ký, ngày hiệu lực, ngày hết hạn không hợp lệ.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày hiệu lực <span class="req">*</span></label>
                        <input type="date" class="form-control" id="effectiveDate" name="effectiveDate" value="<fmt:formatDate value="${contract.effectiveDate}" pattern="yyyy-MM-dd"/>">
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày kết thúc <span class="req">*</span></label>
                        <input type="date" class="form-control" id="endDate" name="endDate" value="<fmt:formatDate value="${contract.endDate}" pattern="yyyy-MM-dd"/>">
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Người phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="owner" name="ownerId">
                            <option value="">-- Chọn nhân viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}" ${staff.userId == contract.ownerId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-owner">Vui lòng chọn người phụ trách.</span>
                    </div>
                    <div class="col-12 field-row">
                        <label>Link file PDF hợp đồng (Google Drive)</label>
                        <input type="url" class="form-control" id="attachmentUrl" name="attachmentUrl"
                               value="${fn:escapeXml(contract.attachmentUrl)}"
                               placeholder="VD: https://drive.google.com/file/d/...">
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            Tải bản PDF đã ký lên Drive rồi dán link vào đây. Để trống nếu chưa có.
                        </span>
                    </div>
                </div>

                <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
                <p style="font-size:0.86rem; color:#6b7280; margin:0 0 4px;">
                    Hạng mục được thêm và gỡ ở trang chi tiết hợp đồng, không sửa tại đây.
                </p>


                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Lưu thay đổi</button>
                </div>
            </form>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            var title = document.getElementById('title');
            if (!title.value.trim()) { document.getElementById('err-title').style.display = 'block'; valid = false; }

            ['customer', 'contractType', 'owner'].forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            var signDate = document.getElementById('signDate').value;
            var effectiveDate = document.getElementById('effectiveDate').value;
            var endDate = document.getElementById('endDate').value;
            if (!signDate || !effectiveDate || !endDate || !(signDate <= effectiveDate && effectiveDate <= endDate)) {
                document.getElementById('err-dates').style.display = 'block';
                valid = false;
            }

            return valid;
        }
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

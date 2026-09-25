<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%--
    Request attribute do TechnicalSupportTicketController#showEditForm
    thiết lập trước khi forward tới trang này:
      - ticket       : poscs.model.TechnicalRequest (phiếu đang sửa)
      - customerList : List<poscs.model.Enterprise>
      - userList      : List<poscs.model.User>

    Dropdown "Hợp đồng liên quan" nạp qua AJAX giống addnewTicket.jsp, JS tự
    chọn lại đúng hợp đồng hiện tại (nếu có) sau khi danh sách tải xong.

    canManage (controller đặt cho mọi action) = false nghĩa là người đang mở
    form là KỸ THUẬT VIÊN ĐƯỢC GIAO phiếu này -- người khác không vào tới đây.
--%>
<%--
    Kỹ thuật viên được giao chỉ ghi được năm trường của khối "Nhân viên kỹ
    thuật xử lý" (cộng ghi chú nội bộ) -- handleUpdate bỏ qua mọi ô khác. Trước
    đây các ô đó vẫn mở cho họ sửa: đổi Mức ưu tiên rồi bấm Lưu, trang chi tiết
    mở ra như đã lưu xong mà ưu tiên vẫn như cũ. Giờ khoá hẳn (chỉ để xem).
    Đây chỉ là lớp trình bày; chốt chặn thật vẫn ở controller.
--%>
<c:set var="lockGeneral" value="${not canManage}"/>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cập nhật phiếu hỗ trợ - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 900px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 24px 28px 28px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 24px 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }
        /* Dòng nhắc bên phải tiêu đề khối: ai viết phần này, hoặc phần này đi đâu. */
        .section-hint {
            font-size: 0.78rem; font-weight: 600; color: #6b7280;
            display: inline-flex; align-items: center; gap: 6px;
        }
        .section-hint i { color: #9ca3af; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row label .req { color: var(--danger); }
        .form-control, .form-select { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .form-control:focus, .form-select:focus { background-color: #ffffff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }
        .form-check-label { font-size: 0.88rem; color: #374151; }

        /* ===== Box chọn khách hàng / hợp đồng (thay cho dropdown) ===== */
        .picker-field {
            display: flex; align-items: center; justify-content: space-between; cursor: pointer;
            padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb;
            background-color: #f9fafb; font-size: 0.9rem; color: #111827;
        }
        .picker-field:hover { border-color: var(--primary-light); }
        /* Chữ đậm như ô chọn bị khoá của Bootstrap bên cạnh (kỹ thuật viên xem
           khách hàng/hợp đồng ở đây); trạng thái chờ vẫn nhạt nhờ .picker-placeholder. */
        .picker-field.disabled { cursor: not-allowed; color: #374151; background-color: #e9ecef; }
        .picker-field i { color: #9ca3af; font-size: 0.85rem; flex-shrink: 0; margin-left: 10px; }
        .picker-placeholder { color: #9ca3af; }

        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 22px 24px 6px; display: flex; justify-content: space-between; align-items: center; }
        .modal-title { font-weight: 700; color: var(--primary-dark); font-size: 1.02rem; }
        .modal-body { padding: 10px 24px 24px; }
        .picker-search { margin-bottom: 12px; }
        .picker-list { max-height: 320px; overflow-y: auto; border: 1px solid #eef2f6; border-radius: 10px; }
        .picker-item { padding: 10px 14px; cursor: pointer; border-bottom: 1px solid #f3f4f6; }
        .picker-item:last-child { border-bottom: none; }
        .picker-item:hover { background: #f0f9ff; }
        .picker-item-title { font-weight: 600; font-size: 0.88rem; color: #111827; }
        .picker-item-sub { font-size: 0.76rem; color: #9ca3af; margin-top: 2px; }
        .picker-empty { padding: 24px; text-align: center; color: #9ca3af; font-size: 0.85rem; }

        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3); }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) {
            .card-box { padding: 20px 18px 22px; }
            /* Nhãn bên phải tiêu đề khối có thể chứa họ tên đầy đủ, tên dài
               thì không đủ chỗ nằm cùng dòng -- cho xuống hàng thay vì đẩy
               tràn ra ngoài thẻ. Giống trang chi tiết phiếu. */
            .section-header { flex-wrap: wrap; gap: 6px; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="ticket" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/ticket?action=view&id=${ticket.ticketId}" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại chi tiết phiếu</a>

        <div class="page-header-row">
            <h2>Cập nhật phiếu hỗ trợ</h2>
            <p>Mã phiếu: <strong style="color:var(--primary-dark)">${fn:escapeXml(ticket.ticketCode)}</strong></p>
        </div>

        <div class="card-box">
            <form id="updateTicketForm" action="${pageContext.request.contextPath}/ticket" method="POST" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="update">
                <input type="hidden" name="ticketId" value="${ticket.ticketId}">
                <%-- Trước đây form này không hiển thị lỗi server trả về, nên mọi
                     redirect kèm ?error=... đều im lặng: người dùng thấy lại
                     đúng cái form và không hiểu vì sao không lưu được. --%>
                <c:if test="${not empty param.error}">
                    <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                        <c:choose>
                            <c:when test="${param.error == 'contract_mismatch'}">Hợp đồng đã chọn không thuộc về khách hàng của phiếu này. Vui lòng chọn lại hợp đồng, hoặc bỏ gắn hợp đồng.</c:when>
                            <c:when test="${param.error == 'invalid'}">Vui lòng nhập đầy đủ các trường bắt buộc.</c:when>
                            <c:when test="${param.error == 'update_failed'}">Không lưu được thay đổi. Vui lòng thử lại.</c:when>
                            <c:when test="${param.error == 'create_failed'}">Không tạo được phiếu hỗ trợ. Vui lòng thử lại.</c:when>
                            <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                        </c:choose>
                    </div>
                </c:if>
                <c:if test="${lockGeneral}">
                    <div class="alert alert-primary py-2 px-3 mb-3 lock-note" style="font-size: 0.9rem; border-radius: 12px;">
                        <i class="fa-solid fa-circle-info me-1"></i>
                        Bạn là kỹ thuật viên được giao phiếu này: chỉ sửa được khối <strong>Nhân viên kỹ thuật xử lý</strong> và <strong>Ghi chú nội bộ</strong>. Các ô còn lại chỉ để xem; cần đổi thì báo Sales hoặc Admin.
                    </div>
                </c:if>

                <div class="section-header">
                    <h5>Thông tin chung</h5>
                    <c:if test="${lockGeneral}"><span class="section-hint"><i class="fa-solid fa-lock"></i> Chỉ xem</span></c:if>
                </div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Khách hàng <span class="req">*</span></label>
                        <div class="picker-field ${lockGeneral ? 'disabled' : ''}" id="customerPickerField" onclick="openCustomerPicker()">
                            <span id="customerPickerText" class="picker-placeholder">-- Chọn khách hàng --</span>
                            <i class="fa-solid fa-magnifying-glass"></i>
                        </div>
                        <input type="hidden" id="customer" name="enterpriseId" value="">
                        <span class="error-text" id="err-customer">Vui lòng chọn khách hàng.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Hợp đồng liên quan</label>
                        <div class="picker-field disabled" id="contractPickerField" onclick="openContractPicker()">
                            <span id="contractPickerText" class="picker-placeholder">Đang tải...</span>
                            <i class="fa-solid fa-magnifying-glass"></i>
                        </div>
                        <input type="hidden" id="contract" name="contractId" value="">
                        <%-- Đánh dấu ô chọn hợp đồng đã thật sự dùng được. Controller cần
                             biết "để trống vì AJAX chưa xong" khác "cố ý bỏ liên kết". --%>
                        <input type="hidden" id="contractLoaded" name="contractLoaded" value="">
                    </div>

                    <div class="col-md-4 field-row">
                        <label>Loại phiếu <span class="req">*</span></label>
                        <select class="form-select" id="ticketType" name="ticketType" ${lockGeneral ? 'disabled' : ''}>
                            <option value="">-- Chọn loại phiếu --</option>
                            <option value="Bảo hành" ${ticket.ticketType == 'Bảo hành' ? 'selected' : ''}>Bảo hành</option>
                            <option value="Bảo trì" ${ticket.ticketType == 'Bảo trì' ? 'selected' : ''}>Bảo trì</option>
                            <option value="Sửa chữa" ${ticket.ticketType == 'Sửa chữa' ? 'selected' : ''}>Sửa chữa</option>
                            <option value="Tư vấn" ${ticket.ticketType == 'Tư vấn' ? 'selected' : ''}>Tư vấn</option>
                            <option value="Khác" ${ticket.ticketType == 'Khác' ? 'selected' : ''}>Khác</option>
                        </select>
                        <span class="error-text" id="err-ticketType">Vui lòng chọn loại phiếu.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Mức ưu tiên <span class="req">*</span></label>
                        <select class="form-select" id="priority" name="priority" ${lockGeneral ? 'disabled' : ''}>
                            <option value="">-- Chọn mức ưu tiên --</option>
                            <option value="Khẩn cấp" ${ticket.priority == 'Khẩn cấp' ? 'selected' : ''}>Khẩn cấp</option>
                            <option value="Cao" ${ticket.priority == 'Cao' ? 'selected' : ''}>Cao</option>
                            <option value="Bình thường" ${ticket.priority == 'Bình thường' ? 'selected' : ''}>Bình thường</option>
                            <option value="Thấp" ${ticket.priority == 'Thấp' ? 'selected' : ''}>Thấp</option>
                        </select>
                        <span class="error-text" id="err-priority">Vui lòng chọn mức ưu tiên.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Kênh tiếp nhận <span class="req">*</span></label>
                        <select class="form-select" id="receptionChannel" name="receptionChannel" ${lockGeneral ? 'disabled' : ''}>
                            <option value="">-- Chọn kênh tiếp nhận --</option>
                            <option value="Điện thoại" ${ticket.receptionChannel == 'Điện thoại' ? 'selected' : ''}>Điện thoại</option>
                            <option value="Email" ${ticket.receptionChannel == 'Email' ? 'selected' : ''}>Email</option>
                            <option value="Trực tiếp" ${ticket.receptionChannel == 'Trực tiếp' ? 'selected' : ''}>Trực tiếp</option>
                            <option value="Website" ${ticket.receptionChannel == 'Website' ? 'selected' : ''}>Website</option>
                        </select>
                        <span class="error-text" id="err-receptionChannel">Vui lòng chọn kênh tiếp nhận.</span>
                    </div>

                    <%-- Hạn xử lý SLA: dashboard ("phiếu sắp/đã quá hạn") và lịch nhắc
                         của NotificationScheduler đều dựa vào cột này. Trước đây không
                         có ô nhập nên phiếu tạo từ hệ thống luôn có sla_deadline NULL,
                         hai chức năng đó coi như không chạy. Không bắt buộc. --%>
                    <div class="col-md-6 field-row">
                        <label>Hạn xử lý (SLA)</label>
                        <input type="datetime-local" class="form-control" id="slaDeadline" name="slaDeadline" value="<fmt:formatDate value="${ticket.slaDeadline}" pattern="yyyy-MM-dd'T'HH:mm"/>" ${lockGeneral ? 'disabled' : ''}>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Kỹ thuật viên phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="technician" name="assignedTechnicianId" ${lockGeneral ? 'disabled' : ''}>
                            <option value="">-- Chọn kỹ thuật viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}" ${staff.userId == ticket.assignedTechnicianId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-technician">Vui lòng chọn kỹ thuật viên phụ trách.</span>
                    </div>

                    <div class="col-md-6 field-row" style="display:flex; align-items:center;">
                        <div class="form-check">
                            <input type="checkbox" class="form-check-input" id="isWarranty" name="isWarranty" ${ticket.warranty ? 'checked' : ''} ${lockGeneral ? 'disabled' : ''}>
                            <label class="form-check-label" for="isWarranty">Còn trong thời hạn bảo hành</label>
                        </div>
                    </div>

                </div>

                <%--
                  Từ đây xuống chia khối theo NGƯỜI VIẾT RA NỘI DUNG, khớp với
                  trang chi tiết phiếu: một khối là lời người tiếp nhận, một
                  khối là đánh giá của kỹ thuật viên.

                  Ở form thì cách chia này còn nói thêm một điều: đúng năm
                  trường trong khối "Nhân viên kỹ thuật xử lý" (trạng thái,
                  nhóm nguyên nhân, nguyên nhân, phương hướng xử lý, kết quả
                  xử lý) là năm trường role Kỹ thuật được sửa trên phiếu giao
                  cho mình -- xem
                  PERMISSIONS.md. Người ở role đó mở form ra là thấy ngay phần
                  nào của mình, không phải thử rồi ăn 403.
                --%>
                <div class="section-header">
                    <h5>Người tạo phiếu ghi nhận</h5>
                    <c:if test="${ticket.createdByUser != null}">
                        <span class="section-hint">
                            <i class="fa-regular fa-user"></i> ${fn:escapeXml(ticket.createdByUser.fullName)}
                            <c:if test="${ticket.createdDate != null}">
                                &middot; <fmt:formatDate value="${ticket.createdDate}" pattern="dd/MM/yyyy"/>
                            </c:if>
                        </span>
                    </c:if>
                </div>
                <div class="row">
                    <div class="col-12 field-row">
                        <label>Mô tả sự cố <span class="req">*</span></label>
                        <textarea class="form-control" id="description" name="description" rows="4" ${lockGeneral ? 'disabled' : ''}>${fn:escapeXml(ticket.description)}</textarea>
                        <span class="error-text" id="err-description">Vui lòng mô tả sự cố.</span>
                    </div>
                </div>

                <%--
                  Không gắn TÊN kỹ thuật viên vào tiêu đề khối này như bên
                  trang xem: ở form, người phụ trách là ô chọn sửa được ngay
                  phía trên, gắn tên tĩnh vào đây thì đổi dropdown một cái là
                  tiêu đề nói sai. Thay bằng một dòng nhắc vai trò.
                --%>
                <div class="section-header">
                    <h5>Nhân viên kỹ thuật xử lý</h5>
                    <span class="section-hint"><i class="fa-solid fa-screwdriver-wrench"></i> Phần do kỹ thuật viên đánh giá</span>
                </div>
                <div class="row">
                    <div class="col-md-4 field-row">
                        <label>Trạng thái <span class="req">*</span></label>
                        <select class="form-select" id="status" name="status">
                            <option value="Mới tiếp nhận" ${ticket.status == 'Mới tiếp nhận' ? 'selected' : ''}>Mới tiếp nhận</option>
                            <option value="Đang xử lý" ${ticket.status == 'Đang xử lý' ? 'selected' : ''}>Đang xử lý</option>
                            <option value="Đã đóng" ${ticket.status == 'Đã đóng' ? 'selected' : ''}>Đã đóng</option>
                        </select>
                    </div>
                    <%-- Nguyên nhân đặt TRƯỚC kết quả, theo đúng mạch khách
                         yêu cầu: hiện tượng -> nguyên nhân -> kết quả. Chỉ có
                         ở form SỬA, không có ở form tạo phiếu: lúc tiếp nhận
                         chưa ai xuống hiện trường thì chưa biết vì sao hỏng. --%>
                    <div class="col-md-4 field-row">
                        <label>Nhóm nguyên nhân</label>
                        <select class="form-select" id="causeCategory" name="causeCategory">
                            <option value="">-- Chưa xác định --</option>
                            <option value="Do vận chuyển" ${ticket.causeCategory == 'Do vận chuyển' ? 'selected' : ''}>Do vận chuyển</option>
                            <option value="Do lắp đặt" ${ticket.causeCategory == 'Do lắp đặt' ? 'selected' : ''}>Do lắp đặt</option>
                            <option value="Do thiết bị" ${ticket.causeCategory == 'Do thiết bị' ? 'selected' : ''}>Do thiết bị</option>
                            <option value="Khác" ${ticket.causeCategory == 'Khác' ? 'selected' : ''}>Khác</option>
                        </select>
                    </div>
                    <div class="col-12 field-row">
                        <label>Nguyên nhân sự cố</label>
                        <textarea class="form-control" id="rootCause" name="rootCause" rows="3" placeholder="Kỹ thuật viên đánh giá vì sao xảy ra sự cố">${fn:escapeXml(ticket.rootCause)}</textarea>
                    </div>
                    <div class="col-12 field-row">
                        <label>Phương hướng xử lý</label>
                        <textarea class="form-control" id="handlingPlan" name="handlingPlan" rows="3" placeholder="Định làm gì để khắc phục (điền khi đã xác định được nguyên nhân)">${fn:escapeXml(ticket.handlingPlan)}</textarea>
                    </div>
                    <div class="col-12 field-row">
                        <label>Kết quả xử lý</label>
                        <textarea class="form-control" id="resolutionSummary" name="resolutionSummary" rows="3" placeholder="Ghi chú kết quả xử lý (điền khi đóng phiếu)">${fn:escapeXml(ticket.resolutionSummary)}</textarea>
                    </div>
                </div>

                <%--
                  Ghi chú nội bộ đứng riêng, không nằm trong hai khối trên: nó
                  KHÔNG lưu vào phiếu mà đi kèm dòng lịch sử của lần đổi trạng
                  thái này. Chỉ viết về BƯỚC CHUYỂN -- nguyên nhân, phương
                  hướng và kết quả đều đã có ô riêng ở khối trên, kể lại ở đây
                  là lặp và làm lịch sử khó đọc. Cùng lý do mà trang chi tiết để "Lịch sử xử lý"
                  tách khỏi hai khối nội dung. Ô luôn để trống khi mở form
                  (không đổ giá trị cũ vào); không đổi trạng thái thì bị bỏ qua.
                --%>
                <div class="section-header">
                    <h5>Ghi cho lịch sử xử lý</h5>
                    <span class="section-hint"><i class="fa-regular fa-clock"></i> Không lưu vào phiếu</span>
                </div>
                <div class="row">
                    <div class="col-12 field-row">
                        <%-- Nói thẳng điều kiện lưu: DAO chỉ ghi ghi chú này kèm dòng lịch
                             sử khi TRẠNG THÁI đổi. Nhãn cũ ("chỉ hiện ở lịch sử xử lý")
                             không nói vế đó, nên ghi chú viết mà không đổi trạng thái là
                             mất lặng lẽ. --%>
                        <label>Ghi chú nội bộ <span class="text-muted" style="text-transform: none; font-weight: 400;">(không bắt buộc, chỉ lưu khi đổi trạng thái)</span></label>
                        <textarea class="form-control" id="internalNote" name="internalNote" rows="2" placeholder="Vì sao đổi trạng thái lần này? (nguyên nhân và kết quả đã có ô riêng ở trên)"></textarea>
                    </div>
                </div>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/ticket?action=view&id=${ticket.ticketId}" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Lưu thay đổi</button>
                </div>
            </form>
        </div>
    </div>

        </div>
    </div>

    <!-- ===== Box chọn khách hàng ===== -->
    <div class="modal fade" id="customerPickerModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
            <div class="modal-content">
                <div class="modal-header">
                    <span class="modal-title">Chọn khách hàng</span>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button>
                </div>
                <div class="modal-body">
                    <input type="text" class="form-control picker-search" id="customerSearchInput" placeholder="Tìm theo tên, mã khách hàng hoặc người phụ trách...">
                    <div class="picker-list" id="customerPickerList">
                        <c:forEach var="customer" items="${customerList}">
                            <div class="picker-item"
                                 data-id="${customer.enterpriseId}"
                                 data-name="${fn:escapeXml(customer.enterpriseName)}"
                                 data-search="${fn:toLowerCase(fn:escapeXml(customer.enterpriseName))} ${fn:toLowerCase(fn:escapeXml(customer.enterpriseCode))} ${fn:toLowerCase(fn:escapeXml(customer.accountOwner.fullName))}">
                                <div class="picker-item-title">${fn:escapeXml(customer.enterpriseName)}</div>
                                <div class="picker-item-sub">
                                    ${fn:escapeXml(customer.enterpriseCode)}
                                    <c:if test="${customer.accountOwner != null}"> &middot; Phụ trách: ${fn:escapeXml(customer.accountOwner.fullName)}</c:if>
                                </div>
                            </div>
                        </c:forEach>
                        <div class="picker-empty" id="customerPickerEmpty" style="display:none;">Không tìm thấy khách hàng phù hợp.</div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- ===== Box chọn hợp đồng liên quan (nạp theo khách hàng đã chọn) ===== -->
    <div class="modal fade" id="contractPickerModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
            <div class="modal-content">
                <div class="modal-header">
                    <span class="modal-title">Chọn hợp đồng liên quan</span>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button>
                </div>
                <div class="modal-body">
                    <input type="text" class="form-control picker-search" id="contractSearchInput" placeholder="Tìm theo mã hoặc tên hợp đồng...">
                    <div class="picker-list" id="contractPickerList">
                        <div class="picker-empty" id="contractPickerEmpty">Chọn khách hàng trước.</div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var contextPath = '${pageContext.request.contextPath}';
        var currentEnterpriseId = '${ticket.enterpriseId}';
        var currentContractId = '${ticket.contractId}';
        // Kỹ thuật viên được giao: khách hàng và hợp đồng chỉ để xem (xem lockGeneral ở đầu trang).
        var lockGeneral = ${lockGeneral};
        var customerHiddenInput = document.getElementById('customer');
        var customerPickerText = document.getElementById('customerPickerText');
        var contractHiddenInput = document.getElementById('contract');
        var contractPickerField = document.getElementById('contractPickerField');
        var contractPickerText = document.getElementById('contractPickerText');
        var contractPickerList = document.getElementById('contractPickerList');

        var customerPickerModal = new bootstrap.Modal(document.getElementById('customerPickerModal'));
        var contractPickerModal = new bootstrap.Modal(document.getElementById('contractPickerModal'));

        function openCustomerPicker() {
            if (lockGeneral) {
                return;
            }
            document.getElementById('customerSearchInput').value = '';
            filterPickerList('customerPickerList', 'customerPickerEmpty', '');
            customerPickerModal.show();
        }

        function openContractPicker() {
            if (contractPickerField.classList.contains('disabled')) {
                return;
            }
            document.getElementById('contractSearchInput').value = '';
            filterPickerList('contractPickerList', 'contractPickerEmpty', '');
            contractPickerModal.show();
        }

        function filterPickerList(listId, emptyId, query) {
            var list = document.getElementById(listId);
            var items = list.querySelectorAll('.picker-item');
            var q = query.trim().toLowerCase();
            var visibleCount = 0;
            items.forEach(function (item) {
                var match = !q || item.dataset.search.indexOf(q) !== -1;
                item.style.display = match ? '' : 'none';
                if (match) { visibleCount++; }
            });
            document.getElementById(emptyId).style.display = (visibleCount === 0 && items.length > 0) ? 'block' : 'none';
        }

        document.getElementById('customerSearchInput').addEventListener('input', function () {
            filterPickerList('customerPickerList', 'customerPickerEmpty', this.value);
        });
        document.getElementById('contractSearchInput').addEventListener('input', function () {
            filterPickerList('contractPickerList', 'contractPickerEmpty', this.value);
        });

        document.getElementById('customerPickerList').addEventListener('click', function (e) {
            var item = e.target.closest('.picker-item');
            if (!item) { return; }
            selectCustomer(item.dataset.id, item.dataset.name);
            customerPickerModal.hide();
        });

        contractPickerList.addEventListener('click', function (e) {
            var item = e.target.closest('.picker-item');
            if (!item) { return; }
            selectContract(item.dataset.id, item.dataset.name);
            contractPickerModal.hide();
        });

        function selectCustomer(id, name) {
            customerHiddenInput.value = id;
            customerPickerText.textContent = name;
            customerPickerText.classList.remove('picker-placeholder');
            resetContractPicker();
            loadContracts(id, null);
        }

        function resetContractPicker() {
            contractHiddenInput.value = '';
            contractPickerText.textContent = '-- Chọn khách hàng trước --';
            contractPickerText.classList.add('picker-placeholder');
            contractPickerField.classList.add('disabled');
        }

        function selectContract(id, code) {
            contractHiddenInput.value = id;
            contractPickerText.textContent = code;
            // id rỗng = mục "Không gắn hợp đồng": hiện lại như chưa chọn gì.
            contractPickerText.classList.toggle('picker-placeholder', !id);
        }

        function loadContracts(enterpriseId, selectedContractId) {
            document.getElementById('contractLoaded').value = '';
            contractPickerField.classList.add('disabled');
            contractPickerText.textContent = 'Đang tải...';
            contractPickerText.classList.add('picker-placeholder');
            contractPickerList.innerHTML = '';
            fetch(contextPath + '/contract/byEnterprise?enterpriseId=' + encodeURIComponent(enterpriseId))
                .then(function (res) { return res.json(); })
                .then(function (contracts) {
                    contractPickerList.innerHTML = '';

                    // Phiếu hỗ trợ không bắt buộc gắn hợp đồng. Không có mục này
                    // thì một khi đã chọn, người dùng chỉ đổi được sang hợp đồng
                    // khác chứ không gỡ ra được.
                    var none = document.createElement('div');
                    none.className = 'picker-item';
                    none.dataset.id = '';
                    none.dataset.name = '-- Không gắn hợp đồng --';
                    none.dataset.search = 'khong gan hop dong';
                    var noneTitle = document.createElement('div');
                    noneTitle.className = 'picker-item-title';
                    noneTitle.textContent = '-- Không gắn hợp đồng --';
                    none.appendChild(noneTitle);
                    contractPickerList.appendChild(none);

                    contracts.forEach(function (c) {
                        var item = document.createElement('div');
                        item.className = 'picker-item';
                        item.dataset.id = c.id;
                        item.dataset.name = c.code;
                        item.dataset.search = (c.code + ' ' + (c.title || '')).toLowerCase();

                        var title = document.createElement('div');
                        title.className = 'picker-item-title';
                        title.textContent = c.code;
                        item.appendChild(title);

                        if (c.title) {
                            var sub = document.createElement('div');
                            sub.className = 'picker-item-sub';
                            sub.textContent = c.title;
                            item.appendChild(sub);
                        }
                        contractPickerList.appendChild(item);
                    });

                    var empty = document.createElement('div');
                    empty.className = 'picker-empty';
                    empty.id = 'contractPickerEmpty';
                    empty.style.display = contracts.length === 0 ? 'block' : 'none';
                    empty.textContent = 'Khách hàng này chưa có hợp đồng nào.';
                    contractPickerList.appendChild(empty);

                    contractPickerText.textContent = contracts.length === 0
                        ? '-- Không có hợp đồng liên quan --'
                        : '-- Chọn hợp đồng --';
                    // Khoá thì ô vẫn mờ; phiếu không gắn hợp đồng thì nói đúng như
                    // vậy thay vì một lời mời "Chọn hợp đồng" không bấm được.
                    if (lockGeneral) {
                        contractPickerText.textContent = '-- Không gắn hợp đồng --';
                    } else {
                        contractPickerField.classList.remove('disabled');
                    }
                    document.getElementById('contractLoaded').value = '1';

                    if (selectedContractId) {
                        var match = contractPickerList.querySelector('.picker-item[data-id="' + selectedContractId + '"]');
                        if (match) {
                            selectContract(match.dataset.id, match.dataset.name);
                        }
                    }
                })
                .catch(function () {
                    contractPickerText.textContent = 'Không tải được danh sách hợp đồng';
                    contractPickerField.classList.add('disabled');
                });
        }

        // ===== Khởi tạo lựa chọn hiện có của phiếu (đang sửa, không phải tạo mới) =====
        // Không gọi selectCustomer() ở đây vì nó tự gọi loadContracts() không kèm
        // currentContractId -- gọi loadContracts() riêng bên dưới, kèm đúng
        // contractId hiện tại để pre-select đúng hợp đồng, tránh nạp trùng 2 lần.
        if (currentEnterpriseId) {
            var currentCustomerItem = document.querySelector('#customerPickerList .picker-item[data-id="' + currentEnterpriseId + '"]');
            if (currentCustomerItem) {
                customerHiddenInput.value = currentCustomerItem.dataset.id;
                customerPickerText.textContent = currentCustomerItem.dataset.name;
                customerPickerText.classList.remove('picker-placeholder');
            }
            loadContracts(currentEnterpriseId, currentContractId);
        }

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            ['customer', 'ticketType', 'priority', 'receptionChannel', 'technician'].forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            var description = document.getElementById('description');
            if (!description.value.trim()) { document.getElementById('err-description').style.display = 'block'; valid = false; }

            return valid;
        }
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

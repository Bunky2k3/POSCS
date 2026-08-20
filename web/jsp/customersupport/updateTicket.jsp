<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do TechnicalSupportTicketController#showEditForm
    thiết lập trước khi forward tới trang này:
      - ticket       : poscs.model.TechnicalRequest (phiếu đang sửa)
      - customerList : List<poscs.model.Enterprise>
      - userList      : List<poscs.model.User>

    Dropdown "Hợp đồng liên quan" nạp qua AJAX giống addnewTicket.jsp, JS tự
    chọn lại đúng hợp đồng hiện tại (nếu có) sau khi danh sách tải xong.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cập nhật phiếu hỗ trợ - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <style>
        :root {
            --primary-dark: #003c6e; --primary: #0568a6; --primary-light: #0f9edb;
            --primary-lighter: #6fd0ff; --accent: #00c2ff;
            --danger: #e2536b; --success: #2fbf8f; --warning: #f5a623;
        }
        body { font-family: 'Inter', sans-serif; background: #f3f4f6; min-height: 100vh; }

        .topbar { background: #fff; box-shadow: 0 2px 12px rgba(0, 60, 110, 0.08); padding: 14px 28px; display: flex; justify-content: space-between; align-items: center; position: sticky; top: 0; z-index: 50; }
        .topbar .brand { font-weight: 700; color: var(--primary-dark); font-size: 1.05rem; display: flex; align-items: center; gap: 10px; }
        .topbar .brand i { color: var(--primary); font-size: 1.2rem; }
        .topbar-left { display: flex; align-items: center; gap: 32px; }
        .topbar-right { display: flex; align-items: center; gap: 18px; }
        .bell-icon { color: #6b7280; font-size: 1.1rem; cursor: pointer; position: relative; }
        .bell-icon .dot { position: absolute; top: -3px; right: -4px; width: 8px; height: 8px; border-radius: 50%; background: var(--danger); border: 1.5px solid #fff; }
        .avatar-mini { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; cursor: pointer; border: 2px solid var(--primary-light); }
        .dropdown-menu { border: none; border-radius: 14px; box-shadow: 0 14px 34px rgba(0, 40, 80, 0.18); padding: 8px; margin-top: 12px !important; min-width: 230px; }
        .dropdown-item { border-radius: 8px; padding: 9px 12px; font-size: 0.87rem; color: #374151; }
        .dropdown-item:hover, .dropdown-item:focus { background: #f0f9ff; color: var(--primary-dark); }
        .dropdown-item.text-danger:hover { background: #fdecef; color: var(--danger) !important; }
        .dd-user-header { display: flex; align-items: center; gap: 10px; padding: 8px 10px 12px; }
        .dd-user-header img { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
        .dd-user-header .dd-name { font-weight: 600; font-size: 0.9rem; color: #111827; }
        .dd-user-header .dd-role { font-size: 0.75rem; color: #6b7280; }
        .notif-dropdown { min-width: 320px; max-height: 380px; overflow-y: auto; }
        .notif-header { display: flex; justify-content: space-between; align-items: center; padding: 6px 10px 10px; font-weight: 700; font-size: 0.9rem; color: var(--primary-dark); }
        .notif-count { background: var(--primary); color: #fff; font-size: 0.68rem; padding: 2px 9px; border-radius: 10px; font-weight: 600; }
        .notif-item { display: flex; gap: 10px; align-items: flex-start; white-space: normal; }
        .notif-icon { width: 34px; height: 34px; border-radius: 50%; background: #eef6fb; color: var(--primary); display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 0.85rem; }
        .notif-text { font-size: 0.85rem; color: #111827; font-weight: 500; line-height: 1.3; }
        .notif-time { font-size: 0.72rem; color: #9ca3af; margin-top: 2px; }

        .app-shell { display: flex; align-items: flex-start; }
        .sidebar {
            width: 240px; flex-shrink: 0;
            background: #fff; border-right: 1px solid #eef2f6;
            padding: 20px 12px; position: sticky; top: 66px;
            height: calc(100vh - 66px); overflow-y: auto;
            display: flex; flex-direction: column;
            transition: width 0.15s ease;
        }
        .sidebar-link {
            display: flex; align-items: center; gap: 12px;
            padding: 11px 14px; margin-bottom: 2px; border-radius: 10px;
            color: #6b7280; font-weight: 600; font-size: 0.88rem; text-decoration: none;
        }
        .sidebar-link i { width: 18px; text-align: center; color: #9ca3af; flex-shrink: 0; }
        .sidebar-link:hover { background: #f0f9ff; color: var(--primary-dark); }
        .sidebar-link.active { background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; }
        .sidebar-link.active i { color: #fff; }
        .sidebar-toggle {
            display: flex; align-items: center; justify-content: center; width: 32px; height: 32px;
            margin: 0 0 12px; padding: 0; border: none; border-radius: 8px;
            background: none; color: #9ca3af; cursor: pointer;
        }
        .sidebar-toggle i { transition: transform 0.15s ease; }
        .sidebar-toggle:hover { background: #f0f9ff; color: var(--primary-dark); }
        .sidebar.collapsed { width: 68px; }
        .sidebar.collapsed .sidebar-link { justify-content: center; }
        .sidebar.collapsed .sidebar-link span { display: none; }
        .sidebar.collapsed .sidebar-toggle i { transform: rotate(180deg); }
        .sidebar.collapsed:hover { width: 240px; }
        .sidebar.collapsed:hover .sidebar-link { justify-content: flex-start; }
        .sidebar.collapsed:hover .sidebar-link span { display: inline; }
        .sidebar.collapsed:hover .sidebar-toggle i { transform: rotate(0deg); }
        .main-content { flex: 1; min-width: 0; }
        @media (max-width: 900px) { .sidebar { display: none; } /* TODO: drawer thu gọn thay vì ẩn hẳn */ }

        .page-container { max-width: 900px; margin: 28px auto; padding: 0 20px 60px; }
        .page-header-row { margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 30px 34px 34px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 30px 0 18px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

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
        .picker-field.disabled { cursor: not-allowed; color: #9ca3af; background-color: #f3f4f6; }
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

        @media (max-width: 768px) { .card-box { padding: 24px 20px 28px; } }
    </style>
</head>
<body>

    <nav class="topbar">
        <div class="topbar-left">
            <div class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</div>
        </div>
        <div class="topbar-right">
            <div class="dropdown">
                <div class="bell-icon" data-bs-toggle="dropdown" aria-expanded="false"><i class="fa-regular fa-bell"></i><span class="dot"></span></div>
                <ul class="dropdown-menu dropdown-menu-end notif-dropdown">
                    <li class="notif-header">Thông báo <span class="notif-count">3 mới</span></li>
                    <li><a class="dropdown-item notif-item" href="#"><span class="notif-icon"><i class="fa-solid fa-file-contract"></i></span><div><div class="notif-text">Hợp đồng #HD-0231 sắp hết hạn</div><div class="notif-time">10 phút trước</div></div></a></li>
                    <li><a class="dropdown-item notif-item" href="#"><span class="notif-icon"><i class="fa-solid fa-headset"></i></span><div><div class="notif-text">Phiếu hỗ trợ #TK-1042 vừa được giao cho bạn</div><div class="notif-time">1 giờ trước</div></div></a></li>
                    <li><a class="dropdown-item notif-item" href="#"><span class="notif-icon"><i class="fa-solid fa-user-plus"></i></span><div><div class="notif-text">Khách hàng mới được thêm: Viettel Bắc Ninh</div><div class="notif-time">Hôm qua</div></div></a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-center small" href="#">Xem tất cả thông báo</a></li>
                </ul>
            </div>
            <div class="dropdown">
                <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header"><img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" alt="avatar"><div><div class="dd-name"><c:out value="${sessionScope.currentUser.fullName}"/></div><div class="dd-role"><c:out value="${sessionScope.currentUser.role.roleName}"/></div></div></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/viewProfile"><i class="fa-regular fa-id-card me-2"></i>Thông tin cá nhân</a></li>
                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/changePassword.jsp"><i class="fa-solid fa-key me-2"></i>Đổi mật khẩu</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-danger" href="${pageContext.request.contextPath}/login?action=logout"><i class="fa-solid fa-arrow-right-from-bracket me-2"></i>Đăng xuất</a></li>
                </ul>
            </div>
        </div>
    </nav>
    <div class="app-shell">
        <aside class="sidebar" id="sidebar">
            <button type="button" class="sidebar-toggle" id="sidebarToggle" aria-label="Thu gọn menu">
                <i class="fa-solid fa-angles-left"></i>
            </button>
            <a href="${pageContext.request.contextPath}/dashboard" class="sidebar-link"><i class="fa-solid fa-house"></i><span>Trang chủ</span></a>
            <a href="${pageContext.request.contextPath}/customer" class="sidebar-link"><i class="fa-solid fa-users"></i><span>Khách hàng</span></a>
            <a href="${pageContext.request.contextPath}/contract" class="sidebar-link"><i class="fa-solid fa-file-contract"></i><span>Hợp đồng</span></a>
            <a href="${pageContext.request.contextPath}/product" class="sidebar-link"><i class="fa-solid fa-box"></i><span>Sản phẩm</span></a>
            <a href="${pageContext.request.contextPath}/ticket" class="sidebar-link active"><i class="fa-solid fa-headset"></i><span>Phiếu hỗ trợ</span></a>
        </aside>
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

                <div class="section-header"><h5>Thông tin chung</h5></div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Khách hàng <span class="req">*</span></label>
                        <div class="picker-field" id="customerPickerField" onclick="openCustomerPicker()">
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
                    </div>

                    <div class="col-md-4 field-row">
                        <label>Loại phiếu <span class="req">*</span></label>
                        <select class="form-select" id="ticketType" name="ticketType">
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
                        <select class="form-select" id="priority" name="priority">
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
                        <select class="form-select" id="receptionChannel" name="receptionChannel">
                            <option value="">-- Chọn kênh tiếp nhận --</option>
                            <option value="Điện thoại" ${ticket.receptionChannel == 'Điện thoại' ? 'selected' : ''}>Điện thoại</option>
                            <option value="Email" ${ticket.receptionChannel == 'Email' ? 'selected' : ''}>Email</option>
                            <option value="Trực tiếp" ${ticket.receptionChannel == 'Trực tiếp' ? 'selected' : ''}>Trực tiếp</option>
                            <option value="Website" ${ticket.receptionChannel == 'Website' ? 'selected' : ''}>Website</option>
                        </select>
                        <span class="error-text" id="err-receptionChannel">Vui lòng chọn kênh tiếp nhận.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Kỹ thuật viên phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="technician" name="assignedTechnicianId">
                            <option value="">-- Chọn kỹ thuật viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}" ${staff.userId == ticket.assignedTechnicianId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-technician">Vui lòng chọn kỹ thuật viên phụ trách.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Trạng thái <span class="req">*</span></label>
                        <select class="form-select" id="status" name="status">
                            <option value="Mới tiếp nhận" ${ticket.status == 'Mới tiếp nhận' ? 'selected' : ''}>Mới tiếp nhận</option>
                            <option value="Đang xử lý" ${ticket.status == 'Đang xử lý' ? 'selected' : ''}>Đang xử lý</option>
                            <option value="Đã đóng" ${ticket.status == 'Đã đóng' ? 'selected' : ''}>Đã đóng</option>
                        </select>
                    </div>

                    <div class="col-md-6 field-row" style="display:flex; align-items:center;">
                        <div class="form-check">
                            <input type="checkbox" class="form-check-input" id="isWarranty" name="isWarranty" ${ticket.warranty ? 'checked' : ''}>
                            <label class="form-check-label" for="isWarranty">Còn trong thời hạn bảo hành</label>
                        </div>
                    </div>

                    <div class="col-12 field-row">
                        <label>Mô tả sự cố <span class="req">*</span></label>
                        <textarea class="form-control" id="description" name="description" rows="4">${fn:escapeXml(ticket.description)}</textarea>
                        <span class="error-text" id="err-description">Vui lòng mô tả sự cố.</span>
                    </div>
                    <div class="col-12 field-row">
                        <label>Kết quả xử lý</label>
                        <textarea class="form-control" id="resolutionSummary" name="resolutionSummary" rows="3" placeholder="Ghi chú kết quả xử lý (điền khi đóng phiếu)">${fn:escapeXml(ticket.resolutionSummary)}</textarea>
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
        var customerHiddenInput = document.getElementById('customer');
        var customerPickerText = document.getElementById('customerPickerText');
        var contractHiddenInput = document.getElementById('contract');
        var contractPickerField = document.getElementById('contractPickerField');
        var contractPickerText = document.getElementById('contractPickerText');
        var contractPickerList = document.getElementById('contractPickerList');

        var customerPickerModal = new bootstrap.Modal(document.getElementById('customerPickerModal'));
        var contractPickerModal = new bootstrap.Modal(document.getElementById('contractPickerModal'));

        function openCustomerPicker() {
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
            contractPickerText.classList.remove('picker-placeholder');
        }

        function loadContracts(enterpriseId, selectedContractId) {
            contractPickerField.classList.add('disabled');
            contractPickerText.textContent = 'Đang tải...';
            contractPickerText.classList.add('picker-placeholder');
            contractPickerList.innerHTML = '';
            fetch(contextPath + '/contract/byEnterprise?enterpriseId=' + encodeURIComponent(enterpriseId))
                .then(function (res) { return res.json(); })
                .then(function (contracts) {
                    contractPickerList.innerHTML = '';
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
                    contractPickerField.classList.remove('disabled');

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

    <script>
        // ===== Thu gọn / mở rộng sidebar =====
        (function () {
            var sidebar = document.getElementById('sidebar');
            var toggle = document.getElementById('sidebarToggle');
            var STORAGE_KEY = 'poscsSidebarCollapsed';
            if (localStorage.getItem(STORAGE_KEY) === '1') {
                sidebar.classList.add('collapsed');
            }
            toggle.addEventListener('click', function () {
                var collapsed = sidebar.classList.toggle('collapsed');
                localStorage.setItem(STORAGE_KEY, collapsed ? '1' : '0');
            });
        })();
    </script>
</body>
</html>

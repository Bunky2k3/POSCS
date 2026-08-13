<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do TechnicalSupportTicketController#showDetail thiết
    lập trước khi forward tới trang này:
      - ticket    : poscs.model.TechnicalRequest (có sẵn .enterprise, .contract,
                    .assignedTechnician, .createdByUser đã join nếu tồn tại)
      - canDelete : boolean -- true nếu phiếu chưa có ai xử lý dở dang (status != "Đang xử lý")

    technicalrequestdevices (thiết bị lỗi) và technicalrequesthistory (lịch
    sử đổi trạng thái) chưa hiển thị dữ liệu thật -- thuộc phạm vi khác.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết phiếu hỗ trợ - POSCS Portal</title>

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

        .page-container { max-width: 1080px; margin: 28px auto; padding: 0 20px 60px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 16px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); }

        .detail-header { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; padding: 26px 30px; margin-bottom: 20px; }
        .detail-header .doc-icon {
            width: 56px; height: 56px; border-radius: 14px;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center; font-size: 1.4rem; flex-shrink: 0;
        }
        .detail-header .doc-info { display: flex; gap: 16px; align-items: center; }
        .detail-header h2 { font-weight: 700; color: #111827; font-size: 1.2rem; margin-bottom: 4px; }
        .ticket-code { color: var(--primary); font-weight: 700; font-size: 0.82rem; }
        .type-badge { display: inline-block; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; background: #f3f4f6; color: #4b5563; margin-left: 8px; }

        .pill { display: inline-flex; align-items: center; gap: 5px; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; white-space: nowrap; margin-left: 8px; }
        .pill .dot { width: 6px; height: 6px; border-radius: 50%; }
        .priority-urgent { background: #fdecef; color: var(--danger); } .priority-urgent .dot { background: var(--danger); }
        .priority-high { background: #fff4e0; color: var(--warning); } .priority-high .dot { background: var(--warning); }
        .priority-normal { background: #eaf6ff; color: var(--primary); } .priority-normal .dot { background: var(--primary); }
        .priority-low { background: #eef2f6; color: #6b7280; } .priority-low .dot { background: #9ca3af; }
        .status-new { background: #eaf6ff; color: var(--primary); } .status-new .dot { background: var(--primary); }
        .status-progress { background: #fff4e0; color: var(--warning); } .status-progress .dot { background: var(--warning); }
        .status-closed { background: #e8faf3; color: var(--success); } .status-closed .dot { background: var(--success); }

        .header-actions { display: flex; gap: 10px; }
        .btn-edit-detail {
            background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; border: none;
            border-radius: 10px; padding: 9px 18px; font-weight: 600; font-size: 0.87rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-edit-detail:hover { color: #fff; background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-delete-detail {
            background: #fff; border: 1.5px solid #e5e7eb; color: #9ca3af;
            border-radius: 10px; padding: 9px 16px; font-weight: 600; font-size: 0.87rem;
            display: inline-flex; align-items: center; gap: 8px; cursor: not-allowed;
        }

        .info-card { padding: 26px 30px 30px; margin-bottom: 20px; }
        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 0 0 18px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row .view-value {
            font-size: 0.95rem; color: #111827; font-weight: 500; min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f9fafb; border-radius: 10px; padding: 8px 14px;
        }
        .field-row .view-value a { color: var(--primary); font-weight: 600; text-decoration: none; }
        .field-row .view-value a:hover { text-decoration: underline; }
        .field-row .view-value.text-block { min-height: 80px; align-items: flex-start; white-space: pre-wrap; }

        @media (max-width: 768px) { .info-card, .detail-header { padding: 20px; } }
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
            <a href="${pageContext.request.contextPath}/ticket" class="sidebar-link active"><i class="fa-solid fa-headset"></i><span>Phiếu hỗ trợ</span></a>
        </aside>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/ticket" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <!-- ===== Header ===== -->
        <div class="detail-header card-box">
            <div class="doc-info">
                <div class="doc-icon"><i class="fa-solid fa-headset"></i></div>
                <div>
                    <span class="ticket-code">${fn:escapeXml(ticket.ticketCode)}</span>
                    <h2>
                        ${fn:escapeXml(ticket.ticketType)}
                        <span class="type-badge"><c:if test="${ticket.warranty}">Còn bảo hành</c:if><c:if test="${!ticket.warranty}">Hết bảo hành</c:if></span>
                        <c:choose>
                            <c:when test="${ticket.priority == 'Khẩn cấp'}"><span class="pill priority-urgent"><span class="dot"></span>Khẩn cấp</span></c:when>
                            <c:when test="${ticket.priority == 'Cao'}"><span class="pill priority-high"><span class="dot"></span>Cao</span></c:when>
                            <c:when test="${ticket.priority == 'Thấp'}"><span class="pill priority-low"><span class="dot"></span>Thấp</span></c:when>
                            <c:otherwise><span class="pill priority-normal"><span class="dot"></span>Bình thường</span></c:otherwise>
                        </c:choose>
                        <c:choose>
                            <c:when test="${ticket.status == 'Đang xử lý'}"><span class="pill status-progress"><span class="dot"></span>Đang xử lý</span></c:when>
                            <c:when test="${ticket.status == 'Đã đóng'}"><span class="pill status-closed"><span class="dot"></span>Đã đóng</span></c:when>
                            <c:otherwise><span class="pill status-new"><span class="dot"></span>Mới tiếp nhận</span></c:otherwise>
                        </c:choose>
                    </h2>
                    <div style="color:#6b7280; font-size:0.85rem;">Khách hàng:
                        <a href="${pageContext.request.contextPath}/customer?action=view&id=${ticket.enterpriseId}" style="color:var(--primary); font-weight:600; text-decoration:none;">
                            <c:choose>
                                <c:when test="${ticket.enterprise != null}">${fn:escapeXml(ticket.enterprise.enterpriseName)}</c:when>
                                <c:otherwise>&mdash;</c:otherwise>
                            </c:choose>
                        </a>
                    </div>
                </div>
            </div>
            <div class="header-actions">
                <a href="${pageContext.request.contextPath}/ticket?action=edit&id=${ticket.ticketId}" class="btn-edit-detail"><i class="fa-solid fa-pen"></i> Sửa thông tin</a>
                <c:choose>
                    <c:when test="${canDelete}">
                        <button type="button" class="btn-delete-detail" style="cursor:pointer; color:var(--danger); border-color:var(--danger);" onclick="confirmDelete(${ticket.ticketId})"><i class="fa-solid fa-trash"></i> Xóa</button>
                    </c:when>
                    <c:otherwise>
                        <button class="btn-delete-detail" disabled title="Không thể xóa phiếu đang có người xử lý dở dang"><i class="fa-solid fa-trash"></i> Xóa</button>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>

        <!-- ===== Thông tin chung ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Thông tin chung</h5></div>
            <div class="row">
                <div class="col-md-6 field-row">
                    <label>Hợp đồng liên quan</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.contract != null}">
                                <a href="${pageContext.request.contextPath}/contract?action=view&id=${ticket.contractId}">${fn:escapeXml(ticket.contract.contractCode)}</a>
                            </c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Kênh tiếp nhận</label>
                    <div class="view-value">${fn:escapeXml(ticket.receptionChannel)}</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Kỹ thuật viên phụ trách</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.assignedTechnician != null}">${fn:escapeXml(ticket.assignedTechnician.fullName)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Người tạo phiếu</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.createdByUser != null}">${fn:escapeXml(ticket.createdByUser.fullName)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Ngày tạo</label>
                    <div class="view-value"><fmt:formatDate value="${ticket.createdDate}" pattern="dd/MM/yyyy"/></div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Thời điểm hoàn tất</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.resolvedAt != null}"><fmt:formatDate value="${ticket.resolvedAt}" pattern="dd/MM/yyyy HH:mm"/></c:when>
                            <c:otherwise>Chưa xử lý xong</c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </div>
        </div>

        <!-- ===== Mô tả sự cố ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Mô tả sự cố</h5></div>
            <div class="view-value text-block">${fn:escapeXml(ticket.description)}</div>
        </div>

        <!-- ===== Kết quả xử lý ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Kết quả xử lý</h5></div>
            <div class="view-value text-block">
                <c:choose>
                    <c:when test="${not empty ticket.resolutionSummary}">${fn:escapeXml(ticket.resolutionSummary)}</c:when>
                    <c:otherwise>Chưa xử lý xong.</c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>

        </div>
    </div>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/ticket" style="display:none">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" id="deleteFormId">
    </form>

    <script>
        function confirmDelete(ticketId) {
            if (confirm('Bạn có chắc chắn muốn xóa phiếu hỗ trợ này?')) {
                document.getElementById('deleteFormId').value = ticketId;
                document.getElementById('deleteForm').submit();
            }
        }
    </script>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>

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

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần đặt request attribute sau trước khi forward tới trang này:
      - employee : poscs.model.User (đã kèm .role, .address.district.province nếu có)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết nhân viên - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 900px; margin: 32px auto; padding: 0 20px 32px; }

        .back-link { display: inline-flex; align-items: center; gap: 6px; color: #6b7280; font-size: 0.87rem; font-weight: 600; text-decoration: none; margin-bottom: 16px; }
        .back-link:hover { color: var(--primary-dark); }

        .profile-card { background: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 15px 40px rgba(0,40,80,0.12); }
        .profile-banner { background: linear-gradient(120deg, var(--primary-dark), var(--primary), var(--primary-light)); padding: 40px 30px 28px; text-align: center; color: #fff; }
        .avatar-img { width: 108px; height: 108px; border-radius: 50%; object-fit: cover; border: 4px solid rgba(255,255,255,0.85); background: #e5e7eb; margin-bottom: 14px; }
        .profile-banner h3 { font-weight: 700; margin-bottom: 6px; }
        .badge-row { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
        .role-badge { display: inline-block; background: rgba(255,255,255,0.18); border: 1px solid rgba(255,255,255,0.4); padding: 4px 16px; border-radius: 20px; font-size: 0.8rem; }
        .status-badge { display: inline-block; padding: 4px 16px; border-radius: 20px; font-size: 0.8rem; font-weight: 600; }
        .status-active { background: rgba(47,191,143,0.22); border: 1px solid rgba(47,191,143,0.5); }
        .status-inactive { background: rgba(226,83,107,0.22); border: 1px solid rgba(226,83,107,0.5); }

        .profile-body { padding: 24px 28px 28px; }
        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 24px 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .btn-outline-edit { border: 1.5px solid var(--primary); color: var(--primary); background: #fff; font-weight: 600; border-radius: 10px; padding: 6px 16px; font-size: 0.83rem; transition: all 0.2s; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-outline-edit:hover { background: var(--primary); color: #fff; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row .view-value { font-size: 0.95rem; color: #111827; font-weight: 500; min-height: 40px; display: flex; align-items: center; border: 1px solid #eef2f6; background: #f9fafb; border-radius: 10px; padding: 8px 14px; }

        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; flex-wrap: wrap; }
        .btn-danger-outline { background: #fff; border: 1.5px solid var(--danger); color: var(--danger); border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; }
        .btn-danger-outline:hover { background: var(--danger); color: #fff; }
        .btn-success-outline { background: #fff; border: 1.5px solid var(--success); color: var(--success); border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; }
        .btn-success-outline:hover { background: var(--success); color: #fff; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5,104,166,0.3); color: #fff; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); color: #fff; }

        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 24px 24px 0; }
        .modal-body { padding: 12px 24px 6px; color: #374151; font-size: 0.92rem; }
        .modal-footer { border-top: none; padding: 18px 24px 24px; }
        .btn-modal-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-danger { background: var(--danger); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .modal-icon-warn { width: 52px; height: 52px; border-radius: 50%; background: #fdecef; color: var(--danger); display: flex; align-items: center; justify-content: center; font-size: 1.3rem; margin-bottom: 4px; }

        @media (max-width: 768px) { .profile-body { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="employee" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <a href="${pageContext.request.contextPath}/employee" class="back-link"><i class="fa-solid fa-arrow-left"></i> Quay lại danh sách nhân viên</a>

        <c:if test="${not empty param.warning && param.warning == 'mail_failed'}">
            <div class="alert alert-warning py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                Đã tạo tài khoản thành công nhưng gửi email thông báo thất bại. Vui lòng cung cấp tài khoản cho nhân viên theo cách khác.
            </div>
        </c:if>
        <c:if test="${not empty param.error && param.error == 'cannot_self_ban'}">
            <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                Không thể tự khóa tài khoản đang đăng nhập của chính bạn.
            </div>
        </c:if>

        <div class="profile-card">
            <div class="profile-banner">
                <img class="avatar-img" src="https://ui-avatars.com/api/?name=${fn:escapeXml(employee.firstName)}&background=ffffff&color=0568a6&size=128" alt="Avatar">
                <h3><c:out value="${employee.fullName}"/></h3>
                <p style="opacity:0.85; margin-bottom:10px;">NV-<c:out value="${employee.userId}"/></p>
                <div class="badge-row">
                    <span class="role-badge"><c:out value="${employee.role.roleName}"/></span>
                    <span class="status-badge ${employee.deleted ? 'status-inactive' : 'status-active'}">${employee.deleted ? 'Ngừng hoạt động' : 'Đang hoạt động'}</span>
                </div>
            </div>

            <div class="profile-body">
                <div class="section-header">
                    <h5>Thông tin công việc</h5>
                    <a href="${pageContext.request.contextPath}/employee?action=edit&id=${employee.userId}" class="btn-outline-edit"><i class="fa-solid fa-pen me-1"></i> Sửa thông tin</a>
                </div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Tên đăng nhập</label>
                        <div class="view-value"><c:out value="${employee.username}"/></div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Email công ty</label>
                        <div class="view-value"><c:out value="${employee.email}"/></div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Phòng ban</label>
                        <div class="view-value"><c:out value="${employee.department}"/></div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Ngày vào làm</label>
                        <div class="view-value"><c:out value="${hireDateText}"/></div>
                    </div>
                </div>

                <div class="section-header"><h5>Thông tin cá nhân</h5></div>
                <div class="row">
                    <div class="col-md-4 field-row">
                        <label>Họ</label>
                        <div class="view-value"><c:out value="${employee.lastName}"/></div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Tên đệm</label>
                        <div class="view-value"><c:out value="${employee.middleName}" default="—"/></div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Tên</label>
                        <div class="view-value"><c:out value="${employee.firstName}"/></div>
                    </div>

                    <div class="col-md-4 field-row">
                        <label>Giới tính</label>
                        <div class="view-value"><c:out value="${employee.gender}"/></div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày sinh</label>
                        <div class="view-value"><c:out value="${dateOfBirthText}"/></div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Số CCCD/CMND</label>
                        <div class="view-value"><c:out value="${employee.citizenId}"/></div>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Số điện thoại</label>
                        <div class="view-value"><c:out value="${employee.phone}"/></div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Email cá nhân</label>
                        <div class="view-value"><c:out value="${employee.personalEmail}" default="—"/></div>
                    </div>
                </div>

                <div class="section-header"><h5>Địa chỉ</h5></div>
                <c:choose>
                    <c:when test="${not empty employee.address}">
                        <div class="row">
                            <div class="col-md-6 field-row">
                                <label>Tỉnh / Thành phố</label>
                                <div class="view-value"><c:out value="${employee.address.district.province.provinceName}"/></div>
                            </div>
                            <div class="col-md-6 field-row">
                                <label>Xã / Phường</label>
                                <div class="view-value"><c:out value="${employee.address.district.districtName}"/></div>
                            </div>
                            <div class="col-12 field-row">
                                <label>Địa chỉ chi tiết</label>
                                <div class="view-value"><c:out value="${employee.address.streetAndLocalName}"/></div>
                            </div>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="view-value">Chưa cập nhật địa chỉ</div>
                    </c:otherwise>
                </c:choose>

                <!-- ===== Khóa / Mở khóa tài khoản (BR-25, BR-26) ===== -->
                <div class="action-bar">
                    <c:choose>
                        <c:when test="${employee.deleted}">
                            <button type="button" class="btn-success-outline" data-bs-toggle="modal" data-bs-target="#toggleStatusModal"><i class="fa-solid fa-lock-open me-1"></i> Mở khóa tài khoản</button>
                        </c:when>
                        <c:otherwise>
                            <button type="button" class="btn-danger-outline" data-bs-toggle="modal" data-bs-target="#toggleStatusModal"><i class="fa-solid fa-lock me-1"></i> Khóa tài khoản</button>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>

        </div>
    </div>

    <!-- ===== Modal xác nhận khóa/mở khóa (BR-26) ===== -->
    <div class="modal fade" id="toggleStatusModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <div class="modal-icon-warn"><i class="fa-solid fa-triangle-exclamation"></i></div>
                </div>
                <div class="modal-body">
                    <h5 class="mb-2" style="font-weight:700; color:#111827;">
                        <c:choose>
                            <c:when test="${employee.deleted}">Xác nhận mở khóa tài khoản</c:when>
                            <c:otherwise>Xác nhận khóa tài khoản</c:otherwise>
                        </c:choose>
                    </h5>
                    <c:choose>
                        <c:when test="${employee.deleted}">
                            Bạn có chắc chắn muốn mở khóa tài khoản của <strong><c:out value="${employee.fullName}"/></strong>? Nhân viên sẽ có thể đăng nhập lại vào hệ thống.
                        </c:when>
                        <c:otherwise>
                            Bạn có chắc chắn muốn khóa tài khoản của <strong><c:out value="${employee.fullName}"/></strong>? Nhân viên sẽ không thể đăng nhập vào hệ thống cho tới khi được mở khóa lại.
                        </c:otherwise>
                    </c:choose>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <form method="POST" action="${pageContext.request.contextPath}/employee">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="toggleStatus">
                        <input type="hidden" name="id" value="${employee.userId}">
                        <button type="submit" class="btn-modal-danger">Xác nhận</button>
                    </form>
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

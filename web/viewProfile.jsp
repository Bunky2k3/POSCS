<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thông tin cá nhân - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .back-link-top {
            color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600;
            display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px;
        }
        .back-link-top:hover { text-decoration: underline; }

        .page-container {
            max-width: 900px;
            margin: 32px auto;
            padding: 0 20px 32px;
        }

        .profile-card {
            background: #ffffff;
            border-radius: 20px;
            overflow: hidden;
            box-shadow: 0 15px 40px rgba(0, 40, 80, 0.12);
        }

        .profile-banner {
            background: linear-gradient(120deg, var(--primary-dark), var(--primary), var(--primary-light));
            padding: 40px 30px 28px;
            text-align: center;
            color: #fff;
        }

        .avatar-img {
            width: 108px; height: 108px; border-radius: 50%;
            object-fit: cover; border: 4px solid rgba(255,255,255,0.85);
            background: #e5e7eb; margin-bottom: 14px;
        }

        .profile-banner h3 { font-weight: 700; margin-bottom: 6px; }
        .role-badge {
            display: inline-block;
            background: rgba(255,255,255,0.18);
            border: 1px solid rgba(255,255,255,0.4);
            padding: 4px 16px; border-radius: 20px;
            font-size: 0.8rem;
        }

        .profile-body { padding: 24px 28px 28px; }

        .section-header {
            display: flex; justify-content: space-between; align-items: center;
            margin: 24px 0 16px; padding-bottom: 10px;
            border-bottom: 1.5px solid #eef2f6;
        }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 {
            font-weight: 700; color: var(--primary-dark);
            font-size: 0.98rem; margin: 0;
        }

        .btn-outline-edit {
            border: 1.5px solid var(--primary); color: var(--primary);
            background: #fff; font-weight: 600; border-radius: 10px;
            padding: 6px 16px; font-size: 0.83rem; transition: all 0.2s;
            text-decoration: none; display: inline-flex; align-items: center;
        }
        .btn-outline-edit:hover { background: var(--primary); color: #fff; }

        .field-row { margin-bottom: 18px; }
        .field-row label {
            font-size: 0.75rem; font-weight: 600; color: #6b7280;
            text-transform: uppercase; letter-spacing: .3px;
            margin-bottom: 6px; display: block;
        }
        .field-row .view-value {
            font-size: 0.95rem; color: #111827; font-weight: 500;
            min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f9fafb;
            border-radius: 10px; padding: 8px 14px;
        }

        @media (max-width: 768px) {
            .profile-body { padding: 20px 18px 22px; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="profile" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/dashboard" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Về trang chủ</a>

        <div class="profile-card">

            <!-- ===== Banner ===== -->
            <div class="profile-banner">
                <img class="avatar-img"
                     src="https://ui-avatars.com/api/?name=<c:out value="${profile.firstName}"/>&background=ffffff&color=0568a6&size=128" alt="Avatar">
                <h3><c:out value="${profile.fullName}"/></h3>
                <span class="role-badge"><c:out value="${profile.role.roleName}"/></span>
            </div>

            <div class="profile-body">

                <!-- ===== Thông tin công việc (chỉ xem, do Admin quản lý) ===== -->
                <div class="section-header"><h5>Thông tin công việc</h5></div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Email đăng nhập</label>
                        <div class="view-value"><c:out value="${profile.email}"/></div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Phòng ban</label>
                        <div class="view-value"><c:out value="${profile.department}"/></div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Vai trò</label>
                        <div class="view-value"><c:out value="${profile.role.roleName}"/></div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Ngày vào làm</label>
                        <div class="view-value"><c:out value="${hireDateText}" default="—"/></div>
                    </div>
                </div>

                <!-- ===== Thông tin cá nhân ===== -->
                <div class="section-header">
                    <h5>Thông tin cá nhân</h5>
                    <a href="${pageContext.request.contextPath}/updateProfile" class="btn-outline-edit">
                        <i class="fa-solid fa-pen me-1"></i> Sửa thông tin
                    </a>
                </div>
                <div class="row">
                    <div class="col-md-4 field-row">
                        <label>Họ</label>
                        <div class="view-value"><c:out value="${profile.lastName}"/></div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Tên đệm</label>
                        <div class="view-value"><c:out value="${profile.middleName}" default="—"/></div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Tên</label>
                        <div class="view-value"><c:out value="${profile.firstName}"/></div>
                    </div>

                    <div class="col-md-4 field-row">
                        <label>Giới tính</label>
                        <div class="view-value"><c:out value="${profile.gender}"/></div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày sinh</label>
                        <div class="view-value"><c:out value="${dateOfBirthText}" default="—"/></div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Số CCCD/CMND</label>
                        <div class="view-value"><c:out value="${profile.citizenId}"/></div>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Số điện thoại</label>
                        <div class="view-value"><c:out value="${profile.phone}"/></div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Email cá nhân</label>
                        <div class="view-value"><c:out value="${profile.personalEmail}" default="—"/></div>
                    </div>
                </div>

                <!-- ===== Địa chỉ ===== -->
                <div class="section-header"><h5>Địa chỉ</h5></div>
                <c:choose>
                    <c:when test="${not empty profile.address}">
                        <div class="row">
                            <div class="col-md-6 field-row">
                                <label>Tỉnh / Thành phố</label>
                                <div class="view-value"><c:out value="${profile.address.district.province.provinceName}"/></div>
                            </div>
                            <div class="col-md-6 field-row">
                                <label>Xã / Phường</label>
                                <div class="view-value"><c:out value="${profile.address.district.districtName}"/></div>
                            </div>
                            <div class="col-12 field-row">
                                <label>Địa chỉ chi tiết</label>
                                <div class="view-value"><c:out value="${profile.address.streetAndLocalName}"/></div>
                            </div>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="view-value">Chưa cập nhật địa chỉ</div>
                    </c:otherwise>
                </c:choose>

            </div>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

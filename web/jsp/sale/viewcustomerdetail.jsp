<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần lấy customer_id từ query param ?id=, truy vấn bảng enterprises
    (JOIN addresses/districts/provinces, JOIN users làm account_owner) + enterprisecontacts
    + contracts (JOIN users làm owner) + technicalrequests (JOIN users làm assigned_technician).
    Nếu không tồn tại thì hiển thị MSG-021 (redirect hoặc forward sang trang lỗi).

    Request attribute cần có:
      - customer      : poscs.model.Enterprise (đã join .address.district.province, .accountOwner)
      - contactList    : List<poscs.model.EnterpriseContact>
      - contractList    : List<poscs.model.Contract> (đã join .owner nếu cần hiển thị thêm)
      - ticketList      : List<poscs.model.TechnicalRequest>
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết khách hàng - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1080px; margin: 28px auto; padding: 0 20px 32px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 16px; }
        .back-link-top:hover { text-decoration: underline; }

        /* ===== Header khách hàng ===== */
        .detail-header {
            display: flex; justify-content: space-between; align-items: flex-start;
            flex-wrap: wrap; gap: 16px; padding: 22px 26px; margin-bottom: 20px;
        }
        .detail-header .company-icon {
            width: 56px; height: 56px; border-radius: 14px; overflow: hidden;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center;
            font-size: 1.4rem; flex-shrink: 0;
        }
        .detail-header .company-icon img { width: 100%; height: 100%; object-fit: cover; }
        .detail-header .company-info { display: flex; gap: 16px; align-items: center; }
        .detail-header h2 { font-weight: 700; color: #111827; font-size: 1.25rem; margin-bottom: 4px; }
        .detail-header .customer-code { color: var(--primary); font-weight: 700; font-size: 0.82rem; }
        .type-badge {
            display: inline-block; padding: 3px 11px; border-radius: 20px;
            font-size: 0.72rem; font-weight: 600; background: #eaf6ff; color: var(--primary-dark);
            margin-left: 8px;
        }
        .rating-badge {
            display: inline-block; padding: 3px 11px; border-radius: 20px;
            font-size: 0.72rem; font-weight: 600; margin-left: 8px;
        }
        .rating-good { background: #e8faf3; color: var(--success); }
        .rating-watch { background: #fff4e0; color: var(--warning); }
        .rating-bad, .rating-risk { background: #fdecef; color: var(--danger); }
        .header-actions { display: flex; gap: 10px; }
        .btn-edit-detail {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            color: #fff; border: none; border-radius: 10px; padding: 9px 18px;
            font-weight: 600; font-size: 0.87rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px;
            box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-edit-detail:hover { color: #fff; background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-delete-detail {
            background: #fff; border: 1.5px solid #f6c3cd; color: var(--danger);
            border-radius: 10px; padding: 9px 16px; font-weight: 600; font-size: 0.87rem;
            display: inline-flex; align-items: center; gap: 8px; cursor: pointer;
        }
        .btn-delete-detail:hover { background: #fdecef; }

        /* ===== Info section ===== */
        .info-card { padding: 22px 26px 26px; margin-bottom: 20px; }
        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 0 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row .view-value {
            font-size: 0.95rem; color: #111827; font-weight: 500;
            min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f9fafb; border-radius: 10px; padding: 8px 14px;
        }

        /* ===== Người liên hệ ===== */
        .contact-item {
            display: flex; align-items: center; gap: 14px; cursor: pointer;
            padding: 14px 16px; border: 1px solid #eef2f6; border-radius: 12px; margin-bottom: 12px;
            transition: border-color 0.12s ease, background 0.12s ease;
        }
        .contact-item:last-child { margin-bottom: 0; }
        .contact-item:hover, .contact-item:focus-visible { border-color: var(--primary-light); background: #f7fcff; outline: none; }
        .contact-avatar {
            width: 44px; height: 44px; border-radius: 50%;
            background: #eaf6ff; color: var(--primary);
            display: flex; align-items: center; justify-content: center; font-size: 1.1rem; flex-shrink: 0;
        }
        .contact-name { font-weight: 600; color: #111827; font-size: 0.9rem; }
        .contact-role { font-size: 0.78rem; color: var(--primary); font-weight: 500; margin-bottom: 2px; }
        .contact-meta { font-size: 0.8rem; color: #6b7280; display: flex; gap: 16px; flex-wrap: wrap; margin-top: 2px; }
        .contact-item-chevron { margin-left: auto; color: #d1d5db; flex-shrink: 0; }

        /* ===== Modal chi tiết người liên hệ ===== */
        .contact-modal-avatar {
            width: 64px; height: 64px; border-radius: 50%; margin: 0 auto 14px;
            background: #eaf6ff; color: var(--primary);
            display: flex; align-items: center; justify-content: center; font-size: 1.6rem;
        }
        .contact-modal-name { text-align: center; font-weight: 700; color: #111827; font-size: 1.05rem; }
        .contact-modal-role { text-align: center; color: var(--primary); font-weight: 500; font-size: 0.85rem; margin-bottom: 18px; }
        .contact-modal-field {
            display: flex; align-items: center; gap: 12px;
            padding: 11px 14px; border: 1px solid #eef2f6; border-radius: 10px; margin-bottom: 10px;
        }
        .contact-modal-field:last-child { margin-bottom: 0; }
        .contact-modal-field i { color: var(--primary); width: 18px; text-align: center; flex-shrink: 0; }
        .contact-modal-field a { color: #111827; font-weight: 500; text-decoration: none; }
        .contact-modal-field a:hover { color: var(--primary); text-decoration: underline; }

        /* ===== Tabs hoạt động gần đây ===== */
        .nav-tabs { border-bottom: 1.5px solid #eef2f6; }
        .nav-tabs .nav-link {
            border: none; color: #6b7280; font-weight: 600; font-size: 0.87rem;
            padding: 10px 4px; margin-right: 24px; border-bottom: 2.5px solid transparent;
        }
        .nav-tabs .nav-link.active { color: var(--primary-dark); border-bottom-color: var(--primary); background: none; }

        .mini-table { width: 100%; margin-top: 16px; }
        .mini-table th {
            font-size: 0.72rem; text-transform: uppercase; color: #9ca3af; font-weight: 700;
            padding: 8px 10px; border-bottom: 1.5px solid #eef2f6; text-align: left;
        }
        .mini-table td { padding: 10px 10px; font-size: 0.86rem; color: #111827; border-bottom: 1px solid #f3f4f6; }
        .mini-table tr:last-child td { border-bottom: none; }
        .mini-table a { color: var(--primary); font-weight: 600; text-decoration: none; }
        .mini-table a:hover { text-decoration: underline; }

        .status-pill { padding: 3px 10px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; }
        .status-active { background: #e8faf3; color: var(--success); }
        .status-soon { background: #fff4e0; color: var(--warning); }
        .status-expired { background: #fdecef; color: var(--danger); }
        .status-pending { background: #fff4e0; color: var(--warning); }
        .status-closed { background: #f3f4f6; color: #6b7280; }

        .empty-mini { text-align: center; color: #9ca3af; font-size: 0.85rem; padding: 30px 10px; }

        /* ===== Modal & toast (dùng lại) ===== */
        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 24px 24px 0; }
        .modal-body { padding: 12px 24px 6px; color: #374151; font-size: 0.92rem; }
        .modal-footer { border-top: none; padding: 18px 24px 24px; }
        .btn-modal-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-danger { background: var(--danger); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .modal-icon-warn { width: 52px; height: 52px; border-radius: 50%; background: #fdecef; color: var(--danger); display: flex; align-items: center; justify-content: center; font-size: 1.3rem; margin-bottom: 4px; }
        .modal-title { font-weight: 700; color: var(--primary-dark); font-size: 1.05rem; }
        .modal-body .form-label { font-weight: 600; font-size: 0.85rem; color: #374151; margin-bottom: 6px; display: block; }
        .modal-body .form-control, .modal-body .form-select {
            width: 100%; padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb;
            background-color: #f9fafb; font-size: 0.9rem;
        }
        .modal-body .form-control:focus, .modal-body .form-select:focus {
            background-color: #ffffff; border-color: var(--primary-light); outline: none;
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
        }

        .toast-msg {
            position: fixed; top: 24px; right: 24px; z-index: 999;
            background: #fff; border-left: 4px solid var(--success);
            border-radius: 12px; padding: 14px 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.15);
            display: flex; align-items: center; gap: 12px; font-size: 0.88rem; color: #111827; font-weight: 500;
            transform: translateX(130%); transition: transform 0.35s ease;
        }
        .toast-msg.show { transform: translateX(0); }
        .toast-msg i { color: var(--success); font-size: 1.2rem; }
        .toast-msg.blocked { border-left-color: var(--danger); }
        .toast-msg.blocked i { color: var(--danger); }

        @media (max-width: 768px) {
            .info-card, .detail-header { padding: 16px; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="customer" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/customer" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <!-- ===== Header ===== -->
        <div class="detail-header card-box">
            <div class="company-info">
                <div class="company-icon">
                    <c:choose>
                        <c:when test="${not empty customer.logoUrl}"><img src="${pageContext.request.contextPath}${fn:escapeXml(customer.logoUrl)}" alt="Logo"></c:when>
                        <c:otherwise><i class="fa-solid fa-building"></i></c:otherwise>
                    </c:choose>
                </div>
                <div>
                    <span class="customer-code">${fn:escapeXml(customer.enterpriseCode)}</span>
                    <h2>
                        ${fn:escapeXml(customer.enterpriseName)}
                        <span class="type-badge">${fn:escapeXml(customer.customerType)}</span>
                        <c:if test="${customer.currentRelationshipRating != null}">
                            <span class="rating-badge
                                ${customer.currentRelationshipRating == 'GOOD' ? 'rating-good' : ''}
                                ${customer.currentRelationshipRating == 'NEEDS_REVIEW' ? 'rating-watch' : ''}
                                ${customer.currentRelationshipRating == 'BAD' ? 'rating-bad' : ''}
                                ${customer.currentRelationshipRating == 'AT_RISK' ? 'rating-risk' : ''}">${customer.currentRelationshipRating}</span>
                        </c:if>
                    </h2>
                    <div style="color:#6b7280; font-size:0.85rem;">
                        ${fn:escapeXml(customer.customerGroup)}
                        <c:if test="${customer.joinDate != null}"> &middot; Tham gia từ <fmt:formatDate value="${customer.joinDate}" pattern="dd/MM/yyyy"/></c:if>
                    </div>
                </div>
            </div>
            <div class="header-actions">
                <button class="btn-edit-detail" type="button" onclick="openEvaluateModal()"><i class="fa-solid fa-chart-line"></i> Đánh giá lại xếp hạng</button>
                <a href="${pageContext.request.contextPath}/customer?action=edit&id=${customer.enterpriseId}" class="btn-edit-detail"><i class="fa-solid fa-pen"></i> Sửa thông tin</a>
                <button class="btn-delete-detail" onclick="openDeleteModal()"><i class="fa-solid fa-trash"></i> Xóa</button>
            </div>
        </div>

        <!-- ===== Thông tin doanh nghiệp ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Thông tin doanh nghiệp</h5></div>
            <div class="row">
                <div class="col-md-6 field-row">
                    <label>Nhóm khách hàng</label>
                    <div class="view-value">${fn:escapeXml(customer.customerGroup)}</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Người phụ trách</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${customer.accountOwner != null}">${fn:escapeXml(customer.accountOwner.fullName)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Số điện thoại</label>
                    <div class="view-value">${fn:escapeXml(customer.phone)}</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Email</label>
                    <div class="view-value">${fn:escapeXml(customer.email)}</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Website</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${not empty customer.website}">${fn:escapeXml(customer.website)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Ngày tham gia</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${customer.joinDate != null}"><fmt:formatDate value="${customer.joinDate}" pattern="dd/MM/yyyy"/></c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-12 field-row">
                    <label>Địa chỉ</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${customer.address != null}">${fn:escapeXml(customer.address.fullAddress)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </div>
        </div>

        <!-- ===== Người đại diện / liên hệ ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Người liên hệ</h5></div>

            <c:choose>
                <c:when test="${empty contactList}">
                    <div class="empty-mini">Chưa có người liên hệ nào được ghi nhận.</div>
                </c:when>
                <c:otherwise>
                    <c:forEach var="contact" items="${contactList}">
                        <div class="contact-item" role="button" tabindex="0"
                             data-name="${fn:escapeXml(contact.fullName)}"
                             data-position="${fn:escapeXml(contact.position)}"
                             data-phone="${fn:escapeXml(contact.contactPhone)}"
                             data-email="${fn:escapeXml(contact.contactEmail)}"
                             onclick="openContactModal(this)"
                             onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();openContactModal(this);}">
                            <div class="contact-avatar"><i class="fa-solid fa-user-tie"></i></div>
                            <div>
                                <div class="contact-role">${fn:escapeXml(contact.position)}</div>
                                <div class="contact-name">${fn:escapeXml(contact.fullName)}</div>
                                <div class="contact-meta">
                                    <c:if test="${not empty contact.contactPhone}"><span><i class="fa-solid fa-phone me-1"></i>${fn:escapeXml(contact.contactPhone)}</span></c:if>
                                    <c:if test="${not empty contact.contactEmail}"><span><i class="fa-solid fa-envelope me-1"></i>${fn:escapeXml(contact.contactEmail)}</span></c:if>
                                </div>
                            </div>
                            <i class="fa-solid fa-chevron-right contact-item-chevron"></i>
                        </div>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </div>

        <!-- ===== Lịch sử đánh giá xếp hạng ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Lịch sử đánh giá xếp hạng</h5></div>

            <c:choose>
                <c:when test="${empty lifecycleEventList}">
                    <div class="empty-mini">Chưa có lần đánh giá nào -- bấm "Đánh giá lại xếp hạng" ở trên để chạy lần đầu.</div>
                </c:when>
                <c:otherwise>
                    <table class="mini-table">
                        <thead>
                            <tr><th>Ngày</th><th>Xếp hạng</th><th>Lý do</th><th>Người ghi nhận</th></tr>
                        </thead>
                        <tbody>
                            <c:forEach var="event" items="${lifecycleEventList}">
                                <tr>
                                    <td><fmt:formatDate value="${event.eventDate}" pattern="dd/MM/yyyy"/></td>
                                    <td>
                                        <span class="rating-badge
                                            ${event.relationshipRating == 'GOOD' ? 'rating-good' : ''}
                                            ${event.relationshipRating == 'NEEDS_REVIEW' ? 'rating-watch' : ''}
                                            ${event.relationshipRating == 'BAD' ? 'rating-bad' : ''}
                                            ${event.relationshipRating == 'AT_RISK' ? 'rating-risk' : ''}">${event.relationshipRating}</span>
                                    </td>
                                    <td>${fn:escapeXml(event.description)}</td>
                                    <td>
                                        ${fn:escapeXml(event.recordedByUser.fullName)}
                                        <c:if test="${event.autoGenerated}"> <span class="type-badge">Tự động</span></c:if>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </c:otherwise>
            </c:choose>
        </div>

        <!-- ===== Hoạt động gần đây ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Hoạt động gần đây</h5></div>

            <ul class="nav nav-tabs" id="activityTabs" role="tablist">
                <li class="nav-item" role="presentation">
                    <button class="nav-link active" data-bs-toggle="tab" data-bs-target="#tab-contracts" type="button">Hợp đồng</button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#tab-tickets" type="button">Phiếu hỗ trợ kỹ thuật</button>
                </li>
            </ul>

            <div class="tab-content">
                <div class="tab-pane fade show active" id="tab-contracts">
                    <c:choose>
                        <c:when test="${empty contractList}">
                            <div class="empty-mini">Khách hàng chưa có hợp đồng nào.</div>
                        </c:when>
                        <c:otherwise>
                            <table class="mini-table">
                                <thead>
                                    <tr><th>Mã hợp đồng</th><th>Tên hợp đồng</th><th>Trạng thái</th><th>Ngày ký</th></tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="contract" items="${contractList}">
                                        <tr>
                                            <td><a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}">${fn:escapeXml(contract.contractCode)}</a></td>
                                            <td>${fn:escapeXml(contract.title)}</td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${contract.status == 'Đang hiệu lực'}"><span class="status-pill status-active">${contract.status}</span></c:when>
                                                    <c:when test="${contract.status == 'Sắp hết hạn'}"><span class="status-pill status-soon">${contract.status}</span></c:when>
                                                    <c:otherwise><span class="status-pill status-expired">${contract.status}</span></c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td><fmt:formatDate value="${contract.signingDate}" pattern="dd/MM/yyyy"/></td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </c:otherwise>
                    </c:choose>
                </div>
                <div class="tab-pane fade" id="tab-tickets">
                    <c:choose>
                        <c:when test="${empty ticketList}">
                            <div class="empty-mini">Khách hàng chưa có phiếu hỗ trợ nào.</div>
                        </c:when>
                        <c:otherwise>
                            <table class="mini-table">
                                <thead>
                                    <tr><th>Mã phiếu</th><th>Mô tả</th><th>Trạng thái</th><th>Ngày tạo</th></tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="ticket" items="${ticketList}">
                                        <tr>
                                            <td><a href="ticketDetail.jsp?id=${ticket.ticketId}">${fn:escapeXml(ticket.ticketCode)}</a></td>
                                            <td>${fn:escapeXml(ticket.description)}</td>
                                            <td><span class="status-pill status-pending">${fn:escapeXml(ticket.status)}</span></td>
                                            <td><fmt:formatDate value="${ticket.createdDate}" pattern="dd/MM/yyyy"/></td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>

        </div>
    </div>

    <!-- ===== Box đánh giá xếp hạng thủ công ===== -->
    <div class="modal fade" id="evaluateModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <form method="POST" action="${pageContext.request.contextPath}/customer">
                    <div class="modal-header">
                        <span class="modal-title">Đánh giá xếp hạng khách hàng</span>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button>
                    </div>
                    <div class="modal-body">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="evaluate">
                        <input type="hidden" name="id" value="${customer.enterpriseId}">
                        <div class="mb-3">
                            <label class="form-label" for="ratingSelect">Xếp hạng quan hệ</label>
                            <select class="form-select" id="ratingSelect" name="rating" required>
                                <option value="" ${empty customer.currentRelationshipRating ? 'selected' : ''} disabled>-- Chọn xếp hạng --</option>
                                <option value="GOOD" ${customer.currentRelationshipRating == 'GOOD' ? 'selected' : ''}>Tốt</option>
                                <option value="NEEDS_REVIEW" ${customer.currentRelationshipRating == 'NEEDS_REVIEW' ? 'selected' : ''}>Cần theo dõi</option>
                                <option value="BAD" ${customer.currentRelationshipRating == 'BAD' ? 'selected' : ''}>Xấu</option>
                                <option value="AT_RISK" ${customer.currentRelationshipRating == 'AT_RISK' ? 'selected' : ''}>Có nguy cơ rời bỏ</option>
                            </select>
                        </div>
                        <div class="mb-2">
                            <label class="form-label" for="reasonInput">Lý do / ghi chú (không bắt buộc)</label>
                            <textarea class="form-control" id="reasonInput" name="description" rows="3" maxlength="255" placeholder="VD: Khách thanh toán đúng hạn, phản hồi tích cực..."></textarea>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                        <button type="submit" class="btn-modal-primary">Lưu đánh giá</button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <!-- ===== Modal xác nhận xóa (MSG-038) ===== -->
    <div class="modal fade" id="deleteModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <div class="modal-icon-warn"><i class="fa-solid fa-triangle-exclamation"></i></div>
                </div>
                <div class="modal-body">
                    <h5 class="mb-2" style="font-weight:700; color:#111827;">Xác nhận xóa khách hàng</h5>
                    Bạn có chắc chắn muốn xóa <strong>${fn:escapeXml(customer.enterpriseName)}</strong>? Hành động này không thể hoàn tác.
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <button type="button" class="btn-modal-danger" id="confirmDeleteBtn">Xóa khách hàng</button>
                </div>
            </div>
        </div>
    </div>

    <!-- ===== Modal chi tiết người liên hệ ===== -->
    <div class="modal fade" id="contactModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <span class="modal-title">Thông tin người liên hệ</span>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button>
                </div>
                <div class="modal-body">
                    <div class="contact-modal-avatar"><i class="fa-solid fa-user-tie"></i></div>
                    <div class="contact-modal-name" id="contactModalName"></div>
                    <div class="contact-modal-role" id="contactModalRole"></div>
                    <div class="contact-modal-field" id="contactModalPhoneRow">
                        <i class="fa-solid fa-phone"></i>
                        <a id="contactModalPhone" href="#"></a>
                    </div>
                    <div class="contact-modal-field" id="contactModalEmailRow">
                        <i class="fa-solid fa-envelope"></i>
                        <a id="contactModalEmail" href="#"></a>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Đóng</button>
                </div>
            </div>
        </div>
    </div>

    <!-- MSG-040: chặn xoá do BR-41 (còn hợp đồng đang hiệu lực) -->
    <c:if test="${param.error == 'has_active_contracts'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Không thể xoá: khách hàng còn hợp đồng đang hiệu lực.</span>
        </div>
    </c:if>

    <!-- Báo kết quả sau khi bấm "Đánh giá lại xếp hạng" -- không có toast này
         thì trang redirect về y hệt lúc trước, người dùng tưởng nút không
         hoạt động (nhất là khi kết quả không đổi so với lần đánh giá gần nhất). -->
    <c:if test="${param.evaluated == '1'}">
        <div class="toast-msg show">
            <i class="fa-solid fa-circle-check"></i>
            <span>
                Đã đánh giá lại.
                <c:choose>
                    <c:when test="${customer.currentRelationshipRating != null}">Xếp hạng hiện tại: <strong>${customer.currentRelationshipRating}</strong></c:when>
                    <c:otherwise>Chưa đủ dữ liệu để xếp hạng.</c:otherwise>
                </c:choose>
            </span>
        </div>
    </c:if>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/customer" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" value="${customer.enterpriseId}">
    </form>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));
        var evaluateModal = new bootstrap.Modal(document.getElementById('evaluateModal'));
        var contactModal = new bootstrap.Modal(document.getElementById('contactModal'));

        function openDeleteModal() {
            deleteModal.show();
        }

        function openEvaluateModal() {
            evaluateModal.show();
        }

        // Điền dữ liệu người liên hệ (đọc từ data-* của .contact-item được bấm)
        // vào modal chi tiết -- ẩn hẳn dòng SĐT/email nếu người đó không có
        // thông tin đó, thay vì hiện dòng trống.
        function openContactModal(el) {
            document.getElementById('contactModalName').textContent = el.dataset.name || '';
            document.getElementById('contactModalRole').textContent = el.dataset.position || 'Chưa cập nhật chức vụ';

            var phone = el.dataset.phone;
            var phoneRow = document.getElementById('contactModalPhoneRow');
            if (phone) {
                var phoneLink = document.getElementById('contactModalPhone');
                phoneLink.textContent = phone;
                phoneLink.href = 'tel:' + phone;
                phoneRow.style.display = '';
            } else {
                phoneRow.style.display = 'none';
            }

            var email = el.dataset.email;
            var emailRow = document.getElementById('contactModalEmailRow');
            if (email) {
                var emailLink = document.getElementById('contactModalEmail');
                emailLink.textContent = email;
                emailLink.href = 'mailto:' + email;
                emailRow.style.display = '';
            } else {
                emailRow.style.display = 'none';
            }

            contactModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            document.getElementById('deleteForm').submit();
        });
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

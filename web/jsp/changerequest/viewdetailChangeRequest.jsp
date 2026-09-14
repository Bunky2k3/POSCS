<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ChangeRequestController#showDetail thiết lập:
      - changeRequest : poscs.model.ChangeRequest (đã join sẵn requester/reviewer)
      - canReview     : boolean -- true nếu người xem được quyền duyệt yêu cầu NÀY

    canReview là quyền trên ĐÚNG yêu cầu này, không phải "có phải cấp trên của
    ai đó không": quản lý vùng A không được duyệt yêu cầu của cấp dưới vùng B.
    Ẩn khối duyệt ở đây chỉ cho gọn màn hình -- chặn thật nằm ở handleReview.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết yêu cầu thay đổi - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 900px; margin: 28px auto; padding: 0 20px 32px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 16px; }
        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); }
        .info-card { padding: 22px 26px 26px; margin-bottom: 20px; }
        .section-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; margin: 0 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }
        .author-tag { font-size: 0.78rem; font-weight: 600; color: #6b7280; display: inline-flex; align-items: center; gap: 6px; }
        .author-tag i { color: #9ca3af; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .view-value { font-size: 0.95rem; color: #111827; font-weight: 500; min-height: 40px; display: flex; align-items: center; border: 1px solid #eef2f6; background: #f9fafb; border-radius: 10px; padding: 8px 14px; }
        .view-value.text-block { min-height: 80px; align-items: flex-start; white-space: pre-wrap; }

        .pill { display: inline-flex; align-items: center; gap: 5px; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; }
        .pill .dot { width: 6px; height: 6px; border-radius: 50%; }
        .st-pending { background: #fff4e0; color: var(--warning); } .st-pending .dot { background: var(--warning); }
        .st-approved { background: #e8faf3; color: var(--success); } .st-approved .dot { background: var(--success); }
        .st-rejected { background: #fdecef; color: var(--danger); } .st-rejected .dot { background: var(--danger); }
        .intent-tag { display: inline-block; margin-left: 8px; padding: 2px 10px; border-radius: 20px; font-size: 0.7rem; font-weight: 600; background: #eef2ff; color: #4338ca; text-transform: none; letter-spacing: 0; }

        .form-control { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .review-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 18px; flex-wrap: wrap; }
        .btn-approve { background: linear-gradient(120deg, var(--success), #34d399); border: none; color: #fff; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; }
        .btn-reject { background: #fff; border: 1.5px solid var(--danger); color: var(--danger); border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; }
        .btn-goto { background: #fff; border: 1.5px solid #e5e7eb; color: var(--primary); border-radius: 10px; padding: 0.55rem 1.1rem; font-weight: 600; font-size: 0.86rem; text-decoration: none; display: inline-flex; align-items: center; gap: 8px; }
        .note-box { font-size: 0.85rem; color: #6b7280; background: #f9fafb; border: 1px solid #eef2f6; border-radius: 10px; padding: 12px 14px; margin-bottom: 16px; }
        @media (max-width: 768px) { .info-card { padding: 16px; } }
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

        <c:if test="${not empty param.error}">
            <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                <c:choose>
                    <c:when test="${param.error == 'already_reviewed'}">Yêu cầu này đã được người khác xử lý trước đó. Tải lại trang để xem quyết định hiện tại.</c:when>
                    <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <!-- ===== Nội dung người gửi đề xuất ===== -->
        <div class="info-card card-box">
            <div class="section-header">
                <h5>Yêu cầu<span class="intent-tag">${fn:escapeXml(changeRequest.intent)} · ${fn:escapeXml(changeRequest.resourceType)}</span></h5>
                <span class="author-tag">
                    <i class="fa-regular fa-user"></i>
                    <c:choose>
                        <c:when test="${changeRequest.requester != null}">${fn:escapeXml(changeRequest.requester.fullName)}</c:when>
                        <c:otherwise>Không rõ</c:otherwise>
                    </c:choose>
                    &middot; <fmt:formatDate value="${changeRequest.createdAt}" pattern="dd/MM/yyyy HH:mm"/>
                </span>
            </div>

            <c:if test="${changeRequest.targetId != null}">
                <div class="field-row">
                    <label>Bản ghi cần thay đổi</label>
                    <div class="view-value">Mã ${changeRequest.targetId}</div>
                </div>
            </c:if>
            <div class="field-row">
                <label>Nội dung đề xuất</label>
                <div class="view-value text-block">${fn:escapeXml(changeRequest.proposedContent)}</div>
            </div>
            <div class="field-row" style="margin-bottom: 0;">
                <label>Lý do</label>
                <div class="view-value text-block">${not empty changeRequest.reason ? fn:escapeXml(changeRequest.reason) : 'Người gửi không ghi lý do.'}</div>
            </div>
        </div>

        <!-- ===== Quyết định của cấp trên ===== -->
        <div class="info-card card-box">
            <div class="section-header">
                <h5>Quyết định của cấp trên</h5>
                <span class="pill ${changeRequest.status == 'Chờ duyệt' ? 'st-pending' : (changeRequest.status == 'Đã duyệt' ? 'st-approved' : 'st-rejected')}">
                    <span class="dot"></span>${fn:escapeXml(changeRequest.status)}
                </span>
            </div>

            <c:choose>
                <c:when test="${changeRequest.pending && canReview}">
                    <%-- Hệ thống KHÔNG tự áp thay đổi khi duyệt (xem ghi chú đầu
                         V17): duyệt xong thì người duyệt tự thực hiện trên form
                         thường, nơi mọi ràng buộc nghiệp vụ đã có sẵn. Nút dưới
                         đây chỉ đưa họ tới đúng chỗ. --%>
                    <div class="note-box">
                        <i class="fa-solid fa-circle-info me-1"></i>
                        Duyệt xong hệ thống <strong>không tự sửa dữ liệu</strong>. Bạn mở màn hình tương ứng và thực hiện,
                        để mọi kiểm tra nghiệp vụ ở đó vẫn chạy và bạn thấy được dữ liệu hiện tại trước khi đổi.
                        <c:if test="${not empty changeRequest.actionPath}">
                            <div style="margin-top: 10px;">
                                <a class="btn-goto" href="${pageContext.request.contextPath}${changeRequest.actionPath}">
                                    <i class="fa-solid fa-arrow-up-right-from-square"></i> Mở màn hình ${fn:escapeXml(changeRequest.resourceType)}
                                </a>
                            </div>
                        </c:if>
                    </div>

                    <form action="${pageContext.request.contextPath}/changerequest" method="POST">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="review">
                        <input type="hidden" name="requestId" value="${changeRequest.requestId}">
                        <div class="field-row">
                            <label for="reviewNote">Ghi chú cho người gửi</label>
                            <textarea class="form-control" id="reviewNote" name="reviewNote" rows="2"
                                      placeholder="Ví dụ: đã cập nhật xong, hoặc vì sao từ chối"></textarea>
                        </div>
                        <div class="review-actions">
                            <button type="submit" name="decision" value="reject" class="btn-reject">
                                <i class="fa-solid fa-xmark me-1"></i> Từ chối
                            </button>
                            <button type="submit" name="decision" value="approve" class="btn-approve">
                                <i class="fa-solid fa-check me-1"></i> Duyệt
                            </button>
                        </div>
                    </form>
                </c:when>

                <c:when test="${changeRequest.pending}">
                    <div class="view-value">Yêu cầu đang chờ cấp trên xem xét.</div>
                </c:when>

                <c:otherwise>
                    <div class="field-row">
                        <label>Người quyết định</label>
                        <div class="view-value">
                            <c:choose>
                                <c:when test="${changeRequest.reviewer != null}">${fn:escapeXml(changeRequest.reviewer.fullName)}</c:when>
                                <c:otherwise>—</c:otherwise>
                            </c:choose>
                            <c:if test="${changeRequest.reviewedAt != null}">
                                &nbsp;·&nbsp;<fmt:formatDate value="${changeRequest.reviewedAt}" pattern="dd/MM/yyyy HH:mm"/>
                            </c:if>
                        </div>
                    </div>
                    <div class="field-row" style="margin-bottom: 0;">
                        <label>Ghi chú</label>
                        <div class="view-value text-block">${not empty changeRequest.reviewNote ? fn:escapeXml(changeRequest.reviewNote) : 'Không có ghi chú.'}</div>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

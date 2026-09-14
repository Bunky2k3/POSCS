<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ChangeRequestController#showList thiết lập:
      - requestList  : List<poscs.model.ChangeRequest>
      - isReviewer   : boolean -- true nếu người xem là cấp trên (hoặc Admin)
      - canSubmit    : boolean -- true nếu người xem là cấp dưới (có cấp trên)
      - statusFilter : String hoặc null

    MỘT trang phục vụ hai vai: cấp dưới thấy yêu cầu mình đã gửi, cấp trên thấy
    yêu cầu cần mình duyệt. Tách thành hai trang riêng sẽ phải nhân đôi gần như
    toàn bộ bảng chỉ để đổi vài chữ ở tiêu đề.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Yêu cầu thay đổi - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1080px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; margin: 0; }
        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 22px 26px 26px; }

        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.3rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; color: #fff; display: inline-flex; align-items: center; gap: 8px; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3); }
        .btn-primary:hover { color: #fff; background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }

        table { width: 100%; border-collapse: collapse; }
        thead th { font-size: 0.72rem; text-transform: uppercase; letter-spacing: .3px; color: #6b7280; font-weight: 600; padding: 10px 12px; border-bottom: 1.5px solid #eef2f6; white-space: nowrap; }
        tbody td { padding: 12px; border-bottom: 1px solid #f3f4f6; font-size: 0.9rem; color: #111827; vertical-align: top; }
        tbody tr:last-child td { border-bottom: none; }
        .cell-content { max-width: 380px; }
        .cell-sub { font-size: 0.78rem; color: #9ca3af; margin-top: 3px; }

        .pill { display: inline-flex; align-items: center; gap: 5px; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; white-space: nowrap; }
        .pill .dot { width: 6px; height: 6px; border-radius: 50%; }
        .st-pending { background: #fff4e0; color: var(--warning); } .st-pending .dot { background: var(--warning); }
        .st-approved { background: #e8faf3; color: var(--success); } .st-approved .dot { background: var(--success); }
        .st-rejected { background: #fdecef; color: var(--danger); } .st-rejected .dot { background: var(--danger); }
        .intent-tag { display: inline-block; padding: 2px 9px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; background: #eef2ff; color: #4338ca; }

        .filter-bar { display: flex; gap: 10px; align-items: center; margin-bottom: 18px; flex-wrap: wrap; }
        .form-select { padding: 0.5rem 0.8rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.88rem; width: auto; }
        .empty-box { padding: 40px 20px; text-align: center; color: #6b7280; font-size: 0.92rem; }
        .empty-box i { font-size: 2rem; color: #d1d5db; display: block; margin-bottom: 10px; }
        .table-scroll { overflow-x: auto; }
        @media (max-width: 768px) { .card-box { padding: 16px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="changerequest" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <div class="page-header-row">
            <div>
                <h2>Yêu cầu thay đổi</h2>
                <p>
                    <c:choose>
                        <c:when test="${isReviewer}">Yêu cầu do cấp dưới của bạn gửi lên, chờ bạn duyệt.</c:when>
                        <c:otherwise>Các yêu cầu bạn đã gửi lên cấp trên và kết quả xử lý.</c:otherwise>
                    </c:choose>
                </p>
            </div>
            <c:if test="${canSubmit}">
                <a href="${pageContext.request.contextPath}/changerequest?action=new" class="btn-primary">
                    <i class="fa-solid fa-plus"></i> Gửi yêu cầu mới
                </a>
            </c:if>
        </div>

        <div class="card-box">
            <%-- Bộ lọc chỉ có nghĩa với người duyệt: người gửi xem hết yêu cầu
                 của mình, số lượng vốn đã ít. --%>
            <c:if test="${isReviewer}">
                <form class="filter-bar" method="GET" action="${pageContext.request.contextPath}/changerequest">
                    <select class="form-select" name="status" onchange="this.form.submit();">
                        <option value="" ${empty statusFilter ? 'selected' : ''}>-- Tất cả trạng thái --</option>
                        <option value="Chờ duyệt" ${statusFilter == 'Chờ duyệt' ? 'selected' : ''}>Chờ duyệt</option>
                        <option value="Đã duyệt" ${statusFilter == 'Đã duyệt' ? 'selected' : ''}>Đã duyệt</option>
                        <option value="Từ chối" ${statusFilter == 'Từ chối' ? 'selected' : ''}>Từ chối</option>
                    </select>
                </form>
            </c:if>

            <c:choose>
                <c:when test="${empty requestList}">
                    <div class="empty-box">
                        <i class="fa-regular fa-folder-open"></i>
                        <c:choose>
                            <c:when test="${isReviewer}">Chưa có yêu cầu nào cần bạn duyệt.</c:when>
                            <c:when test="${canSubmit}">Bạn chưa gửi yêu cầu nào.</c:when>
                            <c:otherwise>Bạn chưa được xếp vào cây tổ chức nên chưa có yêu cầu nào liên quan.</c:otherwise>
                        </c:choose>
                    </div>
                </c:when>
                <c:otherwise>
                    <div class="table-scroll">
                    <table>
                        <thead>
                            <tr>
                                <th>Nội dung đề xuất</th>
                                <th>Loại</th>
                                <c:if test="${isReviewer}"><th>Người gửi</th></c:if>
                                <th>Ngày gửi</th>
                                <th>Trạng thái</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="r" items="${requestList}">
                                <tr>
                                    <td class="cell-content">
                                        ${fn:escapeXml(r.proposedContent)}
                                        <c:if test="${not empty r.reason}">
                                            <div class="cell-sub">Lý do: ${fn:escapeXml(r.reason)}</div>
                                        </c:if>
                                    </td>
                                    <td>
                                        <span class="intent-tag">${fn:escapeXml(r.intent)}</span>
                                        <div class="cell-sub">${fn:escapeXml(r.resourceType)}</div>
                                    </td>
                                    <c:if test="${isReviewer}">
                                        <td>
                                            <c:choose>
                                                <c:when test="${r.requester != null}">${fn:escapeXml(r.requester.fullName)}</c:when>
                                                <c:otherwise>—</c:otherwise>
                                            </c:choose>
                                        </td>
                                    </c:if>
                                    <td><fmt:formatDate value="${r.createdAt}" pattern="dd/MM/yyyy HH:mm"/></td>
                                    <td>
                                        <span class="pill ${r.status == 'Chờ duyệt' ? 'st-pending' : (r.status == 'Đã duyệt' ? 'st-approved' : 'st-rejected')}">
                                            <span class="dot"></span>${fn:escapeXml(r.status)}
                                        </span>
                                    </td>
                                    <td>
                                        <a href="${pageContext.request.contextPath}/changerequest?action=view&id=${r.requestId}">Xem</a>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
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

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thông báo - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 760px; margin: 32px auto; padding: 0 20px 32px; }

        .page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; flex-wrap: wrap; gap: 10px; }
        .page-head h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.3rem; margin: 0; }
        .btn-mark-all {
            border: 1.5px solid var(--primary); color: var(--primary); background: #fff;
            font-weight: 600; border-radius: 10px; padding: 7px 16px; font-size: 0.83rem;
            text-decoration: none; display: inline-flex; align-items: center; gap: 6px;
        }
        .btn-mark-all:hover { background: var(--primary); color: #fff; }

        .notif-list-card { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0,40,80,0.08); overflow: hidden; }
        .notif-row {
            display: flex; align-items: flex-start; gap: 14px; padding: 16px 20px;
            border-bottom: 1px solid #f1f5f9; text-decoration: none;
        }
        .notif-row:last-child { border-bottom: none; }
        .notif-row .notif-icon { width: 40px; height: 40px; font-size: 0.95rem; }
        .notif-row .notif-text { font-size: 0.92rem; color: #111827; font-weight: 500; }
        .notif-row .notif-time { font-size: 0.75rem; color: #9ca3af; margin-top: 3px; }
        .notif-row:not(.unread) .notif-text { font-weight: 400; color: #6b7280; }
        .notif-row:not(.unread) .notif-icon { background: #f3f4f6; color: #9ca3af; }
        .notif-row-body { flex: 1; min-width: 0; }
        .notif-row-action { flex-shrink: 0; align-self: center; }
        .mark-read-link {
            font-size: 0.75rem; color: var(--primary); font-weight: 600; text-decoration: none;
            white-space: nowrap;
        }
        .mark-read-link:hover { text-decoration: underline; }
        .notif-empty-page { padding: 48px 20px; text-align: center; color: #9ca3af; }
        .notif-empty-page i { font-size: 2.2rem; margin-bottom: 12px; display: block; }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <div class="page-head">
            <h2><i class="fa-regular fa-bell me-2"></i>Thông báo</h2>
            <c:if test="${not empty notifications}">
                <a class="btn-mark-all" href="${pageContext.request.contextPath}/notifications?action=readAll">
                    <i class="fa-solid fa-check-double"></i> Đánh dấu tất cả đã đọc
                </a>
            </c:if>
        </div>

        <div class="notif-list-card">
            <c:choose>
                <c:when test="${empty notifications}">
                    <div class="notif-empty-page">
                        <i class="fa-regular fa-bell-slash"></i>
                        Chưa có thông báo nào
                    </div>
                </c:when>
                <c:otherwise>
                    <c:forEach var="notif" items="${notifications}">
                        <div class="notif-row ${notif.read ? '' : 'unread'}">
                            <span class="notif-icon"><i class="fa-regular fa-bell"></i></span>
                            <div class="notif-row-body">
                                <div class="notif-text"><c:out value="${notif.title}"/></div>
                                <div class="notif-time">${notif.relativeTime}</div>
                            </div>
                            <div class="notif-row-action">
                                <c:if test="${not notif.read}">
                                    <a class="mark-read-link" href="${pageContext.request.contextPath}/notifications?action=read&id=${notif.notificationId}">Đánh dấu đã đọc</a>
                                </c:if>
                            </div>
                        </div>
                    </c:forEach>
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

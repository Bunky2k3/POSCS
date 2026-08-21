<%@page pageEncoding="UTF-8"%><%--
    Topbar dùng chung cho mọi trang sau đăng nhập -- include bằng
    <%@ include file="/jsp/common/topbar.jsp" %> (JSP static include, gộp
    thẳng vào lúc biên dịch nên trang gọi nó phải tự khai báo sẵn taglib
    "c" và "fn", đồng thời link web/css/appshell.css và
    web/js/appshell.js trong <head>).

    Không cần request attribute nào cho phần tên/vai trò người dùng -- lấy
    thẳng từ sessionScope.currentUser. Riêng chuông thông báo (unreadNotifCount,
    recentNotifications) do AuthenticationFilter bơm sẵn vào request cho MỌI
    trang sau đăng nhập, xem AuthenticationFilter#isStaticAssetPath.
--%>
<nav class="topbar">
    <div class="topbar-left">
        <a href="${pageContext.request.contextPath}/dashboard" class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</a>
        <button type="button" class="sidebar-toggle" id="sidebarToggle" aria-label="Thu gọn menu">
            <i class="fa-solid fa-bars"></i>
        </button>
    </div>
    <div class="topbar-right">
        <div class="dropdown">
            <div class="bell-icon" data-bs-toggle="dropdown" aria-expanded="false">
                <i class="fa-regular fa-bell"></i><c:if test="${requestScope.unreadNotifCount > 0}"><span class="dot"></span></c:if>
            </div>
            <ul class="dropdown-menu dropdown-menu-end notif-dropdown">
                <li class="notif-header">Thông báo
                    <c:if test="${requestScope.unreadNotifCount > 0}"><span class="notif-count">${requestScope.unreadNotifCount} mới</span></c:if>
                </li>
                <c:choose>
                    <c:when test="${empty requestScope.recentNotifications}">
                        <li class="notif-empty">Chưa có thông báo nào</li>
                    </c:when>
                    <c:otherwise>
                        <c:forEach var="notif" items="${requestScope.recentNotifications}">
                            <li><a class="dropdown-item notif-item ${notif.read ? '' : 'unread'}"
                                   href="${pageContext.request.contextPath}/notifications?action=read&id=${notif.notificationId}">
                                <span class="notif-icon"><i class="fa-regular fa-bell"></i></span>
                                <div><div class="notif-text"><c:out value="${notif.title}"/></div><div class="notif-time">${notif.relativeTime}</div></div>
                            </a></li>
                        </c:forEach>
                    </c:otherwise>
                </c:choose>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item text-center small" href="${pageContext.request.contextPath}/notifications">Xem tất cả thông báo</a></li>
            </ul>
        </div>
        <div class="dropdown">
            <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff"
                 class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
            <ul class="dropdown-menu dropdown-menu-end">
                <li class="dd-user-header">
                    <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" alt="avatar">
                    <div><div class="dd-name"><c:out value="${sessionScope.currentUser.fullName}"/></div><div class="dd-role"><c:out value="${sessionScope.currentUser.role.roleName}"/></div></div>
                </li>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/viewProfile"><i class="fa-regular fa-id-card me-2"></i>Thông tin cá nhân</a></li>
                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/changePassword.jsp"><i class="fa-solid fa-key me-2"></i>Đổi mật khẩu</a></li>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item text-danger" href="${pageContext.request.contextPath}/login?action=logout"><i class="fa-solid fa-arrow-right-from-bracket me-2"></i>Đăng xuất</a></li>
            </ul>
        </div>
    </div>
</nav>

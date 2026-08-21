<%@page pageEncoding="UTF-8"%><%--
    Sidebar điều hướng dùng chung -- include bằng
    <%@ include file="/jsp/common/sidebar.jsp" %> (cùng điều kiện taglib/CSS
    như topbar.jsp, xem ghi chú ở đó).

    Trang gọi PHẢI set request attribute "activeNav" trước khi include, để
    tô sáng đúng mục đang đứng, vd:
        <c:set var="activeNav" value="customer" scope="request"/>
    Giá trị hợp lệ: dashboard | customer | contract | product | ticket | employee.
    Không set thì không mục nào được tô sáng (không lỗi, chỉ mất highlight).
--%>
<aside class="sidebar" id="sidebar">
    <a href="${pageContext.request.contextPath}/dashboard" class="sidebar-link ${activeNav == 'dashboard' ? 'active' : ''}"><i class="fa-solid fa-house"></i><span>Trang chủ</span></a>
    <a href="${pageContext.request.contextPath}/customer" class="sidebar-link ${activeNav == 'customer' ? 'active' : ''}"><i class="fa-solid fa-users"></i><span>Khách hàng</span></a>
    <a href="${pageContext.request.contextPath}/contract" class="sidebar-link ${activeNav == 'contract' ? 'active' : ''}"><i class="fa-solid fa-file-contract"></i><span>Hợp đồng</span></a>
    <a href="${pageContext.request.contextPath}/product" class="sidebar-link ${activeNav == 'product' ? 'active' : ''}"><i class="fa-solid fa-box"></i><span>Sản phẩm</span></a>
    <a href="${pageContext.request.contextPath}/ticket" class="sidebar-link ${activeNav == 'ticket' ? 'active' : ''}"><i class="fa-solid fa-headset"></i><span>Phiếu hỗ trợ</span></a>
    <c:if test="${sessionScope.currentUser.role.roleName == 'Admin'}"><a href="${pageContext.request.contextPath}/employee" class="sidebar-link ${activeNav == 'employee' ? 'active' : ''}"><i class="fa-solid fa-user-tie"></i><span>Nhân viên</span></a></c:if>
</aside>

<%@page pageEncoding="UTF-8"%><%--
    Sidebar điều hướng dùng chung -- include bằng
    <%@ include file="/jsp/common/sidebar.jsp" %> (cùng điều kiện taglib/CSS
    như topbar.jsp, xem ghi chú ở đó).

    Trang gọi PHẢI set request attribute "activeNav" trước khi include, để
    tô sáng đúng mục đang đứng, vd:
        <c:set var="activeNav" value="customer" scope="request"/>
    Giá trị hợp lệ: dashboard | customer | contract | product | ticket | employee
    | changerequest | systemLog.
    Không set thì không mục nào được tô sáng (không lỗi, chỉ mất highlight).

    Riêng "contract" có ba mục con; trang set thêm
        <c:set var="activeContractKind" value="${kind}" scope="request"/>
    với giá trị sell | buy | handover. Thiếu thì mặc định sáng "Hợp đồng bán"
    -- nên trang chi tiết/sửa hợp đồng PHẢI set theo chiều của chính hợp đồng
    đang mở, không thì mở hợp đồng mua vẫn thấy sáng "Hợp đồng bán".

    Riêng "customer" có hai mục con; trang cần set thêm
        <c:set var="activeCustomerKind" value="${kind}" scope="request"/>
    với giá trị buyer | supplier. Thiếu thì mặc định sáng mục "Khách hàng mua"
    -- đúng với việc CustomerController cũng coi kind thiếu là khách mua.
--%>
<aside class="sidebar" id="sidebar">
    <a href="${pageContext.request.contextPath}/dashboard" class="sidebar-link ${activeNav == 'dashboard' ? 'active' : ''}"><i class="fa-solid fa-house"></i><span>Trang chủ</span></a>
    <%-- Khách hàng tách làm hai mục con theo vai (xem ghi chú đầu V20). Cùng
         một trang /customer, khác nhau đúng tham số kind; activeCustomerKind
         quyết định mục con nào sáng, do CustomerController set. --%>
    <div class="sidebar-group-label"><i class="fa-solid fa-users"></i><span>Khách hàng</span></div>
    <div class="sidebar-sub">
        <a href="${pageContext.request.contextPath}/customer?kind=buyer" class="sidebar-link ${activeNav == 'customer' and activeCustomerKind != 'supplier' ? 'active' : ''}"><i class="fa-solid fa-cart-shopping"></i><span>Khách hàng mua</span></a>
        <a href="${pageContext.request.contextPath}/customer?kind=supplier" class="sidebar-link ${activeNav == 'customer' and activeCustomerKind == 'supplier' ? 'active' : ''}"><i class="fa-solid fa-truck-field"></i><span>Nhà cung cấp</span></a>
    </div>
    <%-- Hợp đồng tách hai mục con theo chiều (xem ghi chú đầu V21). Cặp đôi
         với vai khách hàng bị CHÉO: hợp đồng BÁN ký với "Khách hàng mua". --%>
    <div class="sidebar-group-label"><i class="fa-solid fa-file-contract"></i><span>Hợp đồng</span></div>
    <div class="sidebar-sub">
        <%-- Chỉ "khác buy" thôi là chưa đủ: hàng đợi bàn giao cũng đặt activeNav
             = contract (kèm kind = handover), nên mục này từng sáng CÙNG LÚC với
             "Bàn giao xử lý" -- hai mục active một lượt thì không còn đọc ra
             mình đang đứng ở đâu. Phải loại trừ cả handover. --%>
        <a href="${pageContext.request.contextPath}/contract?kind=sell" class="sidebar-link ${activeNav == 'contract' and activeContractKind != 'buy' and activeContractKind != 'handover' ? 'active' : ''}"><i class="fa-solid fa-file-export"></i><span>Hợp đồng bán</span></a>
        <a href="${pageContext.request.contextPath}/contract?kind=buy" class="sidebar-link ${activeNav == 'contract' and activeContractKind == 'buy' ? 'active' : ''}"><i class="fa-solid fa-file-import"></i><span>Hợp đồng mua</span></a>
        <%-- Hàng đợi bàn giao: hợp đồng đang nằm chờ ở Kế toán / Dự án. Để
             trong mục Hợp đồng vì đó là việc của hợp đồng, nhưng KHÔNG mang
             tham số kind -- nó không thuộc chiều bán hay mua nào cả. --%>
        <a href="${pageContext.request.contextPath}/contract?action=handovers" class="sidebar-link ${activeNav == 'contract' and activeContractKind == 'handover' ? 'active' : ''}"><i class="fa-solid fa-share-from-square"></i><span>Bàn giao xử lý</span></a>
    </div>
    <a href="${pageContext.request.contextPath}/product" class="sidebar-link ${activeNav == 'product' ? 'active' : ''}"><i class="fa-solid fa-box"></i><span>Sản phẩm</span></a>
    <a href="${pageContext.request.contextPath}/ticket" class="sidebar-link ${activeNav == 'ticket' ? 'active' : ''}"><i class="fa-solid fa-headset"></i><span>Phiếu hỗ trợ</span></a>
    <c:if test="${sessionScope.currentUser.role.roleName == 'Admin'}"><a href="${pageContext.request.contextPath}/employee" class="sidebar-link ${activeNav == 'employee' ? 'active' : ''}"><i class="fa-solid fa-user-tie"></i><span>Nhân viên</span></a></c:if>
    <%-- Yêu cầu thay đổi: hiện cho MỌI vai, vì cùng một trang phục vụ hai
         phía -- cấp dưới theo dõi yêu cầu mình gửi, cấp trên duyệt yêu cầu của
         cấp dưới. Người chưa xếp vào cây tổ chức mở ra thấy danh sách rỗng,
         đúng chứ không phải lỗi. --%>
    <a href="${pageContext.request.contextPath}/changerequest" class="sidebar-link ${activeNav == 'changerequest' ? 'active' : ''}"><i class="fa-solid fa-inbox"></i><span>Yêu cầu thay đổi</span></a>
    <%-- Nhật ký hệ thống: chỉ Admin. Ẩn ở đây chỉ là cho gọn menu -- chặn thật
         nằm ở AccessControl.requireAdmin trong SystemLogController. --%>
    <c:if test="${sessionScope.currentUser.role.roleName == 'Admin'}"><a href="${pageContext.request.contextPath}/systemLog" class="sidebar-link ${activeNav == 'systemLog' ? 'active' : ''}"><i class="fa-solid fa-file-lines"></i><span>Nhật ký</span></a></c:if>
</aside>

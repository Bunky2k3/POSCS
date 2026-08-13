<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ContractController#showEditForm thiết lập trước khi forward tới trang này:
      - contract     : poscs.model.Contract (hợp đồng đang sửa)
      - customerList : List<poscs.model.Enterprise>
      - userList      : List<poscs.model.User>

    Hạng mục sản phẩm/dịch vụ bên dưới hiện chưa được lưu xuống DB, xem ghi
    chú ở addnewcontract.jsp -- phần này chỉ demo giao diện.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cập nhật hợp đồng - POSCS Portal</title>

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

        .page-container { max-width: 980px; margin: 28px auto; padding: 0 20px 60px; }
        .page-header-row { margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 30px 34px 34px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 30px 0 18px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row label .req { color: var(--danger); }
        .form-control, .form-select { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .form-control:focus, .form-select:focus { background-color: #ffffff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }

        /* ===== Bảng hạng mục ===== */
        .item-table { width: 100%; border-collapse: separate; border-spacing: 0; }
        .item-table th {
            font-size: 0.72rem; text-transform: uppercase; color: #9ca3af; font-weight: 700;
            padding: 8px 10px; border-bottom: 1.5px solid #eef2f6; text-align: left;
        }
        .item-table td { padding: 8px 10px; vertical-align: middle; border-bottom: 1px solid #f3f4f6; }
        .item-table input { width: 100%; padding: 8px 10px; border-radius: 8px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.86rem; }
        .item-table input:focus { outline: none; border-color: var(--primary-light); background: #fff; }
        .item-table .item-subtotal { font-weight: 600; color: #111827; white-space: nowrap; }
        .btn-remove-item { width: 30px; height: 30px; border-radius: 8px; border: none; background: #fdecef; color: var(--danger); cursor: pointer; }
        .btn-remove-item:hover { background: #fbd6dd; }
        .btn-add-item {
            margin-top: 12px; background: #fff; border: 1.5px dashed var(--primary-light); color: var(--primary);
            border-radius: 10px; padding: 8px 16px; font-size: 0.85rem; font-weight: 600; cursor: pointer;
        }
        .btn-add-item:hover { background: #f0f9ff; }

        .totals-box { margin-top: 18px; margin-left: auto; max-width: 320px; }
        .totals-row { display: flex; justify-content: space-between; padding: 7px 0; font-size: 0.88rem; color: #374151; }
        .totals-row.grand { border-top: 1.5px solid #eef2f6; margin-top: 6px; padding-top: 12px; font-weight: 700; font-size: 1rem; color: var(--primary-dark); }

        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3); }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) { .card-box { padding: 24px 20px 28px; } }
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
            <a href="${pageContext.request.contextPath}/dashboard.jsp" class="sidebar-link"><i class="fa-solid fa-house"></i><span>Trang chủ</span></a>
            <a href="${pageContext.request.contextPath}/customer" class="sidebar-link"><i class="fa-solid fa-users"></i><span>Khách hàng</span></a>
            <a href="${pageContext.request.contextPath}/contract" class="sidebar-link active"><i class="fa-solid fa-file-contract"></i><span>Hợp đồng</span></a>
            <a href="${pageContext.request.contextPath}/ticket" class="sidebar-link"><i class="fa-solid fa-headset"></i><span>Phiếu hỗ trợ</span></a>
        </aside>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại chi tiết hợp đồng</a>

        <div class="page-header-row">
            <h2>Cập nhật hợp đồng</h2>
            <p>Mã hợp đồng: <strong style="color:var(--primary-dark)">${fn:escapeXml(contract.contractCode)}</strong></p>
        </div>

        <div class="card-box">
            <form id="createContractForm" action="${pageContext.request.contextPath}/contract" method="POST" onsubmit="return validateForm();">
                <input type="hidden" name="action" value="update">
                <input type="hidden" name="contractId" value="${contract.contractId}">

                <div class="section-header"><h5>Thông tin chung</h5></div>
                <div class="row">
                    <div class="col-12 field-row">
                        <label>Tiêu đề hợp đồng <span class="req">*</span></label>
                        <input type="text" class="form-control" id="title" name="title" value="${fn:escapeXml(contract.title)}">
                        <span class="error-text" id="err-title">Tiêu đề không được để trống.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customer" name="enterpriseId">
                            <option value="">-- Chọn khách hàng --</option>
                            <c:forEach var="customer" items="${customerList}">
                                <option value="${customer.enterpriseId}" ${customer.enterpriseId == contract.enterpriseId ? 'selected' : ''}>${fn:escapeXml(customer.enterpriseName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-customer">Vui lòng chọn khách hàng.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Loại hợp đồng <span class="req">*</span></label>
                        <select class="form-select" id="contractType" name="contractType">
                            <option value="">-- Chọn loại hợp đồng --</option>
                            <option value="Cung cấp thiết bị" ${contract.contractType == 'Cung cấp thiết bị' ? 'selected' : ''}>Cung cấp thiết bị</option>
                            <option value="Thi công lắp đặt" ${contract.contractType == 'Thi công lắp đặt' ? 'selected' : ''}>Thi công lắp đặt</option>
                            <option value="Bảo trì bảo dưỡng" ${contract.contractType == 'Bảo trì bảo dưỡng' ? 'selected' : ''}>Bảo trì bảo dưỡng</option>
                        </select>
                        <span class="error-text" id="err-contractType">Vui lòng chọn loại hợp đồng.</span>
                    </div>

                    <div class="col-md-4 field-row">
                        <label>Ngày ký <span class="req">*</span></label>
                        <input type="date" class="form-control" id="signDate" name="signDate" value="<fmt:formatDate value="${contract.signingDate}" pattern="yyyy-MM-dd"/>">
                        <span class="error-text" id="err-dates">Ngày ký, ngày hiệu lực, ngày hết hạn không hợp lệ.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày hiệu lực <span class="req">*</span></label>
                        <input type="date" class="form-control" id="effectiveDate" name="effectiveDate" value="<fmt:formatDate value="${contract.effectiveDate}" pattern="yyyy-MM-dd"/>">
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày kết thúc <span class="req">*</span></label>
                        <input type="date" class="form-control" id="endDate" name="endDate" value="<fmt:formatDate value="${contract.endDate}" pattern="yyyy-MM-dd"/>">
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Người phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="owner" name="ownerId">
                            <option value="">-- Chọn nhân viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}" ${staff.userId == contract.ownerId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-owner">Vui lòng chọn người phụ trách.</span>
                    </div>
                </div>

                <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
                <table class="item-table">
                    <thead>
                        <tr><th style="width:38%">Tên sản phẩm/dịch vụ</th><th style="width:14%">Số lượng</th><th style="width:20%">Đơn giá (đ)</th><th style="width:20%">Thành tiền</th><th style="width:8%"></th></tr>
                    </thead>
                    <tbody id="itemsBody">
                        <tr class="item-row">
                            <td><input type="text" class="item-name" value="Cáp quang OM4 4FO"></td>
                            <td><input type="number" class="item-qty" min="1" value="500" oninput="calcRow(this)"></td>
                            <td><input type="number" class="item-price" min="0" value="1800000" oninput="calcRow(this)"></td>
                            <td class="item-subtotal">900.000.000 đ</td>
                            <td><button type="button" class="btn-remove-item" onclick="removeRow(this)"><i class="fa-solid fa-xmark"></i></button></td>
                        </tr>
                        <tr class="item-row">
                            <td><input type="text" class="item-name" value="Đầu nối quang SC/APC"></td>
                            <td><input type="number" class="item-qty" min="1" value="1000" oninput="calcRow(this)"></td>
                            <td><input type="number" class="item-price" min="0" value="35000" oninput="calcRow(this)"></td>
                            <td class="item-subtotal">35.000.000 đ</td>
                            <td><button type="button" class="btn-remove-item" onclick="removeRow(this)"><i class="fa-solid fa-xmark"></i></button></td>
                        </tr>
                    </tbody>
                </table>
                <button type="button" class="btn-add-item" onclick="addRow()"><i class="fa-solid fa-plus me-1"></i>Thêm hạng mục</button>

                <div class="totals-box">
                    <div class="totals-row"><span>Tổng tiền hàng</span><span id="subTotal">935.000.000 đ</span></div>
                    <div class="totals-row"><span>Thuế GTGT (10%)</span><span id="vatTotal">93.500.000 đ</span></div>
                    <div class="totals-row grand"><span>Tổng cộng</span><span id="grandTotal">1.028.500.000 đ</span></div>
                </div>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Lưu thay đổi</button>
                </div>
            </form>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        function formatVND(num) {
            return num.toLocaleString('vi-VN') + ' đ';
        }

        function calcRow(el) {
            var row = el.closest('.item-row');
            var qty = parseFloat(row.querySelector('.item-qty').value) || 0;
            var price = parseFloat(row.querySelector('.item-price').value) || 0;
            row.querySelector('.item-subtotal').textContent = formatVND(qty * price);
            calcTotals();
        }

        function calcTotals() {
            var rows = document.querySelectorAll('.item-row');
            var subTotal = 0;
            rows.forEach(function (row) {
                var qty = parseFloat(row.querySelector('.item-qty').value) || 0;
                var price = parseFloat(row.querySelector('.item-price').value) || 0;
                subTotal += qty * price;
            });
            var vat = Math.round(subTotal * 0.1);
            var grand = subTotal + vat;
            document.getElementById('subTotal').textContent = formatVND(subTotal);
            document.getElementById('vatTotal').textContent = formatVND(vat);
            document.getElementById('grandTotal').textContent = formatVND(grand);
        }

        function addRow() {
            var tbody = document.getElementById('itemsBody');
            var tr = document.createElement('tr');
            tr.className = 'item-row';
            tr.innerHTML =
                '<td><input type="text" class="item-name" placeholder="Tên sản phẩm/dịch vụ"></td>' +
                '<td><input type="number" class="item-qty" min="1" value="1" oninput="calcRow(this)"></td>' +
                '<td><input type="number" class="item-price" min="0" value="0" oninput="calcRow(this)"></td>' +
                '<td class="item-subtotal">0 đ</td>' +
                '<td><button type="button" class="btn-remove-item" onclick="removeRow(this)"><i class="fa-solid fa-xmark"></i></button></td>';
            tbody.appendChild(tr);
        }

        function removeRow(btn) {
            var rows = document.querySelectorAll('.item-row');
            if (rows.length <= 1) return; // giữ lại tối thiểu 1 dòng
            btn.closest('.item-row').remove();
            calcTotals();
        }

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            var title = document.getElementById('title');
            if (!title.value.trim()) { document.getElementById('err-title').style.display = 'block'; valid = false; }

            ['customer', 'contractType', 'owner'].forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            var signDate = document.getElementById('signDate').value;
            var effectiveDate = document.getElementById('effectiveDate').value;
            var endDate = document.getElementById('endDate').value;
            if (!signDate || !effectiveDate || !endDate || !(signDate <= effectiveDate && effectiveDate <= endDate)) {
                document.getElementById('err-dates').style.display = 'block';
                valid = false;
            }

            return valid;
        }
    </script>

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

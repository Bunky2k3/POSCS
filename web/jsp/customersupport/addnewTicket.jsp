<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do TechnicalSupportTicketController#showCreateForm
    thiết lập trước khi forward tới trang này:
      - customerList : List<poscs.model.Enterprise> (toàn bộ khách hàng, để đổ dropdown "Khách hàng")
      - userList      : List<poscs.model.User>       (toàn bộ nhân viên, để đổ dropdown "Kỹ thuật viên phụ trách")

    Dropdown "Hợp đồng liên quan" KHÔNG đổ sẵn từ server -- JS nạp qua AJAX
    (GET /contract/byEnterprise?enterpriseId=..., xem ContractLookupController)
    ngay khi chọn khách hàng, vì hợp đồng phụ thuộc khách hàng đã chọn.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tạo phiếu hỗ trợ - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 900px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 24px 28px 28px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 24px 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row label .req { color: var(--danger); }
        .form-control, .form-select { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .form-control:focus, .form-select:focus { background-color: #ffffff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }
        .form-check-label { font-size: 0.88rem; color: #374151; }

        /* ===== Box chọn khách hàng / hợp đồng (thay cho dropdown) ===== */
        .picker-field {
            display: flex; align-items: center; justify-content: space-between; cursor: pointer;
            padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb;
            background-color: #f9fafb; font-size: 0.9rem; color: #111827;
        }
        .picker-field:hover { border-color: var(--primary-light); }
        .picker-field.disabled { cursor: not-allowed; color: #9ca3af; background-color: #f3f4f6; }
        .picker-field i { color: #9ca3af; font-size: 0.85rem; flex-shrink: 0; margin-left: 10px; }
        .picker-placeholder { color: #9ca3af; }

        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 22px 24px 6px; display: flex; justify-content: space-between; align-items: center; }
        .modal-title { font-weight: 700; color: var(--primary-dark); font-size: 1.02rem; }
        .modal-body { padding: 10px 24px 24px; }
        .picker-search { margin-bottom: 12px; }
        .picker-list { max-height: 320px; overflow-y: auto; border: 1px solid #eef2f6; border-radius: 10px; }
        .picker-item { padding: 10px 14px; cursor: pointer; border-bottom: 1px solid #f3f4f6; }
        .picker-item:last-child { border-bottom: none; }
        .picker-item:hover { background: #f0f9ff; }
        .picker-item-title { font-weight: 600; font-size: 0.88rem; color: #111827; }
        .picker-item-sub { font-size: 0.76rem; color: #9ca3af; margin-top: 2px; }
        .picker-empty { padding: 24px; text-align: center; color: #9ca3af; font-size: 0.85rem; }

        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3); }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) { .card-box { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="ticket" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/ticket" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <div class="page-header-row">
            <h2>Tạo phiếu hỗ trợ</h2>
            <p>Tiếp nhận yêu cầu hỗ trợ kỹ thuật mới từ khách hàng</p>
        </div>

        <div class="card-box">
            <form id="createTicketForm" action="${pageContext.request.contextPath}/ticket" method="POST" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="create">

                <div class="section-header"><h5>Thông tin chung</h5></div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Khách hàng <span class="req">*</span></label>
                        <div class="picker-field" id="customerPickerField" onclick="openCustomerPicker()">
                            <span id="customerPickerText" class="picker-placeholder">-- Chọn khách hàng --</span>
                            <i class="fa-solid fa-magnifying-glass"></i>
                        </div>
                        <input type="hidden" id="customer" name="enterpriseId" value="">
                        <span class="error-text" id="err-customer">Vui lòng chọn khách hàng.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Hợp đồng liên quan</label>
                        <div class="picker-field disabled" id="contractPickerField" onclick="openContractPicker()">
                            <span id="contractPickerText" class="picker-placeholder">-- Chọn khách hàng trước --</span>
                            <i class="fa-solid fa-magnifying-glass"></i>
                        </div>
                        <input type="hidden" id="contract" name="contractId" value="">
                    </div>

                    <div class="col-md-4 field-row">
                        <label>Loại phiếu <span class="req">*</span></label>
                        <select class="form-select" id="ticketType" name="ticketType">
                            <option value="">-- Chọn loại phiếu --</option>
                            <option value="Bảo hành">Bảo hành</option>
                            <option value="Bảo trì">Bảo trì</option>
                            <option value="Sửa chữa">Sửa chữa</option>
                            <option value="Tư vấn">Tư vấn</option>
                            <option value="Khác">Khác</option>
                        </select>
                        <span class="error-text" id="err-ticketType">Vui lòng chọn loại phiếu.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Mức ưu tiên <span class="req">*</span></label>
                        <select class="form-select" id="priority" name="priority">
                            <option value="">-- Chọn mức ưu tiên --</option>
                            <option value="Khẩn cấp">Khẩn cấp</option>
                            <option value="Cao">Cao</option>
                            <option value="Bình thường">Bình thường</option>
                            <option value="Thấp">Thấp</option>
                        </select>
                        <span class="error-text" id="err-priority">Vui lòng chọn mức ưu tiên.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Kênh tiếp nhận <span class="req">*</span></label>
                        <select class="form-select" id="receptionChannel" name="receptionChannel">
                            <option value="">-- Chọn kênh tiếp nhận --</option>
                            <option value="Điện thoại">Điện thoại</option>
                            <option value="Email">Email</option>
                            <option value="Trực tiếp">Trực tiếp</option>
                            <option value="Website">Website</option>
                        </select>
                        <span class="error-text" id="err-receptionChannel">Vui lòng chọn kênh tiếp nhận.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Kỹ thuật viên phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="technician" name="assignedTechnicianId">
                            <option value="">-- Chọn kỹ thuật viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}">${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-technician">Vui lòng chọn kỹ thuật viên phụ trách.</span>
                    </div>
                    <div class="col-md-6 field-row" style="display:flex; align-items:flex-end;">
                        <div class="form-check">
                            <input type="checkbox" class="form-check-input" id="isWarranty" name="isWarranty" checked>
                            <label class="form-check-label" for="isWarranty">Còn trong thời hạn bảo hành</label>
                        </div>
                    </div>

                    <div class="col-12 field-row">
                        <label>Mô tả sự cố <span class="req">*</span></label>
                        <textarea class="form-control" id="description" name="description" rows="4" placeholder="Mô tả chi tiết sự cố / yêu cầu của khách hàng"></textarea>
                        <span class="error-text" id="err-description">Vui lòng mô tả sự cố.</span>
                    </div>
                </div>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/ticket" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Tạo phiếu hỗ trợ</button>
                </div>
            </form>
        </div>
    </div>

        </div>
    </div>

    <!-- ===== Box chọn khách hàng ===== -->
    <div class="modal fade" id="customerPickerModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
            <div class="modal-content">
                <div class="modal-header">
                    <span class="modal-title">Chọn khách hàng</span>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button>
                </div>
                <div class="modal-body">
                    <input type="text" class="form-control picker-search" id="customerSearchInput" placeholder="Tìm theo tên, mã khách hàng hoặc người phụ trách...">
                    <div class="picker-list" id="customerPickerList">
                        <c:forEach var="customer" items="${customerList}">
                            <div class="picker-item"
                                 data-id="${customer.enterpriseId}"
                                 data-name="${fn:escapeXml(customer.enterpriseName)}"
                                 data-search="${fn:toLowerCase(fn:escapeXml(customer.enterpriseName))} ${fn:toLowerCase(fn:escapeXml(customer.enterpriseCode))} ${fn:toLowerCase(fn:escapeXml(customer.accountOwner.fullName))}">
                                <div class="picker-item-title">${fn:escapeXml(customer.enterpriseName)}</div>
                                <div class="picker-item-sub">
                                    ${fn:escapeXml(customer.enterpriseCode)}
                                    <c:if test="${customer.accountOwner != null}"> &middot; Phụ trách: ${fn:escapeXml(customer.accountOwner.fullName)}</c:if>
                                </div>
                            </div>
                        </c:forEach>
                        <div class="picker-empty" id="customerPickerEmpty" style="display:none;">Không tìm thấy khách hàng phù hợp.</div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- ===== Box chọn hợp đồng liên quan (nạp theo khách hàng đã chọn) ===== -->
    <div class="modal fade" id="contractPickerModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
            <div class="modal-content">
                <div class="modal-header">
                    <span class="modal-title">Chọn hợp đồng liên quan</span>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button>
                </div>
                <div class="modal-body">
                    <input type="text" class="form-control picker-search" id="contractSearchInput" placeholder="Tìm theo mã hoặc tên hợp đồng...">
                    <div class="picker-list" id="contractPickerList">
                        <div class="picker-empty" id="contractPickerEmpty">Chọn khách hàng trước.</div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var contextPath = '${pageContext.request.contextPath}';
        var customerHiddenInput = document.getElementById('customer');
        var customerPickerText = document.getElementById('customerPickerText');
        var contractHiddenInput = document.getElementById('contract');
        var contractPickerField = document.getElementById('contractPickerField');
        var contractPickerText = document.getElementById('contractPickerText');
        var contractPickerList = document.getElementById('contractPickerList');

        var customerPickerModal = new bootstrap.Modal(document.getElementById('customerPickerModal'));
        var contractPickerModal = new bootstrap.Modal(document.getElementById('contractPickerModal'));

        function openCustomerPicker() {
            document.getElementById('customerSearchInput').value = '';
            filterPickerList('customerPickerList', 'customerPickerEmpty', '');
            customerPickerModal.show();
        }

        function openContractPicker() {
            if (contractPickerField.classList.contains('disabled')) {
                return;
            }
            document.getElementById('contractSearchInput').value = '';
            filterPickerList('contractPickerList', 'contractPickerEmpty', '');
            contractPickerModal.show();
        }

        function filterPickerList(listId, emptyId, query) {
            var list = document.getElementById(listId);
            var items = list.querySelectorAll('.picker-item');
            var q = query.trim().toLowerCase();
            var visibleCount = 0;
            items.forEach(function (item) {
                var match = !q || item.dataset.search.indexOf(q) !== -1;
                item.style.display = match ? '' : 'none';
                if (match) { visibleCount++; }
            });
            document.getElementById(emptyId).style.display = (visibleCount === 0 && items.length > 0) ? 'block' : 'none';
        }

        document.getElementById('customerSearchInput').addEventListener('input', function () {
            filterPickerList('customerPickerList', 'customerPickerEmpty', this.value);
        });
        document.getElementById('contractSearchInput').addEventListener('input', function () {
            filterPickerList('contractPickerList', 'contractPickerEmpty', this.value);
        });

        document.getElementById('customerPickerList').addEventListener('click', function (e) {
            var item = e.target.closest('.picker-item');
            if (!item) { return; }
            selectCustomer(item.dataset.id, item.dataset.name);
            customerPickerModal.hide();
        });

        contractPickerList.addEventListener('click', function (e) {
            var item = e.target.closest('.picker-item');
            if (!item) { return; }
            selectContract(item.dataset.id, item.dataset.name);
            contractPickerModal.hide();
        });

        function selectCustomer(id, name) {
            customerHiddenInput.value = id;
            customerPickerText.textContent = name;
            customerPickerText.classList.remove('picker-placeholder');
            resetContractPicker();
            loadContracts(id);
        }

        function resetContractPicker() {
            contractHiddenInput.value = '';
            contractPickerText.textContent = '-- Chọn khách hàng trước --';
            contractPickerText.classList.add('picker-placeholder');
            contractPickerField.classList.add('disabled');
        }

        function selectContract(id, code) {
            contractHiddenInput.value = id;
            contractPickerText.textContent = code;
            contractPickerText.classList.remove('picker-placeholder');
        }

        function loadContracts(enterpriseId) {
            contractPickerField.classList.add('disabled');
            contractPickerText.textContent = 'Đang tải...';
            contractPickerText.classList.add('picker-placeholder');
            contractPickerList.innerHTML = '';
            fetch(contextPath + '/contract/byEnterprise?enterpriseId=' + encodeURIComponent(enterpriseId))
                .then(function (res) { return res.json(); })
                .then(function (contracts) {
                    contractPickerList.innerHTML = '';
                    contracts.forEach(function (c) {
                        var item = document.createElement('div');
                        item.className = 'picker-item';
                        item.dataset.id = c.id;
                        item.dataset.name = c.code;
                        item.dataset.search = (c.code + ' ' + (c.title || '')).toLowerCase();

                        var title = document.createElement('div');
                        title.className = 'picker-item-title';
                        title.textContent = c.code;
                        item.appendChild(title);

                        if (c.title) {
                            var sub = document.createElement('div');
                            sub.className = 'picker-item-sub';
                            sub.textContent = c.title;
                            item.appendChild(sub);
                        }
                        contractPickerList.appendChild(item);
                    });

                    var empty = document.createElement('div');
                    empty.className = 'picker-empty';
                    empty.id = 'contractPickerEmpty';
                    empty.style.display = contracts.length === 0 ? 'block' : 'none';
                    empty.textContent = 'Khách hàng này chưa có hợp đồng nào.';
                    contractPickerList.appendChild(empty);

                    contractPickerText.textContent = contracts.length === 0
                        ? '-- Không có hợp đồng liên quan --'
                        : '-- Chọn hợp đồng --';
                    contractPickerField.classList.remove('disabled');
                })
                .catch(function () {
                    contractPickerText.textContent = 'Không tải được danh sách hợp đồng';
                    contractPickerField.classList.add('disabled');
                });
        }

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            ['customer', 'ticketType', 'priority', 'receptionChannel', 'technician'].forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            var description = document.getElementById('description');
            if (!description.value.trim()) { document.getElementById('err-description').style.display = 'block'; valid = false; }

            return valid;
        }
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

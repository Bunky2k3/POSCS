<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ContractController#showDetail thiết lập trước khi forward tới trang này:
      - contract         : poscs.model.Contract (có sẵn .enterprise và .owner đã join, .status đã tính theo BR-17)
      - canDelete        : boolean -- true nếu hợp đồng đang ở trạng thái "Chưa hiệu lực" (BR-46)
      - contractProducts : List<poscs.model.ContractProduct> -- hạng mục sản phẩm/dịch vụ (chỉ đọc)

    Hạng mục sản phẩm/dịch vụ hiển thị sản phẩm/số lượng/đơn vị/ghi chú thật
    từ contractproducts -- KHÔNG có đơn giá/thành tiền/VAT vì bảng đó chưa có
    cột lưu giá, và chưa có form để gắn/gỡ sản phẩm (chỉ đọc). Điều khoản/ghi
    chú vẫn chưa hiển thị dữ liệu thật -- contracts chưa có cột lưu điều
    khoản, thuộc phạm vi khác.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết hợp đồng - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1080px; margin: 28px auto; padding: 0 20px 32px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 16px; }
        .back-link-top:hover { text-decoration: underline; }

        .detail-header { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; padding: 22px 26px; margin-bottom: 20px; }
        .detail-header .doc-icon {
            width: 56px; height: 56px; border-radius: 14px;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center; font-size: 1.4rem; flex-shrink: 0;
        }
        .detail-header .doc-info { display: flex; gap: 16px; align-items: center; }
        .detail-header h2 { font-weight: 700; color: #111827; font-size: 1.2rem; margin-bottom: 4px; }
        .contract-code { color: var(--primary); font-weight: 700; font-size: 0.82rem; }
        .type-badge { display: inline-block; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; background: #f3f4f6; color: #4b5563; margin-left: 8px; }

        .status-pill { display: inline-flex; align-items: center; gap: 5px; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; white-space: nowrap; margin-left: 8px; }
        .status-pill .dot { width: 6px; height: 6px; border-radius: 50%; }
        .status-soon { background: #fff4e0; color: var(--warning); }
        .status-soon .dot { background: var(--warning); }

        .header-actions { display: flex; gap: 10px; }
        .btn-edit-detail {
            background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; border: none;
            border-radius: 10px; padding: 9px 18px; font-weight: 600; font-size: 0.87rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-edit-detail:hover { color: #fff; background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-delete-detail {
            background: #fff; border: 1.5px solid #e5e7eb; color: #9ca3af;
            border-radius: 10px; padding: 9px 16px; font-weight: 600; font-size: 0.87rem;
            display: inline-flex; align-items: center; gap: 8px; cursor: not-allowed;
        }

        .info-card { padding: 22px 26px 26px; margin-bottom: 20px; }
        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 0 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row .view-value {
            font-size: 0.95rem; color: #111827; font-weight: 500; min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f9fafb; border-radius: 10px; padding: 8px 14px;
        }
        .field-row .view-value a { color: var(--primary); font-weight: 600; text-decoration: none; }
        .field-row .view-value a:hover { text-decoration: underline; }

        .item-table { width: 100%; }
        .item-table th { font-size: 0.72rem; text-transform: uppercase; color: #9ca3af; font-weight: 700; padding: 8px 10px; border-bottom: 1.5px solid #eef2f6; text-align: left; }
        .item-table td { padding: 10px 10px; font-size: 0.87rem; color: #111827; border-bottom: 1px solid #f3f4f6; }
        .item-table tr:last-child td { border-bottom: none; }
        .item-table td.num { text-align: right; }

        .totals-box { margin-top: 10px; margin-left: auto; max-width: 320px; }
        .totals-row { display: flex; justify-content: space-between; padding: 7px 0; font-size: 0.88rem; color: #374151; }
        .totals-row.grand { border-top: 1.5px solid #eef2f6; margin-top: 6px; padding-top: 12px; font-weight: 700; font-size: 1rem; color: var(--primary-dark); }

        @media (max-width: 768px) { .info-card, .detail-header { padding: 20px; } }

        .toast-msg {
            position: fixed; top: 24px; right: 24px; z-index: 999;
            background: #fff; border-left: 4px solid var(--success);
            border-radius: 12px; padding: 14px 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.15);
            display: flex; align-items: center; gap: 12px; font-size: 0.88rem; color: #111827; font-weight: 500;
            transform: translateX(130%); transition: transform 0.35s ease;
        }
        .toast-msg.show { transform: translateX(0); }
        .toast-msg.blocked { border-left-color: var(--danger); }
        .toast-msg.blocked i { color: var(--danger); font-size: 1.2rem; }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="contract" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <c:if test="${param.error == 'pdf_overflow'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Không xuất được PDF: hợp đồng có quá nhiều dòng sản phẩm/ghi chú dài để vừa 1 trang. Hãy rút gọn ghi chú hoặc liên hệ quản trị viên.</span>
        </div>
    </c:if>

    <div class="page-container">
        <a href="${pageContext.request.contextPath}/contract" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <!-- ===== Header ===== -->
        <div class="detail-header card-box">
            <div class="doc-info">
                <div class="doc-icon"><i class="fa-solid fa-file-contract"></i></div>
                <div>
                    <span class="contract-code">${fn:escapeXml(contract.contractCode)}</span>
                    <h2>
                        ${fn:escapeXml(contract.title)}
                        <span class="type-badge">${fn:escapeXml(contract.contractType)}</span>
                        <c:choose>
                            <c:when test="${contract.status == 'Đang hiệu lực'}"><span class="status-pill status-active"><span class="dot"></span>Đang hiệu lực</span></c:when>
                            <c:when test="${contract.status == 'Sắp hết hạn'}"><span class="status-pill status-soon"><span class="dot"></span>Sắp hết hạn</span></c:when>
                            <c:when test="${contract.status == 'Đã hết hạn'}"><span class="status-pill status-expired"><span class="dot"></span>Đã hết hạn</span></c:when>
                            <c:otherwise><span class="status-pill status-draft"><span class="dot"></span>Chưa hiệu lực</span></c:otherwise>
                        </c:choose>
                    </h2>
                    <div style="color:#6b7280; font-size:0.85rem;">Khách hàng:
                        <a href="${pageContext.request.contextPath}/customer?action=view&id=${contract.enterpriseId}" style="color:var(--primary); font-weight:600; text-decoration:none;">
                            <c:choose>
                                <c:when test="${contract.enterprise != null}">${fn:escapeXml(contract.enterprise.enterpriseName)}</c:when>
                                <c:otherwise>&mdash;</c:otherwise>
                            </c:choose>
                        </a>
                    </div>
                </div>
            </div>
            <div class="header-actions">
                <a href="${pageContext.request.contextPath}/contract?action=exportPdf&id=${contract.contractId}" class="btn-delete-detail" style="cursor:pointer; color:var(--primary); border-color:#e5e7eb;"><i class="fa-solid fa-file-pdf"></i> Xuất PDF</a>
                <a href="${pageContext.request.contextPath}/contract?action=edit&id=${contract.contractId}" class="btn-edit-detail"><i class="fa-solid fa-pen"></i> Sửa thông tin</a>
                <c:choose>
                    <c:when test="${canDelete}">
                        <button type="button" class="btn-delete-detail" style="cursor:pointer; color:var(--danger); border-color:var(--danger);" onclick="confirmDelete(${contract.contractId})"><i class="fa-solid fa-trash"></i> Xóa</button>
                    </c:when>
                    <c:otherwise>
                        <button class="btn-delete-detail" disabled title="Chỉ được xóa hợp đồng ở trạng thái chưa hiệu lực"><i class="fa-solid fa-trash"></i> Xóa</button>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>

        <!-- ===== Thông tin chung ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Thông tin chung</h5></div>
            <div class="row">
                <div class="col-md-6 field-row">
                    <label>Khách hàng</label>
                    <div class="view-value">
                        <a href="${pageContext.request.contextPath}/customer?action=view&id=${contract.enterpriseId}">
                            <c:choose>
                                <c:when test="${contract.enterprise != null}">${fn:escapeXml(contract.enterprise.enterpriseName)}</c:when>
                                <c:otherwise>&mdash;</c:otherwise>
                            </c:choose>
                        </a>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Người phụ trách</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${contract.owner != null}">${fn:escapeXml(contract.owner.fullName)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-4 field-row">
                    <label>Ngày ký</label>
                    <div class="view-value"><fmt:formatDate value="${contract.signingDate}" pattern="dd/MM/yyyy"/></div>
                </div>
                <div class="col-md-4 field-row">
                    <label>Ngày hiệu lực</label>
                    <div class="view-value"><fmt:formatDate value="${contract.effectiveDate}" pattern="dd/MM/yyyy"/></div>
                </div>
                <div class="col-md-4 field-row">
                    <label>Ngày kết thúc</label>
                    <div class="view-value"><fmt:formatDate value="${contract.endDate}" pattern="dd/MM/yyyy"/></div>
                </div>
            </div>
        </div>

        <!-- ===== Hạng mục sản phẩm / dịch vụ ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
            <c:choose>
                <c:when test="${empty contractProducts}">
                    <div class="empty-mini" style="color:#9ca3af; font-size:0.87rem;">Chưa có sản phẩm nào được gắn vào hợp đồng này.</div>
                </c:when>
                <c:otherwise>
                    <table class="item-table">
                        <thead>
                            <tr>
                                <th style="width:36px;">#</th>
                                <th>Sản phẩm</th>
                                <th class="num" style="width:90px;">Số lượng</th>
                                <th style="width:110px;">Đơn vị</th>
                                <th>Ghi chú</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="cp" items="${contractProducts}" varStatus="st">
                                <tr>
                                    <td>${st.index + 1}</td>
                                    <td>
                                        <a href="${pageContext.request.contextPath}/product?action=view&id=${cp.productId}" style="color:var(--primary); font-weight:600; text-decoration:none;">
                                            ${fn:escapeXml(cp.productName)}
                                        </a>
                                        <div style="color:#9ca3af; font-size:0.78rem;">${fn:escapeXml(cp.productCode)}</div>
                                    </td>
                                    <td class="num">${cp.quantity}</td>
                                    <td>${fn:escapeXml(cp.unit)}</td>
                                    <td>${not empty cp.notes ? fn:escapeXml(cp.notes) : '—'}</td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </c:otherwise>
            </c:choose>
        </div>

        <!-- ===== Ghi chú / điều khoản ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Điều khoản & ghi chú</h5></div>
            <div class="empty-mini" style="color:#9ca3af; font-size:0.87rem;">Chưa hỗ trợ trong phiên bản này.</div>
        </div>
    </div>

        </div>
    </div>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/contract" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" id="deleteFormId">
    </form>

    <script>
        function confirmDelete(contractId) {
            if (confirm('Bạn có chắc chắn muốn xóa hợp đồng này?')) {
                document.getElementById('deleteFormId').value = contractId;
                document.getElementById('deleteForm').submit();
            }
        }
    </script>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

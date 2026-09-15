<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ContractController#showDetail thiết lập trước khi forward tới trang này:
      - contract         : poscs.model.Contract (có sẵn .enterprise và .owner đã join, .status đã tính theo BR-17)
      - contractProducts : List<poscs.model.ContractProduct> -- hạng mục sản phẩm/dịch vụ
      - contractPayments : List<poscs.model.ContractPayment> -- kỳ thanh toán
      - paymentScheduled / paymentCollected / paymentOutstanding : ba con số công nợ
      - contractHistory  : List<poscs.model.ContractHistory> -- nhật ký thay đổi, mới nhất trước
      - createdEvent / signedEvent / closedEvent : ba mốc vòng đời, để dựng thanh tiến trình

    TRANG NÀY CHỈ ĐỌC. showDetail cố ý KHÔNG đặt một cờ can* nào, nên mọi khối
    <c:if> điều khiển nút bấm đều tắt. Thao tác làm thay đổi dữ liệu -- ký,
    thanh lý, chấm dứt, huỷ bản ghi, gắn/gỡ hàng hoá, lập kỳ thanh toán, ghi
    nhận đã thu -- nằm ở updatecontract.jsp. Đừng thêm nút thao tác vào đây.

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
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

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

        /* Nhãn trục TIẾN ĐỘ -- viền nét đứt để mắt phân biệt ngay với nhãn
           trục lịch đặc bên cạnh, vì hai nhãn nói hai chuyện khác nhau. */
        .progress-pill {
            display: inline-flex; align-items: center; padding: 3px 11px; border-radius: 20px;
            font-size: 0.72rem; font-weight: 600; white-space: nowrap; margin-left: 8px;
            border: 1.5px dashed transparent;
        }
        /* ===== Thanh tiến trình vòng đời ===== */
        .lifecycle-card { padding: 20px 26px 8px; margin-bottom: 20px; }
        .lifecycle { display: flex; align-items: flex-start; gap: 0; flex-wrap: nowrap; }
        .lc-step { flex: 1 1 0; display: flex; flex-direction: column; align-items: center; text-align: center; position: relative; min-width: 0; }
        /* Đường nối vẽ bằng ::before của chính bước kế tiếp, và nằm DƯỚI chấm
           tròn (z-index) -- vẽ đè lên thì đường cắt ngang qua giữa chấm. */
        .lc-step::before {
            content: ""; position: absolute; top: 17px; right: 50%; left: -50%; height: 2px;
            background: #e5e7eb; z-index: 0;
        }
        .lc-step:first-child::before { display: none; }
        .lc-step.done::before, .lc-step.current::before { background: var(--primary-light); }
        .lc-dot {
            width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
            background: #f3f4f6; color: #9ca3af; border: 2px solid #e5e7eb; font-size: 0.85rem;
            position: relative; z-index: 1; flex-shrink: 0;
        }
        .lc-step.done .lc-dot { background: var(--primary); color: #fff; border-color: var(--primary); }
        .lc-step.current .lc-dot { background: #fff; color: var(--primary); border-color: var(--primary); box-shadow: 0 0 0 4px rgba(15,158,219,.18); }
        .lc-step.cancelled .lc-dot { background: var(--warning); color: #fff; border-color: var(--warning); }
        .lc-title { margin-top: 8px; font-size: 0.85rem; font-weight: 700; color: #111827; }
        .lc-step:not(.done):not(.current):not(.cancelled) .lc-title { color: #9ca3af; font-weight: 600; }
        .lc-meta { font-size: 0.76rem; color: #6b7280; margin-top: 2px; line-height: 1.5; }
        .lc-meta .lc-none { color: #9ca3af; font-style: italic; }
        @media (max-width: 680px) {
            .lifecycle { flex-direction: column; gap: 14px; }
            .lc-step { flex-direction: row; align-items: center; gap: 12px; text-align: left; width: 100%; }
            .lc-step::before { display: none; }
            .lc-title { margin-top: 0; }
        }

        .lc-alert {
            display: flex; align-items: flex-start; gap: 10px; margin: 16px 0 12px;
            background: #fff8ec; border: 1px solid #f5d9a8; border-radius: 10px;
            padding: 11px 14px; font-size: 0.84rem; color: #8a5a00;
        }

        /* ===== Nhật ký dạng dòng thời gian ===== */
        .tl { list-style: none; margin: 0; padding: 0 0 0 26px; position: relative; }
        .tl::before { content: ""; position: absolute; left: 7px; top: 6px; bottom: 6px; width: 2px; background: #eef2f6; }
        .tl-item { position: relative; padding: 0 0 18px; }
        .tl-item:last-child { padding-bottom: 0; }
        .tl-item::before {
            content: ""; position: absolute; left: -23px; top: 5px; width: 10px; height: 10px;
            border-radius: 50%; background: #d1d5db; border: 2px solid #fff;
        }
        /* Mốc vòng đời nổi hơn hẳn dòng sửa đổi -- hai loại dòng này nằm chung
           một dòng thời gian, nhìn giống nhau thì mốc bị chìm giữa các lần sửa. */
        .tl-item.milestone::before { background: var(--primary); width: 12px; height: 12px; left: -24px; }
        .tl-head { font-size: 0.86rem; color: #111827; }
        .tl-item.milestone .tl-head { font-weight: 700; }
        .tl-when { font-size: 0.75rem; color: #9ca3af; margin-left: 6px; white-space: nowrap; }
        .tl-body { font-size: 0.84rem; color: #374151; margin-top: 2px; }
        .tl-note { font-size: 0.82rem; color: #6b7280; margin-top: 3px; }

        .progress-draft  { background: #f3f4f6; color: #4b5563; border-color: #d1d5db; }
        .progress-signed { background: #e8f3ff; color: var(--primary-dark); border-color: var(--primary-light); }
        .progress-frozen { background: #eef7ee; color: #2f6b34; border-color: #a8cfaa; }

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

        .btn-remove-item {
            width: 28px; height: 28px; border-radius: 8px; border: 1px solid #fecaca;
            background: #fff5f5; color: var(--danger); display: inline-flex; align-items: center; justify-content: center;
            font-size: 0.8rem; cursor: pointer;
        }
        .btn-remove-item:hover { background: var(--danger); color: #fff; }

        .add-product-form { margin-top: 18px; padding-top: 18px; border-top: 1.5px solid #eef2f6; }
        .add-product-form .form-label { font-size: 0.75rem; font-weight: 600; color: #6b7280; margin-bottom: 4px; }
        .add-product-form .form-control, .add-product-form .form-select { border-radius: 10px; font-size: 0.88rem; }
        .btn-add-item {
            width: 100%; height: 38px; border-radius: 10px; border: none;
            background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; cursor: pointer;
        }
        .btn-add-item:hover { opacity: 0.9; }

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
    <c:if test="${param.error == 'add_product_invalid'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Không thêm được sản phẩm: vui lòng chọn sản phẩm và nhập số lượng hợp lệ.</span>
        </div>
    </c:if>
    <c:if test="${param.error == 'add_product_failed'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Thêm sản phẩm thất bại -- vui lòng thử lại.</span>
        </div>
    </c:if>
    <c:if test="${param.error == 'remove_product_failed'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Không gỡ được sản phẩm này -- vui lòng thử lại.</span>
        </div>
    </c:if>
    <c:if test="${param.error == 'void_reason_required'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Phải có lý do thì mới huỷ được bản ghi hợp đồng.</span>
        </div>
    </c:if>
    <c:if test="${param.error == 'payment_failed'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Không thực hiện được trên kỳ thanh toán &mdash; kiểm lại số tiền, ngày đến hạn, hoặc hợp đồng đã đóng băng.</span>
        </div>
    </c:if>
    <c:if test="${param.error == 'missing_term'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Chưa ký được: hợp đồng còn thiếu ngày hiệu lực hoặc ngày kết thúc. Vào Sửa thông tin để điền thời hạn trước.</span>
        </div>
    </c:if>
    <c:if test="${param.error == 'progress_failed'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Không chuyển được trạng thái — có thể hợp đồng đã được người khác chuyển trước đó.</span>
        </div>
    </c:if>
    <c:if test="${param.error == 'void_failed'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Không huỷ được bản ghi này -- vui lòng thử lại.</span>
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
                    <%-- Số hợp đồng KHÔNG lặp lại ở đây: nó đã đứng đầu khối
                         "Thông tin chung" bên dưới, hiện cùng mã nội bộ. --%>
                    <h2>
                        ${fn:escapeXml(contract.title)}
                        <span class="type-badge">${fn:escapeXml(contract.contractType)}</span>
                        <c:choose>
                            <c:when test="${contract.status == 'Đang hiệu lực'}"><span class="status-pill status-active"><span class="dot"></span>Đang hiệu lực</span></c:when>
                            <c:when test="${contract.status == 'Sắp hết hạn'}"><span class="status-pill status-soon"><span class="dot"></span>Sắp hết hạn</span></c:when>
                            <c:when test="${contract.status == 'Đã hết hạn'}"><span class="status-pill status-expired"><span class="dot"></span>Đã hết hạn</span></c:when>
                            <c:otherwise><span class="status-pill status-draft"><span class="dot"></span>Chưa hiệu lực</span></c:otherwise>
                        </c:choose>
                        <%-- Nhãn thứ hai, cạnh nhãn lịch ở trên, CỐ Ý không gộp:
                             hai trục khác nhau và lệch nhau ở cả hai chiều. Một
                             hợp đồng "Đã hết hạn" theo lịch mà vẫn "Đã ký" theo
                             tiến độ nghĩa là hết hạn nhưng chưa thanh lý — đúng
                             việc còn tồn mà người dùng cần nhìn ra. Gộp một nhãn
                             là mất hẳn thông tin đó. --%>
                        <span class="progress-pill progress-${contract.draft ? 'draft' : (contract.frozen ? 'frozen' : 'signed')}"
                              title="Trạng thái tiến độ — do người đặt, khác với trạng thái theo lịch bên cạnh">
                            ${fn:escapeXml(contract.progressStatus)}
                        </span>
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
                <%-- Link tới bản PDF đã ký trên Drive, do người dùng tự dán vào
                     form. Controller đã bắt buộc scheme http/https trước khi lưu
                     (xem TextRules.isSafeHttpUrl) -- escape ở đây thôi không đủ,
                     vì nó không chặn được href="javascript:...". --%>
                <%-- Nhãn theo đúng loại link: drivePreviewUrl chỉ khác rỗng khi
                     link thật sự trỏ tới file Drive. Link nội bộ công ty cũng
                     lưu được (isSafeHttpUrl chỉ đòi http/https), nên nói "trên
                     Drive" cho mọi link là sai sự thật. --%>
                <%-- Dùng ${attachmentUrl} (đã được ContractController kiểm lại scheme)
                     chứ KHÔNG dùng thẳng ${contract.attachmentUrl}: fn:escapeXml
                     không vô hiệu hoá được "javascript:" trong href. --%>
                <c:if test="${not empty attachmentUrl}">
                    <a href="${fn:escapeXml(attachmentUrl)}" target="_blank" rel="noopener noreferrer"
                       class="btn-delete-detail" style="cursor:pointer; color:var(--primary); border-color:#e5e7eb;">
                        <c:choose>
                            <c:when test="${not empty drivePreviewUrl}"><i class="fa-brands fa-google-drive"></i> Mở PDF trên Drive</c:when>
                            <c:otherwise><i class="fa-solid fa-up-right-from-square"></i> Mở file PDF</c:otherwise>
                        </c:choose>
                    </a>
                </c:if>
                <c:if test="${attachmentUnsafe}">
                    <span class="btn-delete-detail" style="color:var(--danger); border-color:var(--danger); cursor:default;"
                          title="Link đính kèm của hợp đồng này không phải http/https nên đã bị chặn hiển thị. Vào Sửa thông tin để nhập lại.">
                        <i class="fa-solid fa-triangle-exclamation"></i> Link đính kèm không hợp lệ
                    </span>
                </c:if>
                <a href="${pageContext.request.contextPath}/contract?action=exportPdf&id=${contract.contractId}" class="btn-delete-detail" style="cursor:pointer; color:var(--primary); border-color:#e5e7eb;"><i class="fa-solid fa-file-pdf"></i> Xuất PDF</a>
                <%-- Link duy nhất dẫn tới nơi có thao tác. Mở được cả khi hợp
                     đồng đã đóng băng: lúc đó nội dung khoá lại nhưng vẫn phải
                     ghi nhận được tiền về (tiền bảo hành giữ lại thường về sau
                     thanh lý cả năm), mà nút đó nằm bên trang sửa. --%>
                <c:if test="${canManage}">
                    <a href="${pageContext.request.contextPath}/contract?action=edit&id=${contract.contractId}" class="btn-edit-detail"><i class="fa-solid fa-sliders"></i> Quản lý hợp đồng</a>
                </c:if>
            </div>
        </div>

        <%-- TRANG NÀY CHỈ ĐỌC.

             Mọi thao tác làm thay đổi dữ liệu -- ký, thanh lý, chấm dứt sớm,
             huỷ bản ghi, gắn/gỡ hàng hoá, lập kỳ thanh toán, ghi nhận đã thu --
             đã chuyển sang trang Sửa thông tin (updatecontract.jsp). Trang xem
             chỉ trình bày, không có nút nào gây thay đổi.

             Đừng thêm nút thao tác vào đây. Nếu cần một hành động mới thì nó
             thuộc về trang sửa. --%>
        <!-- ===== Vòng đời hợp đồng ===== -->
        <%-- Thanh này trả lời câu "hợp đồng đang ở đâu trong quy trình" ngay khi
             mở trang. Trước đây thông tin đó chỉ nằm trong một cái nhãn nhỏ ở
             tiêu đề, nên nhìn vào chỉ thấy vài ô dữ liệu rời chứ không thấy quy
             trình -- đúng chỗ khách hàng sẽ hỏi khi demo. --%>
        <div class="info-card card-box lifecycle-card">
            <div class="section-header"><h5>Vòng đời hợp đồng</h5></div>
            <div class="lifecycle">
                <div class="lc-step done">
                    <div class="lc-dot"><i class="fa-solid fa-pen-ruler"></i></div>
                    <div class="lc-title">Soạn thảo</div>
                    <div class="lc-meta">
                        <c:choose>
                            <c:when test="${createdEvent != null}">
                                <fmt:formatDate value="${createdEvent.changedAt}" pattern="dd/MM/yyyy"/><br>
                                ${fn:escapeXml(createdEvent.changedByUser.fullName)}
                            </c:when>
                            <c:otherwise><fmt:formatDate value="${contract.createdAt}" pattern="dd/MM/yyyy"/></c:otherwise>
                        </c:choose>
                    </div>
                </div>

                <div class="lc-step ${signedEvent != null or contract.signingDate != null ? 'done' : (contract.draft ? 'current' : '')}">
                    <div class="lc-dot"><i class="fa-solid fa-signature"></i></div>
                    <div class="lc-title">Ký hợp đồng</div>
                    <div class="lc-meta">
                        <c:choose>
                            <c:when test="${contract.signingDate != null}">
                                <fmt:formatDate value="${contract.signingDate}" pattern="dd/MM/yyyy"/>
                                <c:if test="${signedEvent != null}"><br>${fn:escapeXml(signedEvent.changedByUser.fullName)}</c:if>
                            </c:when>
                            <c:otherwise><span class="lc-none">chưa ký</span></c:otherwise>
                        </c:choose>
                    </div>
                </div>

                <div class="lc-step ${contract.frozen ? (contract.progressStatus == 'Chấm dứt sớm' ? 'cancelled' : 'done') : ''}">
                    <div class="lc-dot"><i class="fa-solid fa-file-circle-check"></i></div>
                    <div class="lc-title">
                        <c:choose>
                            <c:when test="${contract.progressStatus == 'Chấm dứt sớm'}">Chấm dứt sớm</c:when>
                            <c:otherwise>Thanh lý</c:otherwise>
                        </c:choose>
                    </div>
                    <div class="lc-meta">
                        <c:choose>
                            <c:when test="${closedEvent != null}">
                                <fmt:formatDate value="${closedEvent.changedAt}" pattern="dd/MM/yyyy"/><br>
                                ${fn:escapeXml(closedEvent.changedByUser.fullName)}
                                <c:if test="${not empty closedEvent.note}">
                                    <br><span title="${fn:escapeXml(closedEvent.note)}">${fn:escapeXml(closedEvent.note)}</span>
                                </c:if>
                            </c:when>
                            <c:otherwise><span class="lc-none">chưa thanh lý</span></c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </div>

            <c:if test="${overdueUnclosed}">
                <div class="lc-alert">
                    <i class="fa-solid fa-triangle-exclamation" style="margin-top:2px;"></i>
                    <span>
                        Hợp đồng đã <strong>hết hạn</strong> theo lịch (ngày kết thúc
                        <fmt:formatDate value="${contract.endDate}" pattern="dd/MM/yyyy"/>)
                        nhưng <strong>chưa được thanh lý</strong>. Theo quy trình, hợp đồng chỉ coi là
                        xong khi đã thanh lý.
                    </span>
                </div>
            </c:if>
            <c:if test="${contract.frozen}">
                <div class="lc-alert" style="background:#f3f7f3; border-color:#cfe3d0; color:#2f6b34;">
                    <i class="fa-solid fa-lock" style="margin-top:2px;"></i>
                    <span>Hợp đồng đã đóng băng — nội dung không sửa được nữa, kể cả bởi quản trị viên.
                        Phát sinh sau thời điểm này phải lập hợp đồng mới.</span>
                </div>
            </c:if>
        </div>

        <!-- ===== Thông tin chung ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Thông tin chung</h5></div>
            <div class="row">
                <%-- Một mã duy nhất, do người dùng nhập, chính là số ghi trên bản
                     giấy (V28). Trước đó có thêm một "mã hệ thống" HD-xxxx nữa --
                     hai cột cho một khái niệm. --%>
                <div class="col-md-6 field-row">
                    <label>Mã hợp đồng</label>
                    <div class="view-value"><strong>${fn:escapeXml(contract.contractCode)}</strong></div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Loại hợp đồng</label>
                    <div class="view-value">${fn:escapeXml(contract.contractType)}</div>
                </div>

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

                <div class="col-md-6 field-row">
                    <label>Chiều</label>
                    <%-- Cặp đôi CHÉO: hợp đồng 'Bán' ký với đối tác giữ vai
                         'Khách mua'. Ghi rõ nghĩa ra để người đọc không phải
                         nhớ quy ước đó (xem ghi chú đầu V21). --%>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${contract.direction == 'Mua'}">Hợp đồng mua &mdash; mình mua vào</c:when>
                            <c:otherwise>Hợp đồng bán &mdash; mình bán ra</c:otherwise>
                        </c:choose>
                    </div>
                </div>

                <%-- BA MỐC THỜI GIAN ĐÃ BỎ KHỎI ĐÂY theo yêu cầu (2026-09-15).
                     Dữ liệu vẫn còn nguyên trong CSDL và vẫn dùng để tính trạng
                     thái theo lịch (nhãn ở đầu trang) -- chỉ là không bày ra
                     trong khối thông tin chung nữa.

                     Ngày ký vẫn hiện ở thanh tiến trình vòng đời phía trên, vì
                     ở đó nó là MỐC của quy trình chứ không phải một ô dữ liệu.
                     Cần bỏ cả chỗ đó thì nói. --%>
            </div>
        </div>

        <!-- ===== Ký kết & giá trị ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Ký kết &amp; giá trị</h5></div>
            <div class="row">
                <div class="col-md-6 field-row">
                    <label>Người ký bên mình</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${not empty contract.signerName}">
                                ${fn:escapeXml(contract.signerName)}<c:if test="${not empty contract.signerPosition}"><span style="color:#6b7280;">&nbsp;&middot;&nbsp;${fn:escapeXml(contract.signerPosition)}</span></c:if>
                            </c:when>
                            <c:otherwise><span style="color:#9ca3af;">chưa điền</span></c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Người ký bên đối tác</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${not empty contract.counterpartySignerName}">
                                ${fn:escapeXml(contract.counterpartySignerName)}<c:if test="${not empty contract.counterpartySignerPosition}"><span style="color:#6b7280;">&nbsp;&middot;&nbsp;${fn:escapeXml(contract.counterpartySignerPosition)}</span></c:if>
                            </c:when>
                            <c:otherwise><span style="color:#9ca3af;">chưa điền</span></c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <%-- Căn cứ uỷ quyền chỉ hiện khi CÓ: chuyện uỷ quyền hiếm, để ô
                     rỗng ở mọi hợp đồng thì nó thành nhiễu. --%>
                <c:if test="${not empty contract.authorizationRef}">
                    <div class="col-md-6 field-row">
                        <label>Căn cứ uỷ quyền</label>
                        <div class="view-value">${fn:escapeXml(contract.authorizationRef)}</div>
                    </div>
                </c:if>
                <div class="col-md-6 field-row">
                    <label>Nơi ký</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${not empty contract.signingPlace}">${fn:escapeXml(contract.signingPlace)}</c:when>
                            <c:otherwise><span style="color:#9ca3af;">chưa điền</span></c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Giá trị hợp đồng</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${contract.contractValue != null}">
                                <%-- fmt:formatNumber gom nhóm theo locale của request, mà
                                     request không mang Accept-Language thì ra số trần
                                     "1500000000.00". Định dạng ở client bằng vi-VN, giống
                                     cách dashboard đang làm -- ở đó hiện dạng rút gọn
                                     ("1,5 tỷ đ"), còn đây cần con số chính xác. --%>
                                <strong class="money-vnd" data-vnd="${contract.contractValue}">&mdash;</strong>
                            </c:when>
                            <c:otherwise><span style="color:#9ca3af;">chưa chốt</span></c:otherwise>
                        </c:choose>
                    </div>
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
                                <th style="width:40px;"></th>
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
                                    <td>
                                        <%-- Nút này TRƯỚC ĐÂY không hề kiểm quyền: vai chỉ-xem
                                             (Kỹ thuật, CSKH) mở trang cũng thấy nút gỡ, bấm vào
                                             mới nhận 403. Giờ dùng chung đúng một điều kiện với
                                             form thêm bên dưới. --%>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </c:otherwise>
            </c:choose>

            <%-- Hàng hoá là NỘI DUNG hợp đồng, không phải dữ liệu quản trị nội
                 bộ. Ký xong thì nó là chứng cứ, đổi phải đi qua phụ lục -- nên
                 chỉ sửa được khi hợp đồng còn là bản Nháp. Chặn thật nằm ở
                 ContractDAO.insertProducts/deleteProductLine. --%>
        </div>

        <!-- ===== Kỳ thanh toán ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Kỳ thanh toán</h5></div>

            <%-- Ba con số này là lý do bảng bên dưới tồn tại: giá trị hợp đồng
                 là ĐIỀU KHOẢN, tổng các kỳ là KẾ HOẠCH thu, đã thu là THỰC TẾ.
                 Chỗ lệch giữa chúng chính là công nợ. --%>
            <div class="row g-2" style="margin-bottom:14px;">
                <div class="col-md-4">
                    <div style="border:1px solid #eef2f6; border-radius:10px; padding:10px 14px; background:#f9fafb;">
                        <div style="font-size:0.72rem; color:#6b7280; text-transform:uppercase; letter-spacing:.3px;">Tổng các kỳ đã lập</div>
                        <div class="money-vnd" data-vnd="${paymentScheduled}" style="font-weight:700; color:#111827;">&mdash;</div>
                    </div>
                </div>
                <div class="col-md-4">
                    <div style="border:1px solid #cfe3d0; border-radius:10px; padding:10px 14px; background:#f3f7f3;">
                        <div style="font-size:0.72rem; color:#2f6b34; text-transform:uppercase; letter-spacing:.3px;">Đã thu</div>
                        <div class="money-vnd" data-vnd="${paymentCollected}" style="font-weight:700; color:#2f6b34;">&mdash;</div>
                    </div>
                </div>
                <div class="col-md-4">
                    <div style="border:1px solid #f5d9a8; border-radius:10px; padding:10px 14px; background:#fff8ec;">
                        <div style="font-size:0.72rem; color:#8a5a00; text-transform:uppercase; letter-spacing:.3px;">Còn phải thu</div>
                        <div class="money-vnd" data-vnd="${paymentOutstanding}" style="font-weight:700; color:#8a5a00;">&mdash;</div>
                    </div>
                </div>
            </div>

            <c:if test="${paymentMismatch}">
                <div class="lc-alert">
                    <i class="fa-solid fa-triangle-exclamation" style="margin-top:2px;"></i>
                    <span>Tổng các kỳ đã lập <strong>không khớp</strong> giá trị hợp đồng
                        (<span class="money-vnd" data-vnd="${contract.contractValue}">&mdash;</span>).
                        Có thể còn kỳ chưa nhập &mdash; kiểm lại trước khi đối chiếu công nợ.</span>
                </div>
            </c:if>

            <c:choose>
                <c:when test="${empty contractPayments}">
                    <div class="empty-mini" style="color:#9ca3af; font-size:0.87rem;">Chưa lập kỳ thanh toán nào.</div>
                </c:when>
                <c:otherwise>
                    <table class="item-table">
                        <thead>
                            <tr>
                                <th style="width:40px;">#</th>
                                <th>Số tiền</th>
                                <th style="width:130px;">Đến hạn</th>
                                <th style="width:160px;">Tình trạng</th>
                                <th style="width:160px;"></th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="p" items="${contractPayments}" varStatus="row">
                                <tr>
                                    <td>${row.index + 1}</td>
                                    <td><strong class="money-vnd" data-vnd="${p.invoiceAmount}">&mdash;</strong></td>
                                    <td><fmt:formatDate value="${p.dueDate}" pattern="dd/MM/yyyy"/></td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${p.paidDate != null}">
                                                <span style="color:#2f6b34; font-weight:600;">
                                                    <i class="fa-solid fa-circle-check"></i>
                                                    Đã thu <fmt:formatDate value="${p.paidDate}" pattern="dd/MM/yyyy"/>
                                                </span>
                                            </c:when>
                                            <c:otherwise><span style="color:#9ca3af;">Chưa thu</span></c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td style="text-align:right;">
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </c:otherwise>
            </c:choose>

        </div>

        <%-- Đã bỏ thẻ "Điều khoản & ghi chú -- Chưa hỗ trợ trong phiên bản
             này": một thẻ rỗng chỉ để báo là chưa làm thì không nói được gì với
             người dùng, và khi demo cho khách nó là thứ đập vào mắt đầu tiên.
             contracts vẫn chưa có cột lưu điều khoản; khi nào có thì dựng lại
             thẻ này với dữ liệu thật. --%>

        <!-- ===== Nhật ký thay đổi ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Nhật ký thay đổi</h5></div>
            <c:choose>
                <c:when test="${empty contractHistory}">
                    <div class="empty-mini" style="color:#9ca3af; font-size:0.87rem;">Chưa có thay đổi nào được ghi lại.</div>
                </c:when>
                <c:otherwise>
                    <%-- Dòng thời gian chứ không phải bảng: nhật ký này kể một
                         câu chuyện theo thứ tự, mà bảng thì bắt người đọc tự
                         ghép lại từ bốn cột. Mốc vòng đời (có from/to) được làm
                         nổi hơn dòng sửa đổi -- để giống nhau thì mốc bị chìm
                         giữa các lần sửa vặt. --%>
                    <ul class="tl">
                        <c:forEach var="h" items="${contractHistory}">
                            <li class="tl-item ${h.statusChange ? 'milestone' : ''}">
                                <div class="tl-head">
                                    ${fn:escapeXml(h.eventType)}
                                    <span class="tl-when"><fmt:formatDate value="${h.changedAt}" pattern="dd/MM/yyyy HH:mm"/>
                                        &middot; ${fn:escapeXml(h.changedByUser.fullName)}</span>
                                </div>
                                <div class="tl-body">${fn:escapeXml(h.detail)}</div>
                                <%-- note là chữ người dùng gõ, detail là chữ hệ thống sinh.
                                     Tách hẳn dòng để không ai đọc nhầm lý do của người
                                     thành ghi nhận của máy. --%>
                                <c:if test="${not empty h.note}">
                                    <div class="tl-note"><i class="fa-solid fa-quote-left" style="font-size:.7rem;"></i>
                                        ${fn:escapeXml(h.note)}</div>
                                </c:if>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </div>

        <%-- ===== Bản PDF đã ký, xem ngay tại đây =====
             Đặt CUỐI trang: khung nhúng cao 640px, để ở giữa thì nó đẩy mọi khối
             bên dưới ra khỏi tầm mắt và người đọc phải cuộn qua cả trang PDF
             mới tới được nhật ký.

             Chỉ hiện khi link nhận ra được là file Drive (controller dựng sẵn
             drivePreviewUrl). Link tới nơi khác vẫn còn nút "Mở PDF trên Drive"
             ở đầu trang -- nhúng chúng dễ ra khung trắng vì site đó tự chặn. --%>
        <c:if test="${not empty drivePreviewUrl}">
            <div class="info-card card-box">
                <div class="section-header">
                    <h5>Bản PDF đã ký</h5>
                    <a href="${fn:escapeXml(attachmentUrl)}" target="_blank" rel="noopener noreferrer"
                       style="font-size:0.85rem; color:var(--primary); font-weight:600; text-decoration:none;">
                        Mở trên Drive <i class="fa-solid fa-arrow-up-right-from-square"></i>
                    </a>
                </div>
                <iframe src="${fn:escapeXml(drivePreviewUrl)}"
                        style="width:100%; height:640px; border:1px solid #eef2f6; border-radius:12px;"
                        allow="autoplay" title="Bản PDF hợp đồng"></iframe>
                <p style="font-size:0.78rem; color:#9ca3af; margin:10px 0 0;">
                    Không thấy nội dung? File trên Drive có thể đang giới hạn quyền xem --
                    hãy mở bằng link ở trên để đăng nhập Google và kiểm tra quyền truy cập.
                </p>
            </div>
        </c:if>
    </div>

        </div>

    </div>

    <script>
        // Số tiền render ở client để dùng đúng cách gom nhóm của tiếng Việt
        // (dấu chấm), thứ mà fmt:formatNumber không đảm bảo khi request không
        // mang Accept-Language.
        document.querySelectorAll('.money-vnd').forEach(function (el) {
            var n = Number(el.dataset.vnd);
            el.textContent = isNaN(n) ? '—' : n.toLocaleString('vi-VN', { maximumFractionDigits: 0 }) + ' ₫';
        });

    </script>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

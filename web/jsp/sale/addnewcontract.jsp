<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ContractController#showCreateForm thiết lập trước khi forward tới trang này:
      - customerList : List<poscs.model.Enterprise> (toàn bộ khách hàng, để đổ dropdown "Khách hàng")
      - userList      : List<poscs.model.User>       (nhân viên vai Sales, để đổ dropdown "Người phụ trách")

    Form này chỉ tạo bản ghi trong bảng contracts. Hạng mục sản phẩm/dịch vụ
    (contractproducts) được gắn ở trang chi tiết hợp đồng sau khi tạo xong,
    qua action addProduct -- không nhập tại đây.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${empty parentContract ? 'Tạo hợp đồng' : 'Lập phụ lục hợp đồng'} - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 980px; margin: 28px auto; padding: 0 20px 32px; }
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
        <c:set var="activeNav" value="contract" scope="request"/>
        <c:set var="activeContractKind" value="${kind}" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <%-- MỘT trang, hai chế độ. parentContract khác rỗng = đang lập PHỤ LỤC
             cho hợp đồng đó; rỗng = tạo hợp đồng mới.

             Dùng lại trang này chứ không dựng trang thứ hai vì phụ lục là một
             hợp đồng đầy đủ -- cũng mã trên giấy, cũng thời hạn, cũng hàng hoá,
             cũng phải ký. Trang riêng sẽ là bản sao của trang này, rồi hai bản
             sao lệch nhau dần. --%>
        <c:choose>
            <c:when test="${not empty parentContract}">
                <%-- Quay về ĐÚNG danh sách vừa đi ra. Thiếu tham số kind thì controller
                     coi như chiều mặc định (Hợp đồng bán), nên bấm quay lại từ một
                     bản ghi chiều kia là rơi sang danh sách khác hẳn. Dùng lại đúng biến đã
                     tính cho sidebar ở trên, để mục đang sáng và danh sách quay về luôn khớp. --%>
                <a href="${pageContext.request.contextPath}/contract?action=view&id=${parentContract.contractId}" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại hợp đồng gốc</a>
                <div class="page-header-row">
                    <h2>Lập phụ lục hợp đồng</h2>
                    <p>Sửa đổi cho hợp đồng <strong style="color:var(--primary-dark)">${fn:escapeXml(parentContract.contractCode)}</strong>
                       — ${fn:escapeXml(parentContract.title)}</p>
                </div>
                <div class="card-box" style="border-left:4px solid #b45309;">
                    <div style="display:flex; align-items:flex-start; gap:10px; font-size:0.88rem; color:#92400e;">
                        <i class="fa-solid fa-circle-info" style="margin-top:3px;"></i>
                        <span>Phụ lục ra đời ở trạng thái <strong>Nháp</strong> và phải được <strong>ký</strong>
                            như một hợp đồng thường — đó là điểm của nó: sửa đổi trên hợp đồng đã ký phải có
                            chữ ký, không phải một lần bấm Lưu. Khách hàng và chiều mua/bán lấy theo hợp đồng
                            gốc, không chọn lại được.</span>
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <a href="${pageContext.request.contextPath}/contract?kind=${activeContractKind}" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>
                <div class="page-header-row">
                    <h2>Tạo hợp đồng</h2>
                    <p>Khởi tạo hợp đồng mới với khách hàng doanh nghiệp</p>
                </div>
            </c:otherwise>
        </c:choose>

        <div class="card-box">
            <c:if test="${not empty param.error}">
                <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                    <c:choose>
                        <c:when test="${param.error == 'invalid'}">Thông tin hợp đồng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.</c:when>
                        <c:when test="${param.error == 'duplicate_code'}">Mã hợp đồng này đã có hợp đồng khác dùng. Kiểm lại số trên bản giấy hoặc nhập mã khác.</c:when>
                        <c:when test="${param.error == 'create_failed'}">Không lưu được hợp đồng. Vui lòng thử lại.</c:when>
                        <c:when test="${param.error == 'amendment_not_allowed'}">Hợp đồng gốc không nhận được phụ lục: nó phải đã ký và chưa thanh lý, và bản thân nó không được là phụ lục.</c:when>
                        <c:when test="${param.error == 'value_negative'}">Khoản giảm trừ lớn hơn giá trị còn lại của hợp đồng gốc — giá trị hợp đồng sẽ âm. Ô này nhập phần CHÊNH LỆCH, không phải tổng giá trị mới.</c:when>
                        <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                    </c:choose>
                </div>
            </c:if>

            <form id="createContractForm" action="${pageContext.request.contextPath}/contract" method="POST" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="${empty parentContract ? 'create' : 'createAmendment'}">
                <%-- Chiều hợp đồng đi theo mục con người dùng đang đứng.
                     Server đọc chính tham số này chứ không đọc một ô nào
                     người dùng sửa được -- xem buildContractFromRequest.
                     Với phụ lục thì cả chiều lẫn khách hàng lấy từ hợp đồng
                     cha, kind chỉ còn dùng để quay về đúng mục con. --%>
                <input type="hidden" name="kind" value="${kind}">
                <c:if test="${not empty parentContract}">
                    <input type="hidden" name="parentId" value="${parentContract.contractId}">
                </c:if>

                <div class="section-header"><h5>Thông tin chung</h5></div>
                <div class="row">
                    <%-- Mã hợp đồng do NGƯỜI DÙNG nhập, chính là số ghi trên bản
                         giấy. Hệ thống KHÔNG sinh mã nữa (V28) -- trước đó nó tự
                         sinh HD-xxxx và có thêm một ô "Số hợp đồng" riêng, hai cột
                         cho một khái niệm. --%>
                    <div class="col-12 field-row">
                        <label>Mã hợp đồng <span class="req">*</span></label>
                        <input type="text" class="form-control" id="contractCode" name="contractCode"
                               maxlength="50"
                               placeholder="${empty parentContract ? 'VD: 01/2026/HĐKT-POSTEF' : fn:escapeXml(parentContract.contractCode).concat('/PL01')}">
                        <span class="error-text" id="err-contractCode">Mã hợp đồng không được để trống.</span>
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            <c:choose>
                                <c:when test="${not empty parentContract}">
                                    Nhập đúng số ghi trên bản phụ lục giấy. Phụ lục dùng chung không gian mã
                                    với hợp đồng, nên mã này cũng phải là duy nhất.
                                </c:when>
                                <c:otherwise>
                                    Nhập đúng số ghi trên bản hợp đồng giấy. Mỗi hợp đồng một mã, không trùng nhau.
                                </c:otherwise>
                            </c:choose>
                        </span>
                    </div>

                    <div class="col-12 field-row">
                        <label>Tiêu đề hợp đồng <span class="req">*</span></label>
                        <input type="text" class="form-control" id="title" name="title" placeholder="VD: Cung cấp cáp quang OM4 đợt 2">
                        <span class="error-text" id="err-title">Tiêu đề không được để trống.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Khách hàng <span class="req">*</span></label>
                        <c:choose>
                            <%-- Phụ lục ký với đúng đối tác của hợp đồng gốc, nên đây
                                 chỉ còn là thông tin hiển thị. Không gửi lên (không có
                                 name): ContractDAO.insert đọc thẳng từ hợp đồng cha,
                                 nên một POST nặn tay cũng không đổi được đối tác. --%>
                            <c:when test="${not empty parentContract}">
                                <input type="text" class="form-control" value="${fn:escapeXml(parentContract.enterprise.enterpriseName)}" disabled>
                                <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                                    Theo hợp đồng gốc, không đổi được.
                                </span>
                            </c:when>
                            <c:otherwise>
                                <select class="form-select" id="customer" name="enterpriseId">
                                    <option value="">-- Chọn khách hàng --</option>
                                    <c:forEach var="customer" items="${customerList}">
                                        <option value="${customer.enterpriseId}">${fn:escapeXml(customer.enterpriseName)}</option>
                                    </c:forEach>
                                </select>
                                <span class="error-text" id="err-customer">Vui lòng chọn khách hàng.</span>
                            </c:otherwise>
                        </c:choose>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Người phụ trách <span class="req">*</span></label>
                        <%-- Ở chế độ phụ lục, gợi ý sẵn người phụ trách hợp đồng gốc
                             nhưng VẪN cho đổi: nhân sự có thể đã khác từ lúc ký. --%>
                        <select class="form-select" id="owner" name="ownerId">
                            <option value="">-- Chọn nhân viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}"
                                        ${not empty parentContract and staff.userId == parentContract.ownerId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-owner">Vui lòng chọn người phụ trách.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Loại hợp đồng <span class="req">*</span></label>
                        <select class="form-select" id="contractType" name="contractType">
                            <option value="">-- Chọn loại hợp đồng --</option>
                            <%-- Loại hợp đồng theo CHIỀU: hợp đồng mua không dùng chung bộ
                                 chữ với hợp đồng bán. Xem ContractController. --%>
                            <c:forEach var="ct" items="${contractTypeOptions}">
                                <option value="${fn:escapeXml(ct)}">${fn:escapeXml(ct)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-contractType">Vui lòng chọn loại hợp đồng.</span>
                    </div>

                    <%-- KHÔNG có ô ngày nào ở form tạo. Cả ba mốc -- ngày ký,
                         ngày hiệu lực, ngày kết thúc -- đều là KẾT QUẢ, không
                         phải thứ biết trước lúc mở hồ sơ:

                           * hiệu lực và kết thúc là kết quả đàm phán, điền ở
                             form sửa khi hai bên đã thống nhất;
                           * ngày ký do hệ thống đóng dấu lúc bấm nút Ký.

                         Bắt nhập ở đây nghĩa là ép người dùng điền số tạm rồi
                         sửa lại sau, và trong quãng đó CSDL mang một thời hạn
                         chưa ai đồng ý. Không ký được khi còn thiếu thời hạn
                         (ContractDAO.changeProgressStatus), nên bỏ ở đây không
                         mở ra đường nào cho dữ liệu thiếu đi tiếp. --%>
                    <div class="col-12" style="margin-top:6px; margin-bottom:10px;">
                        <div style="font-size:0.8rem; font-weight:700; color:var(--primary-dark); text-transform:uppercase; letter-spacing:.3px;">
                            Thông tin ký kết
                        </div>
                        <div style="font-size:0.78rem; color:#9ca3af; margin-top:2px;">
                            Không bắt buộc — điền khi đã chốt được người ký. Khác đại diện pháp luật
                            của công ty khách: có uỷ quyền thì hai người này khác nhau.
                        </div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Người ký bên mình</label>
                        <input type="text" class="form-control" id="signerName" name="signerName" maxlength="100">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Chức vụ</label>
                        <input type="text" class="form-control" id="signerPosition" name="signerPosition"
                               maxlength="100" placeholder="VD: Giám đốc chi nhánh">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Người ký bên đối tác</label>
                        <input type="text" class="form-control" id="counterpartySignerName" name="counterpartySignerName" maxlength="100">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Chức vụ</label>
                        <input type="text" class="form-control" id="counterpartySignerPosition" name="counterpartySignerPosition" maxlength="100">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Căn cứ uỷ quyền</label>
                        <input type="text" class="form-control" id="authorizationRef" name="authorizationRef"
                               maxlength="255" placeholder="VD: GUQ số 05/2026 ngày 10/01/2026">
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            Chỉ điền khi người ký không phải đại diện pháp luật.
                        </span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Nơi ký</label>
                        <input type="text" class="form-control" id="signingPlace" name="signingPlace"
                               maxlength="255" placeholder="VD: Hà Nội">
                    </div>
                    <%-- HAI CHẾ ĐỘ, như cả trang này. Với hợp đồng gốc, ô tiền là
                         TỔNG giá trị theo điều khoản. Với phụ lục, nó là phần CHÊNH
                         LỆCH cộng vào hợp đồng gốc -- phụ lục không mang một giá trị
                         độc lập, nó sửa con số của hợp đồng kia.

                         Dấu nhập bằng ô chọn chứ không bắt gõ dấu trừ: gõ dấu thì dễ
                         nhầm, và một ô tiền âm in ra màn hình không nói được nó là
                         giảm trừ hay lỗi nhập liệu. Controller ghép dấu vào (xem
                         ContractController.parseContractValue). --%>
                    <c:if test="${not empty parentContract}">
                        <div class="col-md-6 field-row">
                            <label>Điều chỉnh giá trị</label>
                            <select class="form-control" id="valueAdjustment" name="valueAdjustment">
                                <option value="increase">Bổ sung (cộng vào giá trị hợp đồng)</option>
                                <option value="decrease">Giảm trừ (trừ khỏi giá trị hợp đồng)</option>
                                <option value="none" selected>Không đổi giá trị</option>
                            </select>
                            <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                                Phụ lục chỉ gia hạn hoặc sửa hàng hoá thì chọn <strong>Không đổi giá trị</strong>.
                            </span>
                        </div>
                    </c:if>
                    <div class="col-md-6 field-row">
                        <label>${empty parentContract ? 'Giá trị hợp đồng (VNĐ)' : 'Số tiền điều chỉnh (VNĐ)'}</label>
                        <input type="text" class="form-control" id="contractValue" name="contractValue"
                               inputmode="numeric" placeholder="VD: ${empty parentContract ? '1.500.000.000' : '250.000.000'}">
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            <c:choose>
                                <c:when test="${not empty parentContract}">
                                    Nhập số dương, phần CHÊNH LỆCH so với hợp đồng gốc — không phải tổng giá trị mới.
                                    Giá trị hợp đồng gốc hiện là
                                    <strong class="money-vnd" data-vnd="${parentContract.currentValue}">chưa chốt</strong>,
                                    và chỉ đổi khi phụ lục này được <strong>ký</strong>.
                                </c:when>
                                <c:otherwise>
                                    Giá trị theo điều khoản. Số tiền thực thu ghi ở mục Kỳ thanh toán — hai
                                    con số lệch nhau chính là công nợ.
                                </c:otherwise>
                            </c:choose>
                        </span>
                    </div>
                    <div class="col-12 field-row">
                        <div style="background:#f8fafc; border:1px solid #eef2f6; border-radius:10px; padding:10px 14px; font-size:0.82rem; color:#6b7280;">
                            <i class="fa-solid fa-circle-info" style="color:var(--primary);"></i>
                            Lưu xong, hợp đồng ở trạng thái <strong>Nháp</strong> — còn sửa và xoá thoải mái.
                            <strong>Thời hạn</strong> (ngày hiệu lực, ngày kết thúc) điền ở màn hình sửa khi đã
                            chốt với khách; <strong>ngày ký</strong> được ghi vào lúc bấm Ký hợp đồng.
                        </div>
                    </div>

                    <%-- Ô "link file PDF" cũ đã bỏ khỏi form TẠO (V34). Giấy tờ giờ
                         là một danh sách nhiều dòng có loại/ghi chú/người thêm, mà
                         dòng nào cũng cần contract_id -- thứ chỉ có SAU khi lưu.
                         Treo giấy tờ ở trang quản lý, tab "Tài liệu". --%>
                </div>

                <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
                <p style="font-size:0.86rem; color:#6b7280; margin:0 0 4px;">
                    Sau khi tạo xong hợp đồng, mở trang chi tiết để thêm sản phẩm/dịch vụ vào hợp đồng.
                </p>


                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/contract?kind=${activeContractKind}" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Tạo hợp đồng</button>
                </div>
            </form>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Số tiền định dạng ở client bằng vi-VN, giống viewcontractdetail.jsp:
        // fmt:formatNumber gom nhóm theo locale của request, mà request không
        // mang Accept-Language thì nó in ra số trần "1500000000.00".
        document.querySelectorAll('.money-vnd').forEach(function (el) {
            var n = Number(el.dataset.vnd);
            if (el.dataset.vnd === '' || isNaN(n)) { return; }
            el.textContent = n.toLocaleString('vi-VN', { maximumFractionDigits: 0 }) + ' ₫';
        });
        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            var title = document.getElementById('title');
            if (!title.value.trim()) { document.getElementById('err-title').style.display = 'block'; valid = false; }

            var contractCode = document.getElementById('contractCode');
            if (!contractCode.value.trim()) {
                document.getElementById('err-contractCode').style.display = 'block';
                valid = false;
            }

            // Ô "Khách hàng" KHÔNG tồn tại ở chế độ phụ lục (đối tác lấy theo
            // hợp đồng gốc), nên phải bỏ qua khi vắng mặt thay vì nổ ở
            // el.value và chặn luôn việc gửi form.
            ['customer', 'contractType', 'owner'].forEach(function (id) {
                var el = document.getElementById(id);
                if (!el) { return; }
                if (!el.value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            // Không còn ô ngày nào ở form này -- xem ghi chú ở khối form trên.

            return valid;
        }
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

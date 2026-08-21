<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần: validate tên sản phẩm không rỗng, danh mục hợp lệ, tự sinh
    Mã sản phẩm duy nhất (product_code) rồi INSERT vào bảng products, sau đó
    lưu các file ở input "images"/"catalogues" (request.getParts(), có thể
    nhiều file mỗi loại vì input có "multiple") vào productimages/
    productcatalogues -- xem ProductController + poscs.common.FileStorage.

    Request attribute cần có trước khi forward tới trang này:
      - categoryList : List<poscs.model.ProductCategory>  (để đổ dropdown "Danh mục")
      - csrfToken
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thêm sản phẩm - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 900px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 22px; flex-wrap: wrap; gap: 14px; }
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
        .field-row .hint { font-size: 0.78rem; color: #9ca3af; margin-top: 6px; }

        .form-control, .form-select {
            padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb;
            background-color: #f9fafb; font-size: 0.9rem;
        }
        .form-control:focus, .form-select:focus {
            background-color: #ffffff; border-color: var(--primary-light);
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
        }

        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }

        /* ===== Chọn file từ máy (ảnh / catalogue), nhiều file ===== */
        .upload-dropzone {
            border: 1.5px dashed #d7dde5; border-radius: 12px; background: #f9fafb;
            padding: 18px; text-align: center;
        }
        .upload-btn {
            display: inline-flex; align-items: center; gap: 8px; cursor: pointer;
            background: #fff; border: 1.5px solid #e5e7eb; color: var(--primary-dark);
            border-radius: 10px; padding: 9px 18px; font-weight: 600; font-size: 0.87rem;
        }
        .upload-btn:hover { border-color: var(--primary-light); background: #f0f9ff; }
        .upload-hint { font-size: 0.76rem; color: #9ca3af; margin-top: 8px; }

        .file-preview-grid {
            display: flex; flex-wrap: wrap; gap: 12px; margin-top: 14px;
        }
        .file-thumb {
            position: relative; width: 92px; height: 92px; border-radius: 10px;
            overflow: hidden; background: #eef6fb; border: 1px solid #eef2f6;
        }
        .file-thumb img { width: 100%; height: 100%; object-fit: cover; }
        .file-thumb-remove {
            position: absolute; top: 4px; right: 4px; width: 22px; height: 22px; border-radius: 50%;
            background: rgba(17, 24, 39, 0.65); color: #fff; border: none;
            display: flex; align-items: center; justify-content: center; font-size: 0.7rem; cursor: pointer;
        }
        .file-thumb-remove:hover { background: var(--danger); }

        .file-chip-list { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; }
        .file-chip {
            display: flex; align-items: center; gap: 10px;
            border: 1px solid #eef2f6; border-radius: 10px; padding: 9px 12px; background: #f9fafb;
        }
        .file-chip i.fa-file-pdf { color: var(--danger); font-size: 1.05rem; }
        .file-chip-name { font-size: 0.85rem; color: #111827; font-weight: 500; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .file-chip-size { font-size: 0.76rem; color: #9ca3af; flex-shrink: 0; }
        .file-chip-link { font-size: 0.85rem; color: var(--primary); font-weight: 500; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-decoration: none; }
        .file-chip-link:hover { text-decoration: underline; }
        .file-chip-remove {
            width: 26px; height: 26px; border-radius: 8px; border: none; background: #f3f4f6; color: #6b7280;
            display: flex; align-items: center; justify-content: center; font-size: 0.75rem; cursor: pointer; flex-shrink: 0;
        }
        .file-chip-remove:hover { background: #fdecef; color: var(--danger); }

        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            border: none; border-radius: 10px; padding: 0.6rem 1.4rem;
            font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel {
            background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280;
            border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem;
            text-decoration: none; display: inline-flex; align-items: center;
        }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) { .card-box { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="product" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/product" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <div class="page-header-row">
            <div>
                <h2>Thêm sản phẩm</h2>
                <p>Tạo mới thiết bị / sản phẩm trong danh mục POS</p>
            </div>
        </div>

        <div class="card-box">
            <form id="createProductForm" action="${pageContext.request.contextPath}/product" method="POST" enctype="multipart/form-data" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="create">

                <div class="section-header"><h5>Thông tin sản phẩm</h5></div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Tên sản phẩm <span class="req">*</span></label>
                        <input type="text" class="form-control" id="productName" name="productName" placeholder="VD: Máy POS X200">
                        <span class="error-text" id="err-productName">Tên sản phẩm không được để trống.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Danh mục <span class="req">*</span></label>
                        <select class="form-select" id="category" name="categoryId">
                            <option value="">-- Chọn danh mục --</option>
                            <c:forEach var="cat" items="${categoryList}">
                                <option value="${cat.categoryId}">${fn:escapeXml(cat.categoryName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-category">Vui lòng chọn danh mục sản phẩm.</span>
                    </div>
                    <div class="col-12 field-row">
                        <label>Mô tả</label>
                        <textarea class="form-control" id="description" name="description" rows="4" placeholder="Mô tả tính năng, thông số kỹ thuật..."></textarea>
                    </div>
                </div>

                <div class="section-header"><h5>Hình ảnh &amp; tài liệu</h5></div>
                <div class="row">
                    <div class="col-12 field-row">
                        <label>Hình ảnh sản phẩm</label>
                        <div class="upload-dropzone">
                            <label for="imagesInput" class="upload-btn"><i class="fa-solid fa-images"></i> Chọn ảnh từ máy</label>
                            <input type="file" name="images" id="imagesInput" accept="image/*" multiple hidden>
                            <div class="upload-hint">Có thể chọn nhiều ảnh cùng lúc (JPG, PNG, WEBP...).</div>
                        </div>
                        <div class="file-preview-grid" id="imagePreviewGrid"></div>
                    </div>
                    <div class="col-12 field-row">
                        <label>File catalogue</label>
                        <div class="upload-dropzone">
                            <label for="cataloguesInput" class="upload-btn"><i class="fa-solid fa-file-pdf"></i> Chọn file catalogue</label>
                            <input type="file" name="catalogues" id="cataloguesInput" accept="application/pdf" multiple hidden>
                            <div class="upload-hint">Có thể chọn nhiều file catalogue (PDF) cùng lúc.</div>
                        </div>
                        <div class="file-chip-list" id="catalogueChipList"></div>
                    </div>
                </div>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/product" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Tạo sản phẩm</button>
                </div>
            </form>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // ===== Chọn nhiều file (ảnh / catalogue) kèm xem trước + gỡ bớt trước khi
        // submit -- input[type=file][multiple] không cho xoá bớt 1 file trực tiếp
        // nên giữ danh sách File trong JS rồi dựng lại input.files bằng DataTransfer
        // mỗi khi thêm/gỡ, đảm bảo input luôn khớp với những gì đang hiển thị.
        function setupMultiFileField(inputId, previewContainerId, renderItem) {
            var input = document.getElementById(inputId);
            var container = document.getElementById(previewContainerId);
            var files = [];

            function sync() {
                var dt = new DataTransfer();
                files.forEach(function (f) { dt.items.add(f); });
                input.files = dt.files;
            }
            function render() {
                container.innerHTML = '';
                files.forEach(function (f, idx) {
                    container.appendChild(renderItem(f, idx, remove));
                });
            }
            function remove(idx) {
                files.splice(idx, 1);
                sync();
                render();
            }
            input.addEventListener('change', function (e) {
                Array.from(e.target.files).forEach(function (f) { files.push(f); });
                sync();
                render();
            });
        }

        function formatFileSize(bytes) {
            if (bytes < 1024) { return bytes + ' B'; }
            if (bytes < 1024 * 1024) { return (bytes / 1024).toFixed(1) + ' KB'; }
            return (bytes / 1024 / 1024).toFixed(1) + ' MB';
        }

        setupMultiFileField('imagesInput', 'imagePreviewGrid', function (file, idx, removeFn) {
            var wrap = document.createElement('div');
            wrap.className = 'file-thumb';
            var img = document.createElement('img');
            img.alt = file.name;
            var reader = new FileReader();
            reader.onload = function (evt) { img.src = evt.target.result; };
            reader.readAsDataURL(file);
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'file-thumb-remove';
            btn.innerHTML = '<i class="fa-solid fa-xmark"></i>';
            btn.addEventListener('click', function () { removeFn(idx); });
            wrap.appendChild(img);
            wrap.appendChild(btn);
            return wrap;
        });

        setupMultiFileField('cataloguesInput', 'catalogueChipList', function (file, idx, removeFn) {
            var chip = document.createElement('div');
            chip.className = 'file-chip';
            var icon = document.createElement('i');
            icon.className = 'fa-solid fa-file-pdf';
            var name = document.createElement('span');
            name.className = 'file-chip-name';
            name.textContent = file.name;
            var size = document.createElement('span');
            size.className = 'file-chip-size';
            size.textContent = formatFileSize(file.size);
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'file-chip-remove';
            btn.innerHTML = '<i class="fa-solid fa-xmark"></i>';
            btn.addEventListener('click', function () { removeFn(idx); });
            chip.appendChild(icon);
            chip.appendChild(name);
            chip.appendChild(size);
            chip.appendChild(btn);
            return chip;
        });

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            var name = document.getElementById('productName');
            if (!name.value.trim()) { document.getElementById('err-productName').style.display = 'block'; valid = false; }

            var category = document.getElementById('category');
            if (!category.value) { document.getElementById('err-category').style.display = 'block'; valid = false; }

            return valid;
        }
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>

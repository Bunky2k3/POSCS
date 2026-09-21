// Nút hamburger trong topbar (id "sidebarToggle") điều khiển sidebar dùng
// chung (id "sidebar") ở jsp/common/sidebar.jsp. Nó có HAI chế độ tuỳ bề
// ngang màn hình, khớp với breakpoint 900px trong css/appshell.css:
//
//   - Desktop (> 900px): thu gọn / mở rộng cột sidebar tại chỗ. Trạng thái
//     lưu localStorage để giữ nguyên khi chuyển trang (mỗi trang là 1 lần
//     render server riêng, không phải SPA).
//   - Máy nhỏ (<= 900px): sidebar là ngăn kéo trượt từ trái, kèm lớp nền mờ.
//     KHÔNG lưu trạng thái -- mở trang mới thì ngăn kéo phải đóng, không ai
//     muốn vào trang nào cũng bị menu che mất nội dung.
(function () {
    var sidebar = document.getElementById('sidebar');
    var toggle = document.getElementById('sidebarToggle');
    if (!sidebar || !toggle) {
        return;
    }

    var STORAGE_KEY = 'poscsSidebarCollapsed';
    var MOBILE_QUERY = window.matchMedia('(max-width: 900px)');

    var backdrop = document.createElement('div');
    backdrop.className = 'sidebar-backdrop';
    document.body.appendChild(backdrop);

    if (localStorage.getItem(STORAGE_KEY) === '1') {
        sidebar.classList.add('collapsed');
    }

    // Khoá cuộn nền khi ngăn kéo mở: nếu không, vuốt trên lớp nền mờ vẫn cuộn
    // nội dung phía sau, người dùng đóng menu ra thì thấy trang đã trôi đi đâu.
    function setDrawer(open) {
        sidebar.classList.toggle('drawer-open', open);
        backdrop.classList.toggle('show', open);
        document.body.style.overflow = open ? 'hidden' : '';
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    function closeDrawer() {
        setDrawer(false);
    }

    toggle.addEventListener('click', function () {
        if (MOBILE_QUERY.matches) {
            setDrawer(!sidebar.classList.contains('drawer-open'));
            return;
        }
        var collapsed = sidebar.classList.toggle('collapsed');
        localStorage.setItem(STORAGE_KEY, collapsed ? '1' : '0');
    });

    backdrop.addEventListener('click', closeDrawer);

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            closeDrawer();
        }
    });

    // Xoay ngang máy / phóng to cửa sổ qua mốc 900px: dọn trạng thái ngăn kéo
    // để sidebar không kẹt ở dạng overlay khi đã quay về bố cục desktop.
    MOBILE_QUERY.addEventListener('change', closeDrawer);
})();


/* ===================================================================
   Ô lọc dạng bảng chọn (popover): tích nhiều tỉnh, và chọn kỳ
   ===================================================================
   Dùng chung ở Dashboard, danh sách hợp đồng, danh sách khách hàng. Trước đây
   chỉ Dashboard có và mã nằm trong <script> của chính nó; khi màn hình thứ hai
   cần đến thì chép sang là bắt đầu có hai bản sẽ lệch nhau.

   Đánh dấu bằng data-* chứ không bằng id: một trang có thể có nhiều ô, và id
   cứng thì trang thứ hai phải đặt trùng tên mới chạy. */
(function () {
    'use strict';

    /* Các hàm đóng của mọi bảng trên trang. Mở một bảng thì đóng hết bảng còn
       lại: hai bảng rộng 300px cùng mở sẽ đè lên nhau, và nút bấm của bảng dưới
       nằm khuất sau bảng trên. Bấm ra ngoài không giải quyết được vì chính
       handler của nút gọi stopPropagation (nếu không thì bấm nút là mở rồi đóng
       ngay trong cùng một cú bấm). */
    var closers = [];

    /* Mở/đóng một bảng. Bấm TRONG bảng thì không đóng -- tích một ô mà bảng tự
       đóng thì không tích được ô thứ hai. */
    function wirePopover(root) {
        var toggle = root.querySelector('[data-popover-toggle]');
        var panel = root.querySelector('[data-popover-panel]');
        if (!toggle || !panel) { return null; }

        function setOpen(open) {
            if (open) {
                closers.forEach(function (close) { close(); });
            }
            panel.classList.toggle('open', open);
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            if (open && typeof root._onOpen === 'function') { root._onOpen(); }
        }
        closers.push(function () { setOpen(false); });
        toggle.addEventListener('click', function (e) {
            e.stopPropagation();
            setOpen(!panel.classList.contains('open'));
        });
        panel.addEventListener('click', function (e) { e.stopPropagation(); });
        document.addEventListener('click', function () { setOpen(false); });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { setOpen(false); }
        });
        return panel;
    }

    /* Bảng tích tỉnh. KHÔNG tự gửi form sau mỗi lần tích: chọn ba tỉnh là ba
       lần tải trang, hai lần đầu ra kết quả chẳng ai cần. Có nút "Áp dụng". */
    document.querySelectorAll('[data-prov-pop]').forEach(function (root) {
        var panel = wirePopover(root);
        if (!panel) { return; }
        var boxes = panel.querySelectorAll('input[name="provinceId"]');
        function each(fn) { boxes.forEach(fn); }

        var all = panel.querySelector('[data-prov-all]');
        var none = panel.querySelector('[data-prov-none]');
        var mine = panel.querySelector('[data-prov-mine]');
        if (all) { all.addEventListener('click', function () { each(function (b) { b.checked = true; }); }); }
        if (none) { none.addEventListener('click', function () { each(function (b) { b.checked = false; }); }); }
        if (mine) {
            mine.addEventListener('click', function () {
                each(function (b) { b.checked = b.dataset.mine === 'true'; });
            });
        }
    });

    /* Bảng chọn kỳ: năm + quý + tháng trong MỘT ô.
       Hai ô rời (năm, quý/tháng) có một cái bẫy im lặng: chọn "Quý 3" mà quên
       chọn năm thì Period.parse trả null, tức KHÔNG lọc gì, trong khi ô vẫn
       hiện "Quý 3". Ở đây năm luôn đi kèm nên mỗi lựa chọn là một kỳ hoàn
       chỉnh. */
    document.querySelectorAll('[data-period-pop]').forEach(function (root) {
        var panel = wirePopover(root);
        if (!panel) { return; }

        var years = (panel.dataset.years || '').split(',').filter(Boolean).map(Number)
                        .sort(function (a, b) { return a - b; });
        if (!years.length) { return; }
        var selYear = panel.dataset.selectedYear ? Number(panel.dataset.selectedYear) : null;
        var selPeriod = panel.dataset.selectedPeriod || '';
        var viewYear = selYear || years[years.length - 1];

        var label = panel.querySelector('[data-period-year]');
        var prev = panel.querySelector('[data-period-prev]');
        var next = panel.querySelector('[data-period-next]');
        var form = root.closest('form');
        var yearInput = form && form.querySelector('[data-period-year-input]');
        var periodInput = form && form.querySelector('[data-period-value-input]');

        function render() {
            label.textContent = 'Năm ' + viewYear;
            prev.disabled = viewYear <= years[0];
            next.disabled = viewYear >= years[years.length - 1];
            /* Tô đậm CHỈ khi đang xem đúng năm đã chọn -- nếu không, bấm mũi
               tên sang năm khác vẫn thấy "Q3" sáng và tưởng đang xem quý 3 của
               năm đó. */
            panel.querySelectorAll('[data-pick]').forEach(function (b) {
                var pick = b.dataset.pick;
                var on;
                if (pick === 'any') { on = selYear === null; }
                else if (pick === 'year') { on = selYear === viewYear && selPeriod === ''; }
                else { on = selYear === viewYear && selPeriod === pick; }
                b.classList.toggle('sel', on);
            });
        }
        root._onOpen = render;

        prev.addEventListener('click', function () {
            if (viewYear > years[0]) { viewYear--; render(); }
        });
        next.addEventListener('click', function () {
            if (viewYear < years[years.length - 1]) { viewYear++; render(); }
        });

        /* Mỗi lựa chọn ghi thẳng vào hai ô ẩn rồi gửi form -- không giữ trạng
           thái riêng ở tầng JS, nên nạp lại trang thì thứ hiện ra luôn là thứ
           máy chủ đang thật sự lọc. */
        panel.querySelectorAll('[data-pick]').forEach(function (b) {
            b.addEventListener('click', function () {
                if (!form || !yearInput || !periodInput) { return; }
                var pick = b.dataset.pick;
                yearInput.value = pick === 'any' ? '' : viewYear;
                periodInput.value = (pick === 'any' || pick === 'year') ? '' : pick;
                form.submit();
            });
        });
    });
})();

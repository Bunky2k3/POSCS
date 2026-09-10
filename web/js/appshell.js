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

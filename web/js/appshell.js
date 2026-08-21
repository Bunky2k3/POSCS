// Thu gọn / mở rộng sidebar -- nút toggle giờ nằm trong topbar (id
// "sidebarToggle"), tác động lên sidebar (id "sidebar") ở khung dùng chung
// jsp/common/sidebar.jsp. Trạng thái lưu localStorage để giữ nguyên khi
// chuyển trang (mỗi trang là 1 lần render server riêng, không phải SPA).
(function () {
    var sidebar = document.getElementById('sidebar');
    var toggle = document.getElementById('sidebarToggle');
    if (!sidebar || !toggle) {
        return;
    }
    var STORAGE_KEY = 'poscsSidebarCollapsed';
    if (localStorage.getItem(STORAGE_KEY) === '1') {
        sidebar.classList.add('collapsed');
    }
    toggle.addEventListener('click', function () {
        var collapsed = sidebar.classList.toggle('collapsed');
        localStorage.setItem(STORAGE_KEY, collapsed ? '1' : '0');
    });
})();

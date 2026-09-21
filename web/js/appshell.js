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

        /* Neo bảng sao cho nó nằm TRONG màn hình.
           CSS chỉ đoán được theo trang: mặc định neo mép phải (ở Dashboard và
           danh sách khách hàng nút đứng cuối hàng), còn trong khối "Lọc thêm"
           của danh sách hợp đồng thì trang đó tự đặt lại thành neo trái. Đoán
           kiểu đó sai ngay khi hàng lọc xuống dòng hoặc khi thêm bớt một ô --
           bảng rộng 320px chạy ra ngoài mép trang và mất luôn nút "Áp dụng".
           Ở đây đo thật rồi mới quyết: xoá kiểu nội tuyến để về đúng mặc định
           của trang, lệch bên nào thì lật sang bên kia. */
        function neoLai() {
            panel.style.left = '';
            panel.style.right = '';
            var manHinh = document.documentElement.clientWidth;
            var o = panel.getBoundingClientRect();
            if (o.right > manHinh - 8) {
                panel.style.left = 'auto';
                panel.style.right = '0';
                o = panel.getBoundingClientRect();
            }
            if (o.left < 8) {
                panel.style.left = '0';
                panel.style.right = 'auto';
            }
        }

        function setOpen(open) {
            if (open) {
                closers.forEach(function (close) { close(); });
            }
            panel.classList.toggle('open', open);
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            // Đo SAU khi đã hiện: bảng đang display:none thì mọi kích thước là 0.
            if (open) { neoLai(); }
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

// Tiền VNĐ: định dạng ô nhập theo nhóm nghìn và đọc số thành chữ.
//
// Dùng chung cho ô "Giá trị hợp đồng" (tạo / sửa / phụ lục) và ô "Số tiền" của
// kỳ thanh toán, cùng dòng chữ dưới con số ở trang chi tiết hợp đồng. Đánh dấu
// bằng thuộc tính, không phải bằng id, để thêm một ô tiền mới ở màn khác chỉ
// cần gắn data-money là xong:
//
//   <input data-money>                     ô nhập, tự chèn dấu chấm khi gõ
//   <span data-money-words-for="idCuaO">   chữ chạy theo ô đó
//   <span data-money-words data-vnd="...">  chữ cho một con số cố định
//
// Dấu phân nhóm là DẤU CHẤM, khớp phần hiển thị sẵn có (toLocaleString
// 'vi-VN') -- trong một màn hình mà chỗ này chấm chỗ kia phẩy thì người đọc
// không biết 1.500 là một nghìn rưỡi hay một phẩy năm.
(function () {
    'use strict';

    var DON_VI = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
    var HANG = ['', ' nghìn', ' triệu', ' tỷ', ' nghìn tỷ', ' triệu tỷ'];

    // Đọc một nhóm 3 chữ số. dayDu = nhóm này đứng SAU một nhóm lớn hơn, nên
    // phải đọc cả phần trăm rỗng: 1.000.005 là "một triệu không trăm lẻ năm",
    // bỏ "không trăm" đi thì thành "một triệu năm" -- nghe ra 1.000.500.
    function docBaChuSo(n, dayDu) {
        var tram = Math.floor(n / 100);
        var chuc = Math.floor((n % 100) / 10);
        var donVi = n % 10;
        var out = '';

        if (tram > 0) {
            out += DON_VI[tram] + ' trăm';
        } else if (dayDu) {
            out += 'không trăm';
        }
        if (chuc === 0 && donVi > 0 && out !== '') {
            out += ' lẻ';
        }

        if (chuc > 1) {
            out += ' ' + DON_VI[chuc] + ' mươi';
            if (donVi === 1) {
                out += ' mốt';          // hai mươi mốt, không phải "hai mươi một"
            } else if (donVi === 5) {
                out += ' lăm';          // hai mươi lăm
            } else if (donVi > 0) {
                out += ' ' + DON_VI[donVi];
            }
        } else if (chuc === 1) {
            out += ' mười';
            if (donVi === 5) {
                out += ' lăm';          // mười lăm
            } else if (donVi > 0) {
                out += ' ' + DON_VI[donVi];
            }
        } else if (donVi > 0) {
            out += ' ' + DON_VI[donVi];
        }
        return out.replace(/\s+/g, ' ').trim();
    }

    function docSo(n) {
        if (n === 0) {
            return 'không';
        }
        var nhom = [];
        while (n > 0) {
            nhom.push(n % 1000);
            n = Math.floor(n / 1000);
        }
        var phan = [];
        for (var i = nhom.length - 1; i >= 0; i--) {
            if (nhom[i] === 0) {
                continue;               // nhóm rỗng thì bỏ hẳn: 1.000.000 là "một triệu"
            }
            phan.push(docBaChuSo(nhom[i], i < nhom.length - 1) + HANG[i]);
        }
        return phan.join(' ');
    }

    // Đọc số tiền thành chữ, viết hoa chữ đầu. Số âm chỉ xuất hiện trên phụ
    // lục giảm trừ, nên đọc là "Giảm trừ ..." cho đúng nghiệp vụ thay vì "Âm".
    function docTienVND(value) {
        var n = Math.round(Number(value));
        if (!isFinite(n)) {
            return '';
        }
        var am = n < 0;
        var chu = docSo(Math.abs(n));
        if (am) {
            return 'Giảm trừ ' + chu + ' đồng';
        }
        return chu.charAt(0).toUpperCase() + chu.slice(1) + ' đồng';
    }

    function nhomNghin(chuSo) {
        return chuSo.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    }

    // Đọc con số từ một chuỗi có thể mang dấu phân nhóm LẪN dấu thập phân --
    // cùng quy tắc với MoneyVnd.parseOrNull bên Java, xem javadoc ở đó. Cần
    // cho giá trị mặc định của ô sửa: server đổ ra "980000000.00", xoá mọi dấu
    // chấm là thành 98 tỷ.
    //
    // Nhận cả số ÂM, khác MoneyVnd.parseOrNull bên Java (bên đó từ chối, vì
    // không ai được gõ một giá trị hợp đồng âm vào form): hàm này còn đọc
    // data-vnd của trang chi tiết, mà phụ lục giảm trừ mang giá trị âm thật.
    function soTuChuoi(raw) {
        var s = String(raw == null ? '' : raw).replace(/[\s\u00A0\u202F]/g, '');
        if (!s || !/^-?[0-9]+([.,][0-9]+)*$/.test(s)) {
            return null;
        }
        var am = s.charAt(0) === '-';
        if (am) {
            s = s.slice(1);
        }
        var lastDot = s.lastIndexOf('.');
        var lastComma = s.lastIndexOf(',');
        var sep = Math.max(lastDot, lastComma);
        var intPart = s;
        var frac = '';
        if (sep >= 0) {
            var tail = s.slice(sep + 1);
            if ((lastDot >= 0 && lastComma >= 0) || tail.length !== 3) {
                intPart = s.slice(0, sep).replace(/[.,]/g, '');
                frac = tail;
            } else {
                intPart = s.replace(/[.,]/g, '');
            }
        }
        var n = Number((intPart || '0') + (frac ? '.' + frac : ''));
        if (!isFinite(n)) {
            return null;
        }
        return am ? -n : n;
    }

    /**
     * Chuỗi đã định dạng cho một giá trị CHƯA chuẩn -- số server đổ ra, hoặc
     * thứ vừa được dán vào -- hoặc null khi không đọc được, khi đó nơi gọi để
     * nguyên cho người dùng tự sửa.
     */
    function chuanHoaTien(raw) {
        var n = soTuChuoi(raw);
        if (n === null || n < 0) {
            // Ô nhập tiền luôn là số dương: phụ lục giảm trừ lấy dấu từ ô chọn
            // Bổ sung/Giảm trừ bên cạnh, không từ dấu trừ trong ô số.
            return null;
        }
        return nhomNghin(String(Math.round(n)));
    }

    /** Chỉ giữ chữ số rồi phân nhóm -- đường đi lúc người dùng đang GÕ. */
    function giuChuSo(raw) {
        var chuSo = String(raw == null ? '' : raw).replace(/\D/g, '').replace(/^0+(?=\d)/, '');
        return chuSo ? nhomNghin(chuSo) : '';
    }

    /**
     * Giá trị ô sẽ mang sau một lần nhập. Hàm THUẦN, tách riêng khỏi phần gắn
     * sự kiện để test vào được: cả hai lỗi đã bắt trên bản chạy (số âm đọc ra
     * rỗng, dán "980000000.00" thành 98 tỷ) đều nằm ở đây chứ không nằm ở chỗ
     * đăng ký listener.
     */
    function giaTriSauNhap(raw, laDan) {
        if (laDan) {
            // DÁN (hoặc kéo-thả) thì đọc bằng quy tắc đầy đủ: thứ được dán vào
            // thường là số do chính hệ thống đổ ra và còn nguyên đuôi ".00".
            // Chỉ giữ chữ số ở đây là dán "980000000.00" vào ra 98 tỷ -- đúng
            // lỗi đã sửa ở server, nhưng xảy ra TRƯỚC khi form gửi đi nên bên
            // Java không còn gì để cứu.
            var chuan = chuanHoaTien(raw);
            if (chuan !== null) {
                return chuan;
            }
        }
        // Lúc GÕ thì chỉ giữ chữ số: tiền ở đây luôn là số nguyên đồng, mà áp
        // quy tắc đầy đủ vào từng phím sẽ cắn vào chuỗi trung gian -- đang gõ
        // "1.500" thì có một khoảnh khắc ô mang "1.5", đọc thành 1,5 rồi làm
        // tròn là chữ số vừa gõ biến mất dưới tay người dùng. Gõ tay phần thập
        // phân thì phần lẻ dính vào phần nguyên, và dòng chữ ngay dưới ô là thứ
        // để người nhập thấy ngay điều đó.
        return giuChuSo(raw);
    }

    function chuChoO(input) {
        var dich = document.querySelector('[data-money-words-for="' + input.id + '"]');
        if (!dich) {
            return;
        }
        var chuSo = input.value.replace(/\D/g, '');
        dich.textContent = chuSo ? docTienVND(Number(chuSo)) : '';
    }

    // Định dạng lại ô và giữ con trỏ ở đúng chỗ người dùng đang gõ: đếm số
    // CHỮ SỐ bên trái con trỏ rồi đặt lại sau đúng ngần ấy chữ số. Đặt thẳng
    // con trỏ về cuối thì sửa một chữ số ở giữa là nó nhảy ra cuối dòng.
    function dinhDangO(input, laDan) {
        var caret = input.selectionStart;
        var soChuSoTruoc = input.value.slice(0, caret).replace(/\D/g, '').length;
        input.value = giaTriSauNhap(input.value, laDan);
        if (laDan) {
            // Vừa dán xong thì con trỏ thuộc về cuối chuỗi vừa dán, không phải
            // vị trí cũ -- số chữ số đã đổi hẳn so với trước đó.
            soChuSoTruoc = input.value.replace(/\D/g, '').length;
        }

        var pos = 0;
        var dem = 0;
        while (pos < input.value.length && dem < soChuSoTruoc) {
            if (/\d/.test(input.value.charAt(pos))) {
                dem++;
            }
            pos++;
        }
        try {
            input.setSelectionRange(pos, pos);
        } catch (e) {
            // input type khác text thì không đặt được caret -- không sao.
        }
        chuChoO(input);
    }

    Array.prototype.forEach.call(document.querySelectorAll('input[data-money]'), function (input) {
        var banDau = chuanHoaTien(input.value);
        if (banDau !== null) {
            input.value = banDau;
        }
        chuChoO(input);
        input.addEventListener('input', function (ev) {
            var laDan = !!(ev && (ev.inputType === 'insertFromPaste' || ev.inputType === 'insertFromDrop'));
            dinhDangO(input, laDan);
        });
    });

    Array.prototype.forEach.call(document.querySelectorAll('[data-money-words][data-vnd]'), function (el) {
        var n = soTuChuoi(el.getAttribute('data-vnd'));
        el.textContent = n === null ? '' : docTienVND(n);
    });

    // Cửa duy nhất để test vào được phần JS: bộ chạy test nạp chính file này
    // trong một sandbox có document giả (xem test/js/money-vnd.test.js), nên
    // thứ gì không treo ở đây thì không kiểm được. Chỉ mở các hàm THUẦN --
    // phần đụng vào DOM vẫn phải mở trang thật mới biết.
    window.POSCS = window.POSCS || {};
    window.POSCS.docTienVND = docTienVND;
    window.POSCS.soTuChuoi = soTuChuoi;
    window.POSCS.nhomNghin = nhomNghin;
    window.POSCS.giaTriSauNhap = giaTriSauNhap;
})();

# -*- coding: utf-8 -*-
"""Vẽ các sơ đồ cho file Word hướng dẫn sử dụng (mẫu SEP490 Report6).

Ba hình, lưu cạnh file này (tools/guide/word/):
  - tong-quan.png          sơ đồ tổng quan: phân hệ, ai quản lý, luồng dữ liệu
  - vong-doi-hop-dong.png  vòng đời hợp đồng: trục tiến độ + trục lịch
  - trang-thai-phieu.png   trạng thái phiếu hỗ trợ + bốn phần nội dung phiếu

Nội dung bám code, không bám tài liệu cũ: ContractDAO.ALLOWED_TRANSITIONS và
STATUS_*, TechnicalSupportTicketDAO.STATUS_*, AccessControl.FULL_ACCESS_ROLES,
NotificationScheduler, và chữ của các trang hướng dẫn trong app. Đổi luật ở đó
thì vẽ lại ở đây -- chạy:
    python tools/guide/word/diagrams.py
Cần Pillow. Chữ tiếng Việt dùng Segoe UI (Windows); không có thì DejaVu Sans.
"""
import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))

INK = '#1f2937'
MUTED = '#6b7280'
LINE = '#94a3b8'
NAVY = '#003c6e'
BLUE = '#0568a6'
ROLE_COLORS = {'Admin': '#003c6e', 'Sales': '#0568a6', 'Kỹ thuật': '#0f766e'}
FILL = {'plain': '#f8fafc', 'blue': '#eaf4fb', 'green': '#e8f7f1', 'amber': '#fff6e5',
        'gray': '#f1f5f9', 'red': '#fdecef'}
EDGE = {'plain': '#cbd5e1', 'blue': '#8cc3e6', 'green': '#7fd1ad', 'amber': '#f3c67a',
        'gray': '#cbd5e1', 'red': '#f1a3b2'}

_FONT_DIRS = ['C:/Windows/Fonts', '/usr/share/fonts/truetype/dejavu']
_PAD = 20       # lề trong của hộp
_ROLE_H = 44    # hàng nhãn vai trò, kể cả khoảng tránh chân chữ (p, g, y) của tiêu đề


def font(size, bold=False):
    names = (['segoeuib.ttf'] if bold else ['segoeui.ttf']) + \
            (['DejaVuSans-Bold.ttf'] if bold else ['DejaVuSans.ttf'])
    for d in _FONT_DIRS:
        for n in names:
            p = os.path.join(d, n)
            if os.path.exists(p):
                return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def wrap(draw, text, fnt, width):
    """Tách dòng theo độ rộng thật của chữ (không theo số ký tự)."""
    lines = []
    for para in text.split('\n'):
        cur = ''
        for word in para.split(' '):
            trial = (cur + ' ' + word).strip()
            if draw.textlength(trial, font=fnt) <= width or not cur:
                cur = trial
            else:
                lines.append(cur)
                cur = word
        lines.append(cur)
    return lines


def text_block(draw, x, y, text, fnt, width, fill=INK, gap=6):
    """Viết đoạn chữ tự xuống dòng từ (x, y); trả về y ngay dưới dòng cuối."""
    lh = fnt.size + gap
    for line in wrap(draw, text, fnt, width):
        draw.text((x, y), line, font=fnt, fill=fill)
        y += lh
    return y


def box_height(draw, w, title, body='', roles=(), title_size=26, body_size=19):
    """Chiều cao vừa đủ cho nội dung -- hộp cố định chiều cao thì chữ ít để trống cả khoảng lớn."""
    h = 14 + len(wrap(draw, title, font(title_size, True), w - 2 * _PAD)) * (title_size + 6)
    if roles:
        h += _ROLE_H
    if body:
        h += 4 + len(wrap(draw, body, font(body_size), w - 2 * _PAD)) * (body_size + 5)
    return h + 18


def box(draw, x, y, w, h, title, body='', kind='plain', roles=(), title_size=26, body_size=19,
        dashed=False):
    """Hộp bo góc: tiêu đề đậm, nhãn vai trò (nếu có), phần chữ."""
    draw.rounded_rectangle([x, y, x + w, y + h], radius=18, fill=FILL[kind], outline=EDGE[kind], width=3)
    if dashed:  # viền nét đứt: vẽ đè các đoạn cùng màu nền lên viền trên / dưới
        step = 18
        for sx in range(x + 20, x + w - 20, step * 2):
            draw.line([sx, y, sx + step, y], fill=FILL[kind], width=4)
            draw.line([sx, y + h, sx + step, y + h], fill=FILL[kind], width=4)
    ty = text_block(draw, x + _PAD, y + 14, title, font(title_size, True), w - 2 * _PAD, fill=NAVY)
    if roles:
        rx = x + _PAD
        for r in roles:
            f = font(16, True)
            tw = draw.textlength(r, font=f)
            draw.rounded_rectangle([rx, ty + 8, rx + tw + 20, ty + 34], radius=13, fill=ROLE_COLORS[r])
            draw.text((rx + 10, ty + 10), r, font=f, fill='white')
            rx += tw + 30
        ty += _ROLE_H
    if body:
        text_block(draw, x + _PAD, ty + 4, body, font(body_size), w - 2 * _PAD, fill=INK, gap=5)


def row(draw, y, specs, **style):
    """Một hàng hộp cùng chiều cao (của hộp nhiều chữ nhất). specs: [(x, w, title, body, kind, roles)].
    Trả về chiều cao hàng."""
    h = max(box_height(draw, w, t, b, r, style.get('title_size', 26), style.get('body_size', 19))
            for x, w, t, b, k, r in specs)
    for x, w, t, b, k, r in specs:
        box(draw, x, y, w, h, t, b, kind=k, roles=r, **style)
    return h


def arrow(draw, pts, label='', color=LINE, width=4, dashed=False, label_at=0.5, label_dx=0, label_dy=-30,
          head=True, label_size=18, label_xy=None):
    """Mũi tên gấp khúc qua các điểm pts; nhãn (nền trắng) đặt quanh vị trí label_at của đường,
    hoặc đúng chỗ label_xy = (tâm x, mép trên y) khi đường gấp khúc làm vị trí tự tính rơi vào hộp."""
    segs = list(zip(pts, pts[1:]))
    for (x1, y1), (x2, y2) in segs:
        if dashed:
            length = ((x2 - x1) ** 2 + (y2 - y1) ** 2) ** 0.5
            n = max(1, int(length // 16))
            for i in range(0, n, 2):
                a, b = i / n, min(1, (i + 1) / n)
                draw.line([x1 + (x2 - x1) * a, y1 + (y2 - y1) * a, x1 + (x2 - x1) * b, y1 + (y2 - y1) * b],
                          fill=color, width=width)
        else:
            draw.line([x1, y1, x2, y2], fill=color, width=width)
    if head:
        (x1, y1), (x2, y2) = segs[-1]
        length = max(1, ((x2 - x1) ** 2 + (y2 - y1) ** 2) ** 0.5)
        ux, uy = (x2 - x1) / length, (y2 - y1) / length
        s = 16
        draw.polygon([(x2, y2), (x2 - ux * s - uy * s * 0.6, y2 - uy * s + ux * s * 0.6),
                      (x2 - ux * s + uy * s * 0.6, y2 - uy * s - ux * s * 0.6)], fill=color)
    if label:
        total = sum(((b[0] - a[0]) ** 2 + (b[1] - a[1]) ** 2) ** 0.5 for a, b in segs)
        target, run = total * label_at, 0
        mx, my = pts[0]
        for (x1, y1), (x2, y2) in segs:
            seg = ((x2 - x1) ** 2 + (y2 - y1) ** 2) ** 0.5
            if run + seg >= target:
                t = (target - run) / max(seg, 1)
                mx, my = x1 + (x2 - x1) * t, y1 + (y2 - y1) * t
                break
            run += seg
        f = font(label_size, True)
        tw = draw.textlength(label, font=f)
        lx, ly = mx - tw / 2 + label_dx, my + label_dy
        if label_xy:
            lx, ly = label_xy[0] - tw / 2, label_xy[1]
        draw.rounded_rectangle([lx - 8, ly - 4, lx + tw + 8, ly + f.size + 6], radius=8, fill='white')
        draw.text((lx, ly), label, font=f, fill=color)


def canvas(w, h, title):
    img = Image.new('RGB', (w, h), 'white')
    d = ImageDraw.Draw(img)
    d.text((40, 28), title, font=font(34, True), fill=NAVY)
    return img, d


def legend(d, x, y):
    f = font(18, True)
    d.text((x, y), 'Vai trò quản lý:', font=f, fill=MUTED)
    x += d.textlength('Vai trò quản lý:', font=f) + 14
    for r, c in ROLE_COLORS.items():
        tw = d.textlength(r, font=font(16, True))
        d.rounded_rectangle([x, y - 1, x + tw + 20, y + 25], radius=13, fill=c)
        d.text((x + 10, y + 1), r, font=font(16, True), fill='white')
        x += tw + 34


def save(img, bottom, name):
    """Cắt bỏ phần trắng thừa dưới cùng rồi lưu."""
    img.crop((0, 0, img.width, bottom)).save(os.path.join(OUT, name), optimize=True)


# ----------------------------------------------------------------------
# 1. Tổng quan
# ----------------------------------------------------------------------

def tong_quan():
    img, d = canvas(1800, 1400, 'Tổng quan các phân hệ POSCS')
    legend(d, 40, 84)
    L, C, R = (40, 470), (660, 480), (1290, 470)
    y1 = 150
    h1 = row(d, y1, [
        (L[0], L[1], 'Nhân viên & địa bàn',
         'Tạo tài khoản, vai trò, phòng ban. Giao tỉnh phụ trách: mỗi tỉnh một người.', 'gray', ('Admin',)),
        (C[0], C[1], 'Khách hàng',
         'Khách mua và nhà cung cấp. Khách mua tự đứng tên người cầm tỉnh của địa chỉ.', 'blue', ('Admin', 'Sales')),
        (R[0], R[1], 'Trang chủ',
         'Số liệu khách hàng, hợp đồng, doanh thu theo phạm vi, tỉnh và kỳ.', 'plain', ()),
    ])
    y2 = y1 + h1 + 120
    h2 = row(d, y2, [
        (L[0], L[1], 'Phiếu hỗ trợ kỹ thuật',
         'Lập cho khách mua; kỹ thuật viên được giao ghi nguyên nhân, phương hướng, kết quả.', 'amber',
         ('Admin', 'Sales')),
        (C[0], C[1], 'Hợp đồng bán / mua',
         'Nháp → Đã ký → Đã thanh lý. Hàng hoá, kỳ thanh toán, bàn giao, tài liệu, phụ lục.', 'blue',
         ('Admin', 'Sales')),
        (R[0], R[1], 'Sản phẩm',
         'Danh mục thiết bị theo cây 3 cấp, kèm ảnh và catalogue. Không có giá.', 'green', ('Admin', 'Kỹ thuật')),
    ])
    y3 = y2 + h2 + 90
    h3 = row(d, y3, [(C[0], C[1], 'Thông báo', 'Tự gửi mỗi giờ; bấm vào là mở thẳng việc cần làm.',
                      'red', ())])
    m1, m2 = y1 + h1 // 2, y2 + h2 // 2
    arrow(d, [(L[0] + L[1], m1), (C[0], m1)], 'giao tỉnh', label_dy=-32)
    arrow(d, [(C[0] + C[1], m1), (R[0], m1)], 'số liệu', label_dy=-32)
    arrow(d, [(900, y1 + h1), (900, y2)], 'ký hợp đồng với', label_dx=100, label_dy=-12)
    mid1 = y1 + h1 + 60
    arrow(d, [(700, y1 + h1), (700, mid1), (275, mid1), (275, y2)], 'khách mua cần hỗ trợ', label_at=0.45,
          label_dy=-14)
    arrow(d, [(R[0], m2), (C[0] + C[1], m2)], 'hàng hoá', label_dy=-32)
    arrow(d, [(1015, y2 + h2), (1015, y3)], 'sắp hết hạn', label_dx=85, label_dy=-12)
    m3 = y3 + h3 // 2
    arrow(d, [(275, y2 + h2), (275, m3), (C[0], m3)], 'quá hạn SLA', label_at=0.72, label_dy=-32)
    note_y = y3 + h3 + 30
    d.text((40, note_y), 'Kỹ thuật chỉ xem khách hàng, hợp đồng; Sales chỉ xem sản phẩm. Phiếu hỗ trợ: kỹ thuật '
           'viên được giao cập nhật phần xử lý.', font=font(18), fill=MUTED)
    save(img, note_y + 50, 'tong-quan.png')


# ----------------------------------------------------------------------
# 2. Vòng đời hợp đồng
# ----------------------------------------------------------------------

def vong_doi_hop_dong():
    img, d = canvas(1800, 1400, 'Vòng đời hợp đồng')
    d.text((40, 84), 'Tiến độ: người dùng bấm, chỉ đi tới, không quay lại', font=font(22, True), fill=BLUE)
    y = 170
    h = row(d, y, [
        (40, 330, 'Nháp', 'Sửa mọi thông tin, thêm hàng hoá. Xoá được.', 'gray', ()),
        (520, 330, 'Đã ký', 'Điều khoản bị khoá; đổi phải lập phụ lục.', 'blue', ()),
    ])
    mid = y + h // 2
    # Hai trạng thái cuối xếp chồng, cách đều hàng giữa. Từ "Đã ký" đi một thân chung
    # rồi tách hai nhánh; nhãn nằm trên đoạn cuối của nhánh, không đè lên hộp nào.
    eh = box_height(d, 340, 'Đã thanh lý', 'Hợp đồng đã xong. Đóng băng.')
    top_y, bot_y = mid - 20 - eh, mid + 20
    row(d, top_y, [(1080, 340, 'Đã thanh lý', 'Hợp đồng đã xong. Đóng băng.', 'green', ())])
    row(d, bot_y, [(1080, 340, 'Chấm dứt sớm', 'Dừng trước hạn. Đóng băng.', 'amber', ())])
    arrow(d, [(370, mid), (520, mid)], 'Ký', label_dy=-32)
    fork = 900
    for cy, label in ((top_y + eh // 2, 'Thanh lý'), (bot_y + eh // 2, 'Chấm dứt sớm')):
        arrow(d, [(850, mid), (fork, mid), (fork, cy), (1080, cy)], label, label_xy=((fork + 1080) // 2, cy - 32))
    ph_y = y + h + 110
    ph = row(d, ph_y, [(520, 330, 'Phụ lục', 'Cũng đi Nháp → Đã ký; ký rồi mới tính vào giá trị.', 'plain', ())],
             title_size=24, body_size=18, dashed=True)
    arrow(d, [(685, y + h), (685, ph_y)], 'lập phụ lục', label_dx=80, label_dy=-12)
    # xoá / huỷ bản ghi
    note = ('Nháp: người có quyền sửa xoá được.\n\nĐã ký trở đi: chỉ Admin huỷ bản ghi, bắt buộc ghi lý do; '
            'không huỷ được khi đã có phụ lục.')
    hh = box_height(d, 300, 'Xoá / huỷ bản ghi', note, (), 24, 18)
    box(d, 1460, top_y, 300, hh, 'Xoá / huỷ bản ghi', note, kind='red', title_size=24, body_size=18)
    sep = max(ph_y + ph, top_y + hh) + 45
    d.line([40, sep, 1760, sep], fill='#e2e8f0', width=3)
    d.text((40, sep + 26), 'Trạng thái theo lịch: hệ thống tự tính từ ngày hiệu lực và ngày kết thúc, độc lập với '
           'tiến độ', font=font(22, True), fill=BLUE)
    cy = sep + 86
    xs, w = [40, 480, 920, 1360], 400
    cal = [('Chưa hiệu lực', 'Chưa đủ thời hạn, hoặc chưa tới ngày hiệu lực.', 'gray'),
           ('Đang hiệu lực', 'Đang trong thời hạn.', 'green'),
           ('Sắp hết hạn', 'Còn 30 ngày trở xuống; người phụ trách nhận thông báo.', 'amber'),
           ('Đã hết hạn', 'Đã qua ngày kết thúc. Chưa thanh lý thì là việc còn tồn.', 'red')]
    ch = row(d, cy, [(x, w - 40, t, b, k, ()) for x, (t, b, k) in zip(xs, cal)], title_size=24, body_size=18)
    for x in xs[:-1]:
        arrow(d, [(x + w - 40, cy + ch // 2), (x + w, cy + ch // 2)])
    note_y = cy + ch + 28
    d.text((40, note_y), 'Đọc cả hai nhãn: "Đã ký" + "Đã hết hạn" nghĩa là hết hạn mà chưa thanh lý.',
           font=font(19), fill=MUTED)
    save(img, note_y + 50, 'vong-doi-hop-dong.png')


# ----------------------------------------------------------------------
# 3. Trạng thái phiếu hỗ trợ
# ----------------------------------------------------------------------

def trang_thai_phieu():
    img, d = canvas(1800, 1200, 'Trạng thái và nội dung phiếu hỗ trợ kỹ thuật')
    y = 110
    h = row(d, y, [
        (40, 440, 'Mới tiếp nhận', 'Phiếu vừa tạo, chưa ai bắt tay xử lý. Xoá được.', 'gray', ()),
        (680, 440, 'Đang xử lý', 'Kỹ thuật viên đang xử lý. Không xoá được.', 'amber', ()),
        (1320, 440, 'Đã đóng', 'Xử lý xong; lần đầu đóng, hệ thống ghi thời điểm hoàn tất.', 'green', ()),
    ])
    mid = y + h // 2
    arrow(d, [(480, mid), (680, mid)], 'bắt tay xử lý', label_dy=-32)
    arrow(d, [(1120, mid), (1320, mid)], 'xong', label_dy=-32)
    loop = y + h + 55
    arrow(d, [(1540, y + h), (1540, loop), (900, loop), (900, y + h)], 'mở lại', dashed=True, label_at=0.5,
          label_dy=-14)
    ny = loop + 40
    d.text((40, ny), 'Chọn trạng thái nào cũng được, không bắt buộc đi đúng thứ tự; mỗi lần đổi, lịch sử xử lý '
           'của phiếu có thêm một dòng.', font=font(19), fill=MUTED)
    sep = ny + 55
    d.line([40, sep, 1760, sep], fill='#e2e8f0', width=3)
    d.text((40, sep + 26), 'Bốn phần nội dung của phiếu, không phần nào gộp vào phần nào', font=font(22, True),
           fill=BLUE)
    py = sep + 80
    parts = [('Mô tả sự cố', 'Người tiếp nhận ghi khi lập phiếu.', 'blue'),
             ('Nguyên nhân', 'Kỹ thuật viên ghi sau khi kiểm tra.', 'amber'),
             ('Phương hướng xử lý', 'Kỹ thuật viên ghi cách xử lý.', 'amber'),
             ('Kết quả', 'Kỹ thuật viên ghi khi xong.', 'green')]
    xs = [40, 480, 920, 1360]
    ph = row(d, py, [(x, 400, t, b, k, ()) for x, (t, b, k) in zip(xs, parts)], title_size=24, body_size=18)
    for x in xs[:-1]:
        arrow(d, [(x + 400, py + ph // 2), (x + 440, py + ph // 2)])
    note_y = py + ph + 28
    d.text((40, note_y), 'Hạn xử lý (SLA) do Admin hoặc Sales đặt khi lập / sửa phiếu (không bắt buộc). Còn dưới '
           '24 giờ hoặc đã quá hạn mà chưa đóng thì kỹ thuật viên được giao nhận thông báo.',
           font=font(19), fill=MUTED)
    save(img, note_y + 50, 'trang-thai-phieu.png')


if __name__ == '__main__':
    tong_quan()
    vong_doi_hop_dong()
    trang_thai_phieu()
    # In không dấu: console Windows (cp1252) không in được tiếng Việt.
    for n in ('tong-quan.png', 'vong-doi-hop-dong.png', 'trang-thai-phieu.png'):
        print('ve xong', os.path.join(OUT, n))

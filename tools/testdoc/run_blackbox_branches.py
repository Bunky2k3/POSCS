#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Lượt 4: các ca thêm theo PR #149–#167 (bộ test case cập nhật 2026-09-30).

Gồm những thứ ba lượt trước không đụng tới:
  * phạm vi của Sales (PR #149/#155): khoá người phụ trách theo người tạo và
    địa bàn, trang Thêm nhà cung cấp, khách ngoài phạm vi chỉ xem được;
  * luật Người hỗ trợ không là cấp trên trực tiếp (PR #150);
  * các nhánh "ghi xuống CSDL hỏng" (PR #159/#162/#163/#164, PR #154) --
    dựng bằng trigger SIGNAL trên CSDL kiểm thử (testdb.FailingWrite);
  * notfound giữ đúng danh sách (PR #161/#164), từ khoá có & và # (PR #166),
    ô lọc Người xử lý (PR #167), dải nhắc hồ sơ "Còn thiếu" (PR #165).

Chạy SAU run_blackbox.py (lượt đó ghi đè file kết quả); lượt này gộp thêm.
Cần CSDL kiểm thử là POSCS_TEST_DB (mặc định poscs_bbtest) -- xem testdb.py.

Chạy:  python tools/testdoc/run_blackbox_branches.py [BASE_URL] [--log <tomcat.out>]
"""

import json
import pathlib
import re
import sys
import time

import requests

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import run_blackbox as R            # noqa: E402
import run_blackbox_ui as U         # noqa: E402
import testdb as T                  # noqa: E402

expect = R.expect
record = R.record
html = U.html
page_text = U.page_text
manual = U.manual

WARD_HA_NOI, WARD_HAI_PHONG, WARD_LAI_CHAU, WARD_HCM, WARD_CA_MAU = "1", "1022", "352", "2563", "3258"
SEQ = [0]


def uniq():
    """Hậu tố khác nhau cho mỗi bản ghi tạo trong lượt (MST / SĐT / email là UNIQUE)."""
    SEQ[0] += 1
    return "%s%02d" % (R.RUN, SEQ[0])


def loc_of(r):
    return r.headers.get("Location") or ""


def follow(session, loc):
    if not loc:
        return ""
    return page_text(html(session.get(R.BASE + loc.split("/POSCS", 1)[-1])))


def customer_form(**over):
    u = uniq()
    data = {"action": "create", "customerName": "Cty Nhanh %s" % u,
            "customerType": "Nhà mạng viễn thông", "customerGroup": "Tiềm năng",
            "taxCode": "88" + u, "phone": "08" + u[-8:], "email": "nh%s@example.vn" % u,
            "districtId": WARD_HA_NOI, "addressDetail": "So 1 duong Nhanh"}
    data.update(over)
    return data


def multipart(session, path, data, repeated=None):
    """POST multipart; `repeated` = [(tên, giá trị)] cho ô gửi nhiều lần (vd roles)."""
    fields = [(k, (None, "" if v is None else str(v))) for k, v in data.items()]
    fields += [(k, (None, v)) for k, v in (repeated or [])]
    fields.append(("csrfToken", (None, R.csrf(session))))
    return session.post(R.BASE + path, files=fields, allow_redirects=False)


def created(loc, kind):
    m = re.search(r"/%s\?action=view&id=(\d+)" % kind, loc)
    return m.group(1) if m else None


def owner_of(eid):
    return T.scalar("SELECT u.username FROM enterprises e JOIN users u ON u.user_id = e.account_owner_id "
                    "WHERE e.enterprise_id = %s" % eid)


# --------------------------------------------------------------------------
# Khách hàng / nhà cung cấp
# --------------------------------------------------------------------------
def test_customer_scope(S):
    print("\n[Khách hàng — phạm vi Sales, nhà cung cấp, người hỗ trợ]")
    admin = S["admin"]
    s4, _ = R.login("sales4")
    s2, _ = R.login("sales2")
    s3, _ = R.login("sales3")
    c2, _ = R.login("cskh2")
    sales4, sales2, sales3, sale01 = (T.user_id(u) for u in ("sales4", "sales2", "sales3", "sale01"))
    kh1 = T.enterprise_id("KH-0001")        # Hà Nội, sales4 phụ trách

    # --- trang Thêm khách hàng
    loc = loc_of(multipart(c2, "/customer", customer_form(districtId=WARD_HA_NOI, accountOwnerId=sale01)))
    eid = created(loc, "customer")
    form = html(c2.get(R.BASE + "/customer?action=new"))
    opts = [o for o in form.select("#province option") if o.get("value")]
    expect("TC_CUSADD_016", eid is not None and owner_of(eid) == "sales4" and len(opts) >= 18
           and form.find("input", {"type": "hidden", "name": "accountOwnerId", "value": T.user_id("cskh2")}) is None,
           "cskh2 (Sales chưa có tỉnh): ô tỉnh có %d tỉnh; tạo khách ở Hà Nội -> người phụ trách %s"
           % (len(opts), owner_of(eid) if eid else loc))

    loc = loc_of(multipart(admin, "/customer", customer_form(districtId=WARD_HA_NOI, accountOwnerId=sale01)))
    eid = created(loc, "customer")
    expect("TC_CUSADD_017", eid is not None and owner_of(eid) == "sales4",
           "Admin chọn Hà Nội, gửi accountOwnerId=sale01 -> khách đứng tên %s" % (owner_of(eid) if eid else loc))

    loc = loc_of(multipart(s4, "/customer", customer_form(districtId=WARD_HAI_PHONG)))
    shown = follow(s4, loc)
    expect("TC_CUSADD_018", "error=province_not_allowed" in loc
           and "Xã / phường đã chọn không thuộc các tỉnh bạn phụ trách. Vui lòng chọn lại." in shown,
           "sales4 gửi xã ở Hải Phòng -> %s" % loc)

    # --- trang Thêm nhà cung cấp
    form = html(s4.get(R.BASE + "/customer?action=new&kind=supplier"))
    ftxt = page_text(form)
    types = [o.get_text(strip=True) for o in form.select("select[name=customerType] option") if o.get("value")]
    provinces = [o for o in form.select("#province option") if o.get("value")]
    locked = form.find("input", {"type": "hidden", "name": "accountOwnerId", "value": sales4}) is not None
    loc = loc_of(multipart(s4, "/customer", customer_form(kind="supplier", customerType="Nhà sản xuất",
                                                          districtId=WARD_HCM, accountOwnerId=sales2)))
    sup4 = created(loc, "customer")
    code = T.scalar("SELECT enterprise_code FROM enterprises WHERE enterprise_id = %s" % (sup4 or 0))
    role = T.scalar("SELECT GROUP_CONCAT(role) FROM enterprise_roles WHERE enterprise_id = %s" % (sup4 or 0))
    sname = T.scalar("SELECT enterprise_name FROM enterprises WHERE enterprise_id = %s" % (sup4 or 0)) or "?"
    in_sup = U.body_rows(html(s4.get(R.BASE + "/customer", params={"kind": "supplier", "view": "all", "keyword": sname})))
    in_buy = U.body_rows(html(s4.get(R.BASE + "/customer", params={"view": "all", "keyword": sname})))
    expect("TC_CUSADD_019",
           "Thêm nhà cung cấp" in ftxt and types == ["Nhà sản xuất", "Nhà nhập khẩu", "Nhà phân phối", "Đơn vị dịch vụ"]
           and len(provinces) == 34 and locked and sup4 and owner_of(sup4) == "sales4"
           and (code or "").startswith("NCC-") and role == "Nhà cung cấp" and in_sup == 1 and in_buy == 0,
           "Form NCC: loại %s, %d tỉnh, khoá tên sales4: %s; lưu -> mã %s, vai %s, người phụ trách %s; "
           "nằm ở danh sách NCC: %d dòng, danh sách khách mua: %d dòng"
           % (types, len(provinces), "có" if locked else "KHÔNG", code, role, owner_of(sup4) if sup4 else "?",
              in_sup, in_buy))

    loc = loc_of(multipart(admin, "/customer", customer_form(kind="supplier", customerType="Nhà phân phối",
                                                             districtId=WARD_HA_NOI, accountOwnerId=sale01)))
    sup_admin = created(loc, "customer")
    expect("TC_CUSADD_020", sup_admin is not None and owner_of(sup_admin) == "sale01",
           "Admin tạo NCC ở Hà Nội, chọn sale01 -> người phụ trách %s" % (owner_of(sup_admin) if sup_admin else loc))

    # --- Người hỗ trợ: không là cấp trên TRỰC TIẾP của người phụ trách chính
    T.run("UPDATE users SET manager_id = %s WHERE user_id = %s" % (sales2, sales4))
    try:
        loc = loc_of(multipart(admin, "/customer", customer_form(districtId=WARD_HA_NOI, supportOwnerId=sales2)))
        shown = follow(admin, loc)
        expect("TC_CUSADD_022", "error=support_is_superior" in loc and
               "Người hỗ trợ không được là cấp trên trực tiếp của người phụ trách chính." in shown,
               "Khách ở Hà Nội (sales4), người hỗ trợ = sales2 (cấp trên của sales4) -> %s" % loc)
        row = T.run("SELECT enterprise_name, tax_code, phone, email FROM enterprises WHERE enterprise_id = %s" % kh1)[0]
        upd = customer_form(action="update", customerId=kh1, districtId=WARD_HA_NOI, supportOwnerId=sales2,
                            customerName=row[0], taxCode=row[1], phone=row[2], email=row[3])
        loc = loc_of(multipart(admin, "/customer", upd, [("roles", "Khách mua")]))
        expect("TC_CUSEDIT_015", "error=support_is_superior" in loc,
               "Sửa KH-0001 (sales4 phụ trách), người hỗ trợ = sales2 -> %s" % loc)
    finally:
        T.run("UPDATE users SET manager_id = NULL WHERE user_id = %s" % sales4)
    manual("TC_CUSADD_021", "Ô Người hỗ trợ lọc bằng JavaScript phía trình duyệt -- kiểm bằng tay")
    manual("TC_CUSADD_023", "Kiểm tra trùng người hỗ trợ chạy bằng JavaScript phía trình duyệt -- kiểm bằng tay")

    # --- Khách ngoài phạm vi: sales2 với KH-0001 (Hà Nội, sales4)
    page = html(s2.get(R.BASE + "/customer?action=list&view=all&keyword=Sông Hồng"))
    raw = str(page)
    expect("TC_CUSLIST_015", "act-lock" in raw and ("action=edit&id=%s&" % kh1) not in raw,
           "sales2 xem Toàn chi nhánh: dòng KH-0001 %s dấu khoá, %s nút Sửa"
           % ("có" if "act-lock" in raw else "KHÔNG có", "vẫn còn" if ("action=edit&id=%s&" % kh1) in raw else "không có"))
    txt = page_text(html(s2.get(R.BASE + "/customer?action=view&id=%s" % kh1)))
    raw = str(html(s2.get(R.BASE + "/customer?action=view&id=%s" % kh1)))
    expect("TC_CUSVIEW_009", "bạn chỉ xem được" in txt and ("action=edit&id=%s" % kh1) not in raw
           and "Đánh giá lại xếp hạng" not in raw.split("evaluateModal")[0],
           "sales2 mở KH-0001: dòng 'Do ... phụ trách, bạn chỉ xem được' %s; nút Sửa %s"
           % ("có" if "bạn chỉ xem được" in txt else "KHÔNG", "không có" if ("action=edit&id=%s" % kh1) not in raw else "CÒN"))
    NOT_YOURS = "Bạn chỉ xem được khách hàng này: khách do người khác phụ trách."
    r = s2.get(R.BASE + "/customer?action=edit&id=%s" % kh1, allow_redirects=False)
    expect("TC_CUSEDIT_010", "error=not_your_customer" in loc_of(r) and NOT_YOURS in follow(s2, loc_of(r)),
           "sales2 mở thẳng link Sửa KH-0001 -> %s" % loc_of(r))
    name_before = T.scalar("SELECT enterprise_name FROM enterprises WHERE enterprise_id = %s" % kh1)
    loc = loc_of(multipart(s2, "/customer", customer_form(action="update", customerId=kh1, customerName="Doi ten len"),
                           [("roles", "Khách mua")]))
    name_after = T.scalar("SELECT enterprise_name FROM enterprises WHERE enterprise_id = %s" % kh1)
    expect("TC_CUSEDIT_011", "error=not_your_customer" in loc and name_after == name_before,
           "sales2 POST update KH-0001 -> %s; tên %s" % (loc, "không đổi" if name_after == name_before else "BỊ ĐỔI"))
    r = R.post(s2, "/customer", {"action": "delete", "id": kh1})
    alive = T.scalar("SELECT is_deleted FROM enterprises WHERE enterprise_id = %s" % kh1)
    expect("TC_CUSDEL_008", "error=not_your_customer" in loc_of(r) and alive == "0",
           "sales2 POST xoá KH-0001 -> %s; khách %s" % (loc_of(r), "vẫn còn" if alive == "0" else "BỊ XOÁ"))
    rating = T.scalar("SELECT current_relationship_rating FROM enterprises WHERE enterprise_id = %s" % kh1)
    r = R.post(s2, "/customer", {"action": "evaluate", "id": kh1, "rating": "BAD"})
    rating2 = T.scalar("SELECT current_relationship_rating FROM enterprises WHERE enterprise_id = %s" % kh1)
    expect("TC_CUSEVAL_009", "error=not_your_customer" in loc_of(r) and rating2 == rating,
           "sales2 POST đánh giá KH-0001 -> %s; xếp hạng %s" % (loc_of(r), "không đổi" if rating2 == rating else "BỊ ĐỔI"))

    # --- sales3 sửa KH-0003 (Bắc Ninh -- tỉnh của sales4 -- nhưng sales3 đứng tên)
    kh3 = T.enterprise_id("KH-0003")
    form = html(s3.get(R.BASE + "/customer?action=edit&id=%s" % kh3))
    names = sorted(o.get_text(strip=True) for o in form.select("#province option") if o.get("value"))
    want = sorted(["Lạng Sơn", "Tuyên Quang", "Hà Tĩnh", "Bắc Ninh"])
    loc = loc_of(multipart(s3, "/customer", customer_form(action="update", customerId=kh3, districtId=WARD_HA_NOI,
                                                          customerName=T.scalar("SELECT enterprise_name FROM enterprises WHERE enterprise_id = %s" % kh3)),
                           [("roles", "Khách mua")]))
    expect("TC_CUSEDIT_012", names == want and "error=province_not_allowed" in loc,
           "sales3 sửa KH-0003: ô tỉnh %s; gửi xã Hà Nội -> %s" % (names, loc))

    # --- Khách của sales3 ở tỉnh chưa ai cầm (Lai Châu): đổi xã giữ nguyên người phụ trách
    loc = loc_of(multipart(admin, "/customer", customer_form(districtId=WARD_LAI_CHAU, accountOwnerId=sales3)))
    lc = created(loc, "customer")
    other_ward = T.scalar("SELECT districts_id FROM districts WHERE province_id = 19 AND districts_id <> 352 LIMIT 1")
    if lc:
        row = T.run("SELECT enterprise_name, tax_code, phone, email FROM enterprises WHERE enterprise_id = %s" % lc)[0]
        loc = loc_of(multipart(s3, "/customer", customer_form(action="update", customerId=lc, customerName=row[0],
                                                              taxCode=row[1], phone=row[2], email=row[3],
                                                              districtId=other_ward, accountOwnerId=sales2),
                               [("roles", "Khách mua")]))
        ward = T.scalar("SELECT a.districts_id FROM enterprises e JOIN addresses a USING(address_id) "
                        "WHERE e.enterprise_id = %s" % lc)
        expect("TC_CUSEDIT_013", "error" not in loc and ward == other_ward and owner_of(lc) == "sales3",
               "sales3 đổi xã trong Lai Châu, gửi accountOwnerId=sales2 -> %s; người phụ trách %s"
               % (loc, owner_of(lc)))
    else:
        record("TC_CUSEDIT_013", "N/A", "Không dựng được khách ở Lai Châu: %s" % loc, "")

    # --- sales4 sửa nhà cung cấp của mình: người phụ trách khoá, tỉnh tự do
    if sup4:
        form = html(s4.get(R.BASE + "/customer?action=edit&id=%s&kind=supplier" % sup4))
        n_prov = len([o for o in form.select("#province option") if o.get("value")])
        row = T.run("SELECT enterprise_name, tax_code, phone, email FROM enterprises WHERE enterprise_id = %s" % sup4)[0]
        loc = loc_of(multipart(s4, "/customer", customer_form(action="update", customerId=sup4, customerName=row[0],
                                                              taxCode=row[1], phone=row[2], email=row[3],
                                                              customerType="Nhà sản xuất",
                                                              districtId=WARD_CA_MAU, accountOwnerId=sales2),
                               [("roles", "Nhà cung cấp")]))
        ward = T.scalar("SELECT a.districts_id FROM enterprises e JOIN addresses a USING(address_id) "
                        "WHERE e.enterprise_id = %s" % sup4)
        expect("TC_CUSEDIT_014", n_prov == 34 and "error" not in loc and ward == WARD_CA_MAU
               and owner_of(sup4) == "sales4",
               "sales4 sửa NCC: ô tỉnh %d tỉnh; đổi sang Cà Mau, gửi accountOwnerId=sales2 -> %s; người phụ trách %s"
               % (n_prov, loc, owner_of(sup4)))

    # --- notfound giữ danh sách Nhà cung cấp; xoá NCC từ danh sách NCC
    r = admin.get(R.BASE + "/customer?action=view&id=999999&kind=supplier", allow_redirects=False)
    shown = follow(admin, loc_of(r))
    expect("TC_CUSLIST_013", "error=notfound" in loc_of(r) and "kind=supplier" in loc_of(r)
           and "Không tìm thấy nhà cung cấp này. Có thể bản ghi đã bị xoá hoặc đường dẫn không đúng." in shown,
           "Mở NCC id=999999 từ danh sách NCC -> %s" % loc_of(r))
    if sup_admin:
        r = R.post(admin, "/customer", {"action": "delete", "id": sup_admin, "kind": "supplier"})
        gone = T.scalar("SELECT is_deleted FROM enterprises WHERE enterprise_id = %s" % sup_admin)
        expect("TC_CUSDEL_006", loc_of(r).endswith("/customer?kind=supplier") and gone == "1",
               "Xoá NCC từ danh sách NCC -> %s; is_deleted=%s" % (loc_of(r), gone))

    # --- CTRADD_017: tạo hợp đồng MUA hỏng thì về đúng form mua (cần một NCC)
    if sup4:
        buy = {"action": "create", "kind": "buy", "contractCode": "MUA/%s" % uniq(), "title": "HD mua loi",
               "contractType": T.scalar("SELECT 'Mua thiết bị'"), "enterpriseId": sup4, "ownerId": sales4}
        form = html(admin.get(R.BASE + "/contract?action=new&kind=buy"))
        types = [o.get("value") for o in form.select("select[name=contractType] option") if o.get("value")]
        if types:
            buy["contractType"] = types[0]
        with T.FailingWrite("contracts", "BEFORE INSERT"):
            r = R.post(admin, "/contract", buy)
        loc = loc_of(r)
        shown = follow(admin, loc)
        expect("TC_CTRADD_017", "action=new" in loc and "kind=buy" in loc and "error=create_failed" in loc
               and "Không lưu được hợp đồng. Vui lòng thử lại." in shown and "-- Chọn nhà cung cấp --" in shown,
               "Tạo hợp đồng mua khi INSERT contracts hỏng -> %s" % loc)

    # --- Các nhánh ghi CSDL hỏng
    with T.FailingWrite("enterprise_roles", "BEFORE INSERT"):
        loc = loc_of(multipart(admin, "/customer", customer_form()))
    rid = created(loc, "customer")
    roles = T.scalar("SELECT COUNT(*) FROM enterprise_roles WHERE enterprise_id = %s" % (rid or 0))
    expect("TC_CUSADD_024", "error=roles_not_saved" in loc and roles == "0"
           and "Đã lưu thông tin nhưng chưa ghi được vai Khách mua / Nhà cung cấp." in follow(admin, loc),
           "Tạo khách khi INSERT enterprise_roles hỏng -> %s; số vai đã ghi: %s" % (loc, roles))

    victim = created(loc_of(multipart(admin, "/customer", customer_form())), "customer")
    if victim:
        row = T.run("SELECT enterprise_name, tax_code, phone, email FROM enterprises WHERE enterprise_id = %s" % victim)[0]
        with T.FailingWrite("enterprise_roles", "BEFORE INSERT"):
            loc = loc_of(multipart(admin, "/customer",
                                   customer_form(action="update", customerId=victim, customerName=row[0] + " sua",
                                                 taxCode=row[1], phone=row[2], email=row[3]),
                                   [("roles", "Khách mua"), ("roles", "Nhà cung cấp")]))
        roles = T.scalar("SELECT GROUP_CONCAT(role) FROM enterprise_roles WHERE enterprise_id = %s" % victim)
        name = T.scalar("SELECT enterprise_name FROM enterprises WHERE enterprise_id = %s" % victim)
        expect("TC_CUSEDIT_016", "error=roles_not_saved" in loc and roles == "Khách mua" and name.endswith(" sua"),
               "Sửa khách, tick thêm vai NCC khi INSERT enterprise_roles hỏng -> %s; vai còn %s; tên đã lưu: %s"
               % (loc, roles, "có" if name.endswith(" sua") else "KHÔNG"))

        rating = T.scalar("SELECT IFNULL(current_relationship_rating,'') FROM enterprises WHERE enterprise_id = %s" % victim)
        n_ev = T.scalar("SELECT COUNT(*) FROM customer_lifecycle_events WHERE enterprise_id = %s" % victim)
        with T.FailingWrite("customer_lifecycle_events", "BEFORE INSERT"):
            r = R.post(admin, "/customer", {"action": "evaluate", "id": victim, "rating": "AT_RISK"})
        shown = follow(admin, loc_of(r))
        rating2 = T.scalar("SELECT IFNULL(current_relationship_rating,'') FROM enterprises WHERE enterprise_id = %s" % victim)
        n_ev2 = T.scalar("SELECT COUNT(*) FROM customer_lifecycle_events WHERE enterprise_id = %s" % victim)
        expect("TC_CUSEVAL_008", "error=evaluate_failed" in loc_of(r) and rating2 == rating and n_ev2 == n_ev
               and "Chưa lưu được đánh giá. Xếp hạng vẫn như trước, vui lòng thử lại." in shown
               and "Đã đánh giá lại" not in shown,
               "Đánh giá khi INSERT lịch sử hỏng -> %s; xếp hạng %r -> %r; lịch sử %s -> %s dòng"
               % (loc_of(r), rating, rating2, n_ev, n_ev2))

        with T.FailingWrite("enterprises", "BEFORE UPDATE", "NEW.is_deleted <> OLD.is_deleted"):
            r = R.post(admin, "/customer", {"action": "delete", "id": victim})
        alive = T.scalar("SELECT is_deleted FROM enterprises WHERE enterprise_id = %s" % victim)
        expect("TC_CUSDEL_007", ("action=view&id=%s" % victim) in loc_of(r) and "error=delete_failed" in loc_of(r)
               and alive == "0" and "Không xoá được khách hàng. Vui lòng thử lại." in follow(admin, loc_of(r)),
               "Xoá khi UPDATE is_deleted hỏng -> %s; khách %s" % (loc_of(r), "vẫn còn" if alive == "0" else "ĐÃ XOÁ"))


# --------------------------------------------------------------------------
# Từ khoá có & và # vẫn giữ nguyên khi phân trang và xuất Excel (PR #166)
# --------------------------------------------------------------------------
KW = "R&D #1"
KW_ENC = "R%26D+%231"


def check_keyword_paging(cid, session, list_path, seed, count_rows, export_path=None, cells_ok=None):
    for _ in range(12):
        seed()
    page = html(session.get(R.BASE + list_path, params={"keyword": KW}))
    links = [a.get("href") for a in page.find_all("a") if "page=2" in (a.get("href") or "")]
    link_ok = bool(links) and all("keyword=" + KW_ENC in l for l in links)
    p2 = html(session.get(R.BASE + links[0].split("/POSCS", 1)[-1])) if links else None
    rows2 = count_rows(p2) if p2 is not None else None
    search = p2.find("input", {"name": "keyword"}) if p2 is not None else None
    kept = search is not None and search.get("value") == KW
    export_ok = True
    detail = ""
    if export_path:
        exp = [a.get("href") for a in page.find_all("a") if "exportExcel" in (a.get("href") or "")]
        export_ok = bool(exp) and ("keyword=" + KW_ENC) in exp[0]
        if export_ok:
            _, rows = U.xls_table(session.get(R.BASE + exp[0].split("/POSCS", 1)[-1]).content)
            export_ok = bool(rows) and all(cells_ok(r) for r in rows)
            detail = "; file Excel %d dòng, đều khớp từ khoá: %s" % (len(rows), "có" if export_ok else "KHÔNG")
    expect(cid, link_ok and rows2 is not None and rows2 > 0 and kept and export_ok,
           "Từ khoá %r: link trang 2 mang keyword=%s: %s; trang 2 có %s dòng khớp, ô tìm kiếm giữ %r%s"
           % (KW, KW_ENC, "có" if link_ok else "KHÔNG", rows2, search.get("value") if search else None, detail),
           "PR #166")


def test_keyword_paging(S):
    print("\n[Từ khoá có & và # khi phân trang]")
    admin, sales, tech = S["admin"], S["sales"], S["tech"]

    def seed_customer():
        multipart(admin, "/customer", customer_form(customerName="%s Cty %s" % (KW, uniq())))

    check_keyword_paging("TC_CUSLIST_014", admin, "/customer",
                         seed_customer,
                         lambda p: len([n for n in (U.column(p, "Khách hàng") or []) if KW in n]),
                         export_path=True, cells_ok=lambda r: any(KW in str(c) for c in r))

    def seed_contract():
        R.post(admin, "/contract", {"action": "create", "kind": "sell", "contractCode": "KW/%s" % uniq(),
                                    "title": "%s hop dong" % KW, "contractType": "Cung cấp thiết bị",
                                    "enterpriseId": "1", "ownerId": T.user_id("sale01")})

    check_keyword_paging("TC_CTRLIST_028", admin, "/contract",
                         seed_contract,
                         lambda p: len([n for n in (U.column(p, "Hợp đồng") or []) if KW in n]),
                         export_path=True, cells_ok=lambda r: any(KW in str(c) for c in r))

    def seed_product():
        U.upload(tech, "/product", {"action": "create", "productName": "%s SP %s" % (KW, uniq()),
                                    "categoryId": "7"}, {})

    check_keyword_paging("TC_PRDLIST_010", tech, "/product",
                         seed_product,
                         lambda p: len([c for c in p.find_all(class_="product-card-name") if KW in c.get_text()]))

    def seed_ticket():
        R.post(sales, "/ticket", {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
                                  "priority": "Cao", "receptionChannel": "Điện thoại",
                                  "assignedTechnicianId": T.user_id("tech01"),
                                  "description": "%s phieu %s" % (KW, uniq())})

    check_keyword_paging("TC_TKLIST_017", sales, "/ticket",
                         seed_ticket, lambda p: U.body_rows(p),
                         export_path=True, cells_ok=lambda r: any(KW in str(c) for c in r))


# --------------------------------------------------------------------------
# Hợp đồng: notfound giữ danh sách Hợp đồng mua (PR #164)
# --------------------------------------------------------------------------
def test_contract_branches(S):
    print("\n[Hợp đồng — notfound giữ kind=buy]")
    s = S["sales"]
    r = s.get(R.BASE + "/contract?action=view&id=999999&kind=buy", allow_redirects=False)
    page = html(s.get(R.BASE + loc_of(r).split("/POSCS", 1)[-1])) if loc_of(r) else None
    txt = page_text(page) if page is not None else ""
    kind = page.find("input", {"type": "hidden", "name": "kind"}) if page is not None else None
    expect("TC_CTRLIST_027", "error=notfound" in loc_of(r) and "kind=buy" in loc_of(r)
           and "Không tìm thấy hợp đồng này. Có thể bản ghi đã bị huỷ hoặc đường dẫn không đúng." in txt
           and kind is not None and kind.get("value") == "buy",
           "Mở hợp đồng id=999999 từ mục Hợp đồng mua -> %s; trang đích là danh sách %s"
           % (loc_of(r), "Hợp đồng mua" if kind is not None and kind.get("value") == "buy" else "KHÁC"))


# --------------------------------------------------------------------------
# Phiếu hỗ trợ: ô lọc Người xử lý (PR #167), xoá hỏng (PR #159)
# --------------------------------------------------------------------------
def kpi(page):
    return [c.get_text(strip=True) for c in page.select(".status-chip .num")]


def db_counts(tech_id=None):
    where = "is_deleted = 0" + (" AND assigned_technician_id = %s" % tech_id if tech_id else "")
    return [T.scalar("SELECT COUNT(*) FROM technicalrequests WHERE %s AND status = '%s'" % (where, st))
            for st in ("Mới tiếp nhận", "Đang xử lý", "Đã đóng")]


def test_ticket_assignee(S):
    print("\n[Phiếu hỗ trợ — ô lọc Người xử lý]")
    sales = S["sales"]
    tech, _ = R.login("tech")
    t1 = T.user_id("tech01")
    t1_name = T.scalar("SELECT CONCAT_WS(' ', last_name, middle_name, first_name) FROM users WHERE user_id = %s" % t1)

    page = html(tech.get(R.BASE + "/ticket"))
    sel = page.find("select", {"name": "assignee"})
    chosen = sel.find("option", selected=True).get("value") if sel and sel.find("option", selected=True) else None
    who = set(U.column(page, "Người xử lý") or [])
    expect("TC_TKLIST_011", chosen == "mine" and who <= {t1_name} and kpi(page)[:3] == db_counts(t1),
           "tech01 mở danh sách: ô Người xử lý = %r; người xử lý trên bảng %s; KPI %s (CSDL %s)"
           % (chosen, sorted(who), kpi(page)[:3], db_counts(t1)))

    page = html(tech.get(R.BASE + "/ticket?action=list&assignee=all"))
    links = [a.get("href") for a in page.find_all("a") if "page=2" in (a.get("href") or "")]
    p2 = html(tech.get(R.BASE + links[0].split("/POSCS", 1)[-1])) if links else None
    sel2 = p2.find("select", {"name": "assignee"}) if p2 is not None else None
    chosen2 = sel2.find("option", selected=True).get("value") if sel2 and sel2.find("option", selected=True) else None
    expect("TC_TKLIST_012", bool(links) and all("assignee=all" in l for l in links) and chosen2 == "all",
           "tech01 chọn Tất cả rồi sang trang 2: link mang assignee=all: %s; trang 2 ô lọc = %r"
           % ("có" if links and all("assignee=all" in l for l in links) else "KHÔNG", chosen2))

    k3 = T.user_id("kythuat3")
    k3_name = T.scalar("SELECT CONCAT_WS(' ', last_name, middle_name, first_name) FROM users WHERE user_id = %s" % k3)
    page = html(sales.get(R.BASE + "/ticket?action=list&assignee=%s" % k3))
    who = set(U.column(page, "Người xử lý") or [])
    expect("TC_TKLIST_013", who == {k3_name} and kpi(page)[:3] == db_counts(k3),
           "Sales lọc kythuat3: người xử lý trên bảng %s; KPI %s (CSDL %s)"
           % (sorted(who), kpi(page)[:3], db_counts(k3)))

    page = html(sales.get(R.BASE + "/ticket"))
    sel = page.find("select", {"name": "assignee"})
    values = [o.get("value") for o in sel.find_all("option")] if sel else []
    chosen = sel.find("option", selected=True).get("value") if sel and sel.find("option", selected=True) else values[:1]
    expect("TC_TKLIST_014", (chosen in ("all", ["all"])) and "mine" not in values and t1 in values,
           "Sales mở danh sách: ô Người xử lý = %r; có mục 'Phiếu của tôi': %s; có tech01: %s"
           % (chosen, "CÓ" if "mine" in values else "không", "có" if t1 in values else "KHÔNG"))

    page = html(sales.get(R.BASE + "/ticket?action=list&assignee=abc"))
    sel = page.find("select", {"name": "assignee"})
    chosen = sel.find("option", selected=True).get("value") if sel and sel.find("option", selected=True) else None
    expect("TC_TKLIST_015", chosen == "all" and kpi(page)[:3] == db_counts(),
           "assignee=abc -> ô lọc %r; KPI %s bằng toàn bộ (CSDL %s)" % (chosen, kpi(page)[:3], db_counts()))

    head, rows = U.xls_table(sales.get(R.BASE + "/ticket?action=exportExcel&assignee=%s" % k3).content)
    col = head.index("Người xử lý") if "Người xử lý" in head else None
    expect("TC_TKLIST_016", col is not None and rows and all(r[col] == k3_name for r in rows)
           and len(rows) == sum(int(x) for x in db_counts(k3)),
           "Xuất Excel theo kythuat3: %d dòng, đều người xử lý %s" % (len(rows), k3_name))

    # Xoá phiếu hỏng ở CSDL
    r = R.post(sales, "/ticket", {"action": "create", "enterpriseId": "1", "ticketType": "Lỗi phần mềm",
                                  "priority": "Cao", "receptionChannel": "Điện thoại",
                                  "assignedTechnicianId": t1, "description": "Xoa hong " + R.RUN})
    tid = created(loc_of(r), "ticket")
    with T.FailingWrite("technicalrequests", "BEFORE UPDATE", "NEW.is_deleted <> OLD.is_deleted"):
        r = R.post(sales, "/ticket", {"action": "delete", "id": tid or "0"})
    alive = T.scalar("SELECT is_deleted FROM technicalrequests WHERE ticket_id = %s" % (tid or 0))
    expect("TC_TKDEL_006", "error=delete_failed" in loc_of(r) and alive == "0"
           and "Không xoá được phiếu hỗ trợ. Vui lòng thử lại." in follow(sales, loc_of(r)),
           "Xoá phiếu khi UPDATE is_deleted hỏng -> %s; phiếu %s" % (loc_of(r), "vẫn còn" if alive == "0" else "ĐÃ XOÁ"))


# --------------------------------------------------------------------------
# Sản phẩm: tệp gỡ / tải lên hỏng (PR #162), xoá hỏng (PR #159)
# --------------------------------------------------------------------------
def test_product_files(S):
    print("\n[Sản phẩm — tệp chưa lưu được, xoá hỏng]")
    st = S["tech"]

    def create(images):
        fields = [("action", (None, "create")), ("productName", (None, "SP tep %s" % uniq())),
                  ("categoryId", (None, "7")), ("csrfToken", (None, R.csrf(st)))]
        fields += [("images", img) for img in images]
        return st.post(R.BASE + "/product", files=fields, allow_redirects=False)

    two = [("a.jpg", U.JPG, "image/jpeg"), ("b.png", U.PNG, "image/png")]
    with T.FailingWrite("productimages", "BEFORE INSERT"):
        r = create(two)
    pid = created(loc_of(r), "product")
    n = T.scalar("SELECT COUNT(*) FROM productimages WHERE product_id = %s" % (pid or 0))
    expect("TC_PRDADD_012", "error=files_not_saved&notRemoved=0&notAdded=2" in loc_of(r) and n == "0"
           and "Đã lưu thông tin sản phẩm, nhưng 2 tệp chưa tải lên được. Bấm Sửa để làm lại phần này."
           in follow(st, loc_of(r)),
           "Tạo sản phẩm kèm 2 ảnh khi INSERT productimages hỏng -> %s; ảnh đã lưu: %s" % (loc_of(r), n))

    r = create(two)
    pid = created(loc_of(r), "product")
    imgs = [x[0] for x in T.run("SELECT image_id FROM productimages WHERE product_id = %s" % (pid or 0))]
    base = {"action": "update", "productId": pid, "productName": "SP tep sua", "categoryId": "7"}
    with T.FailingWrite("productimages", "BEFORE DELETE"):
        r = U.upload(st, "/product", dict(base, removedImageIds=imgs[0]), {})
    still = T.scalar("SELECT COUNT(*) FROM productimages WHERE image_id = %s" % imgs[0])
    expect("TC_PRDEDIT_010", "error=files_not_saved&notRemoved=1&notAdded=0" in loc_of(r) and still == "1"
           and "Đã lưu thông tin sản phẩm, nhưng 1 tệp chưa gỡ được. Bấm Sửa để làm lại phần này."
           in follow(st, loc_of(r)),
           "Gỡ 1 ảnh khi DELETE productimages hỏng -> %s; ảnh %s" % (loc_of(r), "vẫn còn" if still == "1" else "ĐÃ GỠ"))

    fields = [(k, (None, v)) for k, v in dict(base, removedImageIds=imgs[0]).items()]
    fields += [("images", img) for img in two] + [("csrfToken", (None, R.csrf(st)))]
    with T.FailingWrite("productimages", "BEFORE DELETE", name="tt_gia_lap_pi_del"), \
            T.FailingWrite("productimages", "BEFORE INSERT", name="tt_gia_lap_pi_ins"):
        r = st.post(R.BASE + "/product", files=fields, allow_redirects=False)
    name = T.scalar("SELECT product_name FROM products WHERE product_id = %s" % pid)
    expect("TC_PRDEDIT_011", "error=files_not_saved&notRemoved=1&notAdded=2" in loc_of(r) and name == "SP tep sua"
           and "Đã lưu thông tin sản phẩm, nhưng 1 tệp chưa gỡ được và 2 tệp chưa tải lên được. Bấm Sửa để làm lại phần này."
           in follow(st, loc_of(r)),
           "Gỡ 1 + thêm 2 ảnh khi cả DELETE lẫn INSERT hỏng -> %s; thông tin sản phẩm %s"
           % (loc_of(r), "đã lưu" if name == "SP tep sua" else "CHƯA lưu"))

    r = st.get(R.BASE + "/product?action=view&id=%s&error=files_not_saved&notRemoved=0&notAdded=0" % pid,
               allow_redirects=False)
    txt = page_text(html(r))
    expect("TC_PRDEDIT_012", r.status_code == 200 and "tệp chưa" not in txt,
           "Tham số lỗi tệp bằng 0 -> HTTP %s, thông báo lỗi tệp %s" % (r.status_code, "CÓ" if "tệp chưa" in txt else "không có"))

    with T.FailingWrite("products", "BEFORE UPDATE", "NEW.is_deleted <> OLD.is_deleted"):
        r = R.post(st, "/product", {"action": "delete", "id": pid})
    alive = T.scalar("SELECT is_deleted FROM products WHERE product_id = %s" % pid)
    expect("TC_PRDDEL_006", ("action=view&id=%s" % pid) in loc_of(r) and "error=delete_failed" in loc_of(r)
           and alive == "0" and "Không xoá được sản phẩm. Vui lòng thử lại." in follow(st, loc_of(r)),
           "Xoá sản phẩm khi UPDATE is_deleted hỏng -> %s; sản phẩm %s" % (loc_of(r), "vẫn còn" if alive == "0" else "ĐÃ XOÁ"))


# --------------------------------------------------------------------------
# Nhân viên: địa bàn (PR #154), khoá hỏng (PR #163); hồ sơ "Còn thiếu" (PR #165)
# --------------------------------------------------------------------------
def employee_form(**over):
    u = uniq()
    data = {"action": "create", "lastName": "Kiểm", "middleName": "Thử", "firstName": "Nhánh" + u,
            "gender": "Nữ", "dateOfBirth": "1996-06-06", "citizenId": "0017" + u, "phone": "07" + u[-8:],
            "hireDate": "2025-01-01", "roleId": T.scalar("SELECT role_id FROM roles WHERE role_name = 'Sales'"),
            "departmentId": T.scalar("SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'"),
            "personalEmail": "nhanh%s@gmail.com" % u, "districtId": "1", "addressDetail": "So 1"}
    data.update(over)
    return data


def post_employee(s, data, provinces=()):
    body = [(k, v) for k, v in data.items()] + [("provinceIds", p) for p in provinces]
    body.append(("csrfToken", R.csrf(s)))
    return s.post(R.BASE + "/employee", data=body, allow_redirects=False)


def test_employee_branches(S):
    print("\n[Nhân viên — địa bàn, khoá hỏng; hồ sơ còn thiếu]")
    s = S["admin"]
    HA_NOI, LAI_CHAU = "3", "19"                       # province_id

    r = post_employee(s, employee_form(), [HA_NOI])   # Hà Nội đang do sales4 cầm
    n = T.scalar("SELECT COUNT(*) FROM users WHERE personal_email LIKE 'nhanh%s%%'" % R.RUN)
    expect("TC_EMPADD_017", "error=province_taken" in loc_of(r)
           and "Có tỉnh trong ô Địa bàn phụ trách vừa được giao cho người khác. Chọn lại địa bàn rồi tạo lại."
           in follow(s, loc_of(r)) and n == "0",
           "Tạo nhân viên nhận tỉnh Hà Nội (đã có người cầm) -> %s; bản ghi tạo ra: %s" % (loc_of(r), n))

    with T.FailingWrite("user_provinces", "BEFORE INSERT"):
        r = post_employee(s, employee_form(), [LAI_CHAU])
    m = re.search(r"action=edit&id=(\d+)", loc_of(r))
    expect("TC_EMPADD_018", m is not None and "error=province_not_saved" in loc_of(r)
           and "Thông tin nhân viên đã lưu, nhưng địa bàn thì chưa" in follow(s, loc_of(r)),
           "Tạo nhân viên khi INSERT user_provinces hỏng -> %s" % loc_of(r))
    uid = m.group(1) if m else None

    if uid:
        row = T.run("SELECT last_name, IFNULL(middle_name,''), first_name, gender, date_of_birth, citizen_id, phone, "
                    "personal_email, role_id, department_id, hire_date FROM users WHERE user_id = %s" % uid)[0]
        upd = {"action": "update", "userId": uid, "lastName": row[0], "middleName": row[1], "firstName": row[2],
               "gender": row[3], "dateOfBirth": row[4], "citizenId": row[5], "phone": row[6],
               "personalEmail": row[7], "roleId": row[8], "departmentId": row[9], "hireDate": row[10],
               "districtId": "1", "addressDetail": "So 1"}
        r = post_employee(s, dict(upd, phone="06" + uniq()[-8:]), [HA_NOI])
        held = T.scalar("SELECT COUNT(*) FROM user_provinces WHERE user_id = %s" % uid)
        expect("TC_EMPEDIT_009", "error=province_taken" in loc_of(r)
               and "Chưa lưu gì: có tỉnh trong ô Địa bàn phụ trách đang do người khác cầm" in follow(s, loc_of(r))
               and T.scalar("SELECT phone FROM users WHERE user_id = %s" % uid) == row[6] and held == "0",
               "Sửa, nhận thêm Hà Nội (đã có người cầm) -> %s; hồ sơ và địa bàn giữ như cũ" % loc_of(r))

        new_phone = "05" + uniq()[-8:]
        with T.FailingWrite("user_provinces", "BEFORE INSERT"):
            r = post_employee(s, dict(upd, phone=new_phone), [LAI_CHAU])
        saved = T.scalar("SELECT phone FROM users WHERE user_id = %s" % uid)
        held = T.scalar("SELECT COUNT(*) FROM user_provinces WHERE user_id = %s" % uid)
        expect("TC_EMPEDIT_010", "error=province_not_saved" in loc_of(r) and saved == new_phone and held == "0"
               and "Thông tin nhân viên đã lưu, nhưng địa bàn thì chưa" in follow(s, loc_of(r)),
               "Sửa SĐT + nhận Lai Châu khi INSERT user_provinces hỏng -> %s; SĐT mới %s; tỉnh đang cầm: %s"
               % (loc_of(r), "đã lưu" if saved == new_phone else "CHƯA lưu", held))

        with T.FailingWrite("users", "BEFORE UPDATE", "NEW.is_deleted <> OLD.is_deleted"):
            r = R.post(s, "/employee", {"action": "toggleStatus", "id": uid})
        flag = T.scalar("SELECT is_deleted FROM users WHERE user_id = %s" % uid)
        expect("TC_EMPBAN_007", "error=toggle_failed" in loc_of(r) and flag == "0"
               and "Chưa đổi được trạng thái tài khoản. Tài khoản vẫn như trước, vui lòng thử lại." in follow(s, loc_of(r)),
               "Khoá tài khoản khi UPDATE is_deleted hỏng -> %s; tài khoản %s" % (loc_of(r), "vẫn hoạt động" if flag == "0" else "ĐÃ KHOÁ"))

    # --- Hồ sơ còn thiếu (PR #165): tạo nhân viên KHÔNG có CCCD / SĐT / địa chỉ
    r = post_employee(s, employee_form(citizenId="", phone="", districtId="", addressDetail=""))
    m = re.search(r"id=(\d+)", loc_of(r))
    nid = m.group(1) if m else None
    if not nid:
        record("TC_UPDPROF_012", "N/A", "Không tạo được nhân viên thiếu hồ sơ: %s" % loc_of(r), "")
        return
    uname = T.scalar("SELECT username FROM users WHERE user_id = %s" % nid)
    R.post(s, "/employee", {"action": "sendAccount", "id": nid})
    time.sleep(0.3)
    temp = U.temp_password_from_log()
    new_pw = "HoSo@%s" % R.RUN

    def login(pw):
        ss = requests.Session()
        t = R.csrf(ss, "/login.jsp")
        rr = ss.post(R.BASE + "/login", allow_redirects=False,
                     data={"username": uname, "password": pw, "csrfToken": t})
        return ss, loc_of(rr)

    ss, _ = login(temp)
    ss.post(R.BASE + "/changePassword", allow_redirects=False,
            data={"oldPassword": temp, "newPassword": new_pw, "confirmPassword": new_pw,
                  "csrfToken": R.csrf(ss, "/changePassword.jsp")})
    ss, _ = login(new_pw)
    r = ss.get(R.BASE + "/dashboard", allow_redirects=False)
    target = loc_of(r)
    page = html(ss.get(R.BASE + target.split("/POSCS", 1)[-1])) if target else None
    alert = page.find(class_="alert-danger") if page is not None else None
    # get_text(" ") chèn khoảng trắng quanh <strong> -- bỏ khoảng trắng trước dấu câu.
    txt = re.sub(r"\s+([.,])", r"\1", alert.get_text(" ", strip=True)) if alert else ""
    missing = alert.find("strong").get_text(strip=True) if alert and alert.find("strong") else ""
    want = "số CCCD/CMND, số điện thoại, địa chỉ (tỉnh / thành phố và xã / phường)"
    expect("TC_UPDPROF_012", "updateProfile?onboarding=1" in target
           and txt.startswith("Vui lòng bổ sung thông tin cá nhân trước khi tiếp tục sử dụng hệ thống. Còn thiếu:")
           and missing == want,
           "Nhân viên thiếu CCCD / SĐT / địa chỉ mở Dashboard -> %s; dải nhắc: %r" % (target, txt))

    form = {"lastName": "Kiểm", "middleName": "Thử", "firstName": T.scalar("SELECT first_name FROM users WHERE user_id = %s" % nid),
            "gender": "Nữ", "dob": "1996-06-06", "citizenId": "0018" + uniq(), "phone": "09" + uniq()[-8:],   # hồ sơ chỉ nhận số di động
            "personalEmail": T.scalar("SELECT personal_email FROM users WHERE user_id = %s" % nid),
            "districtId": "1", "addressDetail": "So 2 duong Ho So"}
    r = U.upload(ss, "/UpdateProfileServlet", form, {})
    after = ss.get(R.BASE + "/dashboard", allow_redirects=False)
    expect("TC_UPDPROF_013", "error" not in loc_of(r) and after.status_code == 200,
           "Điền đủ CCCD / SĐT / địa chỉ -> %s; mở Dashboard -> HTTP %s" % (loc_of(r), after.status_code))

    old = T.scalar("SELECT personal_email FROM users WHERE username = 'sales3'")
    T.run("UPDATE users SET personal_email = NULL WHERE username = 'sales3'")
    try:
        s3, first = R.login("sales3")
        dash = s3.get(R.BASE + "/dashboard", allow_redirects=False)
    finally:
        T.run("UPDATE users SET personal_email = '%s' WHERE username = 'sales3'" % old)
    expect("TC_UPDPROF_014", "dashboard" in loc_of(first) and dash.status_code == 200,
           "sales3 đủ bốn ô cá nhân nhưng chưa có email cá nhân: đăng nhập -> %s; Dashboard HTTP %s"
           % (loc_of(first), dash.status_code))


def main():
    args = sys.argv[1:]
    if "--log" in args:
        i = args.index("--log")
        U.TOMCAT_LOG = pathlib.Path(args[i + 1])
        del args[i:i + 2]
    if args:
        R.BASE = args[0].rstrip("/")
    print("Muc tieu:", R.BASE, "| log:", U.TOMCAT_LOG, "| CSDL:", T.DB)

    if R.OUT.is_file():
        R.results.update(json.loads(R.OUT.read_text(encoding="utf-8")))
        print("Nap %d ket qua cua cac luot truoc" % len(R.results))

    # Lượt 2 đổi mật khẩu tech02 / doimk01 thật -- nạp lại tài khoản mẫu.
    T.run((pathlib.Path(__file__).with_name("blackbox") / "fixtures.sql").read_text(encoding="utf-8"))

    S = {}
    for role in ("admin", "sales", "tech"):
        S[role], _ = R.login(role)

    test_customer_scope(S)
    test_contract_branches(S)
    test_ticket_assignee(S)
    test_product_files(S)
    test_employee_branches(S)
    test_keyword_paging(S)

    R.OUT.write_text(json.dumps(R.results, ensure_ascii=False, indent=2), encoding="utf-8")
    counts = {}
    for v in R.results.values():
        counts[v["status"]] = counts.get(v["status"], 0) + 1
    print("\nDa ghi: %s" % R.OUT)
    print("Tong cong: %d test case | %s" % (len(R.results), counts))


if __name__ == "__main__":
    main()

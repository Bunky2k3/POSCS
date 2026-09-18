package poscs.common;

import java.util.Collections;
import java.util.List;

/**
 * Phạm vi "phần việc của tôi" trên hai màn hình danh sách (khách hàng và hợp
 * đồng): tập người phụ trách được tính là mình, và tập tỉnh thuộc địa bàn của
 * mình.
 *
 * <p>Hai tập nối với nhau bằng HOẶC, không phải VÀ. Khách của tôi mà nằm ngoài
 * địa bàn tôi giữ thì vẫn là khách của tôi; khách trong địa bàn tôi giữ mà
 * người khác đứng tên thì vẫn là việc tôi cần thấy. Nối bằng VÀ là mất cả hai
 * nhóm đó, và người dùng không có cách nào đoán ra vì sao.
 *
 * <p>Tập tỉnh RỖNG nghĩa là "chưa được giao địa bàn nào" -- khi đó chỉ lọc
 * theo người phụ trách, KHÔNG phải trả về rỗng. Cùng nguyên tắc với cây tổ
 * chức ở {@code AccessControl}: chưa được xếp vào thì chưa bị siết. Bảng
 * {@code user_provinces} hiện đang trống, nên siết theo kiểu ngược lại là mọi
 * nhân viên mở danh sách ra thấy 0 dòng.
 *
 * <p>{@link #all()} là "không thu hẹp gì" -- dùng cho Admin, cho các vai không
 * cầm khách, và cho lúc người dùng tự bấm "xem toàn chi nhánh".
 *
 * <p>Lớp này ôm LUÔN cửa sổ thời gian ({@link #getActiveWindow()}) chứ không để
 * thành tham số riêng, vì {@code ContractDAO.findAll} đã có mười hai tham số và
 * chính nó ghi chú rằng bộ lọc tiếp theo phải gom thành đối tượng. Cả hai chiều
 * đều trả lời cùng một câu -- "mở danh sách ra thì mặc định thấy những gì" -- nên
 * đứng chung là đúng chỗ: của ai, và trong khoảng nào.
 *
 * <p>Danh sách KHÁCH HÀNG không dùng cửa sổ thời gian (luôn null): khách hàng là
 * danh bạ, không phải việc phát sinh theo tháng.
 */
public final class ListScope {

    private static final ListScope ALL =
            new ListScope(Collections.emptyList(), Collections.emptyList(), null);

    private final List<Integer> ownerIds;
    private final List<Integer> provinceIds;
    private final Period activeWindow;

    private ListScope(List<Integer> ownerIds, List<Integer> provinceIds, Period activeWindow) {
        this.ownerIds = ownerIds == null ? Collections.emptyList() : List.copyOf(ownerIds);
        this.provinceIds = provinceIds == null ? Collections.emptyList() : List.copyOf(provinceIds);
        this.activeWindow = activeWindow;
    }

    /** Không thu hẹp gì: mọi bản ghi, mọi thời điểm. */
    public static ListScope all() {
        return ALL;
    }

    /** Chỉ thu hẹp theo thời gian, không theo người -- Admin mở danh sách hợp đồng. */
    public static ListScope activeIn(Period window) {
        return window == null ? ALL : new ListScope(Collections.emptyList(), Collections.emptyList(), window);
    }

    /** Bản sao kèm cửa sổ thời gian. */
    public ListScope withActiveWindow(Period window) {
        return new ListScope(ownerIds, provinceIds, window);
    }

    /**
     * Khoảng thời gian mà hợp đồng phải CÒN HIỆU LỰC ở đó, hay null = mọi thời
     * điểm. Chú ý: đây KHÁC với bộ lọc "kỳ" sẵn có trên màn hình -- bộ đó lọc theo
     * NGÀY KÝ nên bản nháp rơi ra ngoài, còn cái này lọc theo thời hạn nên những gì
     * đang chạy trong tháng đều ở lại.
     */
    public Period getActiveWindow() {
        return activeWindow;
    }

    /**
     * Thu hẹp về {@code ownerIds} (mình và cấp dưới) hoặc {@code provinceIds}
     * (địa bàn được giao). Không có người phụ trách nào thì coi như không thu
     * hẹp -- không biết "tôi" là ai thì lọc theo tôi là vô nghĩa, và một trang
     * trống không giải thích nổi thì tệ hơn một trang rộng.
     */
    public static ListScope of(List<Integer> ownerIds, List<Integer> provinceIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return ALL;
        }
        return new ListScope(ownerIds, provinceIds, null);
    }

    /** Có thu hẹp theo NGƯỜI không (riêng cửa sổ thời gian hỏi bằng getActiveWindow). */
    public boolean isNarrowed() {
        return !ownerIds.isEmpty();
    }

    public List<Integer> getOwnerIds() {
        return ownerIds;
    }

    public List<Integer> getProvinceIds() {
        return provinceIds;
    }

    /**
     * Sinh mệnh đề WHERE cho phạm vi này và đẩy tham số vào {@code params}
     * theo đúng thứ tự dấu hỏi.
     *
     * <p>Dựng ở đây thay vì chép ở từng DAO vì hai DAO đang dùng cùng một kiểu
     * ghép (danh sách điều kiện nối AND, tham số bind bằng {@code setObject}),
     * và chỗ nào lệch số dấu hỏi với số tham số thì chỉ nổ lúc chạy thật.
     *
     * @param ownerColumn    cột người phụ trách, vd {@code "e.account_owner_id"}
     * @param provinceColumn cột tỉnh của bản ghi, vd {@code "d.province_id"};
     *                       null thì bỏ qua vế địa bàn
     * @return mệnh đề đã bọc ngoặc, hoặc null khi không thu hẹp gì
     */
    public String predicate(String ownerColumn, String provinceColumn, List<Object> params) {
        if (!isNarrowed()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("(");
        sb.append(ownerColumn).append(" IN (").append(placeholders(ownerIds.size())).append(')');
        params.addAll(ownerIds);
        if (provinceColumn != null && !provinceIds.isEmpty()) {
            sb.append(" OR ").append(provinceColumn)
              .append(" IN (").append(placeholders(provinceIds.size())).append(')');
            params.addAll(provinceIds);
        }
        return sb.append(')').toString();
    }

    /**
     * Mệnh đề "còn hiệu lực ở bất kỳ ngày nào trong cửa sổ", hay null khi không lọc.
     *
     * <p>Hai cột ngày ĐỀU cho phép NULL và NULL ở đây được coi là KHỚP: hợp đồng
     * chưa chốt thời hạn là bản nháp đang soạn -- đúng là việc của tháng này, giấu
     * nó đi thì người soạn mở danh sách lên không thấy thứ mình vừa tạo.
     */
    public String activeWindowPredicate(String fromColumn, String toColumn, List<Object> params) {
        if (activeWindow == null) {
            return null;
        }
        params.add(activeWindow.getTo());
        params.add(activeWindow.getFrom());
        return "((" + fromColumn + " IS NULL OR " + fromColumn + " <= ?)"
                + " AND (" + toColumn + " IS NULL OR " + toColumn + " >= ?))";
    }

    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        return sb.toString();
    }
}

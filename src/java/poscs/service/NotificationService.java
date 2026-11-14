package poscs.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import poscs.dao.ContractDAO;
import poscs.dao.NotificationDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.Notification;
import poscs.model.TechnicalRequest;
import poscs.validator.ContractValidator;

/**
 * Nghiệp vụ thông báo (UC-14, UC-15; BR-17, BR-20, BR-21):
 * - Chỉ tự sinh đúng 2 loại thông báo: hợp đồng sắp hết hạn (≤ 30 ngày) gửi
 *   người phụ trách và phiếu hỗ trợ sắp/đã quá hạn SLA (≤ 24 giờ) gửi KTV
 *   được phân công. Mỗi cặp (đối tượng, người nhận) tối đa 1 thông báo.
 * - Truy vấn danh sách không tự đánh dấu đã đọc (BR-21); chỉ xem và đánh dấu
 *   thông báo thuộc chính người dùng (BR-20).
 */
public class NotificationService extends BaseService {

    public static final String REF_CONTRACT_EXPIRING = "contract_expiring";
    public static final String REF_TICKET_SLA = "ticket_sla";
    public static final int CONTRACT_EXPIRING_DAYS = ContractValidator.SOON_THRESHOLD_DAYS;
    public static final int TICKET_SLA_HOURS = 24;
    public static final int TITLE_MAX_LENGTH = 150;

    private final NotificationDAO notificationDAO;
    private final ContractDAO contractDAO;
    private final TechnicalSupportTicketDAO ticketDAO;
    private final Clock clock;

    public NotificationService() {
        this(new NotificationDAO(), new ContractDAO(), new TechnicalSupportTicketDAO(), Clock.systemDefaultZone());
    }

    public NotificationService(NotificationDAO notificationDAO, ContractDAO contractDAO,
            TechnicalSupportTicketDAO ticketDAO) {
        this(notificationDAO, contractDAO, ticketDAO, Clock.systemDefaultZone());
    }

    public NotificationService(NotificationDAO notificationDAO, ContractDAO contractDAO,
            TechnicalSupportTicketDAO ticketDAO, Clock clock) {
        this.notificationDAO = Objects.requireNonNull(notificationDAO, "notificationDAO");
        this.contractDAO = Objects.requireNonNull(contractDAO, "contractDAO");
        this.ticketDAO = Objects.requireNonNull(ticketDAO, "ticketDAO");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Chạy cả 2 luồng sinh thông báo tự động theo lịch; trả về tổng số thông báo mới tạo. */
    public synchronized int generateScheduledNotifications() {
        return generateContractExpiringNotifications() + generateTicketSlaNotifications();
    }

    /**
     * Sinh thông báo cho hợp đồng gốc đang chạy sắp hết hạn (BR-17: còn ≤ 30 ngày)
     * gửi cho người phụ trách; idempotent theo (ownerId, contract_expiring, contractId).
     */
    public int generateContractExpiringNotifications() {
        List<Contract> expiringSoon = contractDAO.findExpiringSoon(Integer.MAX_VALUE);
        if (expiringSoon == null || expiringSoon.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now(clock);
        int created = 0;
        for (Contract c : expiringSoon) {
            if (!isEligibleContract(c, today)) {
                continue;
            }
            if (notificationDAO.existsForUserAndRef(c.getOwnerId(), REF_CONTRACT_EXPIRING, c.getContractId())) {
                continue;
            }
            long daysLeft = daysUntil(today, c.getEndDate());
            String title = buildContractTitle(c, daysLeft);
            if (notificationDAO.insert(c.getOwnerId(), title, REF_CONTRACT_EXPIRING, c.getContractId())) {
                created++;
            }
        }
        return created;
    }

    /**
     * Sinh thông báo cho phiếu hỗ trợ chưa đóng sắp/đã quá hạn SLA (còn ≤ 24 giờ)
     * gửi cho kỹ thuật viên được giao; idempotent theo (technicianId, ticket_sla, ticketId).
     */
    public int generateTicketSlaNotifications() {
        List<TechnicalRequest> dueSoon = ticketDAO.findOverdueOrDueSoon();
        if (dueSoon == null || dueSoon.isEmpty()) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).plusHours(TICKET_SLA_HOURS);
        int created = 0;
        for (TechnicalRequest t : dueSoon) {
            if (!isEligibleTicket(t, cutoff)) {
                continue;
            }
            if (notificationDAO.existsForUserAndRef(
                    t.getAssignedTechnicianId(), REF_TICKET_SLA, t.getTicketId())) {
                continue;
            }
            String title = buildTicketTitle(t);
            if (notificationDAO.insert(t.getAssignedTechnicianId(), title, REF_TICKET_SLA, t.getTicketId())) {
                created++;
            }
        }
        return created;
    }

    /** UC-14 / BR-20: Đếm số thông báo chưa đọc của chính người dùng. */
    public int countUnread(int userId) {
        return userId > 0 ? notificationDAO.countUnread(userId) : 0;
    }

    /** UC-14 / BR-20, BR-21: Lấy N thông báo mới nhất (không tự đánh dấu đã đọc). */
    public List<Notification> findRecentByUser(int userId, int limit) {
        if (userId <= 0 || limit <= 0) {
            return Collections.emptyList();
        }
        List<Notification> list = notificationDAO.findRecentByUser(userId, limit);
        return list != null ? list : Collections.emptyList();
    }

    /** UC-14 / BR-20, BR-21: Lấy toàn bộ thông báo của chính người dùng (không tự đánh dấu đã đọc). */
    public List<Notification> findAllByUser(int userId) {
        if (userId <= 0) {
            return Collections.emptyList();
        }
        List<Notification> list = notificationDAO.findAllByUser(userId);
        return list != null ? list : Collections.emptyList();
    }

    /** UC-15: Đánh dấu 1 thông báo đã đọc; bỏ qua nếu không thuộc userId. */
    public boolean markAsRead(int notificationId, int userId) {
        if (notificationId <= 0 || userId <= 0) {
            return false;
        }
        return notificationDAO.markAsRead(notificationId, userId);
    }

    /** UC-15: Đánh dấu toàn bộ thông báo chưa đọc của userId là đã đọc. */
    public int markAllAsRead(int userId) {
        return userId > 0 ? notificationDAO.markAllAsRead(userId) : 0;
    }

    /**
     * UC-15: Mở 1 thông báo thuộc userId và đánh dấu đã đọc; trả về null nếu không
     * tồn tại hoặc thuộc người khác (không đổi dữ liệu).
     */
    public Notification openNotification(int notificationId, int userId) {
        if (notificationId <= 0 || userId <= 0) {
            return null;
        }
        Notification n = notificationDAO.findByIdForUser(notificationId, userId);
        if (n != null) {
            notificationDAO.markAsRead(n.getNotificationId(), userId);
        }
        return n;
    }

    private boolean isEligibleContract(Contract c, LocalDate today) {
        if (c == null || c.getContractId() <= 0 || c.getOwnerId() <= 0) {
            return false;
        }
        if (c.isDeleted() || c.getParentContractId() != null || c.getEndDate() == null) {
            return false;
        }
        long daysLeft = daysUntil(today, c.getEndDate());
        return daysLeft >= 0 && daysLeft <= CONTRACT_EXPIRING_DAYS;
    }

    private boolean isEligibleTicket(TechnicalRequest t, LocalDateTime cutoff) {
        if (t == null || t.getTicketId() <= 0 || t.getAssignedTechnicianId() <= 0) {
            return false;
        }
        if (t.isDeleted() || TechnicalSupportTicketDAO.STATUS_CLOSED.equals(t.getStatus())) {
            return false;
        }
        Timestamp sla = t.getSlaDeadline();
        return sla != null && !sla.toLocalDateTime().isAfter(cutoff);
    }

    private long daysUntil(LocalDate today, Date endDate) {
        return endDate == null ? -1 : ChronoUnit.DAYS.between(today, endDate.toLocalDate());
    }

    private String buildContractTitle(Contract c, long daysLeft) {
        String code = safeCode(c.getContractCode(), "#" + c.getContractId());
        String suffix = daysLeft >= 0 ? " (còn " + daysLeft + " ngày)." : ".";
        return truncate("Hợp đồng " + code + " sắp hết hạn" + suffix);
    }

    private String buildTicketTitle(TechnicalRequest t) {
        String code = safeCode(t.getTicketCode(), "#" + t.getTicketId());
        return truncate("Phiếu " + code + " sắp/đã quá hạn xử lý SLA.");
    }

    private String safeCode(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return raw.trim();
    }

    private String truncate(String text) {
        if (text.length() <= TITLE_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, TITLE_MAX_LENGTH);
    }
}

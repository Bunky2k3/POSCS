# POSCS Task Tracker — Triển khai

> Tách khỏi `task_tracker.md` ngày 19/11/2026 theo yêu cầu người dùng. File này chỉ chứa quyết định và task của việc triển khai lên VPS (Giai đoạn 11), cùng hướng dẫn cập nhật code sau khi đã lên VPS (cuối file).
> - Nguyên tắc làm việc, "Tiêu chí hoàn thành chung" và "Cách ghi một task mới" **dùng chung với `task_tracker.md`**: đọc ở đầu file đó.
> - Số task giữ dạng `11.x` để không trùng với `task_tracker.md`; nhánh, commit và QA report (`docs/qa/Task-11.x.md`) theo đúng số này.
> - Bước `commit 11.x` đánh `[x]` và ghi `*Đã hoàn thành:*` trong **file này**, và add file này thay cho `task_tracker.md`.

## Quyết định đã chốt

1. **Triển khai (Giai đoạn 11, chốt 19/11/2026):** một VPS Linux đặt tại Việt Nam chạy Docker Compose gồm ba container: Tomcat 11/JDK 21 (chạy WAR build bằng Ant như CI), MySQL 8.4 và Caddy (tự cấp HTTPS). Chỉ chạy **một** bản app, vì phiên đăng nhập, bộ đếm đăng nhập sai và `NotificationScheduler` đều nằm trong bộ nhớ. Giữ đường dẫn `/POSCS`; Caddy chuyển `/` sang `/POSCS/`. IP thật của người dùng lấy bằng `RemoteIpValve` cấu hình ở Tomcat, **không sửa code** (giữ quyết định 1). Múi giờ `Asia/Ho_Chi_Minh` cho cả JVM và MySQL. CSDL production khởi đầu **trống + danh mục** (tỉnh/xã, vai trò, phòng ban, quy tắc đánh giá, thông tin chi nhánh, danh mục sản phẩm POSTEF; ảnh sản phẩm là link ngoài nên không chép file) cộng **một tài khoản admin mật khẩu tạm ngẫu nhiên**, bắt đổi ở lần đăng nhập đầu. Không có tài khoản test, không có khách hàng, hợp đồng, phiếu mẫu. Sao lưu CSDL và thư mục tải lên hằng ngày, giữ 14 bản trên VPS. Sau khi Task 11.2 merge, file migration chỉ gieo dữ liệu mẫu hoặc test phải có dòng đầu `-- poscs:demo-only` (production bỏ qua).
2. **Phát hành sau khi lên VPS (người dùng yêu cầu ghi 21/11/2026):** merge vào `main` **không** tự đưa lên VPS; chỉ triển khai khi người dùng đặt tag `vX.Y.Z` (bản đầu `v1.0.0`; tăng số giữa khi có tính năng, số cuối khi chỉ sửa lỗi), ngoài giờ hành chính và báo chi nhánh trước. Sửa lỗi gấp khi `main` đang có việc dở thì đi nhánh `release/vX.Y` tách từ tag đang chạy. Trong một bản phát hành, migration chỉ thêm cột/bảng, không xoá hay đổi tên. Chi tiết ở mục "Hướng dẫn: cập nhật code sau khi đã lên VPS" cuối file.

## Quyết định còn chờ người dùng

1. **Nơi gửi bản sao lưu ra ngoài VPS** (chặn Task 11.6): **người dùng chọn để sau (19/11/2026)**. Lúc đầu chỉ giữ 14 bản trên VPS; người dùng đã được báo mất VPS là mất luôn bản sao. Khi người dùng yêu cầu làm Task 11.6 thì hỏi lại nơi nhận: Google Drive của chi nhánh qua rclone (đề xuất) hoặc một máy ở chi nhánh kéo về qua SSH mỗi ngày. Bản sao chủ yếu là CSDL; thư mục tải lên hiện chỉ còn logo khách hàng, ảnh và catalogue PDF sản phẩm, ảnh đại diện (tài liệu hợp đồng chỉ lưu link).
2. **VPS và tên miền** (chặn Task 11.7): nhà cung cấp VPS, tên miền, ai đứng tên và trả tiền sau khi bàn giao, ai giữ các mật khẩu trong `deploy/.env`.

---

## Giai đoạn 11: Triển khai lên VPS bằng Docker Compose

> Yêu cầu người dùng 19/11/2026 (chọn phương án "VPS + Docker"), quyết định ở mục 1 "Quyết định đã chốt" đầu file này. **Độc lập với Giai đoạn 8–10**: không sửa `src/`, `web/`, `db/schema.sql`, nên làm xen kẽ được; riêng Task 11.7 (lên VPS thật) nên làm sau khi các giai đoạn đang dở đã merge. Thứ tự 11.1 → 11.7. Máy dev **chưa cài Docker** (kiểm 19/11/2026): không tự cài; mọi kiểm thử Docker chạy trong job CI `docker` (Task 11.1), ảnh runner của GitHub Actions có sẵn Docker.

- [ ] **Task 11.1:** Đóng gói POSCS bằng Docker Compose (Tomcat 11, MySQL 8.4, Caddy), kèm job CI dựng thử.
  - UC: không có.
  - BR / MSG / NFR: `BR-01` (khoá theo IP phải là IP thật của người dùng, không phải IP của Caddy), `NFR-03`, `NFR-09`, `NFR-12` (HTTPS), `NFR-17` (trang Nhật ký hệ thống vẫn đọc được log), `NFR-18` (tự khởi động lại), `NFR-20` (cookie phiên).
  - File sửa (đều là file mới, trừ `.gitignore` và `tests.yml`):
    - `Dockerfile` (gốc repo), hai tầng:
      - Tầng build: JDK 21 (Temurin) + Ant, chạy đúng lệnh `ant ... dist` như bước test trong `.github/workflows/tests.yml` (hai `-D` trỏ vào `lib-build/`), không chạy test. Ra `dist/POSCS.war`.
      - Tầng chạy: image `tomcat` 11 bản **jdk21**, không lấy bản jre (Task 11.3 cần `java` chạy file nguồn). Chép WAR vào `webapps/POSCS.war` (giữ đường dẫn `/POSCS`), chép `deploy/tomcat/server.xml`. Đặt `TZ=Asia/Ho_Chi_Minh` và thêm `-Duser.timezone=Asia/Ho_Chi_Minh -Dfile.encoding=UTF-8` vào `CATALINA_OPTS`.
      - Log: log của app ra stdout (`slf4j-simple`), còn trang Nhật ký hệ thống (`LogFiles`) chỉ đọc file trong `${catalina.base}/logs`. Ghi stdout và stderr của Tomcat đồng thời vào `logs/catalina.out` và ra `docker logs`. Tiến trình java phải nhận được tín hiệu tắt: `docker stop` xong thì log có dòng Tomcat dừng service êm, không bị kill sau 10 giây.
      - Ghim tag cụ thể cho mọi image (tra Docker Hub lúc làm), không dùng `latest`.
    - `.dockerignore`: bỏ `.git/`, `build/`, `dist/`, `docs/`, `tools/`, `nbproject/private/`, `deploy/.env`, file log. Giữ đủ những gì `ant dist` cần; bỏ thêm thư mục nào thì build lại cho chắc.
    - `deploy/tomcat/server.xml`: chép nguyên `conf/server.xml` của đúng tag Tomcat đã ghim, chỉ thêm `RemoteIpValve` trong `<Engine>` (`remoteIpHeader="x-forwarded-for"`, `protocolHeader="x-forwarded-proto"`). Kiểm trong tài liệu Tomcat 11 xem `internalProxies` mặc định đã gồm dải mạng Docker (172.16.0.0/12) chưa; chưa thì khai rõ. Có valve này thì `request.isSecure()` đúng khi đứng sau Caddy và Tomcat tự gắn cờ `Secure` cho cookie phiên. Comment tiếng Việt, không có `--` trong comment XML.
    - `deploy/Caddyfile`: tên miền lấy từ biến `POSCS_DOMAIN`; `reverse_proxy tomcat:8080`; chỉ đúng đường dẫn `/` chuyển hướng sang `/POSCS/`. Không khai `trusted_proxies`, để Caddy không tin `X-Forwarded-For` do trình duyệt tự gửi (người dùng không giả được IP).
    - `deploy/mysql/init/01-app-user.sh`: chạy một lần khi MySQL khởi tạo thư mục dữ liệu trống: tạo CSDL `poscs_db` (utf8mb4, `utf8mb4_unicode_ci`) và tài khoản app `${DB_USER}` chỉ có `SELECT, INSERT, UPDATE, DELETE` trên `poscs_db.*` (như `DEPLOY.md` mục 3). Trước khi chốt quyền, tìm trong `src/java/poscs/dao/` các lệnh cần thêm quyền (`LOCK TABLES`, `CREATE TEMPORARY TABLE`, `TRUNCATE`...); có thì cấp đúng quyền đó và ghi lý do trong report.
    - `deploy/compose.yaml`: ba service `mysql`, `tomcat`, `caddy`, đều `restart: unless-stopped`.
      - `mysql`: tag 8.4 ghim; `--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci --default-time-zone=+07:00`; gắn `deploy/mysql/init/`; healthcheck; **không** mở cổng ra máy chủ.
      - `tomcat`: build từ `Dockerfile` ở gốc; biến `DB_URL` (host `mysql`, kèm `allowPublicKeyRetrieval=true&useSSL=false` như CI), `DB_USER`, `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `UPLOAD_DIR=/data/uploads`; chờ `mysql` healthy; **không** mở cổng 8080 ra máy chủ.
      - `caddy`: mở 80 và 443.
      - Dữ liệu gắn thư mục thật trên máy chủ dưới `${POSCS_DATA_DIR}` (mặc định `/srv/poscs`): `mysql/`, `uploads/`, `logs/` (log Tomcat), `caddy/`. Không dùng volume có tên, để Task 11.4 sao lưu và xoay log trực tiếp.
    - `deploy/.env.example`: đủ các biến trên cùng `MYSQL_ROOT_PASSWORD`, `POSCS_DOMAIN`, `POSCS_DATA_DIR`; giá trị giữ chỗ, chú thích tiếng Việt; không có mật khẩu thật.
    - `deploy/smoke.sh <url gốc>`: kiểm nhanh sau mỗi lần dựng: `/POSCS/login.jsp` trả 200; `/` chuyển sang `/POSCS/`; `Set-Cookie` của phiên có `Secure` và `HttpOnly`; giờ trong container `tomcat` là +07. Khi có biến `SMOKE_USER`, `SMOKE_PASSWORD` (đọc từ biến môi trường, không nhận qua tham số dòng lệnh) thì thử đăng nhập: lấy `csrfToken` từ trang login rồi POST, in kèm đích chuyển hướng (302 vừa là thành công vừa là thất bại, đừng in mã trần). Dùng lại trên VPS ở Task 11.7.
    - `.gitignore`: thêm `/deploy/.env`.
    - `.github/workflows/tests.yml`: thêm job `docker` độc lập: sinh `.env` tạm (mật khẩu ngẫu nhiên, `POSCS_DOMAIN=localhost`, `POSCS_DATA_DIR` là thư mục tạm của runner); `docker compose build`, `up -d`; nạp `db/schema.sql` vào `poscs_db` bằng root (CSDL dùng một lần, chỉ trong CI; production làm theo Task 11.3); chạy `deploy/smoke.sh https://localhost` kèm đăng nhập bằng một tài khoản có sẵn trong phần dữ liệu mẫu của `schema.sql` (`admin` / `Admin@123` chỉ có sau Task 10.6 của `task_tracker.md`; làm trước thì dùng `sales2` / `Poscs@123`) (dùng `curl -k` vì Caddy tự ký chứng chỉ cho localhost); job đỏ thì in `docker compose logs`.
  - File test: job CI `docker` ở trên là phần kiểm thử; không thêm test Java/JS.
  - Không đụng: `src/`, `web/` (kể cả `web/WEB-INF/web.xml`: không bật `<secure>` cứng vì máy dev chạy http; `web/META-INF/context.xml` giữ nguyên), `nbproject/`, `build.xml`, `db/`, `DEPLOY.md` (Task 11.5), job `test` và `js` đang có.
  - Lưu ý: máy dev chưa có Docker, không tự cài; kết quả lấy từ job CI trên PR. Không sửa cách lấy IP trong code (quyết định 1 của `task_tracker.md`); IP thật do `RemoteIpValve` lo, và chỉ an toàn khi cổng 8080 không mở ra ngoài. Không dùng `db/schema.sql` cho production. Cần sửa file ngoài danh sách trên mới chạy được thì dừng hỏi.
  - Xong khi: job CI `docker` xanh: build được image; ba container chạy; `smoke.sh` qua đủ các ý (trang login 200, `/` chuyển hướng, cookie `Secure` + `HttpOnly`, giờ +07, đăng nhập tài khoản mẫu vào được dashboard); `docker compose ps` chỉ mở 80 và 443 ra máy chủ; `ant test` vẫn pass.
  - Nhánh: `chore/task-11.1-docker-compose`.
  - Commit: `chore(deploy): đóng gói bằng Docker Compose với Tomcat, MySQL, Caddy (Task 11.1)`
- [ ] **Task 11.2:** Script áp migration có ghi sổ cho production, và quy ước đánh dấu file chỉ gieo dữ liệu mẫu.
  - UC: không có.
  - BR / MSG / NFR: không có.
  - File sửa:
    - `deploy/db/migrate.sh` (mới): chạy trên máy chủ, gọi `mysql` trong container bằng root (tài khoản app không có quyền đổi cấu trúc):
      - Dùng bảng `schema_migrations` đã có (`version` = tên file bỏ đuôi `.sql`). Chỉ nhận file khớp `V<số>__<mô tả>__<tác giả>.sql`; bỏ qua file mẫu `V{version}__{change_description}__{github_username}.sql` đang nằm trong thư mục.
      - Sắp theo **số** (V10 sau V9, không theo thứ tự chữ). Bỏ file đã có trong `schema_migrations`. Bỏ file có dòng đầu `-- poscs:demo-only`: in tên file đã bỏ, không ghi sổ.
      - Áp từng file với `--default-character-set=utf8mb4`. Mỗi file tự `INSERT INTO schema_migrations` ở đầu (quy ước trong `db/migrations/README.md`), nên: áp xong mà thiếu dòng sổ thì script ghi thêm; **lỗi giữa chừng thì xoá dòng sổ của file đó**, dừng ngay, không áp file sau, in tên file và lỗi. Không tự sửa SQL.
      - Tham số: `--status` (liệt kê file sẽ áp, không chạy gì); `--baseline` (ghi mọi file hợp lệ hiện có vào sổ mà không chạy; dùng cho CSDL vừa dựng từ `schema.sql`, vì `schema.sql` tạo bảng sổ nhưng không có dòng nào); `--dir <thư mục>` (mặc định `db/migrations`, để CI thử bằng file giả).
    - Các file migration cũ **chỉ** gieo dữ liệu mẫu hoặc test: thêm dòng đầu `-- poscs:demo-only`. Đọc nội dung từng file để xác định; ứng viên theo tên: V9, V12, V15, V30, V33, V41 (tài khoản test `tonghop1`; Task 9.2 hẹn đánh dấu khi task này làm). File gieo lại dữ liệu mẫu của Task 10.6 (`task_tracker.md`) mang sẵn dòng này. File vừa đổi cấu trúc vừa gieo dữ liệu mẫu thì **không** đánh dấu, liệt kê trong report. Chỉ thêm một dòng comment, không đổi SQL.
    - `db/migrations/README.md`: thêm mục "Production": (1) file chỉ gieo dữ liệu mẫu hoặc test phải có dòng đầu `-- poscs:demo-only` và không trộn với thay đổi cấu trúc; (2) production áp bằng `deploy/db/migrate.sh`, luôn cập nhật CSDL trước rồi mới đưa code mới lên; (3) trong một bản phát hành, migration chỉ thêm cột hoặc bảng, cần xoá hay đổi tên thì tách hai bản (quyết định 2).
    - `.github/workflows/tests.yml`, job `docker`: sau khi nạp `schema.sql`, chạy `migrate.sh --baseline` thì `--status` phải rỗng. Rồi dùng `--dir` trỏ thư mục tạm chứa một file giả tạo bảng `ci_probe` và một file giả có `-- poscs:demo-only`: bảng `ci_probe` có, file demo bị bỏ qua, chạy lần hai không áp lại. Thêm một file giả lỗi cú pháp: script thoát mã khác 0 và sổ không có dòng của file đó.
  - File test: các bước CI trên.
  - Không đụng: `db/schema.sql`, phần SQL của các file migration, `src/`, `web/`.
  - Lưu ý: MySQL tự commit lệnh DDL, nên file lỗi giữa chừng có thể đã đổi một phần cấu trúc; script chỉ dừng và báo, việc chữa làm tay (Task 11.5 ghi: sao lưu trước khi migrate). Giai đoạn 9, 10 nếu viết migration gieo lại dữ liệu mẫu sau khi task này merge thì phải mang dòng đánh dấu.
  - Xong khi: job CI `docker` xanh với các bước mới; `--status` trên CSDL vừa baseline không còn file nào.
  - Nhánh: `chore/task-11.2-migrate-script`.
  - Commit: `chore(deploy): script áp migration có ghi sổ, bỏ qua file dữ liệu mẫu (Task 11.2)`
- [ ] **Task 11.3:** Khởi tạo CSDL production: trống + danh mục, một tài khoản admin mật khẩu tạm.
  - UC: không có.
  - BR / MSG / NFR: `NFR-11` (băm mật khẩu), `NFR-09`; `BR-03` (admin có email cá nhân để dùng Quên mật khẩu).
  - File sửa:
    - `deploy/db/reference-tables.txt` (mới): các bảng giữ dữ liệu gieo sẵn, mỗi dòng một bảng kèm lý do: `roles`, `departments`, `provinces`, `districts`, `productcategories`, `products`, `productimages`, `productcatalogues`, `customer_evaluation_rules`, `branch_info`. Đối chiếu **mọi** câu `INSERT` trong `db/schema.sql`; bảng nào không chắc là danh mục hay dữ liệu mẫu thì dừng hỏi. Bảng không có trong danh sách thì production để trống (kể cả `users`, `addresses`, `enterprises`, `enterprise_roles`, `user_provinces`, `contracts` và các bảng con, phiếu hỗ trợ).
    - `deploy/db/init-prod.sh` (mới): chạy trên máy chủ sau `docker compose up -d mysql`:
      - **Từ chối chạy** nếu `poscs_db` đã có bảng nào (không bao giờ ghi đè CSDL đang có dữ liệu).
      - Nạp `db/schema.sql` vào CSDL tạm `poscs_seed_tmp`; dựng `poscs_db` từ `mysqldump --no-data` của CSDL tạm cộng `mysqldump --no-create-info` đúng các bảng trong `reference-tables.txt`; xoá CSDL tạm. Dựng lại từ `schema.sql` mỗi lần nên cấu trúc luôn khớp, chỉ phải giữ danh sách bảng.
      - Chạy `deploy/db/migrate.sh --baseline`.
      - Tạo tài khoản `admin`: vai Admin và phòng của Ban giám đốc (tra theo tên trong dữ liệu vừa nạp, không thấy thì dừng), họ tên giữ chỗ để Admin tự sửa, `hire_date` là hôm nay, `personal_email` lấy từ tham số bắt buộc, `must_change_password = 1`.
      - Mật khẩu tạm sinh ngẫu nhiên, ít nhất 16 ký tự; in ra màn hình **một lần**, không ghi file, không ghi log, không truyền qua tham số dòng lệnh (lộ trong `ps`). Băm bằng jBCrypt như `AuthService` và `EmployeeService` (`BCrypt.hashpw(..., BCrypt.gensalt())`, tiền tố `$2a$`), ví dụ chạy file nguồn `deploy/db/HashPassword.java` bằng `java -cp lib/jbcrypt-0.4.jar` trong container `tomcat` (bản jdk), đọc mật khẩu từ stdin. Không dùng `htpasswd -B`: nó ra tiền tố `$2y$`, jBCrypt 0.4 không đọc được.
      - In tóm tắt: số dòng từng bảng danh mục, `users` = 1.
    - `db/migrations/README.md`: thêm vào mục "Production" (Task 11.2): bảng **danh mục** mới mà production cũng cần dữ liệu gieo sẵn thì thêm tên vào `deploy/db/reference-tables.txt`; bảng mới mặc định production để trống.
    - `.github/workflows/tests.yml`, job `docker`: thay bước nạp `schema.sql` bằng `init-prod.sh` (lấy mật khẩu tạm từ đầu ra của script). Kiểm: `users` đúng 1 dòng, `must_change_password = 1`; `enterprises`, `contracts`, `technicalrequests`, `user_provinces` = 0; `products`, `provinces`, `roles` > 0; `migrate.sh --status` rỗng; đăng nhập bằng mật khẩu tạm bị chuyển sang trang đổi mật khẩu (đúng đường dẫn trong `AuthenticationFilter`); chạy `init-prod.sh` lần hai thì thoát mã khác 0 và dữ liệu không đổi. Các bước của Task 11.1, 11.2 vẫn qua (bước đăng nhập của `smoke.sh` đổi sang kiểm chuyển hướng đổi mật khẩu).
  - File test: các bước CI trên. `HashPassword.java` nằm ngoài `src/`, kiểm gián tiếp qua bước đăng nhập.
  - Không đụng: `db/schema.sql`, `src/`, `web/`; `poscs_db` trên máy dev (không bao giờ chạy script này ở đó).
  - Lưu ý: script chạy bằng bash trên Linux; không dùng `>` hay `|` của PowerShell cho file dump (làm hỏng tiếng Việt). Nếu làm sau Giai đoạn 9 (đổi vai trò, phòng ban) thì đọc lại dữ liệu gieo của `roles`, `departments` trước khi viết phần tra tên.
  - Xong khi: job CI `docker` xanh với các kiểm tra trên.
  - Nhánh: `chore/task-11.3-init-prod-db`.
  - Commit: `chore(deploy): khởi tạo CSDL production trống kèm danh mục và admin mật khẩu tạm (Task 11.3)`
- [ ] **Task 11.4:** Sao lưu hằng ngày, khôi phục thử và xoay log trên VPS.
  - UC: không có.
  - BR / MSG / NFR: `NFR-17`, `NFR-18`.
  - File sửa:
    - `deploy/backup.sh` (mới): `mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF --default-character-set=utf8mb4 poscs_db` trong container `mysql`, nén gzip; nén thư mục `uploads/`. Tên file theo ngày giờ +07, lưu vào `BACKUP_DIR` (mặc định `/var/backups/poscs`), chỉ chủ sở hữu đọc được. Giữ 14 bản mới nhất mỗi loại. Lỗi thì thoát mã khác 0 và không xoá bản cũ khi bản mới hỏng.
    - `deploy/restore.sh` (mới): khôi phục một bản CSDL, tuỳ chọn cả `uploads/`. Mặc định khôi phục vào CSDL mới `poscs_restore_check` để thử; ghi đè `poscs_db` chỉ khi truyền rõ `--into poscs_db --yes`, và trước đó tự chạy `backup.sh`.
    - `deploy/cron/poscs` (mới, mẫu cài vào `/etc/cron.d/`): `backup.sh` lúc 02:00 hằng ngày; xoá log Tomcat theo ngày cũ hơn 30 ngày.
    - `deploy/logrotate/poscs` (mới): xoay `logs/catalina.out` hằng ngày, giữ 14 bản, `copytruncate` (Tomcat vẫn đang ghi).
    - `.github/workflows/tests.yml`, job `docker`: chạy `backup.sh` thì có file CSDL và file `uploads`; `restore.sh` vào `poscs_restore_check` thì số dòng `products` khớp `poscs_db`; tạo giả 16 bản cũ, chạy `backup.sh` xong còn 14.
  - File test: các bước CI trên.
  - Không đụng: gửi bản sao ra ngoài VPS (Task 11.6, đang chờ quyết định); `src/`, `web/`, `db/`.
  - Lưu ý: bản sao chứa dữ liệu khách hàng thật: không đưa vào git, không gửi đi đâu trong task này. Dump chạy trong container rồi ghi ra máy chủ bằng bash, không qua PowerShell.
  - Xong khi: job CI `docker` xanh với các bước trên.
  - Nhánh: `chore/task-11.4-backup-restore`.
  - Commit: `chore(deploy): sao lưu hằng ngày, khôi phục thử và xoay log (Task 11.4)`
- [ ] **Task 11.5:** Viết lại `DEPLOY.md` theo cách triển khai Docker Compose, kèm sổ tay vận hành.
  - UC: không có.
  - BR / MSG / NFR: `NFR-12`, `NFR-18`.
  - File sửa:
    - `DEPLOY.md` (viết tiếng Việt cho người vận hành):
      - Chuẩn bị VPS: Ubuntu LTS, 2 vCPU / 4 GB, đặt tại Việt Nam; tài khoản sudo riêng, SSH bằng khoá, tắt đăng nhập bằng mật khẩu và bằng root; `ufw` chỉ mở 22, 80, 443; bật cập nhật bảo mật tự động; cài Docker Engine và compose plugin theo trang chính thức của Docker.
      - Lần đầu: trỏ tên miền về VPS; `git clone`; tạo `deploy/.env` từ `.env.example` (quyền 600); `docker compose up -d mysql`; `init-prod.sh`; `docker compose up -d`; `smoke.sh`; đăng nhập admin, đổi mật khẩu, khai "Thông tin chi nhánh"; gửi thử một OTP (có nhà cung cấp chặn cổng SMTP 587 với tài khoản mới); cài cron và logrotate của Task 11.4; khôi phục thử một lần.
      - Cập nhật phiên bản: `git fetch --tags` rồi `git checkout <tag>` → `backup.sh` → `migrate.sh --status` rồi `migrate.sh` → `docker compose up -d --build` → `smoke.sh`. Luôn **CSDL trước, code sau** (code mới chạy trên CSDL cũ thì thao tác lưu hỏng). Quay lui: checkout tag trước rồi build lại; CSDL đã migrate thì giữ (cột thừa không làm hỏng code cũ), chỉ dùng `restore.sh` khi migration làm hỏng dữ liệu.
      - Phát hành phiên bản: tóm tắt mục "Hướng dẫn: cập nhật code sau khi đã lên VPS" cuối `task_tracker_deploy.md` (đặt tag, nhánh `release/vX.Y` khi sửa gấp, luật migration chỉ thêm, báo chi nhánh trước giờ cập nhật).
      - Sự cố thường gặp: đăng nhập báo sai cho **mọi** tài khoản → sai `DB_PASSWORD` hoặc không kết nối được CSDL (DAO nuốt lỗi); cả chi nhánh cùng bị khoá đăng nhập → `RemoteIpValve` không nhận IP thật; mail OTP không tới → xem log `Loi gui email otp`, kiểm cổng 587; giờ, ngày lệch → kiểm `TZ`.
      - Bỏ câu cũ nói `DB_PASSWORD` mặc định `1234` (đã bỏ từ Task 7.2a). Giữ nội dung mục Email (Gmail SMTP), đổi chỗ đặt biến sang `deploy/.env`.
      - Cách cài tay không dùng Docker (Tomcat + MySQL cài thẳng) rút gọn thành phụ lục cuối.
    - `README.md`: chỉ sửa câu trỏ tới `DEPLOY.md` nếu đang tả cách cũ.
    - `docs/HuongDan_DoiEmailGui.md`: gộp phần mục Email của `DEPLOY.md` còn thiếu (lấy mật khẩu ứng dụng Gmail, khởi động lại và kiểm tra sau khi đổi, đổi người gửi khi chạy bằng Docker) vào mục Email, rồi xoá file bằng `git rm` (người dùng đồng ý 21/11/2026, mục 17 "Quyết định đã chốt" của `task_tracker.md`).
  - File test: không có; chạy `ant test`.
  - Không đụng: các file trong `deploy/` (chỉ tả đúng cách dùng; thấy script sai thì ghi vào report, không sửa), `src/`, `web/`.
  - Lưu ý: đọc các script đã merge của Task 11.1 → 11.4, ghi đúng tên tham số, đường dẫn, tên biến. Không ghi mật khẩu, tên miền hay IP thật vào tài liệu. `docs/HuongDan_DoiEmailGui.md` vào git ở Task 10.8 của `task_tracker.md`: làm Task 11.5 sau khi Task 10.8 merge.
  - Xong khi: mỗi lệnh trong `DEPLOY.md` khớp file trong `deploy/` (QA đối chiếu từng lệnh); không còn câu nói `DB_PASSWORD` mặc định `1234`; `git grep -n "HuongDan_DoiEmailGui" -- . ':!task_tracker.md' ':!task_tracker_deploy.md'` ra rỗng.
  - Nhánh: `docs/task-11.5-deploy-guide`.
  - Commit: `docs(deploy): hướng dẫn triển khai bằng Docker Compose và vận hành (Task 11.5)`
- [ ] **Task 11.6:** Gửi bản sao lưu ra ngoài VPS. **Tạm gác** (mục 1 "Quyết định còn chờ người dùng", người dùng chọn để sau): không tự làm, chỉ làm khi người dùng yêu cầu và đã chốt nơi nhận. Task 11.7 không phải chờ task này.
  - UC: không có.
  - BR / MSG / NFR: không có.
  - File sửa: `deploy/backup.sh` (bước đẩy bản sao sau khi sao lưu xong, bật bằng biến trong `deploy/.env`; không cấu hình thì bỏ qua và ghi log); `deploy/.env.example`; `DEPLOY.md` (cách thiết lập nơi nhận). Chi tiết ghi thêm khi người dùng chốt nơi nhận.
  - File test: job CI `docker`: không cấu hình nơi nhận thì `backup.sh` vẫn thành công như Task 11.4.
  - Không đụng: `src/`, `web/`, `db/`.
  - Lưu ý: không tự tạo tài khoản, khoá API hay thư mục ở dịch vụ bên ngoài; người dùng tự làm, task chỉ viết hướng dẫn. Đẩy lỗi thì bản sao trên VPS vẫn giữ và script thoát mã khác 0.
  - Xong khi: ghi khi chốt nơi nhận.
  - Nhánh: `chore/task-11.6-offsite-backup`.
  - Commit: `chore(deploy): gửi bản sao lưu ra ngoài VPS (Task 11.6)`
- [ ] **Task 11.7:** Lên VPS thật và nghiệm thu. **Bị chặn bởi mục 2 "Quyết định còn chờ người dùng"**; làm cùng người dùng.
  - UC: không có.
  - BR / MSG / NFR: `BR-01`, `BR-03`, `NFR-12`, `NFR-18`, `NFR-20`.
  - File sửa: chỉ `DEPLOY.md` và file trong `deploy/` khi thực tế khác tài liệu.
  - File test: không có.
  - Không đụng: `src/`, `web/`, `db/`.
  - Lưu ý: người dùng tự thuê VPS, mua và trỏ tên miền, trả tiền, giữ mật khẩu; agent không tạo tài khoản ở nhà cung cấp, không thanh toán, không nhập mật khẩu. Không chép CSDL hay mật khẩu từ máy dev lên; `deploy/.env` thật không đưa vào git.
  - Xong khi (kiểm trên VPS):
    - HTTPS hợp lệ; `/` chuyển sang `/POSCS/`; `smoke.sh` qua.
    - Access log của Tomcat ghi IP thật của hai máy khác nhau, không phải IP của Caddy; nhập sai 5 lần từ máy A không khoá máy B (BR-01).
    - Giờ trong container `tomcat` và `mysql` là +07; giờ tạo của bản ghi vừa lưu hiện đúng giờ Việt Nam.
    - Gửi được một OTP thật tới email cá nhân của admin.
    - Tải logo khách hàng lên, `docker compose restart` xong logo vẫn còn.
    - Cron sao lưu chạy qua một đêm có file; `restore.sh` vào CSDL thử thành công.
    - Quét từ bên ngoài chỉ thấy cổng 22, 80, 443.
  - Nhánh: `docs/task-11.7-go-live` (chỉ khi phải sửa tài liệu hoặc script).
  - Commit: `docs(deploy): chỉnh hướng dẫn theo lần triển khai thật (Task 11.7)`

---

## Hướng dẫn: cập nhật code sau khi đã lên VPS

> Ghi theo yêu cầu người dùng 21/11/2026 (quyết định 2 đầu file này). Đây là quy trình làm việc, không phải task. Các lệnh trên VPS dùng script của Task 11.1 → 11.4; trước khi các task đó merge thì đây là kế hoạch, tên lệnh chính xác lấy theo `DEPLOY.md` (Task 11.5). **Agent không tự đặt tag, không tự triển khai**: người dùng quyết định lúc nào phát hành. Bản lên VPS lần đầu (Task 11.7) là `v1.0.0`.

### 1. Tính năng mới hoặc sửa lỗi thường

1. Ghi task vào `task_tracker.md`, chạy `code X.X` → `qa X.X` → `fix X.X` → `commit X.X` như hiện nay.
2. PR vào `main`, CI xanh thì merge. **VPS chưa đổi gì**; gom bao nhiêu task rồi mới phát hành cũng được.
3. Đủ để phát hành thì kiểm trên Tomcat máy dev (chạy thật, không chỉ test). Có đổi giao diện thì hướng dẫn sử dụng phải theo kịp.
4. Đặt tag trên `main`: tăng số giữa khi có tính năng (`v1.1.0`), tăng số cuối khi chỉ sửa lỗi (`v1.1.1`). Trên GitHub: Releases → "Draft a new release" → tạo tag mới → "Generate release notes" (ra danh sách PR, dùng để báo chi nhánh bản mới có gì). Hoặc bằng lệnh:
   ```
   git tag -a v1.1.0 -m "Mô tả ngắn"
   git push origin v1.1.0
   ```
5. Báo chi nhánh giờ cập nhật, chọn ngoài giờ hành chính: ai đang đăng nhập sẽ bị đăng xuất (phiên nằm trong bộ nhớ Tomcat) và app ngắt khoảng nửa phút.
6. Triển khai trên VPS theo mục 4.

### 2. Sửa lỗi gấp

- `main` chỉ có việc đã xong: sửa như mục 1, đặt tag tăng số cuối.
- `main` đang có việc làm dở (ví dụ nửa giai đoạn): sửa trên nhánh của bản đang chạy, để không kéo phần dở lên VPS:
  1. Lần đầu cho dòng phiên bản đó, tạo nhánh từ tag đang chạy: `git switch -c release/v1.1 v1.1.0`, rồi `git push -u origin release/v1.1`.
  2. Nhánh sửa tách từ `release/v1.1`, PR vào `release/v1.1` (CI vẫn chạy với PR này), CI xanh thì merge.
  3. Đặt tag `v1.1.1` trên `release/v1.1`, triển khai theo mục 4.
  4. Đưa bản sửa về `main`: PR `release/v1.1` → `main` (hoặc cherry-pick đúng commit sửa), để bản phát hành sau không mất bản sửa.

### 3. Khi có thay đổi CSDL

- File `V…` mới và phần sửa `db/schema.sql` nằm trong cùng một PR (quy ước ở `db/migrations/README.md`).
- Trong một bản phát hành, migration chỉ **thêm** cột hoặc bảng. Cần xoá hay đổi tên thì tách hai bản: bản trước thêm cái mới và cho code dùng cái mới; bản sau, khi chắc không phải quay lui, mới xoá cái cũ. Nhờ vậy quay lui về tag cũ không phải đụng CSDL (code cũ chạy được với cột thừa, không chạy được khi thiếu cột).
- File chỉ gieo dữ liệu mẫu hoặc test: dòng đầu `-- poscs:demo-only`, production bỏ qua.
- Bảng danh mục mới mà production cũng cần dữ liệu: gieo trong migration thường (không đánh dấu demo-only), và thêm tên bảng vào `deploy/db/reference-tables.txt` cho những lần khởi tạo sau.

### 4. Triển khai trên VPS (làm tay qua SSH)

```
cd <thư mục repo trên VPS>
git fetch --tags
git checkout v1.1.0
deploy/backup.sh
deploy/db/migrate.sh --status
deploy/db/migrate.sh
docker compose -f deploy/compose.yaml up -d --build
deploy/smoke.sh https://<tên miền>
```

- Luôn **sao lưu trước, CSDL trước, code sau**: code mới chạy trên CSDL cũ thì thao tác lưu hỏng.
- `migrate.sh --status` liệt kê file sẽ áp; thấy file lạ hoặc file demo thì dừng lại xem trước khi chạy.
- `smoke.sh` không qua thì xem `docker compose -f deploy/compose.yaml logs tomcat`, rồi quyết định sửa nhanh hay quay lui (mục 5).
- Nếu sau này làm CD, workflow chỉ tự động hoá đúng các bước này khi đặt tag; CD chưa nằm trong Giai đoạn 11.

### 5. Quay lui khi bản mới lỗi

1. `git checkout <tag trước>`, chạy lại `docker compose -f deploy/compose.yaml up -d --build` và `smoke.sh`. Không đụng CSDL (nhờ luật "chỉ thêm" ở mục 3).
2. Chỉ khi migration làm hỏng dữ liệu mới khôi phục bản chụp ngay trước lần triển khai bằng `deploy/restore.sh … --into poscs_db --yes`. Mọi thứ nhập sau thời điểm chụp sẽ mất, nên báo chi nhánh trước.

### 6. Tái hiện lỗi chi nhánh báo

- Thử trước trên `poscs_db` ở máy dev, hoặc CSDL `_it`.
- Không chép CSDL production về máy dev cho tiện: trong đó là khách hàng và hợp đồng thật. Bắt buộc phải chép thì nạp vào một CSDL tạm, xong việc xoá cả CSDL lẫn file dump.

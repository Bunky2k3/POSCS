# -*- coding: utf-8 -*-
"""Chạm thẳng vào CSDL kiểm thử cho những ca giao diện không dựng được.

Dùng cho hai việc: tra id theo mã / username (dữ liệu demo gieo theo username
nên id đổi giữa các lần dựng CSDL), và dựng trạng thái mà bấm trên giao diện
không tạo ra được -- trigger giả lập lỗi ghi (README, mục Ca cần giả lập lỗi
CSDL), cấp trên trực tiếp (chưa có UI nhập manager_id).

Gọi mysql.exe qua subprocess thay vì thêm thư viện: máy dev không có sẵn
pymysql, còn mysql CLI thì ai dựng CSDL test cũng đã có.

Cấu hình qua biến môi trường:
  POSCS_TEST_DB   tên CSDL kiểm thử (mặc định poscs_bbtest)
  MYSQL_EXE       đường dẫn mysql (mặc định bản MySQL 9.3 trên máy dev, rồi "mysql")
  DB_USER / DB_PASSWORD   (mặc định root / 1234)
"""

import os
import pathlib
import subprocess

DB = os.environ.get("POSCS_TEST_DB", "poscs_bbtest")
_DEFAULT_EXE = r"C:\Program Files\MySQL\MySQL Server 9.3\bin\mysql.exe"
EXE = os.environ.get("MYSQL_EXE") or (_DEFAULT_EXE if pathlib.Path(_DEFAULT_EXE).exists() else "mysql")
USER = os.environ.get("DB_USER", "root")
PASSWORD = os.environ.get("DB_PASSWORD", "1234")

# poscs_db là dữ liệu dev thật của người dùng: các hàm dưới đây tạo trigger,
# sửa cấp trên, xoá dữ liệu -- tuyệt đối không được chạy vào đó.
if DB == "poscs_db":
    raise SystemExit("testdb: từ chối chạy trên poscs_db -- đặt POSCS_TEST_DB sang CSDL kiểm thử")


def run(sql):
    """Chạy một (hoặc nhiều) câu SQL, trả về các dòng kết quả dạng list[list[str]]."""
    proc = subprocess.run(
        [EXE, "-h127.0.0.1", "-u" + USER, "-p" + PASSWORD, "-N", "-B",
         "--default-character-set=utf8mb4", DB],
        input=sql.encode("utf-8"), capture_output=True)
    err = proc.stderr.decode("utf-8", "replace")
    err = "\n".join(l for l in err.splitlines() if "Using a password" not in l).strip()
    if proc.returncode != 0:
        raise RuntimeError("SQL lỗi: %s\n%s" % (err, sql))
    out = proc.stdout.decode("utf-8", "replace")
    return [line.split("\t") for line in out.splitlines() if line]


def scalar(sql):
    rows = run(sql)
    return rows[0][0] if rows else None


def user_id(username):
    return scalar("SELECT user_id FROM users WHERE username = '%s'" % username)


def enterprise_id(code):
    return scalar("SELECT enterprise_id FROM enterprises WHERE enterprise_code = '%s'" % code)


class FailingWrite:
    """Trigger SIGNAL chặn một câu ghi trong khối `with`, luôn tự xoá khi ra.

    `when` là điều kiện trong thân trigger (vd "NEW.is_deleted <> OLD.is_deleted");
    None = chặn mọi dòng. Quên xoá trigger thì mọi ca sau trên cùng bảng hỏng
    oan, nên xoá cả lúc bắt đầu (lượt trước bị ngắt giữa chừng) lẫn lúc kết thúc.
    """

    def __init__(self, table, event, when=None, name=None):
        self.name = name or "tt_gia_lap_%s_%s" % (table, event.split()[-1].lower())
        cond = "IF %s THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gia lap loi'; END IF;" % when \
            if when else "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gia lap loi';"
        # mysql CLI cắt câu ở dấu ';' đầu tiên trong thân trigger -- phải đổi
        # DELIMITER như khi nạp bằng tay.
        self.create = ("DELIMITER //\nCREATE TRIGGER %s %s ON %s FOR EACH ROW BEGIN %s END//\nDELIMITER ;\n"
                       % (self.name, event, table, cond))

    def __enter__(self):
        run("DROP TRIGGER IF EXISTS %s" % self.name)
        run(self.create)
        return self

    def __exit__(self, *exc):
        run("DROP TRIGGER IF EXISTS %s" % self.name)
        return False

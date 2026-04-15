INSERT INTO sys_user (username, password, nickname, phone, email, status)
VALUES ('admin', '123456', '商城管理员', '13800000000', 'admin@example.com', 1)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    nickname = VALUES(nickname),
    phone = VALUES(phone),
    email = VALUES(email),
    status = VALUES(status);


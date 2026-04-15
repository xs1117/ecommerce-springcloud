CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    nickname VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    avatar_url LONGTEXT NULL,
    points INT NOT NULL DEFAULT 0,
    member_level VARCHAR(32) NOT NULL DEFAULT 'BRONZE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS avatar_url LONGTEXT NULL;

ALTER TABLE sys_user
    MODIFY COLUMN avatar_url LONGTEXT NULL;

CREATE TABLE IF NOT EXISTS sys_points_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    change_points INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    after_points INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_points_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS coupon_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    points_cost INT NOT NULL,
    threshold DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_coupon (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    threshold DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    order_no VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon_code (code),
    KEY idx_user_coupon_user_id (user_id),
    KEY idx_user_coupon_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mall_notice_status_sort (status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO coupon_template (name, points_cost, threshold, discount_amount, status, description)
SELECT '满100减10券', 100, 100, 10, 1, '积分兑换后，订单满100元可减10元'
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = '满100减10券');

INSERT INTO coupon_template (name, points_cost, threshold, discount_amount, status, description)
SELECT '满200减25券', 220, 200, 25, 1, '积分兑换后，订单满200元可减25元'
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = '满200减25券');

INSERT INTO coupon_template (name, points_cost, threshold, discount_amount, status, description)
SELECT '满300减45券', 360, 300, 45, 1, '积分兑换后，订单满300元可减45元'
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = '满300减45券');

INSERT INTO mall_notice (title, content, sort_no, status)
SELECT '会员日', '全场最高满500减80，部分店铺叠加店铺券', 100, 1
WHERE NOT EXISTS (SELECT 1 FROM mall_notice WHERE title = '会员日');

INSERT INTO mall_notice (title, content, sort_no, status)
SELECT '积分商城上线', '可使用积分兑换优惠券，结算时选择可用券即可抵扣', 90, 1
WHERE NOT EXISTS (SELECT 1 FROM mall_notice WHERE title = '积分商城上线');


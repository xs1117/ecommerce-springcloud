CREATE TABLE IF NOT EXISTS merchant_application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    applicant_user_id BIGINT NOT NULL,
    applicant_username VARCHAR(64) NOT NULL,
    shop_name VARCHAR(128) NOT NULL,
    business_scope VARCHAR(255) NULL,
    contact_phone VARCHAR(32) NULL,
    status VARCHAR(32) NOT NULL,
    review_comment VARCHAR(255) NULL,
    reviewer_user_id BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_merchant_application_user (applicant_user_id),
    KEY idx_merchant_application_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS merchant_store (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    store_name VARCHAR(128) NOT NULL,
    store_intro VARCHAR(255) NULL,
    store_image_url LONGTEXT NULL,
    main_category VARCHAR(64) NULL,
    tags VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_store_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS merchant_product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(255) NULL,
    image_url LONGTEXT NULL,
    category VARCHAR(64) NULL,
    tags VARCHAR(255) NULL,
    price DECIMAL(12,2) NOT NULL,
    stock INT NOT NULL,
    sales_count INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    off_shelf_reason VARCHAR(32) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_merchant_product_store (store_id),
    KEY idx_merchant_product_status_sales (status, sales_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS merchant_product_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(64) NULL,
    content VARCHAR(600) NOT NULL,
    image_urls LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product_comment_product (product_id),
    KEY idx_product_comment_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @has_store_image_url := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_store'
      AND COLUMN_NAME = 'store_image_url'
);
SET @add_store_image_url_sql := IF(
    @has_store_image_url = 0,
    'ALTER TABLE merchant_store ADD COLUMN store_image_url LONGTEXT NULL',
    'SELECT 1'
);
PREPARE add_store_image_url_stmt FROM @add_store_image_url_sql;
EXECUTE add_store_image_url_stmt;
DEALLOCATE PREPARE add_store_image_url_stmt;

ALTER TABLE merchant_store
    MODIFY COLUMN store_image_url LONGTEXT NULL;

SET @has_product_image_url := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_product'
      AND COLUMN_NAME = 'image_url'
);
SET @add_product_image_url_sql := IF(
    @has_product_image_url = 0,
    'ALTER TABLE merchant_product ADD COLUMN image_url LONGTEXT NULL',
    'SELECT 1'
);
PREPARE add_product_image_url_stmt FROM @add_product_image_url_sql;
EXECUTE add_product_image_url_stmt;
DEALLOCATE PREPARE add_product_image_url_stmt;

ALTER TABLE merchant_product
    MODIFY COLUMN image_url LONGTEXT NULL;

SET @has_store_main_category := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_store'
      AND COLUMN_NAME = 'main_category'
);
SET @add_store_main_category_sql := IF(
    @has_store_main_category = 0,
    'ALTER TABLE merchant_store ADD COLUMN main_category VARCHAR(64) NULL',
    'SELECT 1'
);
PREPARE add_store_main_category_stmt FROM @add_store_main_category_sql;
EXECUTE add_store_main_category_stmt;
DEALLOCATE PREPARE add_store_main_category_stmt;

SET @has_store_tags := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_store'
      AND COLUMN_NAME = 'tags'
);
SET @add_store_tags_sql := IF(
    @has_store_tags = 0,
    'ALTER TABLE merchant_store ADD COLUMN tags VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE add_store_tags_stmt FROM @add_store_tags_sql;
EXECUTE add_store_tags_stmt;
DEALLOCATE PREPARE add_store_tags_stmt;

SET @has_product_category := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_product'
      AND COLUMN_NAME = 'category'
);
SET @add_product_category_sql := IF(
    @has_product_category = 0,
    'ALTER TABLE merchant_product ADD COLUMN category VARCHAR(64) NULL',
    'SELECT 1'
);
PREPARE add_product_category_stmt FROM @add_product_category_sql;
EXECUTE add_product_category_stmt;
DEALLOCATE PREPARE add_product_category_stmt;

SET @has_product_tags := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_product'
      AND COLUMN_NAME = 'tags'
);
SET @add_product_tags_sql := IF(
    @has_product_tags = 0,
    'ALTER TABLE merchant_product ADD COLUMN tags VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE add_product_tags_stmt FROM @add_product_tags_sql;
EXECUTE add_product_tags_stmt;
DEALLOCATE PREPARE add_product_tags_stmt;

SET @has_product_off_shelf_reason := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_product'
      AND COLUMN_NAME = 'off_shelf_reason'
);
SET @add_product_off_shelf_reason_sql := IF(
    @has_product_off_shelf_reason = 0,
    'ALTER TABLE merchant_product ADD COLUMN off_shelf_reason VARCHAR(32) NULL AFTER status',
    'SELECT 1'
);
PREPARE add_product_off_shelf_reason_stmt FROM @add_product_off_shelf_reason_sql;
EXECUTE add_product_off_shelf_reason_stmt;
DEALLOCATE PREPARE add_product_off_shelf_reason_stmt;


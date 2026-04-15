CREATE TABLE IF NOT EXISTS order_info_0 (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    pay_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_user_status(user_id, status),
    KEY idx_created_at(created_at)
);

CREATE TABLE IF NOT EXISTS order_info_1 LIKE order_info_0;

CREATE TABLE IF NOT EXISTS order_item_0 (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    store_id BIGINT NULL,
    store_name VARCHAR(128) NULL,
    product_image_url LONGTEXT NULL,
    product_description VARCHAR(255) NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    KEY idx_order_no(order_no)
);

CREATE TABLE IF NOT EXISTS order_item_1 LIKE order_item_0;

SET @has_item0_store_id := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item_0'
      AND COLUMN_NAME = 'store_id'
);
SET @add_item0_store_id_sql := IF(
    @has_item0_store_id = 0,
    'ALTER TABLE order_item_0 ADD COLUMN store_id BIGINT NULL',
    'SELECT 1'
);
PREPARE add_item0_store_id_stmt FROM @add_item0_store_id_sql;
EXECUTE add_item0_store_id_stmt;
DEALLOCATE PREPARE add_item0_store_id_stmt;

SET @has_item0_store_name := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item_0'
      AND COLUMN_NAME = 'store_name'
);
SET @add_item0_store_name_sql := IF(
    @has_item0_store_name = 0,
    'ALTER TABLE order_item_0 ADD COLUMN store_name VARCHAR(128) NULL',
    'SELECT 1'
);
PREPARE add_item0_store_name_stmt FROM @add_item0_store_name_sql;
EXECUTE add_item0_store_name_stmt;
DEALLOCATE PREPARE add_item0_store_name_stmt;

SET @has_item0_product_image_url := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item_0'
      AND COLUMN_NAME = 'product_image_url'
);
SET @add_item0_product_image_url_sql := IF(
    @has_item0_product_image_url = 0,
    'ALTER TABLE order_item_0 ADD COLUMN product_image_url LONGTEXT NULL',
    'SELECT 1'
);
PREPARE add_item0_product_image_url_stmt FROM @add_item0_product_image_url_sql;
EXECUTE add_item0_product_image_url_stmt;
DEALLOCATE PREPARE add_item0_product_image_url_stmt;

SET @has_item0_product_description := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item_0'
      AND COLUMN_NAME = 'product_description'
);
SET @add_item0_product_description_sql := IF(
    @has_item0_product_description = 0,
    'ALTER TABLE order_item_0 ADD COLUMN product_description VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE add_item0_product_description_stmt FROM @add_item0_product_description_sql;
EXECUTE add_item0_product_description_stmt;
DEALLOCATE PREPARE add_item0_product_description_stmt;

SET @has_item1_store_id := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item_1'
      AND COLUMN_NAME = 'store_id'
);
SET @add_item1_store_id_sql := IF(
    @has_item1_store_id = 0,
    'ALTER TABLE order_item_1 ADD COLUMN store_id BIGINT NULL',
    'SELECT 1'
);
PREPARE add_item1_store_id_stmt FROM @add_item1_store_id_sql;
EXECUTE add_item1_store_id_stmt;
DEALLOCATE PREPARE add_item1_store_id_stmt;

SET @has_item1_store_name := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item_1'
      AND COLUMN_NAME = 'store_name'
);
SET @add_item1_store_name_sql := IF(
    @has_item1_store_name = 0,
    'ALTER TABLE order_item_1 ADD COLUMN store_name VARCHAR(128) NULL',
    'SELECT 1'
);
PREPARE add_item1_store_name_stmt FROM @add_item1_store_name_sql;
EXECUTE add_item1_store_name_stmt;
DEALLOCATE PREPARE add_item1_store_name_stmt;

SET @has_item1_product_image_url := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item_1'
      AND COLUMN_NAME = 'product_image_url'
);
SET @add_item1_product_image_url_sql := IF(
    @has_item1_product_image_url = 0,
    'ALTER TABLE order_item_1 ADD COLUMN product_image_url LONGTEXT NULL',
    'SELECT 1'
);
PREPARE add_item1_product_image_url_stmt FROM @add_item1_product_image_url_sql;
EXECUTE add_item1_product_image_url_stmt;
DEALLOCATE PREPARE add_item1_product_image_url_stmt;

SET @has_item1_product_description := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_item_1'
      AND COLUMN_NAME = 'product_description'
);
SET @add_item1_product_description_sql := IF(
    @has_item1_product_description = 0,
    'ALTER TABLE order_item_1 ADD COLUMN product_description VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE add_item1_product_description_stmt FROM @add_item1_product_description_sql;
EXECUTE add_item1_product_description_stmt;
DEALLOCATE PREPARE add_item1_product_description_stmt;


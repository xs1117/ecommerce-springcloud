CREATE TABLE IF NOT EXISTS cart_item (
    user_id BIGINT NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    item_json TEXT NOT NULL,
    PRIMARY KEY (user_id, item_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_item_user_created ON cart_item (user_id, created_at);

CREATE TABLE IF NOT EXISTS cart_behavior (
    user_id BIGINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    event_json TEXT NOT NULL,
    PRIMARY KEY (user_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_behavior_user_created ON cart_behavior (user_id, created_at);


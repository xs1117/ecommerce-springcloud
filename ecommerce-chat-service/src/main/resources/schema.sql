create table if not exists chat_conversation (
    id bigint primary key auto_increment,
    conversation_key varchar(128) not null,
    store_id bigint not null,
    store_title varchar(200) not null,
    store_image_url varchar(512) null,
    buyer_user_id bigint not null,
    buyer_username varchar(100) not null,
    buyer_nickname varchar(100) not null,
    merchant_user_id bigint not null,
    merchant_username varchar(100) not null,
    merchant_nickname varchar(100) not null,
    biz_type varchar(32) not null default 'SHOP_SUPPORT',
    order_no varchar(64) null,
    after_sale_status varchar(32) null,
    admin_joined bit not null default b'0',
    admin_user_id bigint null,
    admin_nickname varchar(100) null,
    source_type varchar(32) not null,
    product_id bigint null,
    product_title varchar(200) null,
    product_image_url varchar(512) null,
    product_description text null,
    product_price decimal(18,2) null,
    last_message_type varchar(32) not null,
    last_message_text varchar(500) not null,
    last_message_at datetime(3) not null,
    buyer_unread_count int not null default 0,
    merchant_unread_count int not null default 0,
    admin_unread_count int not null default 0,
    status varchar(32) not null,
    version bigint not null default 0,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_chat_conversation_key (conversation_key),
    key idx_chat_conversation_buyer (buyer_user_id, last_message_at),
    key idx_chat_conversation_merchant (merchant_user_id, last_message_at),
    key idx_chat_conversation_admin (admin_user_id, last_message_at),
    key idx_chat_conversation_biz (biz_type, after_sale_status, last_message_at),
    key idx_chat_conversation_store (store_id, last_message_at)
) engine=InnoDB default charset=utf8mb4;

alter table chat_conversation add column if not exists biz_type varchar(32) not null default 'SHOP_SUPPORT';
alter table chat_conversation add column if not exists order_no varchar(64) null;
alter table chat_conversation add column if not exists after_sale_status varchar(32) null;
alter table chat_conversation add column if not exists admin_joined bit not null default b'0';
alter table chat_conversation add column if not exists admin_user_id bigint null;
alter table chat_conversation add column if not exists admin_nickname varchar(100) null;
alter table chat_conversation add column if not exists admin_unread_count int not null default 0;
alter table chat_conversation drop index uk_chat_conversation_pair;
create index idx_chat_conversation_admin on chat_conversation (admin_user_id, last_message_at);
create index idx_chat_conversation_biz on chat_conversation (biz_type, after_sale_status, last_message_at);

create table if not exists chat_message (
    id bigint primary key auto_increment,
    conversation_id bigint not null,
    sender_user_id bigint not null,
    sender_role varchar(32) not null,
    sender_nickname varchar(100) not null,
    client_msg_id varchar(128) not null,
    message_type varchar(32) not null,
    content text null,
    payload_json text null,
    created_at datetime(3) not null,
    unique key uk_chat_message_client (conversation_id, client_msg_id),
    key idx_chat_message_conversation_created (conversation_id, created_at),
    constraint fk_chat_message_conversation foreign key (conversation_id) references chat_conversation(id) on delete cascade
) engine=InnoDB default charset=utf8mb4;


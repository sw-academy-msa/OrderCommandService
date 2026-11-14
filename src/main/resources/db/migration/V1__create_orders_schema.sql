-- 주문 서비스의 최초 업무 테이블입니다. 약관 테이블은 V2에서 만듭니다. 엔티티 enum 값은 애플리케이션이 검증하므로 DB CHECK로 고정하지 않습니다.

create table orders.inquiry_images (
    cleaned boolean not null,
    discarded boolean not null,
    verified boolean not null,
    expires_at timestamp(6) with time zone not null,
    size_bytes bigint not null,
    version bigint not null,
    buyer_id uuid not null,
    id uuid not null,
    inquiry_id uuid,
    order_id uuid not null,
    object_key varchar(1000) not null,
    upload_key varchar(1000) not null,
    content_type varchar(255) not null,
    primary key (id)
);

create table orders.order_history (
    occurred_at timestamp(6) with time zone not null,
    actor_id uuid,
    id uuid not null,
    order_id uuid not null,
    action varchar(100) not null,
    primary key (id)
);

create table orders.order_inquiries (
    created_at timestamp(6) with time zone,
    version bigint not null,
    buyer_id uuid not null,
    id uuid not null,
    order_id uuid not null,
    seller_id uuid not null,
    request_hash varchar(64) not null,
    category varchar(100) not null,
    primary key (id)
);

create table orders.order_inquiry_messages (
    message_position integer not null check ((message_position>=0)),
    seller boolean not null,
    created_at timestamp(6) with time zone,
    inquiry_id uuid not null,
    request_key uuid not null,
    body varchar(1000) not null,
    primary key (message_position, inquiry_id)
);

create table orders.order_items (
    item_position integer not null check ((item_position>=0)),
    quantity integer not null,
    option_id uuid not null,
    order_id uuid not null,
    label varchar(500),
    primary key (item_position, order_id)
);

create table orders.outbox_events (
    attempts integer not null,
    created_at timestamp(6) with time zone not null,
    next_attempt_at timestamp(6) with time zone not null,
    published_at timestamp(6) with time zone,
    id uuid not null,
    partition_key varchar(255) not null,
    payload text not null,
    topic varchar(255) not null,
    primary key (id)
);

create table orders.payment_attempts (
    approval_requested boolean not null,
    compensation_requested boolean not null,
    amount bigint not null,
    approved_at timestamp(6) with time zone,
    command_version bigint not null,
    next_check_at timestamp(6) with time zone,
    result_version bigint not null,
    id uuid not null,
    order_id uuid not null unique,
    result_hash varchar(64),
    status varchar(255) not null,
    primary key (id)
);

create table orders.payment_result_receipts (
    processed_at timestamp(6) with time zone not null,
    id uuid not null,
    payload_hash varchar(64) not null,
    primary key (id)
);

create table orders.purchase_orders (
    attempts integer not null,
    dispatch_business_days integer,
    confirmed_at timestamp(6) with time zone,
    consented_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    delivered_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone,
    lease_until timestamp(6) with time zone,
    next_attempt_at timestamp(6) with time zone,
    payment_approved_at timestamp(6) with time zone,
    projection_version bigint not null,
    shipped_at timestamp(6) with time zone,
    shipping_fee bigint,
    total_amount bigint,
    unit_price bigint,
    updated_at timestamp(6) with time zone,
    version bigint not null,
    buyer_id uuid not null,
    id uuid not null,
    lease_token uuid,
    payment_attempt_id uuid unique,
    product_id uuid not null,
    request_key uuid not null,
    reservation_id uuid,
    seller_id uuid,
    postal_code varchar(20),
    recipient_phone varchar(30),
    payment_key_hash varchar(64),
    request_hash varchar(64) not null,
    carrier varchar(100),
    last_failure_code varchar(100),
    recipient_name varchar(100),
    terms_version varchar(100),
    tracking_number varchar(100),
    brand_or_store varchar(200),
    product_name varchar(200),
    detail_address varchar(500),
    road_address varchar(500),
    delivery_memo varchar(1000),
    payment_key varchar(1000),
    delivery varchar(255),
    state varchar(255) not null,
    primary key (id),
    unique (buyer_id, request_key)
);

create table orders.withdrawal_fence (
    canceled boolean not null,
    account_id uuid not null,
    id uuid not null,
    primary key (id)
);

create table orders.withdrawal_gate (
    blocked boolean not null,
    account_id uuid not null,
    operation_id uuid,
    primary key (account_id)
);

create index idx_inquiry_image_inquiry
   on orders.inquiry_images (inquiry_id);

create index idx_inquiry_image_order
   on orders.inquiry_images (order_id);

create index idx_inquiry_image_cleanup
   on orders.inquiry_images (cleaned, expires_at);

create index IDX75ivayhd7wc33mbr2c8b2n0fg
   on orders.order_history (order_id, occurred_at);

create index idx_outbox_pending
   on orders.outbox_events (published_at, next_attempt_at, created_at);

create index idx_order_saga_due
   on orders.purchase_orders (next_attempt_at, lease_until);

create index idx_order_seller
   on orders.purchase_orders (seller_id);

alter table if exists orders.order_inquiry_messages
   add constraint FK680antkufil4tsh75059tpc70
   foreign key (inquiry_id)
   references orders.order_inquiries;

alter table if exists orders.order_items
   add constraint FK4m3smlmiapqd1brpm0ud1ip9t
   foreign key (order_id)
   references orders.purchase_orders;

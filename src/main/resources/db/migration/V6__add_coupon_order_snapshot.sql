-- 과거 주문은 재계산하지 않고 새 주문의 쿠폰 조건과 할인만 보존합니다.
alter table orders.purchase_orders
    add column items_amount bigint,
    add column coupon_issue_id uuid,
    add column coupon_state varchar(255),
    add column coupon_reject_code varchar(255),
    add column coupon_lock_attempts integer not null default 0,
    add column coupon_discount_type varchar(255),
    add column coupon_amount bigint,
    add column coupon_rate_bps integer,
    add column coupon_max_discount bigint,
    add column coupon_min_items_amount bigint,
    add column coupon_definition_version bigint,
    add column coupon_discount bigint;
alter table orders.outbox_events add column failed_at timestamp(6) with time zone;

create table orders.order_analytics_context (
    order_id uuid primary key, analytics_id uuid, client_id varchar(100),
    session_id varchar(40), created_at timestamptz
);

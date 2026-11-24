-- 과거 주문을 갱신하지 않고 새 주문에만 회원 자격과 플랫폼 배송비 지원을 저장합니다.
alter table orders.purchase_orders
    add column original_shipping_fee bigint,
    add column membership_shipping_discount bigint,
    add column membership_eligible boolean,
    add column membership_source_version bigint,
    add column membership_evaluated_at timestamp(6) with time zone,
    add column membership_entitlement_until timestamp(6) with time zone,
    add column shipping_policy_version varchar(40),
    add column membership_rate_bps integer,
    add column membership_amount bigint;

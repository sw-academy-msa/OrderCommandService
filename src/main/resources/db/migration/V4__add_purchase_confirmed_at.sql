-- 구매 확정 시각. 구매자가 확정하거나 배송 완료 7일 뒤 자동으로 채운다. 기존 주문은 null(확정 전)
alter table orders.purchase_orders add column purchase_confirmed_at timestamp(6) with time zone;
-- 자동 확정 대상(배송 완료, 확정 전) 조회용
create index purchase_orders_auto_confirm_idx on orders.purchase_orders (delivered_at)
    where delivery = 'DELIVERED' and purchase_confirmed_at is null;

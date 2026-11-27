-- 실패 당시 구매 한도를 체크아웃 응답에도 보존합니다.
ALTER TABLE orders.purchase_orders ADD COLUMN purchase_limit integer;
ALTER TABLE orders.purchase_orders ADD COLUMN remaining_purchase_quantity integer;

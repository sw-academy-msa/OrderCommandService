-- 약관 전용 Flyway 이력만 관리합니다. 기존 주문·재고·결제 테이블은 생성하거나 변경하지 않습니다.
CREATE TABLE orders.checkout_terms (
    version varchar(100) PRIMARY KEY,
    title varchar(200) NOT NULL,
    content text NOT NULL,
    effective_at timestamptz NOT NULL,
    content_sha256 varchar(64) NOT NULL
);

-- 이미 동의한 본문을 같은 버전으로 바꾸지 못하게 DB에서도 수정·삭제를 거부합니다.
CREATE FUNCTION orders.reject_checkout_terms_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Checkout terms versions are immutable; insert a new version';
END;
$$;
CREATE TRIGGER checkout_terms_immutable
BEFORE UPDATE OR DELETE ON orders.checkout_terms
FOR EACH ROW EXECUTE FUNCTION orders.reject_checkout_terms_mutation();

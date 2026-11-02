-- 별도 Command PostgreSQL에 Order 원본 DB를 준비합니다. 일반 Account DB 서버에서는 실행하지 않습니다.
-- orders_password는 호출 스크립트가 생성해 psql 입력으로만 전달하며 SQL 파일에는 저장하지 않습니다.
BEGIN;
DO $$
BEGIN
    IF current_database() <> 'postgres' OR current_user <> 'command_local_admin' THEN
        RAISE EXCEPTION '로컬 Command DB 관리자·관리 DB가 아닙니다. 대상 서버를 확인하세요.';
    END IF;
    -- 부분 실패 후 재실행해도 기존 비밀번호·소유권·데이터를 덮어쓰지 않습니다.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'orders'
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR NOT rolcanlogin)) THEN
        RAISE EXCEPTION '기존 orders 역할의 권한이 예상과 다릅니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_database WHERE datname = 'order_command'
               AND pg_get_userbyid(datdba) <> current_user) THEN
        RAISE EXCEPTION '기존 Order DB의 소유자가 예상과 다릅니다.';
    END IF;
END $$;

SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'orders') AS create_orders_role \gset
\if :create_orders_role
CREATE ROLE orders LOGIN PASSWORD :'orders_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
\endif
COMMIT;

-- CREATE DATABASE는 트랜잭션 안에서 실행할 수 없습니다. 실패 시 기존 DB를 삭제해 재시도하지 않습니다.
-- 앱은 업무 스키마만 소유하고 DB 소유권은 관리자에 남겨 public/다른 스키마 생성 권한을 막습니다.
SELECT NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'order_command') AS create_orders_database \gset
\if :create_orders_database
CREATE DATABASE order_command TEMPLATE template0 ENCODING 'UTF8';
\endif
REVOKE ALL ON DATABASE order_command FROM PUBLIC;
GRANT CONNECT ON DATABASE order_command TO orders;
ALTER ROLE orders IN DATABASE order_command SET search_path TO orders;
\connect order_command

BEGIN;
-- 여기서부터 새 Order DB만 초기화합니다. 내부 업무 스키마 이름은 기존 JPA 매핑을 유지합니다.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA IF NOT EXISTS orders AUTHORIZATION orders;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='orders' AND pg_get_userbyid(nspowner)='orders') THEN
        RAISE EXCEPTION 'Order 스키마 소유자가 예상과 다릅니다.';
    END IF;
END $$;
REVOKE ALL ON SCHEMA orders FROM PUBLIC;
COMMIT;

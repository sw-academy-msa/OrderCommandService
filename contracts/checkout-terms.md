# 주문·결제 약관 DB 계약

## 저장과 소유권

Order Command가 자기 DB의 orders.checkout_terms만 소유한다. 본문과 제목, 버전, 시행 시각, UTF-8 SHA-256을 저장한다. 회원가입 약관과 개인정보 처리방침을 대체하지 않는다. 초기 checkout-v1 본문은 현재 확정된 구매 절차를 바탕으로 작성한 초안이다. 운영 적용 전 사업자 정보와 실제 서비스 약관 체계에 맞게 문구를 검토해야 하며, 취소·반품의 세부 조건이나 법적 권리 제한을 임의 추가하지 않았다.

등록은 Flyway 마이그레이션으로 한다. JPA는 읽기 전용 엔티티와 @Query 저장소로 조회한다. 관리자 편집 API는 만들지 않는다. DB 트리거도 기존 행 UPDATE/DELETE를 거부한다. 운영자가 DB 소유자 권한으로 트리거를 해제하는 행위까지 방어하는 보장은 아니다.

## API와 프런트 흐름

- GET /api/v1/orders/checkout-terms: 현재 약관의 version, title, content, effectiveAt, contentSha256을 ApiResponse.data로 반환한다.
- GET /api/v1/orders/checkout-terms/{version}: 과거 동의 버전의 동일한 본문을 반환한다. 없는 버전은 TERMS_NOT_FOUND 404다.
- 두 API는 BUYER JWT가 필요하며 PurchaseGateway(로컬 8001)를 사용한다. 일반 Gateway의 주문 Query 조회와 구분한다.
- 현재 약관은 기존 ORDER_CHECKOUT_TERMS_VERSION 설정으로 선택한다. 해당 DB 행이 없거나 시행일 전이면 TERMS_UNAVAILABLE 503이다. 과거·미래 버전의 명시적 본문 조회 자체는 가능하지만 미래 버전으로 결제를 시작할 수는 없다.
- checkout 응답의 data.termsVersion은 동의 전 현재 버전이다. 이미 동의한 주문은 당시 동의 버전을 유지한다.
- 프런트는 현재 약관을 조회해 평문 content를 안전하게 표시하고 동의를 받은 뒤 기존 payment-attempts에 agreed:true와 그 termsVersion을 보낸다. 본문을 HTML로 삽입하지 않는다.
- 기존 서버 버전 대조와 orders의 termsVersion/consentedAt 저장을 재사용한다. 버전 불일치 시 기존 ORDER_INVALID 400을 유지한다. 화면은 새 현재 본문을 조회하고 다시 동의받아야 한다. 같은 payment requestKey의 재요청은 기존 동의와 결제 멱등 계약을 유지한다.

## Flyway 초기 적용

- 의존성: Spring Boot 관리 버전의 spring-boot-starter-flyway와 flyway-database-postgresql.
- 위치: classpath:db/checkout-terms, 스키마: orders, 이력 테이블: checkout_terms_schema_history.
- V1은 약관 테이블과 불변 트리거만 생성하고 V2는 checkout-v1을 등록한다. 기존 주문·결제·Saga 업무 테이블을 생성하거나 삭제하지 않는다.
- local은 기존 Hibernate 테이블이 있는 orders에 baseline 0을 명시하여 약관 전용 이력을 시작한다. V1/V2를 건너뛰지 않는다. 개발 ddl-auto=update는 기존 업무 테이블에 그대로 유지한다.
- prod의 baseline-on-migrate는 false다. 이미 데이터가 있는 운영 orders는 배포 담당자가 DB와 스키마를 확인하고 약관 전용 이력의 baseline 0을 먼저 준비해야 한다. 운영에서 자동 baseline을 무조건 켜거나 기존 테이블을 지우지 않는다. 나머지 업무 테이블의 운영 DDL 준비와 validate 정책은 별개다.
- clean은 비활성이다. 적용된 SQL을 수정하면 checksum 오류가 발생하므로 본문 수정도 새 버전과 새 migration으로 추가한다.
- 테스트에서는 Flyway seed가 Hibernate create-drop으로 사라지지 않도록 격리 PostgreSQL을 update로 준비한다. 개발 DB나 .env를 사용하지 않는다.

## 개정 절차

새 Flyway 버전에 새로운 약관 ID와 본문·시행일을 INSERT한다. 먼저 DB와 프런트 표시를 배포하고 시행 시각 이후 현재 버전 설정을 바꾼다. 같은 배포 풀의 Order 복제본들은 동일한 현재 버전을 사용해야 한다. 기존 약관 행과 주문 동의 이력은 보존한다. 약관 저장소 변경을 Kafka나 Order Query 조회 모델로 복제할 필요는 없다.

## 실제 반영 범위

코드·마이그레이션과 Gateway 경로를 준비하고 격리 PostgreSQL 및 HTTP fixture로 검증한다. 기존 로컬 DB에 migrate를 직접 실행하거나 AWS·ALB를 변경한 것은 아니다. 실제 서버를 새 코드로 실행하면 local Flyway가 처음 한 번 등록한다. ALB 운영 분기에는 위 두 GET 경로를 구매 대상 그룹으로 포함해야 한다.

기술 근거: [Spring Boot DB 초기화](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [Flyway baselineOnMigrate](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

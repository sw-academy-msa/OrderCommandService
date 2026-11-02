# Order 내부 HTTP·Saga·이벤트 계약

Order는 orders 원본과 영속 Saga를 소유합니다. Product의 재고 로직을 복제하거나 다른 서비스 DB를 읽지 않습니다. Product와 Order는 각각 Command/Query 별도 앱입니다.

## 확정 정책

한 판매자·한 상품의 여러 옵션을 주문하며 배송비는 주문당 한 번입니다. Product 확보 성공 후 5분 내 실제 결제 승인되면 확정합니다. 정확히 만료 시각의 승인은 기한 후입니다. 재요청으로 기한을 연장하지 않습니다. 응답이 늦으면 Payment가 확인한 실제 승인 시각을 사용하며 결과 불명 재고를 임의 반환하지 않습니다. 출고 예정 영업일 수는 판매자 지정값입니다.

순번·도착 예정일·취소/반품 접수·구매확정·수수료·정산은 제외합니다. 늦은 승인 취소는 구매 실패 복구이며 일반 주문 취소 API가 아닙니다.

## Product: 실제 경로

    POST /internal/v1/stock-reservations
    GET /internal/v1/stock-reservations/{id}
    POST /internal/v1/stock-reservations/{id}/payment-processing
    POST /internal/v1/stock-reservations/{id}/confirmation
    POST /internal/v1/stock-reservations/{id}/release

확보 요청은 orderId/productId/buyerId/items(optionId,quantity)입니다. 응답 유실 시 같은 orderId와 내용으로 재요청합니다. 확보 ID를 안다면 상태 조회도 가능합니다. 원본 단가·배송비·만료 시각을 사용합니다.

응답에 sellerId/productName/brandOrStore/dispatchBusinessDays/optionLabels 확보 스냅샷을 추가했습니다. 기존 확보의 누락 정보를 임의 백필하지 않습니다. Product 새 코드 적용/재시작이 필요합니다.

인증은 Account RS256 access JWT, audience=stockdrop-api, UUID subject, scope=product.stock 및 Product ORDER_SERVICE_SUBJECT와 일치하는 subject가 필요합니다. Account POST /internal/v1/auth/service-tokens에 Basic 자격증명과 grant_type=client_credentials, scope=product.stock form을 보내 발급받습니다. ORDER_SERVICE_AUTH_ENABLED=true이면 Order가 기존 HTTP/JWT 검증기로 획득·검증·캐시하고 만료 30초 전 재발급합니다. Refresh Token/사용자 로그인은 사용하지 않습니다. Account 장애 때는 아직 유효한 토큰만 쓰고 만료 후에는 차단합니다. 운영 자격증명 회전과 HTTPS 설정은 별도로 적용합니다. 기존 ORDER_PRODUCT_SERVICE_TOKEN은 자동 모드 비활성 시 명시적 진단 호환용입니다.

## Account: 실제 배송지 API

    GET /api/v1/accounts/me/addresses/{addressId}

요청자의 Bearer로 소유권을 확인한 주소를 주문에 복사합니다. 사용자 토큰은 DB·작업·Outbox에 저장하지 않습니다. 외부 조회는 로컬 트랜잭션 밖이며 저장 시 상태/버전을 다시 검사합니다. 주소록 수정/삭제를 과거 주문에 소급하지 않습니다.

## Payment: Outbox + Kafka

승인/재확인/보상 취소는 비공개 명령 토픽, 결과는 별도 결과 토픽을 사용합니다. 기존 Payment HTTP 어댑터는 제거했습니다. 실제 Payment 앱/토스 처리는 아직 없습니다. 상세 계약과 장애/전환 조건은 [payment-kafka-v1.md](payment-kafka-v1.md)를 따릅니다.

## 영속 Saga와 이벤트

RESERVING → HELD → STARTING_PAYMENT → APPROVING_PAYMENT → VERIFYING_PAYMENT/CONFIRMING_STOCK → CONFIRMED. 실패/기한 후 승인 보상은 COMPENSATING_PAYMENT → RELEASING_STOCK → FAILED/EXPIRED입니다.

30초 임대 토큰·다음 시도 시각·횟수·고정 실패 코드·진행 이력을 DB에 보관합니다. HTTP는 DB 트랜잭션 밖입니다. 중단 작업은 임대 만료 후 같은 ID로 복구하고 오래된 작업 결과는 무시합니다. 결과 불명은 5분 경과만으로 종료하지 않습니다. 최대 60초 backoff로 계속 재확인하므로 미완료 Saga/오래된 임대는 운영 알림이 필요합니다.

orders.outbox_events는 변경과 같은 로컬 트랜잭션입니다. ORDER_OUTBOX_PUBLISHER_ENABLED=true일 때 내부 작업이 Kafka ack 후 발행 완료를 기록합니다. 실패는 재시도하며 ack 후 DB 기록 유실 시 같은 ID/본문이 중복 발행됩니다.

- stockdrop.orders.private.v1: key=orderId, ORDER_UPSERT, order 스냅샷.
- stockdrop.orders.inquiries.private.v1: key=orderId, ORDER_INQUIRY_UPSERT, inquiry 스냅샷.
- envelope: eventId/schemaVersion=1/type/aggregateId/aggregateVersion/occurredAt와 스냅샷.
- 주문 버전은 projectionVersion, 문의 버전은 메시지 수입니다. 주문 잠금으로 변경을 직렬화합니다.
- 배송지·구매자 ID·문의·사진 키는 비공개 토픽 ACL/보존 정책이 필요합니다. 결제키/토큰/작업 임대는 발행하지 않습니다.
- Order Query/Consumer는 아직 없습니다. 향후 RDS orders_query와 멱등 이력·버전 비교가 필요합니다. 발행 완료는 조회 반영 완료가 아니며 Kafka 유실 백업도 아닙니다.

## 문의 사진

JPEG/PNG/WebP, 최대 10MiB는 기술 상한, 문의당 최대 5장은 Figma 기준입니다. 크기/MIME/파일 서명/ETag 검증 후 서버 전용 키로 복사합니다. 같은 이미지 완료는 행 잠금으로 직렬화합니다. S3는 DB 롤백 대상이 아니므로 임시/미연결 객체는 권한 만료 후 24시간이 지난 내부 정리 작업이 재시도합니다. 연결된 최종 사진은 정리하지 않습니다. 폐기한 서명 URL도 원래 만료 전까지 PUT 가능하므로 최종 키를 분리하고 정리는 만료 뒤 수행합니다. 운영 CORS/IAM/라이프사이클/보존·삭제 정책은 별도입니다.

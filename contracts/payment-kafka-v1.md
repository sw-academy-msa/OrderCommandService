# Order ↔ Payment Kafka 계약 v1

Order → Payment HTTP 승인/조회/보상 어댑터는 제거했습니다. 재고 확보/진행 표시/확정/해제는 Product 내부 HTTP를 유지합니다. 실제 Payment 앱·토스 구현은 아직 없습니다. 아래 계약을 Payment가 구현해야 구매 흐름이 완성됩니다.

## 요청: stockdrop.payment.commands.private.v1

key는 paymentAttemptId UUID 문자열입니다. JSON 필드는 eventId(UUID), schemaVersion=1, type=PAYMENT_COMMAND, action(APPROVE/CHECK/CANCEL), orderId, paymentAttemptId, commandVersion, occurredAt(UTC), amount(정수 원), expiresAt(최초 확보 만료), cancellationRequested입니다. APPROVE에만 paymentKey가 포함됩니다.

orders.payment_attempts의 요청 상태/명령 버전과 orders.outbox_events를 같은 로컬 트랜잭션에 저장합니다. 같은 승인/취소 동작은 한 번만 기록하고, 결과 불명은 60초 이상 간격의 CHECK로 재확인을 요청합니다. 발행 성공 기록 전 장애로 같은 이벤트가 중복 전송될 수 있습니다. Outbox 보존/재시도는 기존 내부 발행기를 재사용합니다.

Payment는 처리 이력과 결제 시도 원본을 자신의 DB에 저장해야 합니다. 명령 eventId 중복을 제거하고 paymentAttemptId 단위로 승인/취소를 멱등 처리합니다. 토스 호출은 DB 트랜잭션 밖이며 승인 성공 후 DB 저장 전 장애가 나면 토스 결과 재조회로 복구해야 합니다. 승인 결과와 결과 Outbox는 같은 Payment DB 트랜잭션입니다.

commandVersion은 명령 식별/진단용이지 최신 조회 스냅샷 버전이 아닙니다. CHECK가 먼저 왔다고 나중의 APPROVE를 버리면 안 됩니다. 반대로 CANCEL이 먼저 왔다면 취소 의도를 영속 저장하고 뒤늦은 APPROVE를 차단해야 합니다. 같은 시도에서 DECLINED/CANCELED를 확정한 뒤 승인되면 계약 위반입니다.

최초 expiresAt은 재전송으로 바꾸지 않습니다. Payment는 기한 후 새 승인을 실행하지 않고, 실제 승인 시각을 확인합니다. CHECK에서 거래를 못 찾았거나 토스 응답이 불명이면 UNKNOWN/PENDING입니다. 단순 404/타임아웃을 DECLINED로 보내지 않습니다. 기한 후 승인된 결제는 Order가 CANCEL을 요청하며 CANCELED 확인 전 재고를 해제하지 않습니다.

## 결과: stockdrop.payment.results.private.v1

key=paymentAttemptId. eventId(UUID), schemaVersion=1, type=PAYMENT_RESULT, orderId, paymentAttemptId, amount(정수), version(시도별 단조 증가 원본 버전), status(PENDING/UNKNOWN/APPROVED/DECLINED/CANCELED), approvedAt(UTC 밀리초, APPROVED 필수), occurredAt(UTC)을 보냅니다. PENDING/UNKNOWN/DECLINED에는 approvedAt을 넣지 않습니다. 같은 version의 상태/금액/승인 시각은 바꾸지 않습니다.

Order는 주문 잠금 안에서 금액·시도·주문을 검증하고 로컬 결과와 eventId/hash 이력을 같은 트랜잭션으로 저장합니다. 같은 ID의 다른 내용, 같은 버전의 다른 결과, 종료 상태 역행은 DLT로 격리합니다. 오래된 버전은 이력만 남기고 최신 상태를 덮어쓰지 않습니다. 승인 후 취소는 보상 요청이 있는 경우에만 허용합니다. 소비 스레드에서 Product HTTP를 호출하지 않으며 영속 Saga가 결과를 읽고 진행합니다.

DB 장애는 계속 재시도하고 커밋 전 offset을 넘기지 않습니다. 영구 오류는 stockdrop.payment.results.private.v1.DLT에 성공적으로 발행한 뒤 처리합니다. DLT 발행 실패도 재시도합니다. DLT 조사/교정 재처리와 지연 경보는 운영 절차가 필요합니다.

## 비공개 정보·운영·전환

paymentKey는 서버 비밀키가 아닌 거래 식별값이지만 승인 명령 전용 비공개 토픽/Outbox에서만 다룹니다. Order Query 이벤트/HTTP 응답/로그에는 넣지 않습니다. Kafka ACL은 Order 명령 쓰기/결과 읽기, Payment 명령 읽기/결과 쓰기로 제한해야 하며 임의 생산자의 결제 결과를 신뢰하면 안 됩니다. 운영 TLS/SASL/보존·디스크·백업은 별도 검증입니다. 단일 Kafka 장애/디스크 유실에서 무손실을 무조건 보장하지 않습니다.

로컬 토픽은 scripts/Initialize-LocalOrderKafkaTopics.ps1로 생성합니다. Order는 ORDER_OUTBOX_PUBLISHER_ENABLED=true 및 ORDER_PAYMENT_EVENTS_ENABLED=true로 활성화합니다. 같은 Order 복제본은 같은 order-command-payment-v1 그룹을 사용합니다. Payment는 독립 명령 소비 그룹을 사용해야 합니다.

개발 스키마는 JPA 엔티티와 ddl-auto=update로 반영합니다. 운영은 필요한 스키마를 준비한 후 validate로 검증하며 스키마 변경 절차는 배포 준비 시 정합니다. 기존 HTTP 방식의 미완료 결제는 점검 후 CHECK로 전환해야 하며 승인 요청을 무작정 다시 보내지 않습니다. 롤링 중 구 HTTP 코드와 Kafka 코드를 혼용하지 않습니다. SQL/데이터 전환은 검토 후 별도로 적용하며 기존 DB를 삭제하지 않습니다.

# 멤버십 배송비 할인

주문 생성 때 검증된 JWT의 `membership` claim을 읽습니다. `active=true`, 허용 상태 `ACTIVE/GRACE/CANCEL_SCHEDULED`, 정수 `valid_until`과 양수 `version`이 필요합니다. `now < valid_until`일 때만 지원합니다. 누락/형식 오류는 비회원으로 처리합니다. 회원 확인용 Account 동기 호출은 추가하지 않습니다.

`membership.shipping.enabled`는 기본 false입니다. 환경변수로 `MEMBERSHIP_SHIPPING_ENABLED=true`를 설정하면 활성화할 수 있습니다. `mode` 기본 RATE, `rate-bps` 기본 10000(100%), `amount` 기본 0원, `policy-version` 기본 membership-shipping-v1입니다. AMOUNT 방식은 원 배송비와 설정 금액 중 작은 금액을 지원합니다.

평가 시각, 자격 종료 시각/버전, 할인 정책을 새 주문에 저장합니다. 같은 요청 키의 재시도나 이후 토큰/정책 변경은 주문 금액을 바꾸지 않습니다. 기존 `shippingFee`는 원 배송비로 유지하고 `originalShippingFee`, `membershipShippingDiscount`, `effectiveShippingFee`를 별도로 제공합니다. 결제금액은 상품금액+실청구 배송비입니다. 할인액은 플랫폼 부담이며 고객 현금 환불 대상이 아닙니다.

OrderQuery의 새 스냅샷 지원/상품 정산 보정을 먼저 배포해야 합니다. 과거 주문은 nullable 컬럼과 읽기 fallback을 사용하고 대량 UPDATE를 하지 않습니다. Flyway V5는 컬럼 추가만 수행합니다. 멤버십 가입/JWT 발급/청구는 별도 서비스 작업이며 이 PR만으로 가입 기능이 생기지 않습니다. 운영 배포와 설정 활성화는 별도 승인입니다.

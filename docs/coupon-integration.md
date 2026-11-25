# 주문 쿠폰 연동

ORDER_COUPON_ENABLED, ORDER_LIFECYCLE_EVENTS_ENABLED, ORDER_ANALYTICS_ENABLED는 모두 기본 false입니다. 기존 Saga 상태를 늘리지 않습니다. 상품 재고 확보 뒤 동일 orderId로 쿠폰 잠금을 호출하며 확정 거절은 REJECTED, 일시 실패 세 번 재시도 뒤에도 확인 불가는 SKIPPED로 할인 없이 진행합니다. 응답 유실로 실제 잠금이 남아도 최종 lifecycle과 coupon-outcome 조회로 복구합니다.

상품 금액, 쿠폰 할인, 원 배송비, 회원 배송비 할인, 결제 금액 순서로 계산합니다. 쿠폰 할인은 상품 결제 금액을 100원 이상 남기며 정률 중간 계산은 BigInteger로 overflow를 방지합니다. 판매자 본인 상품은 쿠폰 잠금 전에 거절합니다. 쿠폰 요청은 request hash에 포함됩니다. 할인 조건과 결과는 주문에 고정하고 재계산하지 않습니다.

V6은 nullable 쿠폰 필드와 별도 order_analytics_context를 추가합니다. 분석 문맥은 결제 APPROVE의 비공개 명령에만 넣으며 30일 후 정리합니다. 공개 조회와 lifecycle에는 주소, 토큰, 결제 키 및 분석 ID를 싣지 않습니다. checkout OpenAPI가 할인 내역과 거절 사유를 제공합니다.

stockdrop.orders.lifecycle.v1은 HELD, CONFIRMED, FAILED, EXPIRED 전이와 같은 TX에 기록합니다. ID는 nameUUID(ORDER_LIFECYCLE:orderId:phase)입니다. 새 토픽 Outbox는 20회 실패 후 failed_at으로 격리하며 운영자는 같은 ID를 보존해 재처리합니다. DLT와 유한 소비 재시도는 CouponService 및 AIService가 담당합니다.

GET /internal/v1/orders/{orderId}/coupon-outcome은 coupon-service 주체와 order.coupon.read scope를 검증하며 no-store로 phase와 적용 여부 및 할인만 돌려줍니다. Gateway에는 라우팅하지 않습니다. Account order-command 클라이언트에는 기존 product.stock 외에 coupon.lock scope를 추가합니다. 기존 서비스 토큰과 동일한 발급 및 JWKS 검증 경로를 사용합니다.

배포는 OrderQuery, Payment GA OFF, Account 권한, CouponService 순서로 준비한 뒤 lifecycle만 ON으로 시작합니다. 격리 E2E 후 쿠폰 및 분석 수집을 각각 켭니다. 운영 배포, SSM 등록, 실제 결제는 이 변경에 포함하지 않습니다. 되돌릴 때 새 쿠폰 사용을 먼저 끄고 이미 잠긴 쿠폰이 정리될 때까지 lifecycle 및 내부 조회 API와 CouponService 복구를 유지합니다. V6 스키마와 Outbox는 보존합니다.

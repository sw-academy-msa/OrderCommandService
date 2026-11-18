# 주문 제한 Postman 검증

운영자 계정 주문과 판매자 본인 상품 주문을 서버가 거절하는지 확인합니다. 정상 주문이 그대로 되는지도 함께 확인합니다.

| 파일 | 내용 |
|---|---|
| `STOCKDROP-order-guard.postman_collection.json` | 요청 16개, 검증 26개 |
| `STOCKDROP-order-guard.local.postman_environment.json` | 로컬 주소, 테스트 계정 이메일, 상품 ID. 비밀번호는 비워 둡니다 |

## 확인하는 내용
| 폴더 | 요청 | 기대 결과 |
|---|---|---|
| 01 로그인 | 구매자, 판매자, 운영자 로그인 | 토큰 저장. 로그인할 때마다 CSRF를 다시 받습니다 |
| 02 배송지 준비 | 계정별 배송지 등록 | 201 |
| 03 주문 제한 | 운영자 주문 | 403 `ORDER_FORBIDDEN`. 응답에 주문 ID가 없습니다 |
| | 판매자 본인 상품 주문 | 202 뒤 checkout 조회에서 `FAILED` + `PRODUCT_SELF_PURCHASE` |
| 04 정상 주문 회귀 | 일반 구매자 주문 | `HELD` |
| | 판매자가 다른 판매자 상품 주문 | `HELD` |

checkout 조회는 재고 확보가 끝날 때까지(`RESERVING`이 아닐 때까지) 2초 간격으로 최대 10번 다시 조회합니다.

## 준비
1. 로컬에서 Eureka, AccountService, ProductCommandService, OrderCommandService, Gateway(8000), PurchaseGateway(8001)를 실행합니다. Kafka와 각 DB 컨테이너도 떠 있어야 합니다.
   OrderCommandService는 Product 재고 확보를 실제로 호출해야 하므로 `ORDER_PRODUCT_ENABLED=true`, `ORDER_SERVICE_AUTH_ENABLED=true`와 Account에 등록된 서비스 자격증명(`ORDER_SERVICE_CLIENT_ID`, `ORDER_SERVICE_CLIENT_SECRET`)을 켭니다. `ORDER_SERVICE_SUBJECT`는 ProductCommandService의 `ORDER_SERVICE_SUBJECT`와 같아야 합니다. 이 설정이 꺼져 있으면 주문은 만들어져도 `FAILED`·`HELD`까지 가지 않습니다(설정 방법은 [루트 README](../README.md)).
2. 테스트 계정 3개를 로컬 Account DB에 만듭니다. 모두 ACTIVE이고 이메일 인증이 끝난 상태여야 합니다.
   - `pm-buyer@example.test`: BUYER
   - `pm-seller@example.test`: BUYER, SELLER
   - `pm-admin@example.test`: BUYER, ADMIN
3. 로컬 ProductCommand DB에 판매 중(`APPROVED`, 판매 기간 안) 상품 2개와 각 옵션을 만듭니다.
   - `sellerProductId`: 판매자가 `pm-seller`인 상품
   - `otherProductId`: 다른 판매자의 상품

   두 옵션 모두 실행 직전 가용 재고가 1개 이상이어야 합니다. 재고가 0이면 04 폴더 주문이 `HELD`가 되지 않아 주문 제한 문제로 오해할 수 있습니다.

   ID를 바꾸면 환경 파일의 `sellerProductId`, `sellerOptionId`, `otherProductId`, `otherOptionId`도 함께 바꿉니다.

## 실행
비밀번호는 파일과 명령줄 인수에 넣지 않습니다. 계정별로 입력받아 이 창의 환경변수로만 넘기고, `run-newman.js`가 Newman에 메모리로 전달합니다.

```powershell
cd OrderCommandService\postman
function Read-Plain($label) { $s = Read-Host -AsSecureString $label; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)) }
$env:STOCKDROP_BUYER_PASSWORD  = Read-Plain "구매자(pm-buyer) 비밀번호"
$env:STOCKDROP_SELLER_PASSWORD = Read-Plain "판매자(pm-seller) 비밀번호"
$env:STOCKDROP_ADMIN_PASSWORD  = Read-Plain "운영자(pm-admin) 비밀번호"
npx -y -p newman@6.2.1 node run-newman.js
Remove-Item Env:STOCKDROP_BUYER_PASSWORD, Env:STOCKDROP_SELLER_PASSWORD, Env:STOCKDROP_ADMIN_PASSWORD
```

Postman 앱에서는 두 파일을 가져오고 환경의 비밀번호 3개를 현재 값(Current value)에만 넣은 뒤 Collection Runner로 실행합니다.

## 다시 실행할 때
- 04 폴더의 `HELD` 주문은 5분 동안 재고를 잡고 있습니다. 5분 안에 다시 실행하면 같은 상품의 진행 중 주문 때문에 `FAILED` + `PRODUCT_DUPLICATE`가 나옵니다. 5분 뒤에 실행합니다.
- 실행할 때마다 계정별 배송지가 하나씩 늘어납니다.
- 운영자 주문이 DB에 남지 않았는지는 실행 로그의 `adminRequestKey` 값으로 확인합니다. 예전에 운영자 계정으로 만든 주문이 있을 수 있으므로 `buyer_id`와 함께 이번 요청 키로 조회합니다.
  `select count(*) from orders.purchase_orders where buyer_id = '<운영자 ID>' and request_key = '<adminRequestKey>';` → 0이어야 합니다.

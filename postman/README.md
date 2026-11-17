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
2. 테스트 계정 3개를 로컬 Account DB에 만듭니다. 모두 ACTIVE이고 이메일 인증이 끝난 상태여야 합니다.
   - `pm-buyer@example.test`: BUYER
   - `pm-seller@example.test`: BUYER, SELLER
   - `pm-admin@example.test`: BUYER, ADMIN
3. 로컬 ProductCommand DB에 판매 중(`APPROVED`, 판매 기간 안) 상품 2개와 각 옵션을 만듭니다.
   - `sellerProductId`: 판매자가 `pm-seller`인 상품
   - `otherProductId`: 다른 판매자의 상품

   ID를 바꾸면 환경 파일의 `sellerProductId`, `sellerOptionId`, `otherProductId`, `otherOptionId`도 함께 바꿉니다.

## 실행
비밀번호는 파일에 저장하지 않고 실행할 때 넘깁니다.

```powershell
cd OrderCommandService\postman
$pw = Read-Host -AsSecureString "테스트 계정 비밀번호"
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pw))
npx newman@6.2.1 run STOCKDROP-order-guard.postman_collection.json `
  -e STOCKDROP-order-guard.local.postman_environment.json `
  --env-var "buyerPassword=$plain" --env-var "sellerPassword=$plain" --env-var "adminPassword=$plain"
```

Postman 앱에서는 두 파일을 가져오고 환경의 비밀번호 3개를 현재 값(Current value)에만 넣은 뒤 Collection Runner로 실행합니다.

## 다시 실행할 때
- 04 폴더의 `HELD` 주문은 5분 동안 재고를 잡고 있습니다. 5분 안에 다시 실행하면 같은 상품의 진행 중 주문 때문에 `FAILED` + `PRODUCT_DUPLICATE`가 나옵니다. 5분 뒤에 실행합니다.
- 실행할 때마다 계정별 배송지가 하나씩 늘어납니다.
- 운영자 주문이 DB에 남지 않았는지는 `orders.purchase_orders`에서 운영자 `buyer_id`의 행이 없는지로 확인할 수 있습니다.

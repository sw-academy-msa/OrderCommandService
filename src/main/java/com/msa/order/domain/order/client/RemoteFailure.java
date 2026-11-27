package com.msa.order.domain.order.client;

/** 응답 원문·토큰을 예외에 보관하지 않습니다. 연결 실패와 업무 거절을 구분합니다. */
public class RemoteFailure extends RuntimeException {
    private final String code;
    private final boolean definitive;

    public final Integer purchaseLimit;
    public final Integer remainingPurchaseQuantity;

    public RemoteFailure(String code, boolean definitive) {
        this(code, definitive, null, null);
    }

    // 구매 한도 계산 결과를 보존하여 재고 부족과 구분합니다.
    public RemoteFailure(String code, boolean definitive, Integer limit, Integer remaining) {
        super(code);
        this.code = code;
        purchaseLimit = limit;
        remainingPurchaseQuantity = remaining;
        this.definitive = definitive;
    }

    public String code() {
        return code;
    }

    public boolean definitive() {
        return definitive;
    }
}

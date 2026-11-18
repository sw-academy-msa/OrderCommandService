package com.msa.order.domain.order.client;

/** 응답 원문·토큰을 예외에 보관하지 않습니다. 연결 실패와 업무 거절을 구분합니다. */
public class RemoteFailure extends RuntimeException {
    private final String code;
    private final boolean definitive;

    public RemoteFailure(String code, boolean definitive) {
        super(code);
        this.code = code;
        this.definitive = definitive;
    }

    public String code() {
        return code;
    }

    public boolean definitive() {
        return definitive;
    }
}

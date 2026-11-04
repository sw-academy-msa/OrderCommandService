package com.msa.order.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.order.domain.image.exception.ImageErrorCode;
import com.msa.order.domain.image.exception.ImageException;
import com.msa.order.domain.inquiry.exception.InquiryErrorCode;
import com.msa.order.domain.inquiry.exception.InquiryException;
import com.msa.order.domain.order.exception.OrderErrorCode;
import com.msa.order.domain.order.exception.OrderException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

/** 도메인 예외 분리 후에도 기존 ORDER_* 오류 코드·상태·메시지와 응답 형식을 유지하는지 검증합니다. */
class GlobalExceptionHandlerTest {
    @ParameterizedTest
    @MethodSource("failures")
    void domainExceptionsKeepExistingHttpContract(
            BusinessException failure, int httpStatus, String code, String message)
            throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new FailureController(failure))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        MDC.put("traceId", "fixture-trace");
        try {
            mvc.perform(get("/fixture/errors"))
                    .andExpect(status().is(httpStatus))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.message").value(message))
                    .andExpect(jsonPath("$.traceId").value("fixture-trace"))
                    .andExpect(jsonPath("$.violations").isEmpty());
        } finally {
            MDC.remove("traceId");
        }
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(
                        new OrderException(OrderErrorCode.NOT_FOUND),
                        404,
                        "ORDER_NOT_FOUND",
                        "주문을 찾을 수 없습니다."),
                Arguments.of(
                        new OrderException(OrderErrorCode.OWNER),
                        403,
                        "ORDER_OWNER",
                        "본인 주문만 처리할 수 있습니다."),
                Arguments.of(
                        new OrderException(OrderErrorCode.STATE),
                        409,
                        "ORDER_STATE",
                        "현재 주문 상태에서는 처리할 수 없습니다."),
                Arguments.of(
                        new InquiryException(InquiryErrorCode.DUPLICATE),
                        409,
                        "ORDER_DUPLICATE",
                        "같은 요청 키의 주문 내용이 다릅니다."),
                Arguments.of(
                        new ImageException(ImageErrorCode.INVALID),
                        400,
                        "ORDER_INVALID",
                        "주문 입력값이 올바르지 않습니다."),
                Arguments.of(
                        new ImageException(ImageErrorCode.UNAVAILABLE),
                        503,
                        "ORDER_UNAVAILABLE",
                        "외부 연동 설정이나 응답이 준비되지 않았습니다."));
    }

    /** 비정적 내부 클래스로 두어 실제 애플리케이션 컴포넌트 검색에 포함되지 않게 합니다. */
    @RestController
    class FailureController {
        private final BusinessException failure;

        FailureController(BusinessException failure) {
            this.failure = failure;
        }

        @GetMapping("/fixture/errors")
        public String fail() {
            throw failure;
        }
    }
}

package com.oj.agent.start.config;

import com.oj.agent.common.api.Result;
import com.oj.agent.core.trace.controller.TraceController;
import com.oj.agent.security.exception.SecurityErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TraceController.class)
public class TraceControllerExceptionHandler {

    // 处理链路接口的非法参数异常。
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error("TRACE_BAD_REQUEST", e.getMessage()));
    }

    // 处理链路接口的当前用户缺失异常。
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<Void>> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(SecurityErrorCode.AUTHN_007.getCode(), e.getMessage()));
    }
}

package com.oj.agent.admin.config;

import com.oj.agent.admin.user.controller.AdminUserManagementController;
import com.oj.agent.common.api.Result;
import com.oj.agent.security.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 管理端用户接口异常处理器。
 */
@RestControllerAdvice(assignableTypes = AdminUserManagementController.class)
public class AdminUserManagementExceptionHandler {

    /**
     * 处理请求参数校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "请求参数校验失败";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error("VALIDATION_ERROR", message));
    }

    /**
     * 处理业务校验异常。
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Void>> handleValidationException(ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(e.getErrorCode(), e.getMessage()));
    }
}

package com.oj.agent.common.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 5679018624309023727L;

    public static final String SUCCESS_CODE = "0";

    private String code;

    private String message;

    private T data;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }

    public static <T> Result<T> success() {
        return new Result<T>()
                .setCode(SUCCESS_CODE)
                .setMessage("操作成功");
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(SUCCESS_CODE)
                .setMessage("操作成功")
                .setData(data);
    }

    public static <T> Result<T> success(T data, String message) {
        return new Result<T>()
                .setCode(SUCCESS_CODE)
                .setMessage(message)
                .setData(data);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<T>()
                .setCode(code)
                .setMessage(message);
    }

    public static <T> Result<T> error(String message) {
        return new Result<T>()
                .setCode("-1")
                .setMessage(message);
    }
}

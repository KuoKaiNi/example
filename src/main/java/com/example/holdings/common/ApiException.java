package com.example.holdings.common;

import org.springframework.http.HttpStatus;

/** 業務錯誤：帶 HTTP 狀態碼與錯誤代碼，網頁顯示 message，API 回 {"error":{"code","message"}}。 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public static ApiException notFound(long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "找不到編號 " + id + " 的持股");
    }
}

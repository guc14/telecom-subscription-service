// ── ApiResponse.java ──────────────────────────────────────────────────────────
package com.guc.telecom.dto;

public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200; r.message = "success"; r.data = data;
        return r;
    }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}

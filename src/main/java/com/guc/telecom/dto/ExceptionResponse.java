package com.guc.telecom.dto;

public class ExceptionResponse {
    private boolean success = false;
    private String errorCode;
    private String message;
    private String path;

    public ExceptionResponse(String errorCode, String message, String path) {
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
    }

    public boolean isSuccess() { return success; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
}

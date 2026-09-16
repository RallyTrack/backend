package com.rallytrack.backend.global.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final int status;
    private final String errorCode;
    public ApiException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
    public static ApiException notFound() {
        return new ApiException(404, "RESOURCE_NOT_FOUND", "영상을 찾을 수 없습니다.");
    }
}

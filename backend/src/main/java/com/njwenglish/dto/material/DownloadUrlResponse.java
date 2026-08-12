package com.njwenglish.dto.material;

/**
 * presigned 다운로드 URL. 유효기간이 짧아서 응답에 담겨 나가도 오래 살아 있지 않다.
 * 프론트는 받은 즉시 이동시킨다. expiresIn은 초 단위다.
 */
public record DownloadUrlResponse(String downloadUrl, String fileName, long expiresIn) {
}

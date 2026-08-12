package com.njwenglish.common.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * presigned URL만 만든다. 파일 자체는 클라이언트가 S3로 직접 PUT하므로
 * 서버가 바이트를 다루는 경로는 없다.
 *
 * <p>자격증명은 기본 체인(환경변수 · EC2 인스턴스 프로파일)에서 지연 해석된다.
 * 그래서 자격증명이 없는 로컬에서도 부팅은 되고, presign을 호출할 때 비로소 실패한다.
 */
@Configuration
public class S3ClientFactory {

    @Bean
    public S3Presigner s3Presigner(@Value("${app.s3.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }

    /** 사진 삭제에만 쓴다. 업로드·다운로드는 presigned URL이다. */
    @Bean
    public S3Client s3Client(@Value("${app.s3.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }
}

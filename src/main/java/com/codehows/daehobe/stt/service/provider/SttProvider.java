package com.codehows.daehobe.stt.service.provider;

import com.codehows.daehobe.stt.dto.SttSummaryResult;
import com.codehows.daehobe.stt.dto.SttTranscriptionResult;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

public interface SttProvider {

    /**
     * 음성 파일을 STT 서비스에 보내 변환을 요청합니다.
     *
     * @param audioFile 변환할 음성 파일
     * @return 비동기 작업 ID를 포함하는 Mono
     */
    Mono<String> requestTranscription(Resource audioFile);

    /**
     * STT 작업의 현재 상태를 조회합니다.
     *
     * @param jobId 조회할 작업 ID
     * @return STT 결과를 포함하는 Mono
     */
    Mono<SttTranscriptionResult> checkTranscriptionStatus(String jobId);

    /**
     * 텍스트 요약을 요청합니다.
     *
     * @param text 요약할 원본 텍스트
     * @return 비동기 작업 ID를 포함하는 Mono
     */
    Mono<String> requestSummary(String text);

    /**
     * 요약 작업의 현재 상태를 조회합니다.
     *
     * @param jobId 조회할 작업 ID
     * @return 요약 결과를 포함하는 Mono
     */
    Mono<SttSummaryResult> checkSummaryStatus(String jobId);
}

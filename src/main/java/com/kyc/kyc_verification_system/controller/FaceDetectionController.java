package com.kyc.kyc_verification_system.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.kyc.kyc_verification_system.dto.PythonCheckResultResponse;
import com.kyc.kyc_verification_system.service.PythonApiService;

@RestController
@RequestMapping("/kyc")
@CrossOrigin("*")
public class FaceDetectionController {

    private static final Logger log = LoggerFactory.getLogger(FaceDetectionController.class);

    private final WebClient webClient;
    private final PythonApiService pythonApiService;

    public FaceDetectionController(PythonApiService pythonApiService) {
        this.webClient = WebClient.builder()
                .baseUrl("http://localhost:5000")
                .build();
        this.pythonApiService = pythonApiService;
    }

    @PostMapping(value = "/check-result", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PythonCheckResultResponse> checkResult(
            @RequestParam("file") MultipartFile file,
            @RequestParam("session_id") UUID sessionId,
            @RequestParam("attempt_no") int attemptNo) throws Exception {

        PythonCheckResultResponse response = pythonApiService.postCheckResult(
                sessionId.toString(), file.getBytes(), attemptNo);

        System.out.println("[check_result] response: " + response);
        log.info("[check_result] session={} attempt={} response={}", sessionId, attemptNo, response);

        return ResponseEntity.ok(response);
    }

    @GetMapping(
            value = "/video-feed",
            produces = MediaType.MULTIPART_MIXED_VALUE
    )
    public StreamingResponseBody videoFeed(
           ) {

        return outputStream -> {

            webClient.get()
                    .uri("/video_feed")
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .toStream()
                    .forEach(dataBuffer -> {

                        try {

                            byte[] bytes =
                                    new byte[dataBuffer.readableByteCount()];

                            dataBuffer.read(bytes);

                            outputStream.write(bytes);

                            outputStream.flush();

                        } catch (Exception e) {

                            e.printStackTrace();
                        }
                    });
        };
    }
}
package com.hexbank.shared.adapter.in.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/** Monta o problem+json padrão (code + requestId); reaproveitado pelos advices dos módulos. */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static ResponseEntity<ProblemDetail> respond(ProblemDetail problem, String code, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("requestId", RequestIdFilter.currentRequestId(request));
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}

package com.hexbank.accounts.adapter.in.http;

import com.hexbank.accounts.adapter.in.http.request.OpenAccountRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/v1/accounts")
public class AccountController {

    @PostMapping
    public ResponseEntity<?> open(@RequestBody  OpenAccountRequest request) {
        return ResponseEntity.status(200).build();
    }
}

package com.ticketwar.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminSessionController {

    @GetMapping("/api/admin/auth/session")
    public Map<String, Boolean> currentSession() {
        return Map.of("authenticated", true);
    }
}

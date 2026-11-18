package com.backendapp.demo.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
    String mensaje,
    LocalDateTime enviadoEn
) {
}

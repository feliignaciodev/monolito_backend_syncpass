package com.backendapp.demo.dto;

import jakarta.validation.constraints.NotNull;

public record ActivoPatchRequest(@NotNull Boolean activo) {
}
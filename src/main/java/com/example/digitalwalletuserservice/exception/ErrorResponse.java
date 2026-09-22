package com.example.digitalwalletuserservice.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private int status;
    private String message;
    private LocalDateTime timestamp;
}
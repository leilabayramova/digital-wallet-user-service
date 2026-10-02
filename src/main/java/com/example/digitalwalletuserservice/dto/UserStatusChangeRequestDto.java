package com.example.digitalwalletuserservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class UserStatusChangeRequestDto {

    @NotNull(message = "UpdatedAt is required")
    private LocalDateTime updatedAt;
}

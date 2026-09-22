package com.example.digitalwalletuserservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserRequestDto {

    @NotBlank(message = "Full name cannot be empty")
    @Size(min = 2, max = 150, message = "Full name must contain between 2 and 150 characters")
    private String fullName;
}
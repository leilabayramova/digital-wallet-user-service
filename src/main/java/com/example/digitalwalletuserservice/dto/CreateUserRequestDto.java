package com.example.digitalwalletuserservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequestDto {

    @NotBlank(message = "Full name cannot be empty")
    @Size(min = 2, max = 150, message = "Full name must contain between 2 and 150 characters")
    private String fullName;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email format is invalid")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;
}
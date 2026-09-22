package com.example.digitalwalletuserservice.controller;

import com.example.digitalwalletuserservice.dto.CreateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UpdateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UserResponseDto;
import com.example.digitalwalletuserservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDto createUser(
            @Valid @RequestBody CreateUserRequestDto requestDto
    ) {
        return userService.createUser(requestDto);
    }

    @GetMapping("/{id}")
    public UserResponseDto getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PatchMapping("/{id}")
    public UserResponseDto updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequestDto requestDto
    ) {
        return userService.updateUser(id, requestDto);
    }
    @PatchMapping("/{id}/activate")
    public UserResponseDto activateUser(@PathVariable Long id) {
        return userService.activateUser(id);
    }

    @PatchMapping("/{id}/deactivate")
    public UserResponseDto deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }
}
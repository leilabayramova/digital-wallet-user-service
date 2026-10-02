package com.example.digitalwalletuserservice.controller;

import com.example.digitalwalletuserservice.dto.CreateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UpdateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UserResponseDto;
import com.example.digitalwalletuserservice.dto.UserStatusChangeRequestDto;
import com.example.digitalwalletuserservice.service.IdempotencyService;
import com.example.digitalwalletuserservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(
            @Valid @RequestBody CreateUserRequestDto requestDto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<UserResponseDto> existingResponse =
                    idempotencyService.findResponse(idempotencyKey, requestDto, UserResponseDto.class);

            if (existingResponse.isPresent()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(existingResponse.get());
            }
        }

        UserResponseDto createdUser = userService.createUser(requestDto);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.saveResponse(idempotencyKey, requestDto, createdUser);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @GetMapping("/{id}")
    public UserResponseDto getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping
    public Page<UserResponseDto> getAllUsers(
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return userService.getAllUsers(pageable);
    }

    @PatchMapping("/{id}")
    public UserResponseDto updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequestDto requestDto
    ) {
        return userService.updateUser(id, requestDto);
    }
    @PatchMapping("/{id}/activate")
    public UserResponseDto activateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusChangeRequestDto requestDto
    ) {
        return userService.activateUser(id, requestDto);
    }

    @PatchMapping("/{id}/deactivate")
    public UserResponseDto deactivateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusChangeRequestDto requestDto
    ) {
        return userService.deactivateUser(id, requestDto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
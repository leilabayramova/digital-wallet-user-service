package com.example.digitalwalletuserservice.controller;

import com.example.digitalwalletuserservice.dto.CreateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UpdateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UserResponseDto;
import com.example.digitalwalletuserservice.dto.UserStatusChangeRequestDto;
import com.example.digitalwalletuserservice.exception.EmailAlreadyExistsException;
import com.example.digitalwalletuserservice.exception.UserNotFoundException;
import com.example.digitalwalletuserservice.service.IdempotencyService;
import com.example.digitalwalletuserservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private IdempotencyService idempotencyService;

    private UserResponseDto sampleResponse() {
        return UserResponseDto.builder()
                .id(1L)
                .fullName("John Doe")
                .email("john.doe@example.com")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createUser_shouldReturn201() throws Exception {
        CreateUserRequestDto requestDto = new CreateUserRequestDto();
        requestDto.setFullName("John Doe");
        requestDto.setEmail("john.doe@example.com");

        when(userService.createUser(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
    }

    @Test
    void createUser_shouldReturn400_whenEmailInvalid() throws Exception {
        CreateUserRequestDto requestDto = new CreateUserRequestDto();
        requestDto.setFullName("John Doe");
        requestDto.setEmail("not-an-email");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        CreateUserRequestDto requestDto = new CreateUserRequestDto();
        requestDto.setFullName("John Doe");
        requestDto.setEmail("john.doe@example.com");

        when(userService.createUser(any()))
                .thenThrow(new EmailAlreadyExistsException("User already exists with email: john.doe@example.com"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void createUser_shouldReplayStoredResponse_whenIdempotencyKeyAlreadyUsed() throws Exception {
        CreateUserRequestDto requestDto = new CreateUserRequestDto();
        requestDto.setFullName("John Doe");
        requestDto.setEmail("john.doe@example.com");

        UserResponseDto storedResponse = sampleResponse();

        when(idempotencyService.findResponse(eq("key-123"), any(), eq(UserResponseDto.class)))
                .thenReturn(Optional.of(storedResponse));

        mockMvc.perform(post("/api/users")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_shouldStoreResponse_whenIdempotencyKeyNotSeenBefore() throws Exception {
        CreateUserRequestDto requestDto = new CreateUserRequestDto();
        requestDto.setFullName("John Doe");
        requestDto.setEmail("john.doe@example.com");

        when(idempotencyService.findResponse(eq("key-456"), any(), eq(UserResponseDto.class)))
                .thenReturn(Optional.empty());
        when(userService.createUser(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/users")
                        .header("Idempotency-Key", "key-456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated());

        verify(idempotencyService).saveResponse(eq("key-456"), any(), any(UserResponseDto.class));
    }

    @Test
    void createUser_shouldReturn409_whenIdempotencyKeyReusedWithDifferentPayload() throws Exception {
        CreateUserRequestDto requestDto = new CreateUserRequestDto();
        requestDto.setFullName("John Doe");
        requestDto.setEmail("john.doe@example.com");

        when(idempotencyService.findResponse(eq("key-789"), any(), eq(UserResponseDto.class)))
                .thenThrow(new com.example.digitalwalletuserservice.exception.IdempotencyKeyConflictException(
                        "Idempotency-Key 'key-789' was already used with a different request payload."));

        mockMvc.perform(post("/api/users")
                        .header("Idempotency-Key", "key-789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict());

        verify(userService, never()).createUser(any());
    }

    @Test
    void updateUser_shouldReturn409_whenOptimisticLockingFails() throws Exception {
        UpdateUserRequestDto requestDto = new UpdateUserRequestDto();
        requestDto.setFullName("Jane Doe");
        requestDto.setVersion(3L);

        when(userService.updateUser(eq(1L), any()))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        mockMvc.perform(patch("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void getUserById_shouldReturn200() throws Exception {
        when(userService.getUserById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUserById_shouldReturn404_whenNotFound() throws Exception {
        when(userService.getUserById(99L))
                .thenThrow(new UserNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllUsers_shouldReturnPagedResult() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserResponseDto> page = new PageImpl<>(List.of(sampleResponse()), pageable, 1);

        when(userService.getAllUsers(any())).thenReturn(page);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void updateUser_shouldReturn200() throws Exception {
        UpdateUserRequestDto requestDto = new UpdateUserRequestDto();
        requestDto.setFullName("Jane Doe");
        requestDto.setVersion(0L);

        when(userService.updateUser(eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateUser_shouldReturn400_whenVersionMissing() throws Exception {
        UpdateUserRequestDto requestDto = new UpdateUserRequestDto();
        requestDto.setFullName("Jane Doe");

        mockMvc.perform(patch("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateUser_shouldReturn200() throws Exception {
        UserStatusChangeRequestDto requestDto = new UserStatusChangeRequestDto();
        requestDto.setUpdatedAt(LocalDateTime.now());

        when(userService.activateUser(eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/users/1/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk());
    }

    @Test
    void activateUser_shouldReturn409_whenUpdatedAtIsStale() throws Exception {
        UserStatusChangeRequestDto requestDto = new UserStatusChangeRequestDto();
        requestDto.setUpdatedAt(LocalDateTime.now());

        when(userService.activateUser(eq(1L), any()))
                .thenThrow(new OptimisticLockingFailureException("stale updatedAt"));

        mockMvc.perform(patch("/api/users/1/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivateUser_shouldReturn200() throws Exception {
        UserStatusChangeRequestDto requestDto = new UserStatusChangeRequestDto();
        requestDto.setUpdatedAt(LocalDateTime.now());

        when(userService.deactivateUser(eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/users/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk());
    }

    @Test
    void deactivateUser_shouldReturn409_whenUpdatedAtIsStale() throws Exception {
        UserStatusChangeRequestDto requestDto = new UserStatusChangeRequestDto();
        requestDto.setUpdatedAt(LocalDateTime.now());

        when(userService.deactivateUser(eq(1L), any()))
                .thenThrow(new OptimisticLockingFailureException("stale updatedAt"));

        mockMvc.perform(patch("/api/users/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteUser_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUser_shouldReturn404_whenNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new UserNotFoundException("User not found with id: 99"))
                .when(userService).deleteUser(99L);

        mockMvc.perform(delete("/api/users/99"))
                .andExpect(status().isNotFound());
    }
}

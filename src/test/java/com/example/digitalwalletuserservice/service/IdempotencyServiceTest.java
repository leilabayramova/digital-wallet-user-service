package com.example.digitalwalletuserservice.service;

import com.example.digitalwalletuserservice.dto.CreateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UserResponseDto;
import com.example.digitalwalletuserservice.entity.IdempotencyKeyEntity;
import com.example.digitalwalletuserservice.exception.IdempotencyKeyConflictException;
import com.example.digitalwalletuserservice.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private IdempotencyService idempotencyService;

    private IdempotencyService service() {
        return new IdempotencyService(idempotencyKeyRepository, objectMapper);
    }

    private String hash(Object payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void findResponse_shouldReturnEmpty_whenKeyNotStored() {
        idempotencyService = service();

        CreateUserRequestDto requestPayload = new CreateUserRequestDto();
        requestPayload.setFullName("John Doe");
        requestPayload.setEmail("john.doe@example.com");

        when(idempotencyKeyRepository.findByIdempotencyKey("missing-key")).thenReturn(Optional.empty());

        Optional<UserResponseDto> result =
                idempotencyService.findResponse("missing-key", requestPayload, UserResponseDto.class);

        assertThat(result).isEmpty();
    }

    @Test
    void findResponse_shouldDeserializeStoredResponse_whenPayloadMatches() {
        idempotencyService = service();

        CreateUserRequestDto requestPayload = new CreateUserRequestDto();
        requestPayload.setFullName("John Doe");
        requestPayload.setEmail("john.doe@example.com");

        UserResponseDto original = UserResponseDto.builder()
                .id(1L)
                .fullName("John Doe")
                .email("john.doe@example.com")
                .active(true)
                .build();

        IdempotencyKeyEntity entity = IdempotencyKeyEntity.builder()
                .idempotencyKey("key-123")
                .requestHash(hash(requestPayload))
                .responseBody(objectMapper.writeValueAsString(original))
                .build();

        when(idempotencyKeyRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(entity));

        Optional<UserResponseDto> result =
                idempotencyService.findResponse("key-123", requestPayload, UserResponseDto.class);

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void findResponse_shouldThrowConflict_whenPayloadDiffersFromStoredHash() {
        idempotencyService = service();

        CreateUserRequestDto originalPayload = new CreateUserRequestDto();
        originalPayload.setFullName("John Doe");
        originalPayload.setEmail("john.doe@example.com");

        CreateUserRequestDto differentPayload = new CreateUserRequestDto();
        differentPayload.setFullName("Jane Doe");
        differentPayload.setEmail("jane.doe@example.com");

        IdempotencyKeyEntity entity = IdempotencyKeyEntity.builder()
                .idempotencyKey("key-123")
                .requestHash(hash(originalPayload))
                .responseBody("{}")
                .build();

        when(idempotencyKeyRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() ->
                idempotencyService.findResponse("key-123", differentPayload, UserResponseDto.class))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void saveResponse_shouldPersistSerializedEntityWithRequestHash() {
        idempotencyService = service();

        CreateUserRequestDto requestPayload = new CreateUserRequestDto();
        requestPayload.setFullName("John Doe");
        requestPayload.setEmail("john.doe@example.com");

        UserResponseDto response = UserResponseDto.builder()
                .id(1L)
                .email("john.doe@example.com")
                .build();

        idempotencyService.saveResponse("key-123", requestPayload, response);

        verify(idempotencyKeyRepository).saveAndFlush(any(IdempotencyKeyEntity.class));
    }

    @Test
    void saveResponse_shouldSwallowConflict_whenKeyAlreadyStoredConcurrently() {
        idempotencyService = service();

        CreateUserRequestDto requestPayload = new CreateUserRequestDto();
        requestPayload.setFullName("John Doe");
        requestPayload.setEmail("john.doe@example.com");

        UserResponseDto response = UserResponseDto.builder()
                .id(1L)
                .email("john.doe@example.com")
                .build();

        when(idempotencyKeyRepository.saveAndFlush(any(IdempotencyKeyEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        idempotencyService.saveResponse("key-123", requestPayload, response);
    }
}

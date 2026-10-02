package com.example.digitalwalletuserservice.service;

import com.example.digitalwalletuserservice.entity.IdempotencyKeyEntity;
import com.example.digitalwalletuserservice.exception.IdempotencyKeyConflictException;
import com.example.digitalwalletuserservice.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> findResponse(String idempotencyKey, Object requestPayload, Class<T> responseType) {
        Optional<IdempotencyKeyEntity> existingEntity =
                idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);

        if (existingEntity.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyKeyEntity entity = existingEntity.get();

        if (!entity.getRequestHash().equals(hash(requestPayload))) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key '" + idempotencyKey + "' was already used with a different request payload."
            );
        }

        return Optional.of(objectMapper.readValue(entity.getResponseBody(), responseType));
    }

    @Transactional
    public void saveResponse(String idempotencyKey, Object requestPayload, Object response) {
        IdempotencyKeyEntity entity = IdempotencyKeyEntity.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(hash(requestPayload))
                .responseBody(objectMapper.writeValueAsString(response))
                .build();

        try {
            idempotencyKeyRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            log.info("Idempotency key already stored by a concurrent request. idempotencyKey={}", idempotencyKey);
        }
    }

    private String hash(Object payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}

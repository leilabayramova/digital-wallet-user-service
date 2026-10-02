package com.example.digitalwalletuserservice.service;

import com.example.digitalwalletuserservice.dto.CreateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UpdateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UserResponseDto;
import com.example.digitalwalletuserservice.dto.UserStatusChangeRequestDto;
import com.example.digitalwalletuserservice.entity.UserEntity;
import com.example.digitalwalletuserservice.exception.EmailAlreadyExistsException;
import com.example.digitalwalletuserservice.exception.UserNotFoundException;
import com.example.digitalwalletuserservice.mapper.UserMapper;
import com.example.digitalwalletuserservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponseDto createUser(CreateUserRequestDto requestDto) {
        String normalizedEmail = requestDto.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("User creation failed: email already exists");

            throw new EmailAlreadyExistsException(
                    "User already exists with email: " + normalizedEmail
            );
        }

        UserEntity userEntity = UserMapper.toEntity(requestDto);
        userEntity.setEmail(normalizedEmail);

        UserEntity savedUserEntity = userRepository.save(userEntity);

        log.info("User created successfully. userId={}", savedUserEntity.getId());

        return UserMapper.toResponseDto(savedUserEntity);
    }

    public UserResponseDto getUserById(Long id) {
        UserEntity userEntity = getUserEntityById(id);

        return UserMapper.toResponseDto(userEntity);
    }

    public Page<UserResponseDto> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(UserMapper::toResponseDto);
    }


    public UserResponseDto updateUser(Long id, UpdateUserRequestDto requestDto) {
        UserEntity userEntity = getUserEntityById(id);

        if (!userEntity.getVersion().equals(requestDto.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "User was updated by another request. Expected version "
                            + userEntity.getVersion() + " but got " + requestDto.getVersion()
            );
        }

        UserMapper.updateEntity(userEntity, requestDto);

        UserEntity updatedUserEntity = userRepository.save(userEntity);

        log.info("User updated successfully. userId={}", updatedUserEntity.getId());

        return UserMapper.toResponseDto(updatedUserEntity);
    };


    public UserResponseDto activateUser(Long id, UserStatusChangeRequestDto requestDto) {
        UserEntity userEntity = getUserEntityById(id);

        checkNotStale(userEntity, requestDto.getUpdatedAt());

        userEntity.setActive(true);

        UserEntity updatedUserEntity = userRepository.save(userEntity);

        log.info("User activated successfully. userId={}", updatedUserEntity.getId());

        return UserMapper.toResponseDto(updatedUserEntity);
    }

    public UserResponseDto deactivateUser(Long id, UserStatusChangeRequestDto requestDto) {
        UserEntity userEntity = getUserEntityById(id);

        checkNotStale(userEntity, requestDto.getUpdatedAt());

        userEntity.setActive(false);

        UserEntity updatedUserEntity = userRepository.save(userEntity);

        log.info("User deactivated successfully. userId={}", updatedUserEntity.getId());

        return UserMapper.toResponseDto(updatedUserEntity);
    }

    private void checkNotStale(UserEntity userEntity, LocalDateTime expectedUpdatedAt) {
        if (!userEntity.getUpdatedAt().isEqual(expectedUpdatedAt)) {
            throw new OptimisticLockingFailureException(
                    "User was updated by another request. Expected updatedAt "
                            + userEntity.getUpdatedAt() + " but got " + expectedUpdatedAt
            );
        }
    }

    public void deleteUser(Long id) {
        UserEntity userEntity = getUserEntityById(id);

        userRepository.delete(userEntity);

        log.info("User deleted successfully. userId={}", id);
    }

    private UserEntity getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + id)
                );
    }
}
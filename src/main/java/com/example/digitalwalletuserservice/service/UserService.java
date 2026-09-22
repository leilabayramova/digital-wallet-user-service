package com.example.digitalwalletuserservice.service;

import com.example.digitalwalletuserservice.dto.CreateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UpdateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UserResponseDto;
import com.example.digitalwalletuserservice.entity.UserEntity;
import com.example.digitalwalletuserservice.exception.EmailAlreadyExistsException;
import com.example.digitalwalletuserservice.exception.UserNotFoundException;
import com.example.digitalwalletuserservice.mapper.UserMapper;
import com.example.digitalwalletuserservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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


    public UserResponseDto updateUser(Long id, UpdateUserRequestDto requestDto) {
        UserEntity userEntity = getUserEntityById(id);

        UserMapper.updateEntity(userEntity, requestDto);

        UserEntity updatedUserEntity = userRepository.save(userEntity);

        log.info("User updated successfully. userId={}", updatedUserEntity.getId());

        return UserMapper.toResponseDto(updatedUserEntity);
    };


    public UserResponseDto activateUser(Long id) {
        UserEntity userEntity = getUserEntityById(id);

        userEntity.setActive(true);

        UserEntity updatedUserEntity = userRepository.save(userEntity);

        log.info("User activated successfully. userId={}", updatedUserEntity.getId());

        return UserMapper.toResponseDto(updatedUserEntity);
    }

    public UserResponseDto deactivateUser(Long id) {
        UserEntity userEntity = getUserEntityById(id);

        userEntity.setActive(false);

        UserEntity updatedUserEntity = userRepository.save(userEntity);

        log.info("User deactivated successfully. userId={}", updatedUserEntity.getId());

        return UserMapper.toResponseDto(updatedUserEntity);
    }

    private UserEntity getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + id)
                );
    }
}
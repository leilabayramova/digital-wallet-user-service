package com.example.digitalwalletuserservice.mapper;


import com.example.digitalwalletuserservice.dto.CreateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UpdateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UserResponseDto;
import com.example.digitalwalletuserservice.entity.UserEntity;
public interface UserMapper {

    static UserEntity toEntity(CreateUserRequestDto requestDto) {
        return UserEntity.builder()
                .fullName(requestDto.getFullName())
                .email(requestDto.getEmail())
                .build();
    }

    static UserResponseDto toResponseDto(UserEntity userEntity) {
        return UserResponseDto.builder()
                .id(userEntity.getId())
                .fullName(userEntity.getFullName())
                .email(userEntity.getEmail())
                .active(userEntity.isActive())
                .createdAt(userEntity.getCreatedAt())
                .updatedAt(userEntity.getUpdatedAt())
                .build();
    }

    static void updateEntity(UserEntity userEntity, UpdateUserRequestDto requestDto) {
        userEntity.setFullName(requestDto.getFullName());
    }
}
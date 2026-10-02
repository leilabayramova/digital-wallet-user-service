package com.example.digitalwalletuserservice.service;

import com.example.digitalwalletuserservice.dto.CreateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UpdateUserRequestDto;
import com.example.digitalwalletuserservice.dto.UserResponseDto;
import com.example.digitalwalletuserservice.dto.UserStatusChangeRequestDto;
import com.example.digitalwalletuserservice.entity.UserEntity;
import com.example.digitalwalletuserservice.exception.EmailAlreadyExistsException;
import com.example.digitalwalletuserservice.exception.UserNotFoundException;
import com.example.digitalwalletuserservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        userEntity = UserEntity.builder()
                .id(1L)
                .fullName("John Doe")
                .email("john.doe@example.com")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    @Test
    void createUser_shouldSaveAndReturnUser_whenEmailDoesNotExist() {
        CreateUserRequestDto requestDto = new CreateUserRequestDto();
        requestDto.setFullName("John Doe");
        requestDto.setEmail(" John.Doe@Example.com ");

        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        UserResponseDto result = userService.createUser(requestDto);

        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(result.getFullName()).isEqualTo("John Doe");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void createUser_shouldThrow_whenEmailAlreadyExists() {
        CreateUserRequestDto requestDto = new CreateUserRequestDto();
        requestDto.setFullName("John Doe");
        requestDto.setEmail("john.doe@example.com");

        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(requestDto))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserById_shouldReturnUser_whenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        UserResponseDto result = userService.getUserById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void getUserById_shouldThrow_whenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getAllUsers_shouldReturnMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserEntity> entityPage = new PageImpl<>(List.of(userEntity), pageable, 1);

        when(userRepository.findAll(pageable)).thenReturn(entityPage);

        Page<UserResponseDto> result = userService.getAllUsers(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void updateUser_shouldUpdateFullName() {
        UpdateUserRequestDto requestDto = new UpdateUserRequestDto();
        requestDto.setFullName("Jane Doe");
        requestDto.setVersion(0L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        UserResponseDto result = userService.updateUser(1L, requestDto);

        assertThat(result.getFullName()).isEqualTo("Jane Doe");
    }

    @Test
    void updateUser_shouldThrow_whenNotFound() {
        UpdateUserRequestDto requestDto = new UpdateUserRequestDto();
        requestDto.setFullName("Jane Doe");
        requestDto.setVersion(0L);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(99L, requestDto))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrow_whenVersionIsStale() {
        UpdateUserRequestDto requestDto = new UpdateUserRequestDto();
        requestDto.setFullName("Jane Doe");
        requestDto.setVersion(5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        assertThatThrownBy(() -> userService.updateUser(1L, requestDto))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void activateUser_shouldSetActiveTrue() {
        userEntity.setActive(false);

        UserStatusChangeRequestDto requestDto = new UserStatusChangeRequestDto();
        requestDto.setUpdatedAt(userEntity.getUpdatedAt());

        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        UserResponseDto result = userService.activateUser(1L, requestDto);

        assertThat(result.isActive()).isTrue();
    }

    @Test
    void activateUser_shouldThrow_whenUpdatedAtIsStale() {
        UserStatusChangeRequestDto requestDto = new UserStatusChangeRequestDto();
        requestDto.setUpdatedAt(userEntity.getUpdatedAt().minusMinutes(5));

        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        assertThatThrownBy(() -> userService.activateUser(1L, requestDto))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_shouldSetActiveFalse() {
        UserStatusChangeRequestDto requestDto = new UserStatusChangeRequestDto();
        requestDto.setUpdatedAt(userEntity.getUpdatedAt());

        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        UserResponseDto result = userService.deactivateUser(1L, requestDto);

        assertThat(result.isActive()).isFalse();
    }

    @Test
    void deactivateUser_shouldThrow_whenUpdatedAtIsStale() {
        UserStatusChangeRequestDto requestDto = new UserStatusChangeRequestDto();
        requestDto.setUpdatedAt(userEntity.getUpdatedAt().minusMinutes(5));

        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        assertThatThrownBy(() -> userService.deactivateUser(1L, requestDto))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_shouldDeleteEntity_whenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        userService.deleteUser(1L);

        verify(userRepository).delete(userEntity);
    }

    @Test
    void deleteUser_shouldThrow_whenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).delete(any(UserEntity.class));
    }
}

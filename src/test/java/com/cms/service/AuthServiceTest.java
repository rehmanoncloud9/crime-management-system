package com.cms.service;

import com.cms.model.User;
import com.cms.model.enums.UserStatus;
import com.cms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService();

        // ✅ IMPORTANT: disable DB session
        authService.setSessionEnabled(false);
    }

    @Test
    void testAuthenticate_UserNotFound() {
        when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> 
            authService.authenticateInternal("unknown", "password", userRepository, null));
    }

    @Test
    void testAuthenticate_UserLocked() {
        User user = new User();
        user.setUsername("lockedUser");
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("lockedUser")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("lockedUser")).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () -> 
            authService.authenticateInternal("lockedUser", "password", userRepository, null));
    }

    @Test
    void testAuthenticate_UserInactive() {
        User user = new User();
        user.setUsername("inactiveUser");
        user.setStatus(UserStatus.SUSPENDED);

        when(userRepository.findByEmail("inactiveUser")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("inactiveUser")).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () -> 
            authService.authenticateInternal("inactiveUser", "password", userRepository, null));
    }

    @Test
    void testAuthenticate_Success() {
        User user = new User();
        user.setUsername("testUser");

        String hash = BCrypt.hashpw("correctPassword", BCrypt.gensalt());
        user.setPasswordHash(hash);

        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(3);

        when(userRepository.findByEmail("testUser")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(user));

        User result = authService.authenticateInternal("testUser", "correctPassword", userRepository, null);

        assertNotNull(result);
        assertEquals("testUser", result.getUsername());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());

        verify(userRepository).update(user);
    }

    @Test
    void testAuthenticate_WrongPassword_LocksUser() {
        User user = new User();
        user.setUsername("testUser");

        String hash = BCrypt.hashpw("correctPassword", BCrypt.gensalt());
        user.setPasswordHash(hash);

        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(4);

        when(userRepository.findByEmail("testUser")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> 
            authService.authenticateInternal("testUser", "wrongPassword", userRepository, null));

        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());

        verify(userRepository, atLeastOnce()).update(user);
    }
}
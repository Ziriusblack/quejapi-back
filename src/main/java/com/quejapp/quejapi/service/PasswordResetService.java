package com.quejapp.quejapi.service;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.quejapp.quejapi.model.PasswordResetToken;
import com.quejapp.quejapi.model.User;
import com.quejapp.quejapi.repository.PasswordResetTokenRepository;
import com.quejapp.quejapi.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private static final String INVALID = "Código inválido o expirado.";

    private final SecureRandom random = new SecureRandom();
    private final UserRepository usersRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /**
     * Genera y envía un código. No revela si el correo existe: siempre termina igual.
     */
    public void requestReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        if (email.isEmpty()) return;

        Optional<User> userOpt = usersRepo.findByEmail(email);
        if (userOpt.isEmpty()) return;
        User user = userOpt.get();

        Optional<PasswordResetToken> previoOpt = tokenRepo.findFirstByEmailOrderByCreatedAtDesc(email);
        if (previoOpt.isPresent() && previoOpt.get().getCreatedAt() != null
                && System.currentTimeMillis() - previoOpt.get().getCreatedAt().getTime() < RESEND_COOLDOWN_SECONDS * 1000L) {
            return;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        Date now = new Date();

        // Reutiliza el documento si existe para evitar duplicados o fallos de deleteByEmail
        PasswordResetToken resetToken = previoOpt.orElseGet(PasswordResetToken::new);
        resetToken.setEmail(email);
        resetToken.setCodeHash(passwordEncoder.encode(code));
        resetToken.setCreatedAt(now);
        resetToken.setExpiresAt(new Date(now.getTime() + EXPIRATION_MINUTES * 60_000L));
        resetToken.setAttempts(0);

        tokenRepo.save(resetToken);

        final String nombre = user.getFirstname();
        CompletableFuture.runAsync(() -> emailService.sendResetCode(email, nombre, code));
    }

    /**
     * Valida el código y cambia la contraseña. Lanza IllegalArgumentException con un mensaje para el usuario.
     */
    public void resetPassword(String rawEmail, String code, String newPassword) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();

        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException(INVALID);
        }

        PasswordResetToken token = tokenRepo.findFirstByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException(INVALID));

        if (token.getExpiresAt() == null || token.getExpiresAt().before(new Date())) {
            tokenRepo.delete(token);
            throw new IllegalArgumentException(INVALID);
        }
        if (token.getAttempts() >= MAX_ATTEMPTS) {
            tokenRepo.delete(token);
            throw new IllegalArgumentException("Demasiados intentos. Solicita un código nuevo.");
        }
        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            token.setAttempts(token.getAttempts() + 1);
            tokenRepo.save(token);
            throw new IllegalArgumentException(INVALID);
        }

        User user = usersRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(INVALID));
        user.setPassword(passwordEncoder.encode(newPassword));
        usersRepo.save(user);

        tokenRepo.delete(token);
        log.info("Contraseña restablecida para {}", email);
    }
}
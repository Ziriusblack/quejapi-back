package com.quejapp.quejapi.model;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Código de recuperación de contraseña (se guarda solo el hash, nunca el código). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("password_resets")
public class PasswordResetToken {
    @Id
    private String id;

    @Indexed
    private String email;

    private String codeHash;

    // Si tienes auto-index-creation activo, Mongo borra el documento solo al vencer.
    // De todos modos el servicio valida la expiración por código.
    @Indexed(expireAfterSeconds = 0)
    private Date expiresAt;

    private Date createdAt;
    private int attempts;
}

package com.goodfellaz17.account.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * SpotifyAccount entity for R2DBC reactive database access.
 * Maps to PostgreSQL pipeline_spotify_accounts table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("pipeline_spotify_accounts")
public class SpotifyAccount {

    @Id
    private Long id;

    @Column("email")
    private String email;

    @Column("password")
    private String password;

    @Column("spotify_user_id")
    private String spotifyUserId;

    @Column("status")
    private String status; // CREATED, PENDING_EMAIL_VERIFICATION, ACTIVE, DEGRADED, BANNED

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("last_played_at")
    private LocalDateTime lastPlayedAt;

    @Column("created_date")
    private LocalDateTime createdDate;

    @Column("updated_date")
    private LocalDateTime updatedDate;

    /**
     * Constructor for creating new accounts with minimal fields
     */
    public SpotifyAccount(String email, String password, String spotifyUserId, String status) {
        this.email = email;
        this.password = password;
        this.spotifyUserId = spotifyUserId;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.createdDate = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "SpotifyAccount{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", spotifyUserId='" + spotifyUserId + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", lastPlayedAt=" + lastPlayedAt +
                '}';
    }
}

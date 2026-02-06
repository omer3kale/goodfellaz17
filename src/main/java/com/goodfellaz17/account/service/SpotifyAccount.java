package com.goodfellaz17.account.service;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * SpotifyAccount entity for R2DBC reactive database access.
 * Maps to PostgreSQL pipeline_spotify_accounts table.
 *
 * Note: Explicit getters/setters used instead of Lombok due to
 * Java 25 + Lombok 1.18.x annotation processor compatibility issues.
 */
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
     * No-args constructor required by R2DBC
     */
    public SpotifyAccount() {
    }

    /**
     * All-args constructor
     */
    public SpotifyAccount(Long id, String email, String password, String spotifyUserId,
                          String status, LocalDateTime createdAt, LocalDateTime lastPlayedAt,
                          LocalDateTime createdDate, LocalDateTime updatedDate) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.spotifyUserId = spotifyUserId;
        this.status = status;
        this.createdAt = createdAt;
        this.lastPlayedAt = lastPlayedAt;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

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

    // Explicit getters for IDE compatibility (Lombok should generate these but VS Code LSP has issues)
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getSpotifyUserId() { return spotifyUserId; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastPlayedAt() { return lastPlayedAt; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public LocalDateTime getUpdatedDate() { return updatedDate; }

    // Explicit setters for IDE compatibility
    public void setId(Long id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setSpotifyUserId(String spotifyUserId) { this.spotifyUserId = spotifyUserId; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setLastPlayedAt(LocalDateTime lastPlayedAt) { this.lastPlayedAt = lastPlayedAt; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    public void setUpdatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; }

    /**
     * Builder pattern - static factory method
     */
    public static SpotifyAccountBuilder builder() {
        return new SpotifyAccountBuilder();
    }

    /**
     * Builder class for SpotifyAccount
     */
    public static class SpotifyAccountBuilder {
        private Long id;
        private String email;
        private String password;
        private String spotifyUserId;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime lastPlayedAt;
        private LocalDateTime createdDate;
        private LocalDateTime updatedDate;

        public SpotifyAccountBuilder id(Long id) { this.id = id; return this; }
        public SpotifyAccountBuilder email(String email) { this.email = email; return this; }
        public SpotifyAccountBuilder password(String password) { this.password = password; return this; }
        public SpotifyAccountBuilder spotifyUserId(String spotifyUserId) { this.spotifyUserId = spotifyUserId; return this; }
        public SpotifyAccountBuilder status(String status) { this.status = status; return this; }
        public SpotifyAccountBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public SpotifyAccountBuilder lastPlayedAt(LocalDateTime lastPlayedAt) { this.lastPlayedAt = lastPlayedAt; return this; }
        public SpotifyAccountBuilder createdDate(LocalDateTime createdDate) { this.createdDate = createdDate; return this; }
        public SpotifyAccountBuilder updatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; return this; }

        public SpotifyAccount build() {
            return new SpotifyAccount(id, email, password, spotifyUserId, status,
                                      createdAt, lastPlayedAt, createdDate, updatedDate);
        }
    }
}

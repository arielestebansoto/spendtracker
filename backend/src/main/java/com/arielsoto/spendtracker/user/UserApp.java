package com.arielsoto.spendtracker.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"oauth_provider", "oauth_id"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserApp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false)
    private OAuthProvider oauthProvider;

    @Column(name = "oauth_id", nullable = false, length = 255)
    private String oauthId;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "privacy_policy_version", length = 50)
    private String privacyPolicyVersion;

    @Column(name = "terms_version", length = 50)
    private String termsVersion;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
}
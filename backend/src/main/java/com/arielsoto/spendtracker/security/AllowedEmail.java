package com.arielsoto.spendtracker.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Only read by the app: rows are managed by hand with SQL, see docs/access-allowlist.md.
@Entity
@Table(name = "allowed_emails")
@Getter
@NoArgsConstructor
public class AllowedEmail {

    @Id
    @Column(length = 255)
    private String email;
}

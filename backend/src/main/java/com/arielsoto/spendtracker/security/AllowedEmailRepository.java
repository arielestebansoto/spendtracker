package com.arielsoto.spendtracker.security;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedEmailRepository extends JpaRepository<AllowedEmail, String> {
}

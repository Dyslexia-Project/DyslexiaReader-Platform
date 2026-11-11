package com.example.dyslexiaapp.speech;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TtsCacheRepository extends JpaRepository<TtsCacheEntry, String> {
    void deleteByExpiresAtBefore(Instant now);
}

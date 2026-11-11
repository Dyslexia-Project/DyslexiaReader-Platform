package com.example.dyslexiaapp.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingProfileRepository extends JpaRepository<ReadingProfile, UUID> {
    List<ReadingProfile> findAllByPresetTrueOrderByDisplayNameAsc();
    Optional<ReadingProfile> findByShareCode(String shareCode);
    boolean existsByShareCode(String shareCode);
}

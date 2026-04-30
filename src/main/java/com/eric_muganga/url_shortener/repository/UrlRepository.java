package com.eric_muganga.url_shortener.repository;

import com.eric_muganga.url_shortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {
    Optional<Url> findByShortCode(String shortCode);

    @Query("SELECT u FROM Url u WHERE u.shortCode = ?1 AND u.isDeleted = false")
    Optional<Url> findActiveByShortCode(String shortCode);
}

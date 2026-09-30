package com.travelprice.repository;
import com.travelprice.domain.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    List<Evidence> findByDestinationSlugAndPublishedAtBetweenOrderByPublishedAtDesc(String slug, LocalDate start, LocalDate end);
    boolean existsByDestinationSlugAndUrl(String slug, String url);
}

package com.travelprice.repository;
import com.travelprice.domain.PriceMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Collection;
import java.util.List;
public interface PriceMentionRepository extends JpaRepository<PriceMention, Long> {
    @EntityGraph(attributePaths = "evidence")
    List<PriceMention> findByEvidenceIdIn(Collection<Long> ids);
}

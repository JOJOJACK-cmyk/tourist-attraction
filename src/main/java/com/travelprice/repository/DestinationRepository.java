package com.travelprice.repository;
import com.travelprice.domain.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DestinationRepository extends JpaRepository<Destination, Long> {
    Optional<Destination> findBySlug(String slug);
}

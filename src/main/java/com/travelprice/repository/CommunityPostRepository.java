package com.travelprice.repository;
import com.travelprice.domain.CommunityPost;
import org.springframework.data.jpa.repository.*;
public interface CommunityPostRepository extends JpaRepository<CommunityPost,Long>,JpaSpecificationExecutor<CommunityPost>{
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CommunityPost p where p.id=:id")
    java.util.Optional<CommunityPost> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    boolean existsByImageKeyAndHiddenFalse(String imageKey);
    boolean existsByImageKey(String imageKey);
}

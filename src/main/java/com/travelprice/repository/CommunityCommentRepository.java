package com.travelprice.repository;
import com.travelprice.domain.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CommunityCommentRepository extends JpaRepository<CommunityComment,Long>{
    List<CommunityComment> findTop100ByPostIdOrderByCreatedAtDesc(Long postId);
    void deleteByPostId(Long postId);
}

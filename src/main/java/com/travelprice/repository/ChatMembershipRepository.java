package com.travelprice.repository;
import com.travelprice.domain.ChatMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ChatMembershipRepository extends JpaRepository<ChatMembership,Long>{
    Optional<ChatMembership> findByRoomIdAndMemberId(Long roomId,Long memberId);
    List<ChatMembership> findByMemberIdAndActiveTrueOrderByIdDesc(Long memberId);
    List<ChatMembership> findByRoomIdAndActiveTrueOrderByIdAsc(Long roomId);
    long countByRoomIdAndActiveTrue(Long roomId);
    boolean existsByRoomIdAndAlias(Long roomId,String alias);
}

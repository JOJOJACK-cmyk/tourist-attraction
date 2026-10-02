package com.travelprice.repository;
import com.travelprice.domain.ChatRoom;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface ChatRoomRepository extends JpaRepository<ChatRoom,Long>{
    Optional<ChatRoom> findByInviteToken(String token);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ChatRoom r where r.id=:id") Optional<ChatRoom> lock(@Param("id")Long id);
}

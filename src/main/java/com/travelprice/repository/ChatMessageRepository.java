package com.travelprice.repository;
import com.travelprice.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ChatMessageRepository extends JpaRepository<ChatMessage,Long>{
    List<ChatMessage> findTop50ByRoomIdOrderByIdDesc(Long roomId);
    List<ChatMessage> findTop50ByRoomIdAndIdLessThanOrderByIdDesc(Long roomId,Long before);
}

package com.travelprice.api;
import com.travelprice.api.ChatModels.*;
import com.travelprice.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chat;public ChatController(ChatService chat){this.chat=chat;}
    @GetMapping("/rooms") public List<RoomView> list(Authentication auth){return chat.list(auth);}
    @PostMapping("/rooms") @ResponseStatus(HttpStatus.CREATED) public RoomView create(@Valid @RequestBody RoomInput input,Authentication auth){return chat.create(input,auth);}
    @GetMapping("/invites/{token}") public InviteView invite(@PathVariable String token,Authentication auth){return chat.invite(token,auth);}
    @PostMapping("/invites/{token}/join") public RoomView join(@PathVariable String token,@Valid @RequestBody JoinInput input,Authentication auth){return chat.join(token,input,auth);}
    @GetMapping("/rooms/{id}") public RoomView detail(@PathVariable Long id,Authentication auth){return chat.detail(id,auth);}
    @GetMapping("/rooms/{id}/messages") public History history(@PathVariable Long id,@RequestParam(required=false)Long before,Authentication auth){return chat.history(id,before,auth);}
    @PostMapping("/rooms/{id}/messages") @ResponseStatus(HttpStatus.CREATED) public MessageView send(@PathVariable Long id,@Valid @RequestBody MessageInput input,Authentication auth){return chat.send(id,input,auth);}
    @DeleteMapping("/rooms/{id}/membership") @ResponseStatus(HttpStatus.NO_CONTENT) public void leave(@PathVariable Long id,Authentication auth){chat.leave(id,auth);}
    @DeleteMapping("/rooms/{id}/participants/{participant}") @ResponseStatus(HttpStatus.NO_CONTENT) public void kick(@PathVariable Long id,@PathVariable Long participant,Authentication auth){chat.kick(id,participant,auth);}
    @PostMapping("/rooms/{id}/close") @ResponseStatus(HttpStatus.NO_CONTENT) public void close(@PathVariable Long id,Authentication auth){chat.close(id,auth);}
}

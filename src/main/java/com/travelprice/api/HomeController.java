package com.travelprice.api;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.security.core.Authentication;
import com.travelprice.service.MemberService;
@Controller
public class HomeController {
    private final MemberService members;
    public HomeController(MemberService members) { this.members=members; }
    @ModelAttribute("admin")
    public boolean admin(Authentication auth) { var member=members.current(auth); return member!=null && member.isAdmin(); }
    @GetMapping("/")
    public String home() { return "index"; }
    @GetMapping("/destinations/sokcho")
    public String sokchoIntroduction() { return "destination-sokcho"; }
    @GetMapping("/community")
    public String community() { return "community"; }
    @GetMapping({"/support","/support/success","/support/fail","/support/cancel"})
    public String support() { return "support"; }
    @GetMapping("/chat")
    public String chat() { return "chat"; }
    @GetMapping("/admin/reviews")
    public String adminReviews() { return "admin-reviews"; }
}

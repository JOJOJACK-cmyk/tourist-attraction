package com.travelprice.api;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.security.core.Authentication;
import com.travelprice.service.MemberService;
import com.travelprice.service.DestinationGuideCatalog;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
@Controller
public class HomeController {
    private final MemberService members;
    private final DestinationGuideCatalog guides;
    public HomeController(MemberService members, DestinationGuideCatalog guides) { this.members=members; this.guides=guides; }
    @ModelAttribute("admin")
    public boolean admin(Authentication auth) { var member=members.current(auth); return member!=null && member.isAdmin(); }
    @ModelAttribute("signedIn")
    public boolean signedIn(Authentication auth) { return members.current(auth)!=null; }
    @ModelAttribute("loginUrl")
    public String loginUrl(HttpServletRequest request) { var query=request.getQueryString(); return LoginReturnPath.login(request.getRequestURI()+(query==null?"":"?"+query),null); }
    @GetMapping("/login")
    public String login(@RequestParam(required=false) String next, Model model) { model.addAttribute("loginNext",LoginReturnPath.safe(next)); return "login"; }
    @GetMapping("/")
    public String home() { return "index"; }
    @GetMapping("/destinations/sokcho")
    public String sokchoIntroduction() { return "destination-sokcho"; }
    @GetMapping("/destinations/{slug}")
    public String destinationIntroduction(@PathVariable String slug, Model model) {
        model.addAttribute("guide", guides.find(slug).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관광지 소개를 찾을 수 없습니다.")));
        return "destination-guide";
    }
    @GetMapping("/community")
    public String community() { return "community"; }
    @GetMapping("/visit-reviews")
    public String visitReviews(Model model) { model.addAttribute("moderation",false); return "visit-reviews"; }
    @GetMapping("/admin/visit-reviews")
    public String visitModeration(Model model) { model.addAttribute("moderation",true); return "visit-reviews"; }
    @GetMapping({"/support","/support/success","/support/fail","/support/cancel"})
    public String support() { return "support"; }
    @GetMapping("/chat")
    public String chat() { return "chat"; }
    @GetMapping("/admin/reviews")
    public String adminReviews() { return "admin-reviews"; }
}

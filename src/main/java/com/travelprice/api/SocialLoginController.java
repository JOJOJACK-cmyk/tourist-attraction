package com.travelprice.api;

import com.travelprice.config.SocialLoginConfig.Registrations;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Map;

@Controller
public class SocialLoginController {
    public static final String RETURN_BOARD="socialReturnBoard";
    private final Registrations registrations;
    public SocialLoginController(Registrations registrations){this.registrations=registrations;}
    @GetMapping("/api/auth/social/providers") @ResponseBody
    public Map<String,Boolean> providers(){return Map.of("kakao",registrations.available("kakao"),"google",registrations.available("google"));}
    @GetMapping("/auth/social/{provider}")
    public void start(@PathVariable String provider,@RequestParam(defaultValue="free")String board,HttpServletRequest request,HttpServletResponse response)throws IOException{
        var target="question".equals(board)?"question":"free";
        if(!registrations.available(provider)){response.sendRedirect("/community?board="+target+"&loginError=unavailable#board");return;}
        request.getSession().setAttribute(RETURN_BOARD,target);
        response.sendRedirect("/oauth2/authorization/"+provider);
    }
    public static String returnPath(HttpServletRequest request,boolean failed){
        var session=request.getSession(false);Object board=session==null?null:session.getAttribute(RETURN_BOARD);
        if(session!=null)session.removeAttribute(RETURN_BOARD);
        return "/community?board="+("question".equals(board)?"question":"free")+(failed?"&loginError=social":"")+"#board";
    }
}

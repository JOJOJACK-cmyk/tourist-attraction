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
    public static final String RETURN_NEXT="socialReturnNext";
    private final Registrations registrations;
    public SocialLoginController(Registrations registrations){this.registrations=registrations;}
    @GetMapping("/api/auth/social/providers") @ResponseBody
    public Map<String,Boolean> providers(){return Map.of("naver",registrations.available("naver"),"google",registrations.available("google"));}
    @GetMapping("/auth/social/{provider}")
    public void start(@PathVariable String provider,@RequestParam(defaultValue="free")String board,@RequestParam(required=false)String next,HttpServletRequest request,HttpServletResponse response)throws IOException{
        var target="question".equals(board)?"question":"free";
        var session=request.getSession();session.removeAttribute(RETURN_NEXT);session.removeAttribute(RETURN_BOARD);
        if(!registrations.available(provider)){response.sendRedirect(next==null?"/community?board="+target+"&loginError=unavailable#board":LoginReturnPath.login(next,"unavailable"));return;}
        if(next!=null)session.setAttribute(RETURN_NEXT,LoginReturnPath.safe(next));else session.setAttribute(RETURN_BOARD,target);
        response.sendRedirect("/oauth2/authorization/"+provider);
    }
    public static String returnPath(HttpServletRequest request,boolean failed){
        var session=request.getSession(false);Object board=session==null?null:session.getAttribute(RETURN_BOARD);
        Object next=session==null?null:session.getAttribute(RETURN_NEXT);
        if(session!=null){session.removeAttribute(RETURN_BOARD);session.removeAttribute(RETURN_NEXT);}
        if(next instanceof String path)return failed?LoginReturnPath.login(path,"social"):LoginReturnPath.safe(path);
        return "/community?board="+("question".equals(board)?"question":"free")+(failed?"&loginError=social":"")+"#board";
    }
}

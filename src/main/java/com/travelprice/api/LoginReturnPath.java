package com.travelprice.api;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class LoginReturnPath {
    private LoginReturnPath(){}
    private static final Set<String> PAGES=Set.of("/","/community","/chat","/visit-reviews","/support","/admin/reviews","/admin/visit-reviews");
    public static String safe(String value){
        if(value==null||!value.startsWith("/")||value.startsWith("//")||value.contains("\\")||value.chars().anyMatch(c->Character.isISOControl(c)))return "/";
        try{var uri=URI.create(value);if(uri.isAbsolute()||uri.getRawAuthority()!=null)return "/";
            var path=uri.getPath();return PAGES.contains(path)||path.matches("/destinations/[a-z-]+")?value:"/";
        }catch(IllegalArgumentException e){return "/";}
    }
    public static String login(String next,String error){return "/login?next="+URLEncoder.encode(safe(next),StandardCharsets.UTF_8)+(error==null?"":"&loginError="+error);}
}

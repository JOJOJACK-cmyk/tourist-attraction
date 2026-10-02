package com.travelprice.api;
import com.travelprice.api.SupportModels.*;
import com.travelprice.service.SupportService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/support/orders")
public class SupportController {
    private static final String OWNER="supportDemoOwner";
    private final SupportService support;public SupportController(SupportService support){this.support=support;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)public OrderView create(@Valid @RequestBody CreateInput input,HttpServletRequest request){return support.create(input,owner(request,true));}
    @GetMapping("/{id}")public OrderView detail(@PathVariable String id,HttpServletRequest request){return support.detail(id,owner(request,false));}
    @GetMapping("/config")public ConfigView config(){return support.config();}
    @PostMapping("/{id}/confirm")public OrderView confirm(@PathVariable String id,@Valid @RequestBody ConfirmInput input,HttpServletRequest request){return support.confirm(id,input,owner(request,false));}
    private String owner(HttpServletRequest request,boolean create){
        var session=request.getSession(create);if(session==null)return null;
        synchronized(session){var value=(String)session.getAttribute(OWNER);if(value==null&&create){value=UUID.randomUUID().toString();session.setAttribute(OWNER,value);}return value;}
    }
}

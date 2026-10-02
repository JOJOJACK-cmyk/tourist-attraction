package com.travelprice.service;

import com.travelprice.domain.Member;
import com.travelprice.repository.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class SocialMemberService {
    private final MemberRepository members;private final PasswordEncoder encoder;private final TransactionTemplate transactions;
    public SocialMemberService(MemberRepository members,PasswordEncoder encoder,PlatformTransactionManager manager){this.members=members;this.encoder=encoder;this.transactions=new TransactionTemplate(manager);}
    public Member resolve(String provider,String subject){
        if(!("kakao".equals(provider)||"google".equals(provider)) || subject==null || subject.isBlank() || subject.length()>255)
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_social_identity"));
        final String identity;
        try{identity=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        try {
            return transactions.execute(status->members.findBySocialProviderAndSocialSubject(provider,identity).orElseGet(()->
                members.saveAndFlush(Member.social("s_"+UUID.randomUUID().toString().replace("-","").substring(0,28),encoder.encode(UUID.randomUUID().toString()),provider,identity))));
        }catch(DataIntegrityViolationException race){
            // Another simultaneous first login may already have created this identity.
            return members.findBySocialProviderAndSocialSubject(provider,identity).orElseThrow(()->race);
        }
    }
}

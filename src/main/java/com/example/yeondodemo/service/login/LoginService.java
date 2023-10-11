package com.example.yeondodemo.service.login;

import com.example.yeondodemo.dto.login.AuthenticationDTO;
import com.example.yeondodemo.dto.login.GoogleInfoResponse;
import com.example.yeondodemo.dto.login.GoogleRequest;
import com.example.yeondodemo.dto.login.GoogleResponse;
import com.example.yeondodemo.entity.RefreshEntity;
import com.example.yeondodemo.entity.Workspace;
import com.example.yeondodemo.filter.Timer;
import com.example.yeondodemo.repository.etc.KeywordRepository;
import com.example.yeondodemo.repository.etc.RefreshRedisRepository;
import com.example.yeondodemo.repository.studyfield.StudyFieldRepository;
import com.example.yeondodemo.repository.user.RealUserRepository;
import com.example.yeondodemo.repository.user.UserRepository;
import com.example.yeondodemo.dto.LoginUserDTO;
import com.example.yeondodemo.dto.UserProfileDTO;
import com.example.yeondodemo.utils.JwtTokenProvider;
import com.example.yeondodemo.utils.ReturnUtils;
import com.example.yeondodemo.validation.WorkspaceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.Authenticator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service @Slf4j  @RequiredArgsConstructor
public class LoginService {
    @Value("${jwt.secret}")
    String jwtSecret;
    private final RealUserRepository realUserRepository;
    private final JwtTokenProvider provider;
    private final ValidationService validationService;
    @Transactional
    public ResponseEntity updateRefreshToken(String jwt){
        String email = provider.getUserName(jwt);
        log.info("Expried Token.. , email: {}", email);

        if(validationService.checkRefreshToken(jwt, email)){
            log.info("Valid Expried Token.., email: {}", email);
            String refresh = validationService.makeRefreshTokenAndSaveToRedis(email);
            return new ResponseEntity<>(validationService.getJwtHeaders(email, refresh), HttpStatus.OK);
        }else{
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }
    }

    public HttpHeaders test(String email){
        String refresh = validationService.makeRefreshTokenAndSaveToRedis(email);
        return validationService.getJwtHeaders(email, refresh);
    }

    @Transactional
    public ResponseEntity googleLogin(String authCode){
        RestTemplate restTemplate = new RestTemplate();
        //트라이 익셉션. 
        ResponseEntity<GoogleInfoResponse> infoResponse = validationService.getResponseFromGoogle(authCode, restTemplate);
        
        String email= infoResponse.getBody().getEmail();
        String name = infoResponse.getBody().getName();

        if(realUserRepository.exist(email)==null) {join(email);}

        String refresh = validationService.makeRefreshTokenAndSaveToRedis(email);
        return new ResponseEntity<>(ReturnUtils.mapReturn("username", name), validationService.getJwtHeaders(email, refresh), HttpStatus.OK);
    }

    public void join(String email){
        realUserRepository.save(email);
    }
}

package com.example.yeondodemo.Scheduluer;

import com.example.yeondodemo.utils.JwtTokenProvider;
import com.example.yeondodemo.validation.WorkspaceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component @Slf4j @RequiredArgsConstructor
public class LoginScheduler {
    private final JwtTokenProvider provider;
    @Scheduled(fixedDelay = 10000)
    public void loginGarbageCollector(){
        List<String> garbageList = new ArrayList<>();
        for(String key: WorkspaceValidator.login.keySet()){
            if(!provider.validateToken(key)){
                log.info("delete in login... {}", key);
                garbageList.add(key);
            }
        }
        for(String key:garbageList){
            WorkspaceValidator.login.remove(key);
        }
    }
}

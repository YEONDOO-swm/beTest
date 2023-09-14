package com.example.yeondodemo.service.search;

import com.example.yeondodemo.dto.*;
import com.example.yeondodemo.dto.paper.PaperResultRequest;
import com.example.yeondodemo.dto.python.PythonQuestionResponse;
import com.example.yeondodemo.entity.Paper;
import com.example.yeondodemo.repository.etc.BatisAuthorRepository;
import com.example.yeondodemo.repository.paper.PaperBufferRepository;
import com.example.yeondodemo.repository.paper.PaperInfoRepository;
import com.example.yeondodemo.repository.paper.PaperRepository;
import com.example.yeondodemo.repository.history.QueryHistoryRepository;
import com.example.yeondodemo.repository.user.LikePaperRepository;
import com.example.yeondodemo.utils.ConnectPythonServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class PaperService {
    private final QueryHistoryRepository queryHistoryRepository;
    private final PaperInfoRepository paperInfoRepository;
    private final PaperBufferRepository paperBufferRepository;
    private final PaperRepository paperRepository;
    private final LikePaperRepository likePaperRepository;
    private final BatisAuthorRepository authorRepository;
    @Value("${python.address}")
    private String pythonapi;
    @Transactional
    public Map getPaperQuestion(String paperid, Long workspaceId, String query){
        List<PaperHistory> paperHistories = queryHistoryRepository.findByUserAndIdOrderQA4Python(workspaceId, paperid);
        List<List<String>>  histories = new ArrayList<>();
        List<String> t = null;
        for (PaperHistory paperHistory : paperHistories) {
            if(paperHistory.isWho()){
                 t = new ArrayList<>();
            }
            t.add(paperHistory.getContent());
            if(!paperHistory.isWho()){
                histories.add(t);
            }
        }
        PythonQuestionResponse answer = ConnectPythonServer.question(new PythonQuestionDTO(paperid, histories, query), pythonapi);
        Integer idx = queryHistoryRepository.getLastIdx(workspaceId, paperid);
        if(idx == null) {idx=0;}
        queryHistoryRepository.save(new QueryHistory(workspaceId, paperid, idx+2, false, answer));
        queryHistoryRepository.save(new QueryHistory(workspaceId, paperid, idx+1, true, query));
        Map<String, String> ret = new HashMap<>();
        ret.put("answer", answer.getAnswer());
        return ret;
    }

    @Transactional
    public Flux<ServerSentEvent<String>> getPaperQuestionStream(String paperid, Long workspaceId, String query){
        List<PaperHistory> paperHistories = queryHistoryRepository.findByUserAndIdOrderQA4Python(workspaceId, paperid);
        List<List<String>>  histories = new ArrayList<>();
        List<String> t = null;
        for (PaperHistory paperHistory : paperHistories) {
            if(paperHistory.isWho()){
                t = new ArrayList<>();
            }
            t.add(paperHistory.getContent());
            if(!paperHistory.isWho()){
                histories.add(t);
            }
        }
        //Flux<String> answerStream = ConnectPythonServer.questionStream(new PythonQuestionDTO(paperid, histories, query), pythonapi);
        List<String> answerList = new ArrayList<>();

       return WebClient.create()
                .post()
                .uri(pythonapi + "/question")
                .body(Mono.just(new PythonQuestionDTO(paperid, histories, query)), PythonQuestionDTO.class)
                .retrieve()
                .bodyToFlux(String.class).map(data -> {
            answerList.add(data);
            return ServerSentEvent.builder(data).build();
        }).doOnComplete(
                       () -> {
                           Integer idx = queryHistoryRepository.getLastIdx(workspaceId, paperid);
                           String answer = String.join("",answerList);
                           if(idx == null) {idx=0;}
                           queryHistoryRepository.save(new QueryHistory(workspaceId, paperid, idx+2, false, answer));
                           queryHistoryRepository.save(new QueryHistory(workspaceId, paperid, idx+1, true, query));
                       }
               );
    }
    public void updateInfoRepository(PythonPaperInfoDTO pythonPaperInfoDTO, String paperid){
        for(String insight: pythonPaperInfoDTO.getInsights()){
            paperInfoRepository.save(new PaperInfo(paperid, "insight", insight));
        }
        for(String q: pythonPaperInfoDTO.getQuestions()){
            paperInfoRepository.save(new PaperInfo(paperid, "question", q));
        }
        for(String s: pythonPaperInfoDTO.getSubjectRecommends()){
            paperInfoRepository.save(new PaperInfo(paperid, "subjectrecommend", s));
        }
//        for(String r: pythonPaperInfoDTO.getReferences()){
//            paperInfoRepository.save(new PaperInfo(paperid, "reference", r));
//        }
        paperBufferRepository.update(paperid, new BufferUpdateDTO(true, new Date()));
    }
    @Transactional
    public RetPaperInfoDTO getPaperInfo(String paperid, Long workspaceId){
        log.info("getPaperInfo... ");
        if((!paperBufferRepository.isHit(paperid))){
            //goto python server and get data
            log.info("go to python server.... ");
            PythonPaperInfoDTO pythonPaperInfoDTO = ConnectPythonServer.requestPaperInfo(paperid, pythonapi);

            log.info("python return : {}", pythonPaperInfoDTO);
            if(pythonPaperInfoDTO == null) {
                return null;
            }
            updateInfoRepository(pythonPaperInfoDTO, paperid);
        };
        PythonPaperInfoDTO pythonPaperInfoDTO = new PythonPaperInfoDTO();
        pythonPaperInfoDTO.setInsights(paperInfoRepository.findByPaperIdAndType(paperid, "insight"));
        // pythonPaperInfoDTO.setReferences(paperInfoRepository.findByPaperIdAndType(paperid, "reference"));
        pythonPaperInfoDTO.setQuestions(paperInfoRepository.findByPaperIdAndType(paperid, "question"));
        pythonPaperInfoDTO.setSubjectRecommends(paperInfoRepository.findByPaperIdAndType(paperid, "subjectrecommend"));
        Paper paper = paperRepository.findById(paperid);
        RetPaperInfoDTO paperInfoDTO = new RetPaperInfoDTO(paper, pythonPaperInfoDTO, queryHistoryRepository.findByUsernameAndPaperIdOrderQA(workspaceId, paperid));
        if(likePaperRepository.isLike(workspaceId, paperid)){paperInfoDTO.getPaperInfo().setIsLike(true);};
        log.info("paper info: {}", paperInfoDTO);
        return paperInfoDTO;
    }
    @Transactional
    public void resultScore(PaperResultRequest paperResultRequest){
        queryHistoryRepository.updateScore(paperResultRequest.getId(), paperResultRequest.getScore());
    }

}

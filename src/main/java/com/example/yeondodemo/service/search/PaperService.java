package com.example.yeondodemo.service.search;

import com.example.yeondodemo.dto.*;
import com.example.yeondodemo.dto.paper.ExpiredKeyDTO;
import com.example.yeondodemo.dto.paper.PaperAnswerResponseDTO;
import com.example.yeondodemo.dto.paper.PaperResultRequest;
import com.example.yeondodemo.dto.paper.item.ExportItemDTO;
import com.example.yeondodemo.dto.paper.item.ExportItemResponse;
import com.example.yeondodemo.dto.paper.item.ItemAnnotation;
import com.example.yeondodemo.dto.paper.item.DeleteItemDTO;
import com.example.yeondodemo.dto.python.PaperPythonFirstResponseDTO;
import com.example.yeondodemo.dto.python.PythonQuestionResponse;
import com.example.yeondodemo.dto.python.Token;
import com.example.yeondodemo.entity.Paper;
import com.example.yeondodemo.exceptions.PythonServerException;
import com.example.yeondodemo.filter.ReadPaper;
import com.example.yeondodemo.repository.paper.PaperBufferRepository;
import com.example.yeondodemo.repository.paper.PaperInfoRepository;
import com.example.yeondodemo.repository.paper.PaperRepository;
import com.example.yeondodemo.repository.history.QueryHistoryRepository;
import com.example.yeondodemo.repository.paper.item.BatisItemAnnotationRepository;
import com.example.yeondodemo.repository.user.LikePaperRepository;
import com.example.yeondodemo.utils.ConnectPythonServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service @RequiredArgsConstructor @Slf4j
public class PaperService {
    private final QueryHistoryRepository queryHistoryRepository;
    private final PaperInfoRepository paperInfoRepository;
    private final PaperBufferRepository paperBufferRepository;
    private final PaperRepository paperRepository;
    private final LikePaperRepository likePaperRepository;
    private final BatisItemAnnotationRepository itemAnnotationRepository;
    private Map<Long, ExpiredKeyDTO> answerIdMap;
    @Value("${python.address}")
    private String pythonapi;
    @Value("${python.key}")
    private String pythonKey;

    @PostConstruct
    public void init(){
        answerIdMap = new ConcurrentHashMap<Long, ExpiredKeyDTO>();
    }
    public void timeout(){
        List<Long>  timeoutList = new ArrayList<>();
        for(Long key: answerIdMap.keySet()){
            if (answerIdMap.get(key).getExpired()<System.currentTimeMillis()){
                timeoutList.add(key);
            }
        }
        for(Long key: timeoutList){
            answerIdMap.remove(key);
        }
    }

    @Transactional
    public ResponseEntity deletePaperItem(DeleteItemDTO deleteItemDTO){
        itemAnnotationRepository.delete(deleteItemDTO);
        return new ResponseEntity<>(HttpStatus.OK);
    }
    @Transactional
    public ResponseEntity putPaperItem(ItemAnnotation paperItem){
        log.info("Update Item: {}", paperItem);
        itemAnnotationRepository.update(paperItem);
        return new ResponseEntity<>(HttpStatus.OK);
    }
    public ResponseEntity exportPaper(ExportItemDTO exportItemDTO){
        //todo: 파이썬쪽 로직 짜여지면 통신하는거 추가할것.
        return new ResponseEntity<>(new ExportItemResponse(), HttpStatus.OK);
    }
    @Transactional
    public ResponseEntity postPaperItem(ItemAnnotation paperItem){
        log.info("Store Item: {}", paperItem);
        return new ResponseEntity<>(itemAnnotationRepository.save(paperItem), HttpStatus.OK);
    }
    public Long getResultId(Long key) throws IllegalAccessError{
        if (answerIdMap.get(key) != null){
            return answerIdMap.get(key).getRid();
        }else{
            throw new IllegalAccessError("Invalid Access Key");
        }
    }
    @Transactional
    public ResponseEntity storePythonToken(String key,Long rid, Token track){
        if(!key.equals(pythonKey)){
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }
        queryHistoryRepository.updateToken(rid, track);
        return new ResponseEntity(HttpStatus.OK);
    }
    public PythonQuestionDTO getPythonQuestionDTO(String paperid, Long workspaceId, QuestionDTO query){
        List<PaperHistory> paperHistories = queryHistoryRepository.findByUserAndIdOrderQA4Python(workspaceId, paperid);
        return new PythonQuestionDTO(paperid, query, paperHistories);
    }
    @Transactional
    public PaperAnswerResponseDTO getPaperQuestion(String paperid, Long workspaceId, QuestionDTO query){

        PythonQuestionDTO pythonQuestionDTO = getPythonQuestionDTO(paperid, workspaceId, query);
        PythonQuestionResponse answer = ConnectPythonServer.question(pythonQuestionDTO, pythonapi);

        log.info("Python response is.. {}", answer);
        PaperAnswerResponseDTO paperAnswerResponseDTO = new PaperAnswerResponseDTO(answer);
        Long idx = queryHistoryRepository.getLastIdx(workspaceId, paperid);
        if(idx == null) {idx=0L;}

        queryHistoryRepository.save(new QueryHistory(workspaceId, paperid, idx+2, false, paperAnswerResponseDTO));
        queryHistoryRepository.save(new QueryHistory(workspaceId, paperid, idx+1, true, query));

        return paperAnswerResponseDTO;
    }

   /* @Transactional
    public Flux<ServerSentEvent<String>> getPaperQuestionStream(String paperid, Long workspaceId, QuestionDTO query){
        List<List<String>>  histories = getQuestionHistories(paperid, workspaceId);
        List<String> answerList = new ArrayList<>();

        Long lastIdx = queryHistoryRepository.getLastIdx(workspaceId, paperid);
        final long idx = (lastIdx == null) ? 0L : lastIdx;
        QueryHistory queryHistory = new QueryHistory(workspaceId, paperid, idx+1, true, query);
        queryHistoryRepository.save(queryHistory);
        log.info("History Id is... {}", queryHistory.getId());

       return WebClient.create()
                .post()
                .uri(pythonapi + "/chat?historyId="+queryHistory.getId())
                .body(Mono.just(new PythonQuestionDTO(paperid, histories, query)), PythonQuestionDTO.class)
                .retrieve()
                .bodyToFlux(String.class).map(data -> {
                   int lastIndex = data.lastIndexOf("\n");
                   String trimmedData = lastIndex != -1 ? data.substring(0, lastIndex) : data;
                   answerList.add(trimmedData);
                   return ServerSentEvent.builder(trimmedData).build();
        }).doOnComplete(
                       () -> {
                           String answer = String.join("",answerList);
                           //queryHistoryRepository.save(new QueryHistory(workspaceId, paperid, idx+2, false, answer));
                       }
               );
    }*/
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
        paperBufferRepository.update(paperid, new BufferUpdateDTO(true, new Date()));
    }

    public void updateInfoRepositoryV2(String pythonPaperInfoDTO, String paperid){
        paperInfoRepository.save(new PaperInfo(paperid, "welcomeAnswer", pythonPaperInfoDTO));
        paperBufferRepository.update(paperid, new BufferUpdateDTO(true, new Date()));
    }

    public void updateInfoRepositoryV3(PaperPythonFirstResponseDTO pythonPaperInfoDTO, String paperid){
        String summary = pythonPaperInfoDTO.getSummary();
        List<String> questions = pythonPaperInfoDTO.getQuestions();
        paperInfoRepository.save(new PaperInfo(paperid, "summary", summary));
        for (String question : questions) {
            paperInfoRepository.save(new PaperInfo(paperid, "questions", question));
        }
        paperBufferRepository.update(paperid, new BufferUpdateDTO(true, new Date()));
    }
    public void checkPaperCanCached(String paperid){
        if((!paperBufferRepository.isHit(paperid))){
            //goto python server and get data
            log.info("go to python server.... ");
            PaperPythonFirstResponseDTO pythonPaperInfoDTO = ConnectPythonServer.requestPaperInfo(paperid, pythonapi);

            log.info("python return : {}", pythonPaperInfoDTO);
            if(pythonPaperInfoDTO == null) {
                throw new PythonServerException("Get Null Data From python Server");
            }
            updateInfoRepositoryV3(pythonPaperInfoDTO, paperid);
        };
    }
    @Transactional @ReadPaper
    public RetPaperInfoDTO getPaperInfo(String paperid, Long workspaceId) throws JsonProcessingException {
        log.info("getPaperInfo... ");
        checkPaperCanCached(paperid);
        RetPaperInfoDTO paperInfoDTO = makeRetPaperInfoDTO(paperid, workspaceId);
        log.info("paper info: {}", paperInfoDTO);
        return paperInfoDTO;
    }

    public RetPaperInfoDTO makeRetPaperInfoDTO(String paperid, long workspaceId) throws JsonProcessingException {
        String pythonPaperInfoDTO = paperInfoRepository.findByPaperIdAndType(paperid, "summary");
        List<String> questions = paperInfoRepository.findManyByPaperIdAndType(paperid, "questions");
        Paper paper = paperRepository.findById(paperid);
        List<ItemAnnotation> paperItems = itemAnnotationRepository.findByPaperIdAndWorkspaceId(paperid, workspaceId);
        Boolean isLike = likePaperRepository.isLike(workspaceId, paperid);
        List<PaperHistory> paperHistories = queryHistoryRepository.findByUsernameAndPaperIdOrderQA(workspaceId, paperid);
        return  new RetPaperInfoDTO(paper, pythonPaperInfoDTO, questions, paperHistories, paperItems, isLike);

    }
    @Transactional
    public void resultScore(PaperResultRequest paperResultRequest){
        queryHistoryRepository.updateScore(paperResultRequest.getId(), paperResultRequest.getScore());
    }

}

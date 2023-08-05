package com.example.yeondodemo.repository.history.mapper;

import com.example.yeondodemo.dto.PaperHistory;
import com.example.yeondodemo.dto.QueryHistory;
import com.example.yeondodemo.dto.history.PaperHistoryDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface QueryHistoryMapper {
    void save(QueryHistory queryHistory);
    List<PaperHistory> findByUsernameAndPaperid(String username, String paperIsd);
    Integer getLastIdx(String username, String paperId);
    List<PaperHistoryDTO> findByUsername(String username);
    List<PaperHistory> findByUsernameAndPaperIdOrderQA(String username, String paperIsd);
    List<PaperHistory> findByUserAndIdOrderQA4Python(String username, String paperIsd);
    PaperHistory findByUsernameAndId(String username, Long id);
    void updateScore(Long id, Integer score);

}

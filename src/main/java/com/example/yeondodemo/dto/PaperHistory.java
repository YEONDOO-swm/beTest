package com.example.yeondodemo.dto;

import com.example.yeondodemo.dto.paper.item.ItemPosition;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class PaperHistory {
    private Long id;
    private boolean who;
    private String content;
    private Integer score;
    private String positionString;
    private ItemPosition position;
    private String context;
    private String extraPaperId;
    private List<String> paperIds;
}

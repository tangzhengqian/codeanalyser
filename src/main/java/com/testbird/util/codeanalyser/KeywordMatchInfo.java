package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class KeywordMatchInfo {
    @JsonProperty(value = "file")
    String file;
    @JsonProperty(value = "keyword")
    String keyword;
    @JsonProperty(value = "keywordLevel")
    String keywordLevel;
    @JsonProperty(value = "type")
    String type;
    @JsonProperty(value = "line")
    String line;
    @JsonProperty(value = "beforeLines")
    List<String> beforeLines = new LinkedList<>();
    @JsonProperty(value = "afterLines")
    List<String> afterLines = new LinkedList<>();
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty(value = "lineNumber")
    Integer lineNumber;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty(value = "startPos")
    int startPos;
    // end pos is exclusive
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty(value = "endPos")
    int endPos;

    public KeywordMatchInfo(String file, String keyword, String type, String line, Integer lineNumber, int startPos, int endPos) {
        this.file = file;
        this.keyword = keyword;
        this.type = type;
        this.line = line;
        this.lineNumber = lineNumber;
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public KeywordMatchInfo(){
    }
    @Override
    public String toString() {
        return "keyword: " + keyword + ", type: " + type + ", line: " + line;
    }
}

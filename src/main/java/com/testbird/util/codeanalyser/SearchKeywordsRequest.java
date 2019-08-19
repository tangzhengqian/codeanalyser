package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SearchKeywordsRequest {
    // mandatory fields
    @JsonProperty(value = "key")
    String key;
    @JsonProperty(value = "type")
    String type;
    @JsonProperty(value = "includeFileTypes")
    List<String> includeFileTypes;
    @JsonProperty(value = "keywordInfos")
    List<KeywordInfo> keywordInfos;

    @JsonProperty(value = "href")
    String href;
    @JsonProperty(value = "repoInfo")
    RepoInfo repoInfo;

    @JsonProperty(value = "resultAttr")
    ResultAttr resultAttr;
    @JsonProperty(value = "ignoreFiles")
    List<String> ignoreFiles;
    // ignoreKeywords
    @JsonProperty(value = "ignoreKeywords")
    List<String> ignoreKeywords;
    List<Pattern> ignoreKeywordPatterns = new LinkedList<>();
    // ignoreFileTypes
    // @JsonProperty(value = "ignoreFileTypes")
    // List<String> ignoreFileTypes;
    @JsonProperty(value = "ignoreCommentLines")
    boolean ignoreCommentLines;

}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class RepoInfo {
    // {"type":"repo", "repoInfo": { "repoType":"svn", "repoAddr":"svn://...", "username":"name", "password":"123", "repoVersion":"1234"}}
    @JsonProperty(value = "repoType")
    String repoType;
    @JsonProperty(value = "repoAddr")
    String repoAdrr;
    @JsonProperty(value = "username")
    String userName;
    @JsonProperty(value = "password")
    String password;
    @JsonProperty(value = "lastRepoVersion")
    String lastRepoVersion;
    @JsonProperty(value = "repoVersion")
    String repoVersion;
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class KeywordInfo {
    @JsonProperty(value = "level")
    String level;
    @JsonProperty(value = "regexp_keywords")
    List<String> keywords;
    @JsonProperty(value = "keywordPatterns")
    List<Pattern> keywordPatterns;
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class ResultAttr {
    @JsonProperty(value = "extraLineCnt")
    int extraLineCnt;
}

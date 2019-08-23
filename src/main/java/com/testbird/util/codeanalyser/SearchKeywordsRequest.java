package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 扫描请求的对象
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SearchKeywordsRequest {
    // mandatory fields
    @JsonProperty(value = "key")
    String key;//任务key
    @JsonProperty(value = "type")
    String type;//类型, file or repo
    @JsonProperty(value = "includeFileTypes")
    List<String> includeFileTypes; //
    @JsonProperty(value = "keywordInfos")
    List<KeywordInfo> keywordInfos; //关键字

    @JsonProperty(value = "href")
    String href; //file文件下载地址
    @JsonProperty(value = "repoInfo")
    RepoInfo repoInfo; //svn的信息

    @JsonProperty(value = "resultAttr")
    ResultAttr resultAttr;
    @JsonProperty(value = "ignoreFiles")
    List<String> ignoreFiles; //忽略文件
    // ignoreKeywords
    @JsonProperty(value = "ignoreKeywords")
    List<String> ignoreKeywords; //忽略关键字
    List<Pattern> ignoreKeywordPatterns = new LinkedList<>(); //忽略文件正则表达式
    // ignoreFileTypes
    // @JsonProperty(value = "ignoreFileTypes")
    // List<String> ignoreFileTypes;
    @JsonProperty(value = "ignoreCommentLines")
    boolean ignoreCommentLines; //扫描时是否忽略注释

}

/**
 * svn仓库信息
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class RepoInfo {
    // {"type":"repo", "repoInfo": { "repoType":"svn", "repoAddr":"svn://...", "username":"name", "password":"123", "repoVersion":"1234"}}
    @JsonProperty(value = "repoType")
    String repoType; //目前只支持svn类型
    @JsonProperty(value = "repoAddr")
    String repoAdrr; //仓库地址
    @JsonProperty(value = "username")
    String userName;
    @JsonProperty(value = "password")
    String password;
    @JsonProperty(value = "lastRepoVersion")
    String lastRepoVersion; //上一次扫描的版本
    @JsonProperty(value = "repoVersion")
    String repoVersion; //指定checkout的版本
}

/**
 * 关键字信息
 */
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

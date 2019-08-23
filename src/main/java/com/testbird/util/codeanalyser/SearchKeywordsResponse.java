package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 扫描返回的response对象
 */
// Search keywords result HTTP POST data
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SearchKeywordsResponse {
    @JsonProperty(value = "result")
    Result result = new Result();
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class Result {
    // response is asynchronous
    // to match response with request
    @JsonProperty(value = "key")
    String key; //任务key
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty(value = "success")
    boolean success;
    @JsonProperty(value = "errMsg")
    String errMsg;
    @JsonProperty(value = "resultFileUrl")
    String resultFileUrl; //扫描结果文件的下载地址
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty(value = "resultCount")
    long resultCount;//结果数量
    @JsonProperty(value = "measures")
    SearchKeywordsMeasures measures;
    @JsonProperty(value = "repoVersion")
    String repoVersion; //当前扫描的版本
    // milliseconds
    @JsonProperty(value = "lastChangeTime")
    long lastChangeTime; //当前扫描的更新时间
}

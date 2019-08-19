package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    String key;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty(value = "success")
    boolean success;
    @JsonProperty(value = "errMsg")
    String errMsg;
    @JsonProperty(value = "resultFileUrl")
    String resultFileUrl;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty(value = "resultCount")
    long resultCount;
    @JsonProperty(value = "measures")
    SearchKeywordsMeasures measures;
    @JsonProperty(value = "repoVersion")
    String repoVersion;
    // milliseconds
    @JsonProperty(value = "lastChangeTime")
    long lastChangeTime;
}

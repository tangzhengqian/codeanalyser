package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

// Search keywords result file
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SearchKeywordsResult {
    @JsonProperty(value = "key")
    String key;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty(value = "resultCount")
    long resultCount;
    // @JsonProperty(value = "result")
    // List<KeywordMatchInfo> keywordMatchInfos = new LinkedList<>();
    @JsonProperty(value = "measures")
    SearchKeywordsMeasures measures = new SearchKeywordsMeasures();
    @JsonProperty(value = "repoVersion")
    String repoVersion;
    @JsonProperty(value = "lastRepoVersion")
    String lastRepoVersion;
    // milliseconds
    @JsonProperty(value = "lastChangeTime")
    long lastChangeTime;
    @JsonProperty(value = "versionLogs")
    String versionLogs;
    @JsonProperty(value = "versionHistories")
    List<VersionInfo> versionHistories= new LinkedList<>();
    @JsonProperty(value = "timestamp")
    long ts;
    @Override
    public String toString() {
        return String.format("%s search result: result count: %d, %s", key, resultCount, measures);
    }

    public long getResultCount() {
        return resultCount;
    }
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class SearchKeywordsMeasures {
    SearchKeywordsMeasures() {}
    // Total lines searched
    @JsonProperty(value = "totalLineCount")
    long totalLineCount;
    @JsonProperty(value = "totalSize")
    long totalSize;
    @JsonProperty(value = "downloadTime")
    long downloadTime;
    @JsonProperty(value = "searchTime")
    long searchTime;

    @Override
    public String toString() {
        return String.format("Total size searched: %d, total lines searched: %d, download time: %d, search time: %d",
                totalSize, totalLineCount, downloadTime, searchTime);
    }
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class VersionInfo {
    @JsonProperty(value = "repoVersion")
    String repoVersion;
    @JsonProperty(value = "comment")
    String comment;
}
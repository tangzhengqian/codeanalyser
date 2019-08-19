package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.testbird.util.common.FileServerUploadHelper;
import com.testbird.util.common.JsonTransfer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.testbird.util.codeanalyser.Config.getTaskBaseDir;
import static com.testbird.util.codeanalyser.Config.getTaskResultFile;

public class SearchTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SearchTask.class);
    SearchKeywordsRequest request;
    SearchKeywordsResponse response;
    Future<?> task;
    Process downloadSrcProcess;
    volatile boolean canceled;
    volatile boolean finished;
    String errMsg;
    SearchKeywordsResult result;

    Object cancelSync = new Object();
    Object finishSync = new Object();

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient HTTPCLIENT = new OkHttpClient();

    SearchTask(SearchKeywordsRequest request) {
        this.request = request;
    }

    @Override
    public void run() {
        throw new RuntimeException("Not implemented");
    }

    public void setTask(Future<?> task) {
        this.task = task;
    }
    synchronized public void setDownloadSrcProcess(Process p) {
        downloadSrcProcess = p;
    }

    synchronized public void stopDownloadSrcProcess() {
        if (null != downloadSrcProcess && downloadSrcProcess.isAlive()) {
            downloadSrcProcess.destroyForcibly();
        }
        downloadSrcProcess = null;
    }

    void stop () {
        stopDownloadSrcProcess();

        boolean tmpCanceled = canceled;
        if (!tmpCanceled) {
            synchronized (cancelSync) {
                logger.info("{} found running task", request.key);
                tmpCanceled = canceled;
                if (!tmpCanceled) {
                    canceled = tmpCanceled = true;
                    if (null != task) {
                        task.cancel(true);
                    }
                } else {
                    logger.info("{} stopping in progress.", request.key);
                }
            }
        } else {
            logger.info("{} stopping in progress.", request.key);
        }
        boolean tmpFinished = finished;
        if (!tmpFinished) {
            synchronized (finishSync) {
                tmpFinished = finished;
                if (!tmpFinished) {
                    logger.info("{} waiting for task to end.", request.key);
                    try {
                        finishSync.wait(30 * 1000);
                    } catch (InterruptedException e) {
                        logger.warn("{} waiting for task finish exception", request.key, e);
                    }
                }
            }
        }
        logger.info("{} cancel task end.", request.key);
    }

    void taskFinished() {
        response = new SearchKeywordsResponse();
        response.result.key = request.key;
        synchronized (cancelSync) {
            if (!canceled) {
                if (StringUtils.isEmpty(errMsg) && null != result) {
                    result.ts = System.currentTimeMillis();

                    String resultFileName = request.key + ".json";
                    String resultUrl = Config.getFileServerUrl() + "/" + resultFileName;
                    response.result.success = true;
                    response.result.resultFileUrl = resultUrl;
                    response.result.resultCount = result.resultCount;
                    response.result.measures = result.measures;
                    response.result.repoVersion = result.repoVersion;
                    response.result.lastChangeTime = result.lastChangeTime;
                } else {
                    response.result.success = false;
                    response.result.errMsg = errMsg;
                }
            } else {
                errMsg = "canceled";
                response.result.success = false;
                response.result.errMsg = errMsg;
                logger.info("{} task canceled.", request.key);
            }
        }
        saveResponse();
        handleSearchResult();
        synchronized (finishSync) {
            finished = true;
            finishSync.notifyAll();
        }
    }

    void handleSearchResult() {
        logger.info("Handle task result:\n{}", result);
        boolean ok = false;
        try {
            if (response.result.success) {
                ok = handleTaskSucceed();
            } else {
                ok = handleTaskFail();
            }
        } catch (Throwable throwable) {
            logger.error("Failed to handle search result: {}, {}", throwable.getMessage(), throwable.getStackTrace());
        }
        if (ok) {
            logger.info("{} task ok", request.key);
            FileUtils.deleteQuietly(new File(getTaskBaseDir(request.key)));
            KeywordsAnalyseController.removeTask(request.key);
        } else { // try again later
            logger.info("{} save task for later", request.key);
            markAsFinish();
        }
    }

    boolean handleTaskSucceed() throws IOException {
        String resultFilePath = getTaskResultFile(request.key);
        File resultFile = new File(resultFilePath);
        if (!resultFile.isFile() && null != result) {
            logger.info("{} saving search result to {}", request.key, resultFilePath);
            saveSearchResult(resultFilePath);
        }
        if (resultFile.isFile()) {
            if (uploadFileWithRetry(resultFilePath, 6, 5)) {
                FileUtils.deleteQuietly(resultFile);
                return notifySearchResult();
            } else {
                logger.error("{} failed to upload result file.", request.key);
                return false;
            }
        } else {
            return notifySearchResult();
        }
    }

    boolean handleTaskFail() {
        return notifySearchResult();
    }

    void checkResponse() {
        if (null == response) {
            try {
                String respStr = FileUtils.readFileToString(new File(Config.getTaskResponseFile(request.key)), StandardCharsets.UTF_8);
                response = JsonTransfer.toBean(respStr, SearchKeywordsResponse.class);
            } catch (Throwable e) {
                String message = e.getClass().getName() + ":" + e.getMessage();
                logger.error("{} check result error {}", request.key, message, e);
            }
        }
    }

    private void markAsFinish() {
        logger.debug("{} try to markAsFinish", request.key);
        File finishFile = new File(Config.getTaskFinishFile(request.key));
        try {
            if (!finishFile.exists()) {
                finishFile.createNewFile();
            }
        } catch (Exception e) {
            String message = e.getClass().getName() + ":" + e.getMessage();
            logger.error("{} failed to markAsFinish {}", request.key, message);
        }
    }

    private void saveResponse() {
        try {
            String respStr = JsonTransfer.toJsonFormatString(response);
            Files.write(Paths.get(Config.getTaskResponseFile(request.key)), respStr.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("{} failed to save response.", request.key);
        }
    }

    private void saveSearchResult(String resultFilePath) throws IOException {
        File tmpResultFile = new File(Config.getTaskTmpResultFile(result.key));
        if (tmpResultFile.isFile()) {
            JsonGenerator g = null;
            JsonParser p = null;
            try {
                p = KeywordsAnalyser.jsonFactory.createParser(tmpResultFile);
                JsonToken t = p.nextToken();

                t = p.nextToken();
                if ((JsonToken.FIELD_NAME != t) || !"result".equals(p.getCurrentName())) {
                    logger.error("Not valid json: {}", p.getCurrentName());
                    throw new IOException("Not valid json: " + p.getCurrentName());
                }
                t = p.nextToken();
                if (JsonToken.START_ARRAY != t) {
                    logger.error("{} not valid json: ", p.getCurrentName());
                    throw new IOException("Not valid json: " + p.getCurrentName());
                }

                g = KeywordsAnalyser.jsonFactory.createGenerator(new File(resultFilePath), JsonEncoding.UTF8);
                g.setPrettyPrinter(new DefaultPrettyPrinter());
                g.writeStartObject();

                g.writeStringField("key", result.key);
                g.writeNumberField("resultCount", result.resultCount);
                g.writeNumberField("timestamp", result.ts);
                g.writeObjectField("measures", result.measures);
                if (!StringUtils.isEmpty(result.repoVersion)) {
                    g.writeStringField("repoVersion", result.repoVersion);
                }
                if (!StringUtils.isEmpty(result.lastRepoVersion)) {
                    g.writeStringField("lastRepoVersion", result.lastRepoVersion);
                }
                if (0 != result.lastChangeTime) {
                    g.writeNumberField("lastChangeTime", result.lastChangeTime);
                }
                logger.info("---versionLogs: {}", result.versionLogs);
                if (!StringUtils.isEmpty(result.versionLogs)) {
                    g.writeStringField("versionLogs", result.versionLogs);
                }

                // results
                g.writeArrayFieldStart("result");
                while (JsonToken.END_ARRAY != (t = p.nextToken())) {
                    KeywordMatchInfo keywordMatchInfo = p.readValueAs(KeywordMatchInfo.class);
                    g.writeObject(keywordMatchInfo);
                    keywordMatchInfo = null;
                }
                g.writeEndArray();
                g.writeEndObject();
                FileUtils.deleteQuietly(tmpResultFile);
            } finally {
                if (null != p) {
                    p.close();
                }
                if (null != g) {
                    g.close();
                }
            }
        } else {
            logger.error("{} keywords result file not exist.", result.key);
            throw new IOException("keywords result file not exist.");
        }
    }

    private boolean uploadFileWithRetry(String filePath, int nRetry, long timeout) {
        boolean result = true;
        while (nRetry-- > 0) {
            result = FileServerUploadHelper.uploadFile(Config.getFileServerUrl(), new File(filePath), false);
            if (result) {
                break;
            } else if (nRetry > 0){
                try {
                    logger.info("Waiting for {} seconds to retry", timeout);
                    TimeUnit.SECONDS.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        return result;
    }

    private boolean notifySearchResult() {
        String respNotifUrl = Config.getRespNotifUrl();
        logger.info("Sending search response {}, {}: {}", respNotifUrl, response.result.key, JsonTransfer.toJsonFormatString(response));
        RequestBody body = RequestBody.create(JSON, JsonTransfer.toJsonString(response));
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(respNotifUrl)
                .post(body)
                .build();
        boolean successful = false;
        int retryInterval = 10; // 10s
        int retryTimes = 3;
        int idx = 0;
        int respCode;
        while (!successful && idx++ < retryTimes) {
            try {
                okhttp3.Response resp = HTTPCLIENT.newCall(request).execute();
                successful = resp.isSuccessful();
                respCode = resp.code();
                ResponseBody responseBody = resp.body();
                String bodyStr = responseBody == null ? "" : responseBody.string();
                try {
                    resp.close();
                } catch (Exception e) {
                    // ignore
                }
                if (!successful) {
                    logger.warn("{} Failed to notify search result: {}, {}", response.result.key, respCode, bodyStr);
                    break;
                }
            } catch (IOException e) {
                logger.warn("{} Failed to notify search result: {}, try again {}s later", response.result.key, e.getMessage(), retryInterval);
                try {
                    TimeUnit.SECONDS.sleep(retryInterval);
                } catch (InterruptedException e2) {
                    // ignore
                }
            }
        }

        if (successful) {
            logger.info("{} notify response ok", response.result.key);
        } else {
            logger.info("{} notify response failed", response.result.key);
        }
        return successful;
    }
}

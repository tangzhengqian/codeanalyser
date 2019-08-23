package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.testbird.util.common.JsonTransfer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.testbird.util.codeanalyser.KeywordsAnalyseController.getRegExPatterns;
import static com.testbird.util.codeanalyser.KeywordsAnalyseController.preHandleList;

/**
 * 配置文件类
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String TASK_DIR = "tasks";
    private static final String DOWNLOAD_SRC_DIR = "src";
    public static final String RESULT_DIR = "result";
    private static final String RESPONSE_FILE = "response.json";
    public static final String FINISH_FILE = "finish";
    private static String mDataDir = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "keywords_analysis";
    private static String mFileServerUrl;
    private static final String KEY_FILE_SERVER_URL = "FILE_SERVER_URL";

    private static String mRespNotifUrl;
    private static final String KEY_RESPONSE_NOTIF_URL = "KEYWORDS_SCANNER_RESPONSE_NOTIF_URL";
    private static final String DEFAULT_RESPONSE_NOTIF_URL = "http://quail.lab.tb/api/scanner/scan/execution/result/";//默认的response上传地址

    private static KeywordsAnalysisConfig keywordsAnalysisConfig;
    private static final int DEFAULT_MAX_TASK_COUNT = 4;
    private static final int DEFAULT_MAX_MATCH_STR_LEN = 2048;

    public static final int MAX_ANALYSE_TIME_IN_MINUTES = 15;

    public static void loadKeywordsAnalysisConfig() {
        try {
            // File jarFile = new File(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            File configFile = new File(System.getProperty("user.dir"), "keywords_analysis.json");
            String configStr = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            keywordsAnalysisConfig = JsonTransfer.toBean(configStr, KeywordsAnalysisConfig.class);
            keywordsAnalysisConfig.ignoreFileTypes = preHandleList(keywordsAnalysisConfig.ignoreFileTypes);
            keywordsAnalysisConfig.ignoreFiles = preHandleList(keywordsAnalysisConfig.ignoreFiles);
            if (null != keywordsAnalysisConfig.ignoreFiles) {
                keywordsAnalysisConfig.ignoreFilePatterns = Arrays.asList(getRegExPatterns(keywordsAnalysisConfig.ignoreFiles));
            }
            if (null != keywordsAnalysisConfig.commentLinePatterns) {
                keywordsAnalysisConfig.commentLinePatterns.removeIf(s -> StringUtils.isEmpty(StringUtils.trim(s)));
                keywordsAnalysisConfig.commentLinePatterns.replaceAll(String::trim);
                keywordsAnalysisConfig.commentLinePatterns = KeywordsAnalyseController.removeDuplicates(keywordsAnalysisConfig.commentLinePatterns);
            }
            logger.info("Keywords analysis config: {}", JsonTransfer.toJsonFormatString(keywordsAnalysisConfig));

            keywordsAnalysisConfig.javaExts    = preHandleList(keywordsAnalysisConfig.javaExts);
            keywordsAnalysisConfig.jsExts      = preHandleList(keywordsAnalysisConfig.jsExts);
            keywordsAnalysisConfig.cAndCppExts = preHandleList(keywordsAnalysisConfig.cAndCppExts);
            keywordsAnalysisConfig.pyExts      = preHandleList(keywordsAnalysisConfig.pyExts);
            keywordsAnalysisConfig.htmlExts    = preHandleList(keywordsAnalysisConfig.htmlExts);
            keywordsAnalysisConfig.cSharpExts  = preHandleList(keywordsAnalysisConfig.cSharpExts);

            // init tmp dir
            try {
                FileUtils.forceMkdir(new File(mDataDir));
            } catch (IOException e2) {
                logger.warn("Failed to create tmp directory: {}", e2.getMessage());
            }
        } catch (IOException e) {
            logger.info("Failed to load keywords ignore config: {}", e.getMessage());
        }
    }

    public static KeywordsAnalysisConfig getKeywordsAnalysisConfig() {
        return keywordsAnalysisConfig;
    }

    public static List<String> getCommentLinePatterns() {
        return keywordsAnalysisConfig.commentLinePatterns;
    }

    public static List<String> getIgnoreFileTypes() {
        return keywordsAnalysisConfig.ignoreFileTypes;
    }

    synchronized public static List<Pattern> getIgnoreFilePatterns() {
        return keywordsAnalysisConfig.ignoreFilePatterns;
    }

    public static int getMaxTaskCount() {
        if (keywordsAnalysisConfig.maxTaskCount <= 0) {
            keywordsAnalysisConfig.maxTaskCount = DEFAULT_MAX_TASK_COUNT;
        }
        return keywordsAnalysisConfig.maxTaskCount;
    }

    public static int getMaxMatchStringLen() {
        if (keywordsAnalysisConfig.maxMatchStringLen <= 0) {
            keywordsAnalysisConfig.maxMatchStringLen = DEFAULT_MAX_MATCH_STR_LEN;
        }
        return keywordsAnalysisConfig.maxMatchStringLen;
    }
    public static boolean isIgnoreSvnSSLCert() {
        return keywordsAnalysisConfig.ignoreSvnServerSSLCert;
    }

    public static boolean isVerbose() {
        return keywordsAnalysisConfig.verbose;
    }

    public static String getTaskDir() {
        String dir = Paths.get(mDataDir, TASK_DIR).toString();
        try {
            FileUtils.forceMkdir(new File(dir));
        } catch (IOException e) {
            logger.error("Failed to create tmp directory: {}", e.getMessage());
        }
        return dir;
    }

    public static String getTaskBaseDir(String taskId) {
        String dir = Paths.get(mDataDir, TASK_DIR, taskId).toString();
        try {
            FileUtils.forceMkdir(new File(dir));
        } catch (IOException e) {
            logger.error("Failed to create tmp directory: {}", e.getMessage());
        }
        return dir;
    }

    public static String getTaskSubDir(String taskId, String subDir) {
        String dir = Paths.get(getTaskBaseDir(taskId), subDir).toString();
        try {
            FileUtils.forceMkdir(new File(dir));
        } catch (IOException e) {
            logger.error("Failed to create tmp directory: {}", e.getMessage());
        }
        return dir;
    }

    public static String getTaskDownloadSrcDir(String taskId) {
        return getTaskSubDir(taskId, DOWNLOAD_SRC_DIR);
    }

    public static String getTaskResultDir(String taskId) {
        return getTaskSubDir(taskId, RESULT_DIR);
    }
    public static String getTaskTmpResultFile(String taskId) {
        return Paths.get(getTaskResultDir(taskId), taskId + "_keywords.json").toString();
    }
    public static String getTaskResponseFile(String taskId) {
        return Paths.get(getTaskResultDir(taskId), RESPONSE_FILE).toString();
    }

    public static String getTaskResultFile(String taskId) {
        return Paths.get(getTaskResultDir(taskId), taskId + ".json").toString();
    }

    public static String getTaskFinishFile(String taskId) {
        return Paths.get(getTaskResultDir(taskId), FINISH_FILE).toString();
    }

    public static String getFileServerUrl() {
        mFileServerUrl = System.getenv(KEY_FILE_SERVER_URL);
        if (StringUtils.isEmpty(mFileServerUrl)) {
            mFileServerUrl = "http://file.lab.tb/upload";
        }
        return mFileServerUrl;
    }

    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-+=";
    public static String randomAlphaNumeric(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }

    public static String getRespNotifUrl() {
        mRespNotifUrl = System.getenv(KEY_RESPONSE_NOTIF_URL);
        if (StringUtils.isEmpty(mRespNotifUrl)) {
            // mRespNotifUrl = "http://192.168.111.35:7400/keywords/search/response";
            mRespNotifUrl = DEFAULT_RESPONSE_NOTIF_URL;
        }
        return mRespNotifUrl;
    }
}

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
class KeywordsAnalysisConfig {
    @JsonProperty(value = "maxTaskCount")
    int maxTaskCount;
    @JsonProperty(value = "ignoreFileTypes")
    List<String> ignoreFileTypes;
    @JsonProperty(value = "ignoreFiles")
    List<String> ignoreFiles;
    List<Pattern> ignoreFilePatterns;
    @JsonProperty(value = "ignoreSvnServerSSLCert")
    boolean ignoreSvnServerSSLCert;
    @JsonProperty(value = "verbose")
    boolean verbose;
    @JsonProperty(value = "maxMatchStringLen")
    int maxMatchStringLen;
    @JsonProperty(value = "commentLinePatterns")
    List<String> commentLinePatterns;

    // source file extensions
    @JsonProperty(value = "javaExts")
    List<String> javaExts;
    @JsonProperty(value = "jsExts")
    List<String> jsExts;
    @JsonProperty(value = "cAndCppExts")
    List<String> cAndCppExts;
    @JsonProperty(value = "pyExts")
    List<String> pyExts;
    @JsonProperty(value = "htmlExts")
    List<String> htmlExts;
    @JsonProperty(value = "cSharpExts")
    List<String> cSharpExts;

}

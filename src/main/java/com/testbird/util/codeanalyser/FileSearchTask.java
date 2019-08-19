package com.testbird.util.codeanalyser;

import com.testbird.util.common.Downloader;
import com.testbird.util.common.FileUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static com.testbird.util.codeanalyser.Config.MAX_ANALYSE_TIME_IN_MINUTES;
import static com.testbird.util.codeanalyser.Config.getTaskBaseDir;

public class FileSearchTask extends SearchTask {
    private static final Logger logger = LoggerFactory.getLogger(FileSearchTask.class);
    public FileSearchTask(SearchKeywordsRequest request) {
        super(request);
    }
    @Override
    public void run() {
        logger.info("{} start to search.", request.key);
        String downloadDir = getTaskBaseDir(request.key);
        String filename;
        int slashIdx = request.href.lastIndexOf("/");
        if (slashIdx > 0 ) {
            filename = request.href.substring(slashIdx);
        } else {
            filename = request.href;
        }
        String remoteUrl = Config.getFileServerUrl() + "/" + filename;
        String localFilePath = downloadDir + File.separator + filename;
        long start = System.currentTimeMillis();
        if (!Downloader.download(remoteUrl, localFilePath)) {
            errMsg =  "Download file failed: " + request.href;
            taskFinished();
            return;
        }
        long end = System.currentTimeMillis();
        long downloadTime = end - start;
        logger.info("{} download time: {}s", request.key, downloadTime / 1000);

        logger.info("{} start to search.", request.key);
        start = System.currentTimeMillis();
        try {
            String fileType = FileUtil.guessFileType(localFilePath);
            if (!StringUtils.equalsIgnoreCase(fileType, FileUtil.FILE_TYPE_ZIP)) {
                errMsg = "Not zip";
            } else {
                if (FileUtil.decompressFile(new File(localFilePath), fileType, downloadDir)) {
                    FileUtils.deleteQuietly(new File(localFilePath));
                    SrcCodeAnalyser srcCodeAnalyser = new SrcCodeAnalyser();
                    result = srcCodeAnalyser.searchKeywordsInDir(downloadDir, this);
                    result.key = request.key;
                    // result.resultCount = result.keywordMatchInfos.size();
                    result.measures.downloadTime = downloadTime;
                    end = System.currentTimeMillis();
                    long searchTime = (end - start) / 1000;
                    result.measures.searchTime = end - start;
                    logger.info("Search time: {}ms", result.measures.searchTime);

                    if (TimeUnit.MILLISECONDS.toSeconds(searchTime) > MAX_ANALYSE_TIME_IN_MINUTES) {
                        logger.error("Search time too long!!!");
                    }
                } else {
                    errMsg = "Failed to unzip file";
                }
            }
        } catch (Throwable e) {
            errMsg = e.getMessage() + ": " + e.getStackTrace();
        } finally {
            logger.info("{} task finally.", request.key);
            taskFinished();
        }
    }
}

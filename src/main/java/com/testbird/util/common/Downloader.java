package com.testbird.util.common;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件下载
 */
public class Downloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Downloader.class);
    private static final Map<String, String> FILE_NAME_LOCK_MAP = new ConcurrentHashMap<>();

    /**
     * 同步下载（使用了文件锁）
     * @param remote
     * @param local
     * @return
     */
    public static boolean downloadSync(String remote, String local) {
        synchronized (getFileNameLock(local)) {
            return download(remote, local);
        }
    }

    public static boolean download(String httpUrl, String saveFile) {
        LOGGER.info("start download {} -> {}", httpUrl, saveFile);
        HttpURLConnection conn = null;
        int contentLength = -1, responseCode = -1, retry = 2;
        try {
            conn = (HttpURLConnection) new URL(httpUrl).openConnection();
            conn.setConnectTimeout(60 * 1000);
            conn.setReadTimeout(60 * 1000);
            contentLength = conn.getContentLength();
            responseCode = conn.getResponseCode();
        } catch (IOException e) {
            LOGGER.error("{} connect error", httpUrl, e);
        }
        if (conn != null) {
            conn.disconnect();
        }
        LOGGER.info("{} contentLength {}, responseCode {}", httpUrl, contentLength, responseCode);
        if (contentLength == -1 || responseCode > 400) {
            LOGGER.error("{} connect error", httpUrl);
            return false;
        }
        File file = new File(saveFile);
        if (file.length() == contentLength) {
            return true;
        } else {
            FileUtils.deleteQuietly(file);
        }
        while (!Thread.currentThread().isInterrupted() && file.length() != contentLength && retry > 0) {
            try {
                conn = (HttpURLConnection) new URL(httpUrl).openConnection();
                conn.setConnectTimeout(60 * 1000);
                conn.setReadTimeout(120 * 1000);
                if (contentLength > 500000000) {
                    String range = "bytes=" + file.length() + "-" + (contentLength / retry);
                    LOGGER.info("{} request range {}", httpUrl, range);
                    conn.setRequestProperty("Range", range);
                }
            } catch (IOException e) {
                LOGGER.error("{} download error", httpUrl, e);
            }
            if (conn != null) {
                try (
                        FileOutputStream fs = new FileOutputStream(file, true);
                        InputStream is = conn.getInputStream()
                ) {
                    int byteRead;
                    byte[] buffer = new byte[4096];
                    while (!Thread.currentThread().isInterrupted() && (byteRead = is.read(buffer)) != -1) {
                        fs.write(buffer, 0, byteRead);
                    }
                } catch (IOException e) {
                    LOGGER.error("{} download error", httpUrl, e);
                } finally {
                    conn.disconnect();
                }
            }
            retry --;
        }
        LOGGER.info("stop download {}, {} -> {}, {}", httpUrl, contentLength, saveFile, file.length());
        return file.length() == contentLength;
    }

    private static String getFileNameLock(String fileName) {
        String key = "lock_" + fileName;
        if (!FILE_NAME_LOCK_MAP.containsKey(key)) {
            FILE_NAME_LOCK_MAP.put(key, fileName);
        }
        return FILE_NAME_LOCK_MAP.get(key);
    }
}

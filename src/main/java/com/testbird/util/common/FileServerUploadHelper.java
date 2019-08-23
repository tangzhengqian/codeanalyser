package com.testbird.util.common;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * 文件上传工具类
 */
public class FileServerUploadHelper {
    private static final Logger sLogger = LoggerFactory.getLogger(FileServerUploadHelper.class);


    public static boolean uploadFile(String fileServerUrl, File file, boolean keepFileInLocal) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File subFile : files) {
                if (subFile.isDirectory()) {
                    if (!uploadFile(fileServerUrl, subFile, keepFileInLocal)) {
                        return false;
                    }
                } else {
                    if (putFile(fileServerUrl, subFile)) {
                        if (!keepFileInLocal) {
                            subFile.delete();
                        }
                    } else {
                        return false;
                    }
                }
            }
        } else {
            if (putFile(fileServerUrl, file)) {
                if (!keepFileInLocal) {
                    file.delete();
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean putFile(String fileServerUrl, File file) {
        String url = fileServerUrl + "/" + file.getName();
        sLogger.debug("start upload file with url : " + url );
        HttpPut put = new HttpPut(url);
        EntityBuilder builder = EntityBuilder.create();
        builder.setFile(file);
        HttpEntity entity = builder.build();
        put.setEntity(entity);
        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(60000).setConnectTimeout(1000).build();
        put.setConfig(requestConfig);
        HttpResponse response = null;
        try (CloseableHttpClient client = HttpClients.createDefault()){
            response = client.execute(put);
        } catch (IOException e) {
            sLogger.error("{} uploader error {}", file, e.getMessage(), e);
        }
        if (response != null) {
            sLogger.debug("{} response status code {}", file, response.getStatusLine().getStatusCode());
        }
        return response != null && (response.getStatusLine().getStatusCode() == 204 ||
                response.getStatusLine().getStatusCode() == 201 ||
                response.getStatusLine().getStatusCode() == 200);
    }
}

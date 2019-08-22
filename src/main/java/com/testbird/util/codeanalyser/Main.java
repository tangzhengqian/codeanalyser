package com.testbird.util.codeanalyser;

import com.testbird.util.common.FileUtil;
import com.testbird.util.common.NetworkUtils;
import com.testbird.util.common.Slf4jLogMessageObserver;
import org.restexpress.RestExpress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
   need to set env var TB_NET_INTERFACE_NAME, FILE_SERVER_URL
 */
public class Main {
    static { System.setProperty("logback.configurationFile", "logback_canaly.xml"); }
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String SERVICE_NAME = "keywords_analyser";

    private static final int REST_SERVER_PORT = 7400;
    private static final int REST_THREAD_POOL_SIZE = 20;
    private static final int REQUEST_MAX_CONTENT_SIZE = 1024 * 1024 * 100;

    private static RestExpress restExpress;

    public static void main(String[] args) {
        LOGGER.info("Keywords Analyser version: {}", FileUtil.getReleaseVersion());
        Config.loadKeywordsAnalysisConfig();

        restExpress = initializeServer();
        Thread restExpressThread = new Thread(() -> restExpress.awaitShutdown());
        restExpressThread.start();
        try {
            restExpressThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static RestExpress initializeServer() {
        LOGGER.info("Starting server on {}:{}", NetworkUtils.getIPAddress(), REST_SERVER_PORT);
        // RestExpress.setDefaultSerializationProvider(new SerializationProvider());
        RestExpress server = new RestExpress()
                .setName(SERVICE_NAME)
                .setBaseUrl(String.format("http://%s:%s", NetworkUtils.getIPAddress(), REST_SERVER_PORT))
                .setExecutorThreadCount(REST_THREAD_POOL_SIZE)
                .setMaxContentSize(REQUEST_MAX_CONTENT_SIZE)
                .addMessageObserver(new Slf4jLogMessageObserver());
        Routes.define(server);
        server.bind(REST_SERVER_PORT);
        LOGGER.info("Starting server succeed.");
        return server;
    }
}

package com.testbird.util.codeanalyser;

import io.netty.handler.codec.http.HttpMethod;
import org.restexpress.RestExpress;

/**
 * Rest Server接口定义
 */
public class Routes {
    private static KeywordsAnalyseController keywordsAnalyseController = new KeywordsAnalyseController();
    static void define(RestExpress server) {
        //RestServer的 /keywords/search 接口会在 keywordsAnalyseController 类的 searchKeywords 方法中处理
        server.uri("/keywords/search", keywordsAnalyseController)
                .action("searchKeywords", HttpMethod.POST);
        //类推
        server.uri("/keywords/stop", keywordsAnalyseController)
                .action("stopSearchKeywords", HttpMethod.POST);
    }
}

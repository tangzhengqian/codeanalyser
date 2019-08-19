package com.testbird.util.codeanalyser;

import io.netty.handler.codec.http.HttpMethod;
import org.restexpress.RestExpress;

public class Routes {
    private static KeywordsAnalyseController keywordsAnalyseController = new KeywordsAnalyseController();
    static void define(RestExpress server) {
        server.uri("/keywords/search", keywordsAnalyseController)
                .action("searchKeywords", HttpMethod.POST);
        server.uri("/keywords/stop", keywordsAnalyseController)
                .action("stopSearchKeywords", HttpMethod.POST);
        // only for test
        // server.uri("/keywords/search/response", keywordsAnalyseController)
        //         .action("searchKeywordsResp", HttpMethod.POST);
    }
}

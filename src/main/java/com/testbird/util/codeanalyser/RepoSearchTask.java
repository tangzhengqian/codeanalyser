package com.testbird.util.codeanalyser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.testbird.util.codeanalyser.Config.MAX_ANALYSE_TIME_IN_MINUTES;

public class RepoSearchTask extends SearchTask {
    private static final Logger logger = LoggerFactory.getLogger(SearchTask.class);
    public RepoSearchTask(SearchKeywordsRequest request) {
        super(request);
    }
    @Override
    public void run() {
        try {
            logger.info("{} start to search.", request.key);
            RepoInfo repoInfo = request.repoInfo;
            switch (repoInfo.repoType) {
                case "svn":
                    // result = SrcCodeAnalyser.searchKeywordsInSvnRepo(this);
                    SrcCodeAnalyser srcCodeAnalyser = new SrcCodeAnalyser();
                    result = srcCodeAnalyser.searchKeywordsInSvnRepo(this);
                    result.key = request.key;
                    break;
                // case "git":
                //     // result = SrcCodeAnalyser.searchKeywordsInGitRepo(repoInfo.repoAdrr, repoInfo.userName, repoInfo.password,
                //     //         reqData.keywordInfos, reqData.resultAttr);
                //     // result.key = reqData.key;
                //     break;
                default:
                    throw new RuntimeException("Should not happen.");
            }
            if (result.measures.searchTime / (60 * 1000) > MAX_ANALYSE_TIME_IN_MINUTES) {
                logger.error("Search time too long!!!");
            }
        } catch (SearchCanceledException e) {
            errMsg = "Canceled.";
        } catch (SrcCodeAnalyser.DownloadException e) {
            errMsg = "Failed to download src: " + e.getMessage();
        } catch (Throwable e) {
            errMsg = e.getMessage() + ": " + e.getStackTrace();
        } finally {
            logger.info("{} task finally.", request.key);
            taskFinished();
        }
    }
}

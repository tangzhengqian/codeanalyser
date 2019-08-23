package com.testbird.util.codeanalyser;

import com.testbird.util.common.JsonTransfer;

import com.testbird.util.common.WorkerThreadFactory;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class KeywordsAnalyseController {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeywordsAnalyseController.class);

    private static final String ERR_INVALID_SEARCH_TARGET_TYPE = "Search target type error: none or not supported: %s";
    private static final String ERR_NO_KEYWORDS = "No keywords to search.";
    private static final String ERR_NOTHING_TO_SEARCH = "Nothing to search.";
    private static final String ERR_REPO_TYPE_NOT_SUPPORTED = "Src repo type not supported: %s";
    private static final String ERR_NO_FILE_TYPES = "No file types to search.";

    private static ThreadPoolExecutor searchTaskExecutorPool = (ThreadPoolExecutor)Executors.newFixedThreadPool(Config.getMaxTaskCount(),
            new WorkerThreadFactory("Keywords-Analysis"));//执行任务的线程池
    private static ConcurrentHashMap<String, SearchTask> runningTasks = new ConcurrentHashMap<>();//当前的执行任务列表

    /**
     * 扫描关键字
     * @param request
     * @param response
     */
    public void searchKeywords(Request request, Response response) {
        // {"type":"file", "key": "xxx", "keywords":[], "regExpKeywords": [], "href"="file.lab.tb/upload/xxxx.zip"}
        // {
        //     "type":"repo", "key": "xxx", "keywords":[], "regExpKeywords": [],
        //     "repoInfo": { "repoType":"svn", "repoAddr":"svn://...", "username":"name", "password":"123", "repoVersion":"1234"}
        // }
        ByteBuf bf = request.getBody();
        String bodyStr = bf.toString(StandardCharsets.UTF_8);
        LOGGER.debug("Search request:\n{}", bodyStr);
        int activeTaskCount = searchTaskExecutorPool.getActiveCount();
        LOGGER.debug("Active task count: {}", activeTaskCount);
        try {
            SearchKeywordsRequest reqData = request.getBodyAs(SearchKeywordsRequest.class, "Request body error.");//把请求的json数据转为对象
            validateRequest(reqData);

            preHandleRequest(reqData);

            switch (reqData.type) {
                case "file":
                    searchKeywordsInFile(reqData, response);
                    break;
                case "repo":
                    searchKeywordsInRepo(reqData, response);
                    break;
                default:
                    break;
            }
        } catch (BadRequestException e) {
            LOGGER.error("Bad request: {}", e.getMessage());
            response.setResponseStatus(new HttpResponseStatus(400, e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Failed to handle request: {}, {}", e.getMessage(), e.getStackTrace());
            response.setResponseStatus(new HttpResponseStatus(500, e.getMessage()));
        }
    }

    /**
     * 停止扫描
     * @param request
     * @param response
     */
    public void stopSearchKeywords(Request request, Response response) {
        // {"key": "xxx"}
        ByteBuf bf = request.getBody();
        String bodyStr = bf.toString(StandardCharsets.UTF_8);
        LOGGER.debug("Stop search request:\n{}", bodyStr);
        try {
            Map<String, Object> requestMap = JsonTransfer.toMap(bodyStr);
            if (null != requestMap && requestMap.containsKey("key")) {
                String key = (String) requestMap.get("key");
                SearchTask searchTask = runningTasks.get(key);
                if (null != searchTask) {
                    searchTask.stop();
                    response.setResponseCreated();
                } else {
                    LOGGER.info("{} task may have already finished or not exist", key);
                    response.setResponseStatus(new HttpResponseStatus(200, "Task may have already finished or not exist"));
                }
            } else {
                LOGGER.info("Bad request");
                response.setResponseStatus(HttpResponseStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            LOGGER.info("Bad request: {}", e.getMessage());
            response.setResponseStatus(new HttpResponseStatus(400, e.getMessage()));
        }
    }

    /**
     * 从任务列表中移除任务
     * @param key
     */
    public static void removeTask(String key) {
        runningTasks.remove(key);
    }

    /**
     * 预处理并验证请求
     * @param request
     */
    public static void preHandleRequest(SearchKeywordsRequest request) {

        request.ignoreKeywords = preHandleList(request.ignoreKeywords);
        if (null != request.ignoreKeywords) {
            request.ignoreKeywordPatterns = Arrays.asList(getRegExPatterns(request.ignoreKeywords));
            LOGGER.info("{} Ignore keywords: {}", request.key, Arrays.toString(request.ignoreKeywords.toArray()));
        }
        request.ignoreFiles = preHandleList(request.ignoreFiles);
        if (null != request.ignoreFiles) {
            LOGGER.info("{} Ignore files: {}", request.key, Arrays.toString(request.ignoreFiles.toArray()));
        }

        boolean bAnyKeywords = false;
        for (KeywordInfo keywordInfo : request.keywordInfos) {
            if (null != keywordInfo.keywords) {
                keywordInfo.keywords = preHandleList(keywordInfo.keywords);
                if (null != request.ignoreKeywords) {
                    keywordInfo.keywords.removeIf(keyword -> StringUtils.equalsAnyIgnoreCase(keyword, request.ignoreKeywords.toArray(new String[0])));
                }
                if (keywordInfo.keywords.size() > 0) {
                    bAnyKeywords = true;
                }
                LOGGER.info("{} Keywords: {}", request.key, Arrays.toString(keywordInfo.keywords.toArray()));
                keywordInfo.keywordPatterns = Arrays.asList(getRegExPatterns(keywordInfo.keywords));
            }
        }
        if (!bAnyKeywords) {
            throw new BadRequestException(ERR_NO_KEYWORDS);
        }

        if (null != request.ignoreFiles) {
            if (request.type.equals("repo")) {
                String repoAddr = request.repoInfo.repoAdrr;
                repoAddr = StringUtils.stripEnd(repoAddr, "/");//去掉末尾的/
                repoAddr = repoAddr.toLowerCase();

                List<String> ignoreFiles = new LinkedList<>();
                for (String filePath : request.ignoreFiles) {
                    filePath = StringUtils.stripEnd(filePath, "/");

                    if (filePath.equals(repoAddr)) {
                        continue;
                    }
                    if (filePath.startsWith(repoAddr)) {
                        String newPath = filePath.substring(repoAddr.length() + 1);
                        LOGGER.info("{} Ignore file new path: {}", request.key, newPath);
                        ignoreFiles.add(newPath);
                    } else {
                        ignoreFiles.add(filePath);
                    }
                }
                request.ignoreFiles = removeDuplicates(ignoreFiles);
            }
        }
    }

    /**
     * 处理list数据
     * @param list
     * @return
     */
    static List<String> preHandleList(List<String> list) {
        if (null != list) {
            list.removeIf(s -> StringUtils.isEmpty(StringUtils.trim(s)));//移除isEmpty的数据
            list.replaceAll(String::toLowerCase);//把list中的数据全部变为小写
            list.replaceAll(String::trim);//把list中的数据全部去掉两边空格
            list = removeDuplicates(list);
        }
        return list;
    }

    /**
     * 移除重复的数据
     * @param list
     * @return
     */
    static List<String> removeDuplicates(List<String> list) {
        Set<String> set = new TreeSet<>((s1, s2) -> StringUtils.compareIgnoreCase(s1, s2));
        set.addAll(list);
        list.clear();
        list.addAll(set);
        return list;
    }

    /**
     * 验证请求参数是否正确
     * @param reqData
     */
    private void validateRequest(SearchKeywordsRequest reqData) {
        if (StringUtils.isEmpty(reqData.key)) {
            throw new BadRequestException("No request identifier");
        }
        if (runningTasks.containsKey(reqData.key)) {
            throw new BadRequestException("Duplicated task key: " + reqData.key);
        }
        if (StringUtils.isEmpty(reqData.type) || !StringUtils.equalsAny(reqData.type, "file", "repo")) {
            throw new BadRequestException(String.format(ERR_INVALID_SEARCH_TARGET_TYPE, reqData.type));
        }

        if (null == reqData.includeFileTypes || reqData.includeFileTypes.size() <= 0) {
            throw new BadRequestException(ERR_NO_FILE_TYPES);
        }
        reqData.includeFileTypes.replaceAll(s -> {
            s = StringUtils.strip(s);
            s = StringUtils.stripStart(s, ".");
            return s;
        });
        reqData.includeFileTypes = preHandleList(reqData.includeFileTypes);
        if (null != reqData.includeFileTypes && reqData.includeFileTypes.size() > 0) {
            LOGGER.info("{} include file types: {}", reqData.key, Arrays.toString(reqData.includeFileTypes.toArray()));
        } else {
            throw new BadRequestException(ERR_NO_FILE_TYPES);
        }

        if (null == reqData.keywordInfos || reqData.keywordInfos.size() == 0) {
            throw new BadRequestException(ERR_NO_KEYWORDS);
        }

        switch (reqData.type) {
            case "file":
                reqData.href = StringUtils.trim(reqData.href);
                if (StringUtils.isEmpty(reqData.href)) {
                    throw new BadRequestException(ERR_NOTHING_TO_SEARCH);
                }
                break;
            case "repo":
                validateRepoInfo(reqData);
                break;
            default:
                break;
        }
    }

    /**
     * 将Collection<String> 转成 Pattern[]
     * @param regExps
     * @return
     */
    public static Pattern[] getRegExPatterns(Collection<String> regExps) {
        Pattern[] patterns = null;
        if (null != regExps) {
            patterns = regExps.stream().filter(regexp -> !StringUtils.isEmpty(regexp)).map(regexp -> Pattern.compile(regexp, CASE_INSENSITIVE)).toArray(Pattern[]::new);
        }
        return patterns;
    }

    /**
     * 从压缩文件中搜索关键字
     * @param reqData
     * @param response
     * @throws BadRequestException
     */
    private void searchKeywordsInFile(SearchKeywordsRequest reqData, Response response) throws BadRequestException {
        SearchTask searchTask = new FileSearchTask(reqData);
        runningTasks.put(reqData.key, searchTask);
        // searchTask.start();
        Future<?> future = searchTaskExecutorPool.submit(searchTask);
        searchTask.setTask(future);
        response.setResponseCreated();
    }

    /**
     * 从svn仓库里搜索关键字
     * @param reqData
     * @param response
     * @throws BadRequestException
     */
    private void searchKeywordsInRepo(SearchKeywordsRequest reqData, Response response) throws BadRequestException {
        reqData.repoInfo.repoType = "svn";
        SearchTask searchTask = new RepoSearchTask(reqData);
        runningTasks.put(reqData.key, searchTask);
        // searchTask.start();
        Future<?> future = searchTaskExecutorPool.submit(searchTask);
        searchTask.setTask(future);
        response.setResponseCreated();
    }


    /**
     * 验证svn仓库信息
     * @param reqData
     * @throws BadRequestException
     */
    private void validateRepoInfo(SearchKeywordsRequest reqData) throws BadRequestException {
        if (null == reqData.repoInfo) {
            throw new BadRequestException(ERR_NOTHING_TO_SEARCH);
        }
        if (!StringUtils.equalsAny(reqData.repoInfo.repoType, "svn"/*, "git"*/)) {
            throw new BadRequestException(String.format(ERR_REPO_TYPE_NOT_SUPPORTED, reqData.repoInfo.repoType));
        }
        reqData.repoInfo.repoAdrr = StringUtils.trim(reqData.repoInfo.repoAdrr);
        if (StringUtils.isEmpty(reqData.repoInfo.repoAdrr)) {
            throw new BadRequestException(ERR_NOTHING_TO_SEARCH);
        }
    }

}

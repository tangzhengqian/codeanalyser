package com.testbird.util.codeanalyser;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * svn下载工具类
 */
public class SrcDownloader {
    private static final Logger logger = LoggerFactory.getLogger(SrcDownloader.class);

    public static final String SVN_OPTIONS_IGNORE_SSL_CERT = " --non-interactive --trust-server-cert-failures=unknown-ca,cn-mismatch,expired,not-yet-valid,other";

    static SrcDownloadResult downloadGitRepo(String srcRepoUrl, String userName, String password, String localDir) {
        logger.info("Downloading git src {} -> {}", srcRepoUrl, localDir);
        String cmd = "git clone " + srcRepoUrl;
        SrcDownloadResult srcDownloadResult = executeDownload(cmd, localDir, null);
        logger.info("Downloading result: {}", srcDownloadResult.success ? "ok" : srcDownloadResult.errMsg);
        return srcDownloadResult;
    }

    // Repository URL examples:
    // Apache HTTP Server: https://svn.example.com/repos/MyRepo/MyProject/trunk
    // svnserve: svn://svn.example.com/repos/MyRepo/MyProject/branches/MyBranch
    //           svn+ssh://
    // Direct access (Unix-style): file:///var/svn/repos/MyRepo/MyProject/tags/1.1.0
    // Direct access (Windows-style): file:///C:/Repositories/MyRepo/trunk/MyProject
    // svn checkout URL[@REV]... [PATH]
    // Options
    // --depth ARG
    // --force
    // --ignore-externals
    // --quiet (-q)
    // --revision (-r) REV
    // Global options:
    // --username ARG
    // --password ARG
    // svn checkout -r 2 file:///var/svn/repos/test mine
    static SrcDownloadResult downloadSvnRepo(String srcRepoUrl, String version, String userName, String password, String localDir, SearchTask searchTask) {
        logger.info("Downloading svn src {} -> {}, version: {}", srcRepoUrl, localDir, version);
        String cmd = "svn checkout " + srcRepoUrl + " " + localDir;
        if (!StringUtils.isEmpty(version)) {
            cmd += " -r " + version;
        }
        if (!StringUtils.isEmpty(userName) && !StringUtils.isEmpty(password)) {
            cmd += " --username " + userName;
            cmd += " --password " + password;
        }
        if (Config.isIgnoreSvnSSLCert()) {
            cmd += SVN_OPTIONS_IGNORE_SSL_CERT;
        }
        SrcDownloadResult srcDownloadResult = executeDownload(cmd, localDir, searchTask);
        logger.info("Downloading result: {}", srcDownloadResult.success ? "ok" : srcDownloadResult.errMsg);
        return srcDownloadResult;
    }

    // Checked out revision 17.
    private static final Pattern svnRevisionPattern = Pattern.compile("Checked out revision (\\d+)");
    static SrcDownloadResult executeDownload(String cmd, String localDir, SearchTask searchTask) {
        logger.info("cmd: {} localDir: {}", cmd, localDir);
        SrcDownloadResult downloadResult = new SrcDownloadResult();
        String errMsg = "";
        ProcessBuilder builder = new ProcessBuilder();
        Process p = null;
        int exitValue = -1;
        if (SystemUtils.IS_OS_WINDOWS) {
            builder.command("cmd", "/c", cmd);
        } else {
            builder.command("sh", "-c", cmd);
        }
        try {
            builder.directory(new File(localDir));
            p = builder.start();
            searchTask.setDownloadSrcProcess(p);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while (!searchTask.canceled && null != (line = br.readLine())) {
                    Matcher matcher = svnRevisionPattern.matcher(line);
                    if (Config.isVerbose()) {
                        logger.info(line);
                    }
                    if (matcher.find()) {
                        logger.info("Source version: {}", line);
                        downloadResult.version = matcher.group(1);
                    }
                }
            } catch (IOException e) {
                logger.info("{}", e.getMessage());
                errMsg += "\n" + e.getMessage();
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while (!searchTask.canceled && null != (line = br.readLine())) {
                    logger.error(line);
                    errMsg += "\n" + line;
                }
            } catch (IOException e) {
                logger.error("{}", e.getMessage());
                errMsg += "\n" + e.getMessage();
            }
            if (!searchTask.canceled) {
                exitValue = p.waitFor();
            }
        } catch (Exception e) {
            logger.error("{}", e.getMessage());
            errMsg += "\n" + e.getMessage();

        } finally {
            searchTask.stopDownloadSrcProcess();
        }
        if (searchTask.canceled) {
            downloadResult.success = false;
            downloadResult.errMsg = "canceled";
        } else {
            if (0 != exitValue) {
                downloadResult.success = false;
                downloadResult.errMsg = errMsg;
            } else {
                downloadResult.success = true;
                downloadResult.errMsg = null;
            }
        }
        return downloadResult;
    }
}

class SrcDownloadResult {
    boolean success;
    String version;
    String errMsg;
}
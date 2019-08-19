package com.testbird.util.common;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.SystemUtils.*;

public class RuntimeEnv {
    private static final Logger mLogger = LoggerFactory.getLogger(RuntimeEnv.class);

    public static List<String> runShell(String command, long milliseconds) {
        CommandLine commandline = CommandLine.parse(command);
        return runShell(commandline, null, milliseconds);
    }
    public static List<String> runShell(String command, String workDir, long milliseconds) {
        CommandLine commandline = CommandLine.parse(command);
        return runShell(commandline, workDir, milliseconds);
    }

    public static List<String> runShell(CommandLine commandLine, String workDir, long milliseconds) {
        return runShell(commandLine, workDir, milliseconds, true);
    }
    public static List<String> runShell(CommandLine commandLine, String workDir, long milliseconds, boolean verbose) {
        if (verbose) {
            mLogger.info(commandLine.toString());
        }
        List<String> results = new ArrayList<>();
        try {
            DefaultExecutor exec = new DefaultExecutor();
            exec.setExitValues(null);
            if (!StringUtils.isEmpty(workDir)) {
                exec.setWorkingDirectory(new File(workDir));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream error = new ByteArrayOutputStream();
            PumpStreamHandler streamHandler = new PumpStreamHandler(output, error);
            exec.setStreamHandler(streamHandler);
            ExecuteWatchdog watchdog = new ExecuteWatchdog(milliseconds);
            exec.setWatchdog(watchdog);
            int exit = exec.execute(commandLine);
            String charset = IS_OS_WINDOWS ? "GBK" : "UTF-8";
            results.add(output.toString(charset));
            results.add(error.toString(charset));
            results.add(String.valueOf(exit));
        } catch (Exception e) {
            mLogger.error("runShell Exception", e);
        }
        return results;
    }

    public static List<String> runShell2(String command, String workDir) {
        List<String> results = new ArrayList<>();
        ProcessBuilder builder = new ProcessBuilder();
        Process p = null;
        if (SystemUtils.IS_OS_WINDOWS) {
            builder.command("cmd", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        try {
            if (workDir != null) {
                builder.directory(new File(workDir));
            }
            builder.redirectErrorStream(true);
            p = builder.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while (null != (line = br.readLine())) {
                    results.add(line);
                }
            } catch (IOException e) {
                mLogger.error("runShell2 IOException", e);
            }

        } catch (Exception e) {
            mLogger.error("runShell2 Exception", e);
        } finally {
            if (p != null && p.isAlive()) {
                p.destroy();
            }
        }
        return results;
    }

    public static String getSystemId() {
        if (IS_OS_LINUX) {
            return getLinuxSystemId();
        } else if (IS_OS_MAC) {
            return getMacSystemId();
        } else {
            mLogger.warn("OS not supported.");
            return null;
        }
    }

    private static final String LSBLK = "lsblk --nodeps -o name,serial";
    // NAME SERIAL
    // sda P02532100895
    private static final String LSBLK2 = "lsblk -o uuid";
    //  lsblk -o uuid
    // UUID

    // ec5c9ab8-b71e-46e6-aac9-5ccc757a02d2
    // 971ffe7e-0c71-40c1-97a9-bdb6b167d4b7
    public static String getLinuxSystemId() {
        String sysId = null;
        CommandLine commandline = CommandLine.parse(LSBLK);
        List<String> results = runShell(commandline, null, 3000, false);
        if (null != results && results.size() >= 3) {
            if (StringUtils.equals("0", results.get(2))) {
                String[] lines = results.get(0).split("\n");
                for (String line : lines) {
                    if (StringUtils.startsWithAny(line.trim(), "sd", "hd", "sr")) {
                        sysId = line.split("\\s+")[1].trim();
                        break;
                    }
                }
            } else {
                mLogger.error("failed to get system ID: {}", results.get(2));
            }
        }
        if (null == sysId) {
            results = runShell(LSBLK2, 3000);
            if (null != results && results.size() >= 3) {
                if (StringUtils.equals("0", results.get(2))) {
                    String[] lines = results.get(0).split("\n");
                    for (String line : lines) {
                        if (line.trim().isEmpty() || line.trim().equalsIgnoreCase("UUID")) {
                            continue;
                        }
                        sysId = lines[2].trim();
                        break;
                    }
                } else {
                    mLogger.error("failed to get system ID: {}", results.get(2));
                }
            } else {
                mLogger.error("failed to get system ID: {}", results.get(2));
            }
        }
        return sysId;
    }

    private static final String SYS_PROFILER = "system_profiler SPHardwareDataType";
    public static String getMacSystemId() {
        CommandLine commandline = CommandLine.parse(SYS_PROFILER);
        List<String> results = runShell(commandline, null, 3000, false);
        if (null != results && results.size() >= 3) {
            if (StringUtils.equals("0", results.get(2))) {
                String[] lines = results.get(0).split("\n");
                for (String line : lines) {
                    if (line.trim().startsWith("Serial Number (system)")) {
                        return line.split(":")[1].trim();
                    }
                }
                mLogger.error("failed to get system ID: {}", results.get(0));
                return null;
            } else {
                mLogger.error("failed to get system ID: {}", results.get(2));
                return null;
            }
        } else {
            mLogger.error("failed to get system ID.");
            return null;
        }
    }
}

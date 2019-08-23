package com.testbird.util.common;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by penghongqin on 16-1-13.
 */
public class FileUtil {
    private static final Logger sLogger = LoggerFactory.getLogger(FileUtil.class);

    public static String getFileMd5(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            MappedByteBuffer byteBuffer = in.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(byteBuffer);
            BigInteger bi = new BigInteger(1, md5.digest());
            return bi.toString(16);
        } catch (Exception e) {
            sLogger.error("getFileMd5 failed.", e);
        }
        return null;
    }

    private static File[] listFiles(File dir, final String... extensions) {
        FileFilter filter = file -> {
            for (String ext : extensions) {
                if (file.getName().endsWith(ext)) {
                    return true;
                }
            }
            return false;
        };
        return dir.listFiles(filter);
    }

    public static boolean doZip(File zipFile, boolean keep, File... files) {
        BufferedInputStream bis = null;
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(zipFile)))) {
            byte data[] = new byte[1024];
            for (File file : files) {
                sLogger.debug("zip file begin: {}", file.getAbsolutePath());
                bis = new BufferedInputStream(new FileInputStream(file),
                        data.length);
                ZipEntry entry = new ZipEntry(file.getName());
                zos.putNextEntry(entry);
                int count = -1;
                while ((count = bis.read(data, 0, data.length)) != -1) {
                    zos.write(data, 0, count);
                }
                bis.close();
                if (!keep) {
                    file.delete();
                }
                sLogger.debug("zip file end: {}", file.getAbsolutePath());
            }
            bis = null;
            return true;
        } catch (IllegalArgumentException | IOException e) {
            sLogger.warn("doZip exception", e);
            return false;
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    sLogger.warn("close bis exception", e);
                }
            }
        }
    }

    public static boolean doZipFiles(String key, String path) {
        File dir = new File(path);
        if (StringUtils.isEmpty(path) || !dir.exists()) {
            return false;
        }
        File[] inputFiles = dir.listFiles();
        if (inputFiles == null || inputFiles.length == 0) {
            return false;
        }
        File zipFile = new File(path, key);
        if (!zipFile.exists() || zipFile.delete()) {
            return doZip(zipFile, false, inputFiles);
        }
        return false;
    }

    public static boolean doZipFiles(String key, String path, String... extensions) {
        File[] inputFiles = listFiles(new File(path), extensions);
        if (inputFiles == null || inputFiles.length == 0) {
            return false;
        }
        File zipFile = new File(path, key);
        if (!zipFile.exists() || zipFile.delete()) {
            return doZip(zipFile, false, inputFiles);
        }
        return false;
    }

    public static boolean doZipFiles(File zipFile, boolean keep, List<String> paths) {
        boolean allSuccess = true;
        if (zipFile.exists() && !zipFile.delete()) {
            return false;
        }
        List<File> inputFiles = new ArrayList<>();
        for (String path : paths) {
            File file = new File(path);
            if (StringUtils.isEmpty(path) || !file.exists()) {
                continue;
            }
            if (file.isDirectory()) {
                inputFiles.addAll(Arrays.asList(file.listFiles()));
            } else {
                inputFiles.add(file);
            }
        }
        for (File file : inputFiles) {
            sLogger.debug("mFilesForUpload file: {}, size: {}", file.getAbsolutePath(), file.length());
        }
        doZip(zipFile, keep, (File[])inputFiles.toArray(new File[inputFiles.size()]));
        sLogger.debug("zipFile file: {}, size: {}", zipFile.getAbsolutePath(), zipFile.length());

        return allSuccess;
    }

    public static boolean copyFile(String oldPath, String newPath) {
        try (InputStream inStream = new FileInputStream(oldPath);
             FileOutputStream fs = new FileOutputStream(newPath)) {
            byte []buffer = new byte[2048];
            int byteRead;
            while ((byteRead = inStream.read(buffer)) != -1) {
                fs.write(buffer, 0, byteRead);
            }
            return true;
        } catch (Exception e) {
            sLogger.warn("copyFile exception", e);
        }
        return false;
    }

    /**
     * 获取当前版本的TAG信息（gradle编译的时候会把信息写到resources/tag文件中）
     * @return
     */
    public static String getReleaseVersion() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(FileUtil.class.getResourceAsStream("/tag")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public static List<File> iterateFiles(File dir) {
        FileIterator fileIterator = new FileIterator();
        fileIterator.iterate(dir);
        return fileIterator.getFiles();
    }

    public static final String FILE_TYPE_UNKNOWN = "unknown";
    public static final String FILE_TYPE_TEXT = "text";
    public static final String FILE_TYPE_ZIP  = "zip";
    public static final String FILE_TYPE_GZIP  = "gzip";
    public static final String FILE_TYPE_TAR   = "tar";
    public static final String FILE_TYPE_BZIP = "bzip2";
    public static final String FILE_TYPE_EXECUTABLE = "executable";
    public static final String FILE_TYPE_BINARY = "binary";
    // file -b replay-stop.log
    // UTF-8 Unicode text, with very long lines, with CRLF, LF line terminators
    // file aaa.apk
    // aaa.apk: Zip archive data, at least v2.0 to extract
    // file gz.tgz
    // gz.tgz: gzip compressed data, last modified: Thu Dec 27 03:03:28 2018, from Unix
    // file -b bzip.tgz  // brief mode
    // bzip2 compressed data, block size = 900k
    // file -b proxy.tar
    // POSIX tar archive


    // file -b adb
    // ELF 64-bit LSB executable, x86-64, version 1 (SYSV), dynamically linked, interpreter /lib64/ld-linux-x86-64.so.2, for GNU/Linux 2.6.32, BuildID[sha1]=98024d0663309dceddb12f668d86eaf93cfcb01a, stripped
    // file aaa.dll
    // PE32 executable (DLL) (GUI) Intel 80386, for MS Windows
    // file /usr/lib/libau.so.2
    // /usr/lib/libau.so.2: symbolic link to libau.so.2.7
    // file /usr/lib/libau.so.2.7
    // /usr/lib/libau.so.2.7: ELF 64-bit LSB shared object, x86-64, version 1 (SYSV), dynamically linked, BuildID[sha1]=35d03efd97dc7f9f4d773fd62c25724ef774b251, stripped
    // file aaa.so
    // symbolic link to /lib64/libnss_nis.so.2
    // file -b /usr/lib64/libpthread.a
    // current ar archive
    // file  /lib64/librt-2.23.so
    // /lib64/librt-2.23.so: ELF 64-bit LSB shared object, x86-64, version 1 (SYSV), dynamically linked, BuildID[sha1]=10aa224d76e2287b3a61ceb65be8f9e534385404, for GNU/Linux 2.6.32, stripped

    // file -b  /usr/lib/libapr-1.0.dylib
    // Mach-O universal binary with 2 architectures: [i386:Mach-O dynamically linked shared library i386] [x86_64:Mach-O 64-bit dynamically linked shared library x86_64]
    //        /usr/lib/libapr-1.0.dylib (for architecture i386):	Mach-O dynamically linked shared library i386
    // /usr/lib/libapr-1.0.dylib (for architecture x86_64):	Mach-O 64-bit dynamically linked shared library x86_64
    // file -b /Applications/Xcode.app/Contents/Developer/usr/lib/llvm-gcc/4.2.1/libgcc.a
    //     Mach-O universal binary with 2 architectures: [x86_64:current ar archive random library] [i386:current ar archive random library]
    //             /Applications/Xcode.app/Contents/Developer/usr/lib/llvm-gcc/4.2.1/libgcc.a (for architecture x86_64):	current ar archive random library
    // /Applications/Xcode.app/Contents/Developer/usr/lib/llvm-gcc/4.2.1/libgcc.a (for architecture i386):	current ar archive random library

    // file -b /Applications/Xcode.app/Contents/Developer/usr/lib/libXcodeExtension.a
    // current ar archive random library

    public static String guessFileType(String filePath) {
        String type = FILE_TYPE_UNKNOWN;

        CommandLine commandline = CommandLine.parse("which file");
        List<String> results = RuntimeEnv.runShell(commandline, null, 3000, false);
        if (!(results.size() == 3)) {
            return type;
        }
        commandline = CommandLine.parse(String.format("file -b \"%s\"",filePath));
        results = RuntimeEnv.runShell(commandline, null, 3000, false);
        if (results.size() == 3 && StringUtils.equals(results.get(2), "0")) {
            String output = results.get(0);
            output = output.toLowerCase();
            if (output.contains("text")) {
                type = FILE_TYPE_TEXT;
            } else if (output.contains("zip archive")) {
                sLogger.debug("{} file type: {}", filePath, output);
                type = FILE_TYPE_ZIP;
            } else if (output.contains("gzip compressed")) {
                sLogger.debug("{} file type: {}", filePath, output);
                type = FILE_TYPE_GZIP;
            } else if (output.contains("bzip2 compressed")) {
                sLogger.debug("{} file type: {}", filePath, output);
                type = FILE_TYPE_BZIP;
            } else if (output.contains("tar archive")) {
                sLogger.debug("{} file type: {}", filePath, output);
                type = FILE_TYPE_TAR;
            } else if (output.contains("executable")) {
                sLogger.debug("{} file type: {}", filePath, output);
                type = FILE_TYPE_EXECUTABLE;
            } else if (output.contains("dynamically linked") ||
                    output.contains("current ar archive") ||
                    output.contains("binary")) {
                type = FILE_TYPE_BINARY;
            }
        } else {
            sLogger.warn("Failed to get file type: {}", filePath);
        }
        return type;
    }

    private static final String UNZIP_CMD = "unzip %s -d %s";
    private static final String GUNZIP_CMD = "gunzip -c -k -f %s > %s";
    private static final String BUNZIP_CMD = "bunzip2 -c -k -f %s > %s";
    private static final String TAR_UNZIP_CMD = "tar -C %s -xvf %s";
    private static final List<String> COMPRESSED_TYPES = new LinkedList<>();
    static {
        Collections.addAll(COMPRESSED_TYPES, FILE_TYPE_ZIP, FILE_TYPE_GZIP, FILE_TYPE_BZIP, FILE_TYPE_TAR);
    }
    public static boolean isCompressedFile(String fileType) {
        return COMPRESSED_TYPES.contains(fileType);
    }
    public static boolean decompressFile(File file, String fileType, String outDir) {
        String filePath = file.getAbsolutePath();
        String fileName = file.getName();
        String cmd = null;
        boolean printStdOut = true;
        switch (fileType) {
            case FILE_TYPE_ZIP:
                cmd = String.format(UNZIP_CMD, filePath, outDir);
                break;
            case  FILE_TYPE_GZIP:
                printStdOut =false;
                cmd = String.format(GUNZIP_CMD, filePath, Paths.get(outDir, FilenameUtils.getBaseName(fileName)).toString());
                break;
            case  FILE_TYPE_BZIP:
                printStdOut =false;
                cmd = String.format(BUNZIP_CMD, filePath, Paths.get(outDir, FilenameUtils.getBaseName(fileName)).toString());
                break;
            case FILE_TYPE_TAR:
                cmd = String.format(TAR_UNZIP_CMD, outDir, filePath);
                break;
            default:
                sLogger.info("Ignore file of type {}: {}", filePath, fileType);
                break;
        }

        if (null != cmd) {
            // List<String> results = RuntimeEnv.runShell(cmd, -1);
            String result = executeCmd(cmd, printStdOut);
            if (StringUtils.isEmpty(result)) {
            // if (results.size() >= 3 && StringUtils.equals("0", results.get(2))) {
                return true;
            } else {
                sLogger.warn("Failed to unzip file {}: {}", filePath, result);
                return false;
            }
        } else {
            return false;
        }
    }
    static String executeCmd(String cmd, boolean printStdOut) {
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
            sLogger.info(cmd);
            p = builder.start();
            if (printStdOut) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while (null != (line = br.readLine())) {
                        sLogger.info(line);
                    }
                } catch (IOException e) {
                    sLogger.info("{}", e.getMessage());
                    errMsg += "\n" + e.getMessage();
                }
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while (null != (line = br.readLine())) {
                    sLogger.error(line);
                    errMsg += "\n" + line;
                }
            } catch (IOException e) {
                sLogger.error("{}", e.getMessage());
                errMsg += "\n" + e.getMessage();
            }
            try {
                exitValue = p.waitFor();
            } catch (InterruptedException e) {
                // ignore
            }
        } catch (Exception e) {
            sLogger.error("{}", e.getMessage());
            errMsg += "\n" + e.getMessage();
        } finally {
            if (null != p && p.isAlive()) {
                p.destroyForcibly();
            }
        }
        if (0 == exitValue) {
            errMsg = null;
        }
        return errMsg;
    }

    public static void forceMkDir(final File dir) throws IOException {
        FileUtils.forceMkdir(dir);
        if (!dir.isDirectory()) {
            if (dir.exists()) {
                FileUtils.deleteQuietly(dir);
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                //do nothing
            }
            FileUtils.forceMkdir(dir);
        }
    }

    private static class FileIterator {
        private List<File> files = new ArrayList<>();

        public List<File> getFiles() {
            return this.files;
        }

        void iterate(File dir) {
            if (dir == null || !dir.exists()) {
                sLogger.warn("dir not exists : {}", dir == null ? null : dir.getAbsolutePath());
                return;
            }
            if (!dir.isDirectory()) {
                sLogger.warn("dir not directory : {}", dir.getAbsolutePath());
                return;
            }
            File[] files = dir.listFiles();
            if (files != null && files.length != 0) {
                for (File file2 : files) {
                    if (file2.isDirectory()) {
                        iterate(file2);
                    } else {
                        this.files.add(file2);
                    }
                }
            }
        }
    }
}

package com.testbird.util.common;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {
    private static final Logger mLogger = LoggerFactory.getLogger(ZipUtils.class);

    private static final int BUFFER = 1024 * 8;

    public static boolean doZip(File zipFile, boolean keep, List<File> files) {
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(zipFile)))) {
            byte data[] = new byte[1024];
            for (File file : files) {
                try (BufferedInputStream origin = new BufferedInputStream(new FileInputStream(file),
                        data.length)) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    out.putNextEntry(entry);
                    int count;
                    while ((count = origin.read(data, 0, data.length)) != -1) {
                        out.write(data, 0, count);
                    }
                    if (!keep && !file.delete()) {
                        return false;
                    }
                }
            }
            return true;
        } catch (IllegalArgumentException | IOException e) {
            mLogger.error("doZip failed", e);
        }
        return false;
    }

    public static List<File> unZipFileTo(File origin, File target) {
        if (origin == null || !origin.exists()) {
            mLogger.error("zip file {} must not be null", origin);
            return null;
        }
        mLogger.debug(" unzip... :" + origin.getName());
        String name = origin.getAbsolutePath();
        String suffix;
        if (name.lastIndexOf(".") == -1) {
            mLogger.debug("unknown file");
            return null;
        }
        suffix = name.substring(name.lastIndexOf("."));
        if (!suffix.equals(".apk") && !suffix.equals(".zip")
                && !suffix.equals(".xpk") && !suffix.equals(".nsapk")
                && !suffix.equals(".nsxpk")) {
            mLogger.warn("unknown file suffix");
            return null;
        }
        String baseFolder = target.getAbsolutePath();
        if (!target.exists() && !target.mkdirs()) {
            mLogger.warn("failed to create target unzipped folder");
            return null;
        }

        List<File> returnFiles = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(origin));
             BufferedInputStream bin = new BufferedInputStream(zis)) {
            ZipEntry entry;
            File item;
            String itemName;
            while ((entry = zis.getNextEntry()) != null) {
                itemName = entry.getName();
                item = new File(baseFolder + File.separator + itemName);
                returnFiles.add(item);
                mLogger.debug("unzip " + item.getAbsolutePath());
                if (entry.isDirectory()) {
                    if (!item.mkdirs()) {
                        mLogger.warn("failed to create dir: " + item.getAbsolutePath());
                    }
                } else {
                    try {
                        unzip(item, bin, false, null);
                    } catch (Throwable e) {
                        mLogger.error("unzip error: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            mLogger.error(e.getMessage() + e.getStackTrace());
            throw new RuntimeException("Unzip file failed: " + e.getMessage());
        }
        return returnFiles;
    }

    private static void unzip(File file, BufferedInputStream bin,
                              boolean isLastFile, String base) throws IOException {
        if (isLastFile) {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                throw new IOException("create parent dirs failed: " + file.getParent());
            }
            byte b[] = new byte[BUFFER];
            int len;
            try (BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(file))) {
                while ((len = bin.read(b)) != -1) {
                    bout.write(b, 0, len);
                }
            }
        } else {
            if (StringUtils.isEmpty(base)) {
                base = file.getParent();
            }
            String name = file.getAbsolutePath();
            File basedir = new File(base);
            if (!basedir.exists() && !basedir.mkdirs()) {
                throw new IOException("create base dir failed: " + basedir.getAbsolutePath());
            }
            String subFileName = name.substring(base.length() + 1,
                    name.length());
            if (subFileName.contains("\\")) {
                String subBase = base + "\\"
                        + subFileName.substring(0, subFileName.indexOf("\\"));
                unzip(file, bin, false, subBase);
            } else {
                unzip(file, bin, true, null);
            }
        }
    }
}

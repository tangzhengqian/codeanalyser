package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.testbird.util.common.JsonTransfer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*

Constant pool:
    #1 = Class              #229          // java/lang/Integer
    #2 = Methodref          #25.#230      // java/lang/Object."<init>":()V
    #3 = Fieldref           #12.#231      // com/testbird/rio/Main.LOGGER:Lorg/slf4j/Logger;
    #4 = String             #232          // ========== WELCOME ==========
    ... ...
  #232 = Utf8               ========== WELCOME ==========


 LocalVariableTable:
        Start  Length  Slot  Name   Signature
           96      29     3     e   Lorg/quartz/SchedulerException;
          168       5     4     e   Ljava/lang/InterruptedException;
            0     174     0 config   Lcom/testbird/rio/Configuration;
           34     140     1 deviceManager   Lcom/testbird/rio/bridge/DeviceManager;
           86      88     2 scheduler   Lorg/quartz/Scheduler;
          155      19     3 restExpressThread   Ljava/lang/Thread;
 */
public class ByteCodeAnalyser implements KeywordsAnalyser {
    private static final Logger logger = LoggerFactory.getLogger(ByteCodeAnalyser.class);
    private static final String CONSTANT_POOL = "Constant pool:";
    private static final String LOCAL_VAR_TABLE = "LocalVariableTable:";
    public static final int BYTE_CODE_MAGIC = 0xCAFEBABE;

    public static void main(String[] args) {
        String file = "/Users/tengjp/git/testcenter/build/classes/java/main/com/testbird/rio/util/ByteCodeAnalyser.class";
        String[] keywords = new String[] {"username", "password", "root", "keyWordInfoList"};
        List<KeywordMatchInfo> keywordMatchInfoList = new LinkedList<>();
        ByteCodeAnalyser byteCodeAnalyser = new ByteCodeAnalyser();
        SearchKeywordsResult result = new SearchKeywordsResult();
        ResultAttr resultAttr = new ResultAttr();
        resultAttr.extraLineCnt = 3;
        // byteCodeAnalyser.searchKeywords(new File(file), file, Arrays.asList(keywords), null, resultAttr, result);
        System.out.println(JsonTransfer.toJsonFormatString(keywordMatchInfoList));
    }

    public static boolean isClassFile(String filePath) {
        try (FileInputStream sr = new FileInputStream(filePath)) {
            byte[] javaBytecodeMagic = new byte[4];
            int n = sr.read(javaBytecodeMagic, 0, 4);
            if (4 == n) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
                byteBuffer.put(javaBytecodeMagic);
                byteBuffer.flip();
                if (byteBuffer.asIntBuffer().get() == ByteCodeAnalyser.BYTE_CODE_MAGIC) {
                    return true;
                }
            }
        } catch (IOException e) {

        }
        return  false;
    }


    @Override
    public void searchKeywords(String classFile, String zipFilePath) {
        // ProcessBuilder builder = new ProcessBuilder();
        // Process p = null;
        // String cmd = "javap -verbose " + classFile;
        // if (SystemUtils.IS_OS_WINDOWS) {
        //     builder.command("cmd", "/c", cmd);
        // } else {
        //     builder.command("sh", "-c", cmd);
        // }
        // try {
        //     p = builder.start();
        //     try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        //         // result.keywordMatchInfos.addAll(findInConstantPool(baseDir, classFile, br, keyWords, keywordsPatterns));
        //         // result.keywordMatchInfos.addAll(findInLocalVariableTable(baseDir, classFile, br, keyWords, keywordsPatterns));

        //     } catch (IOException e) {
        //         logger.error("{}", e.getMessage());
        //     }
        //     p.waitFor(5, TimeUnit.MINUTES);
        // } catch (Exception e) {
        //     logger.error("{}", e.getMessage());

        // } finally {
        //     if (null != p && p.isAlive()) {
        //         p.destroyForcibly();
        //     }
        // }
        // Set<KeywordMatchInfo> keywordMatchInfoSet = new TreeSet<>(new KeyWordInfoComparator());
        // keywordMatchInfoSet.addAll(result.keywordMatchInfos);
        // result.keywordMatchInfos = Arrays.asList(keywordMatchInfoSet.toArray(new KeywordMatchInfo[keywordMatchInfoSet.size()]));
    }

    private static List<KeywordMatchInfo> findInConstantPool(File baseDir, String file, BufferedReader br, List<String> keyWords, Pattern[] keywordsPatterns) throws IOException {
        String absPath = baseDir.getAbsolutePath();
        int baseDirLen = absPath.endsWith("/") ? absPath.length() : absPath.length() + 1;
        List<KeywordMatchInfo> keywordMatchInfos = new LinkedList<>();
        String line;
        while (null != (line = br.readLine())) {
            if (line.startsWith(CONSTANT_POOL)) break;
        }

        // start of constant pool
        System.out.println(line);
        line = br.readLine();
        int idxEqual = -1;
        int idxType = -1;
        int idxTypeEnd = -1;
        if (!StringUtils.isEmpty(line) && line.trim().startsWith("#")) {
            idxEqual = line.indexOf('=');
            idxType  = idxEqual + 2;
        }
        Matcher m;
        do {
            if (!(line.startsWith(" ") && line.trim().startsWith("#"))) break; // end of constant pool
            idxTypeEnd = line.indexOf(' ', idxType);
            if (idxTypeEnd < 0) {
                idxTypeEnd = line.length();
            }
            String type = line.substring(idxType, idxTypeEnd);
            String value = idxTypeEnd < line.length() ? line.substring(idxTypeEnd) : "";
            value = value.trim();
            if (StringUtils.isEmpty(value)) continue;

            if (null != keyWords) {
                for (String key : keyWords) {
                    if (value.contains(key)) {
                        if (type.equals("String")) {
                            int idxValue = value.indexOf('/') + 3;
                            value = value.substring(idxValue);
                        }
                        // TO DO
                        keywordMatchInfos.add(new KeywordMatchInfo(file.substring(baseDirLen), key, type, value, null, 0, 0));
                    }
                }
            }
            if (null != keywordsPatterns) {
                for (Pattern pattern : keywordsPatterns) {
                    m = pattern.matcher(value);
                    if (m.find()) {
                        keywordMatchInfos.add(new KeywordMatchInfo(file.substring(baseDirLen), pattern.toString(), type, value, null, 0, 0));
                    }
                }
            }
        } while (null != (line = br.readLine()));
        return keywordMatchInfos;
    }

    /*
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
          134      98     8    br   Ljava/io/BufferedReader;
            0     315     0 classFile   Ljava/lang/String;
            0     315     1 keyWords   Ljava/util/List;
           12     303     2 builder   Ljava/lang/ProcessBuilder;
           14     301     3     p   Ljava/lang/Process;
           18     297     4 password   Ljava/lang/String;
           22     293     5 username   Ljava/lang/String;
           31     284     6 keyWordInfoList   Ljava/util/List;
           52     263     7   cmd   Ljava/lang/String;
     */
    private static List<KeywordMatchInfo> findInLocalVariableTable(File baseDir, String file, BufferedReader br, List<String> keyWords, Pattern[] keywordsPatterns) throws IOException {
        List<KeywordMatchInfo> keywordMatchInfoList = new LinkedList<>();
        String absPath = baseDir.getAbsolutePath();
        int baseDirLen = absPath.endsWith("/") ? absPath.length() : absPath.length() + 1;
        String line;
        while (null != (line = br.readLine())) {
            if (line.endsWith(LOCAL_VAR_TABLE)) {
                int idxFirstNonSpace = line.lastIndexOf(' ');
                // ignore the header line
                br.readLine();
                while (null != (line = br.readLine())) {
                    if ((line.length() < idxFirstNonSpace + 2) || line.charAt(idxFirstNonSpace + 1) != ' ') break;
                    String[] fields = line.trim().split("\\s+");
                    String varName = fields[3];
                    for (String key : keyWords) {
                        if (varName.contains(key)) {
                            keywordMatchInfoList.add(new KeywordMatchInfo(file.substring(baseDirLen), key, "Variable", varName, null, 0, 0));
                        }
                    }
                }
            }
        }
        return keywordMatchInfoList;
    }

    private static class KeyWordInfoComparator implements Comparator<KeywordMatchInfo> {
        @Override
        public int compare(KeywordMatchInfo o1, KeywordMatchInfo o2) {
            if (o1.keyword.equals(o2.keyword)) {
                // remove duplicate entries
                if (o1.type.equals(o2.type) && o1.line.equals(o2.line)) {
                    logger.info("duplicates: {}, {}", o1, o2);
                    return 0;
                } else if (o1.line.equals(o2.line)) {
                    return o1.type.compareTo(o2.type);
                } else {
                    return o1.line.compareTo(o2.line);
                }
            } else {
                return o1.keyword.compareTo(o2.keyword);
            }
        }
    }
}

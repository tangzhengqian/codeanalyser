package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.testbird.util.common.RuntimeEnv;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.testbird.util.codeanalyser.Config.getTaskDownloadSrcDir;

public class SrcCodeAnalyser implements KeywordsAnalyser {
    private static final Logger logger = LoggerFactory.getLogger(SrcCodeAnalyser.class);

    private SearchTask searchTask;
    private File baseDir;
    private SearchKeywordsResult result;
    private JsonGenerator jsonGenerator;

    // current line information
    String currentLine;
    int currentLineNumber; // start from 1
    int currentIdx = -1; // index of where currently we are
    // range of current string line that should be searched
    // excluding any comments
    int validStartIdx = -1, validEndIdx = -1;

    int singleQuoteStart = -1, doubleQuoteStart = -1;
    int tripleSingleQuoteStart = -1;
    int tripleDoubleQuoteStart = -1;
    int lineCommentStart = -1;
    // reused for css block comments
    int blockCommentStart = -1;

    int htmlTagStart = -1, htmlCommentStart = -1;
    int htmlStyleAttrStart = -1;
    int htmlStyleTagStart = -1, htmlScriptTagStart = -1;

    // before and after lines of the current line
    private List<String> beforeLines = new LinkedList<>();
    private List<String> afterLines  = new LinkedList<>();
    private String lastLine;
    private int extraLineCnt = 3;
    private String beforeJointLine;
    private String afterJointLine;
    private String jointExtraLine;

    private void checkCanceled() throws SearchCanceledException {
        if (searchTask.canceled) {
            throw new SearchCanceledException();
        }
    }

    private void resetLineState() {
        currentIdx = -1;
        validStartIdx = -1;
        validEndIdx = -1;

        singleQuoteStart = -1;
        doubleQuoteStart = -1;
        tripleSingleQuoteStart = -1;
        tripleDoubleQuoteStart = -1;
        lineCommentStart = -1;
        blockCommentStart = -1;


        htmlTagStart = -1;
        htmlCommentStart = -1;
        htmlStyleAttrStart = -1;
        htmlStyleTagStart = -1;
        htmlScriptTagStart = -1;

        beforeJointLine = null;
        afterJointLine = null;
        jointExtraLine = null;

    }

    @Override
    public void searchKeywords(String filePath, String zipFilePath) throws SearchCanceledException {

        String absPath = baseDir.getAbsolutePath();
        int baseDirLen = absPath.endsWith("/") ? absPath.length() : absPath.length() + 1;
        result.measures.totalSize += new File(filePath).length();

        String realFilePath;
        // it's search files in an unzipped file
        if (null != zipFilePath) {
            realFilePath = Paths.get(zipFilePath, filePath.substring(baseDirLen)).toString();
        } else {
            realFilePath = filePath.substring(baseDirLen);
        }

        try (LineNumberReader br = new LineNumberReader(new FileReader(filePath))) {
            // String truncLine;
            String fileExt = FilenameUtils.getExtension(filePath);
            if (null != fileExt) {
                fileExt = fileExt.toLowerCase();
            }
            String fileType = guessSourceFileType(fileExt);
            boolean commentSupported = isCommentSupported(fileType);

            resetLineState();
            beforeLines.clear();
            afterLines.clear();

            // search line by line
            currentLine = br.readLine();
            if (null != currentLine) {
                prepareExtraLines(br, beforeLines, afterLines);
                currentIdx = 0;
                ++result.measures.totalLineCount;
            }

            while (null != currentLine) {
                checkCanceled();

                boolean readNewLine = true;
                try {
                    currentLineNumber = br.getLineNumber();

                    if (currentLine.trim().isEmpty()) {
                        continue;
                    }

                    if (searchTask.request.ignoreCommentLines && commentSupported) {
                        readNewLine = searchLinesIgnoreComments(realFilePath, fileExt);
                    } else { // don't ignore comments
                        beforeJointLine = StringUtils.join(beforeLines, "\n");
                        afterJointLine = StringUtils.join(afterLines, "\n");
                        jointExtraLine = beforeJointLine + afterJointLine;
                        regMatchKeywords(realFilePath, currentLine, currentLineNumber);
                    }
                } catch (Exception e) {
                    logger.error("Failed to process line, file:{}:{}", realFilePath, currentLineNumber);
                    logger.error("line: {}", currentLine);
                    logger.error("Error: {}, {}", e.getMessage(), e.getStackTrace());
                    resetLineState();
                } finally {
                    if (readNewLine) {
                        lastLine = currentLine;
                        currentLine = br.readLine();
                        if (null != currentLine) {
                            // update before and after lines, used to show in search result
                            prepareExtraLines(br, beforeLines, afterLines);
                            ++result.measures.totalLineCount;
                            currentIdx = 0;
                            validStartIdx = validEndIdx = -1;
                        }
                    }
                }
            } // loop read line
        } catch (IOException e) {
            logger.error("Search keywords in {} failed: {}, {}", realFilePath, e.getMessage(), e.getStackTrace());
        }
    }

    private boolean isCommentSupported(String fileType) {
        boolean b = false;
        switch (fileType) {
            case "java":
            case "js":
            case "c":
            case "c#":
            case "python":
            case "html":
                b = true;
                break;
            default:
                break;
        }
        return b;
    }


    /**
     * Search a line for a valid substring if any, or update the information of
     * where we are(in a string, met line comments, or in block comments)
     * @param filePath
     * @return whether a new line should be read to continue
     *
     */
    private boolean searchCppLineIgnoreComments(String filePath) throws IOException, SearchCanceledException {
        int endPos;
        int len;
        char ch;

        endPos = currentLine.length();
        processLineLoop:
        while (!searchTask.canceled && currentIdx < endPos) {
            ch = currentLine.charAt(currentIdx);
            switch (ch) {
                case '\'':
                    // ignore single quotes in string or comment
                    if (-1 != doubleQuoteStart || -1 != lineCommentStart || -1 != blockCommentStart) {
                        if (-1 != doubleQuoteStart) {
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                        }
                        currentIdx++;
                        continue;
                    }
                    if (-1 == singleQuoteStart) {
                        // 'a'; is valid line
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        singleQuoteStart = currentIdx++;
                    } else {
                        currentIdx++;
                        singleQuoteStart = -1;
                    }
                    break;

                case '\\':
                    if (-1 != lineCommentStart || -1 != blockCommentStart) {
                        currentIdx++;
                        continue;
                    }
                    if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                        // '\\' or "xxx\\yyy"
                        if (currentIdx < endPos - 1) {
                            char chTmp = currentLine.charAt(currentIdx + 1);
                            if ('\\' == chTmp || '"' == chTmp || '\'' == chTmp) {
                                currentIdx += 2;
                                continue;
                            }
                        }
                    }
                    currentIdx++;
                    break;

                case '"':
                    if (-1 != singleQuoteStart || -1 != lineCommentStart || -1 != blockCommentStart) {
                        // ignore double quotes in char or comment
                        currentIdx++;
                        continue;
                    }

                    if (-1 == doubleQuoteStart) {
                        // "a"; is valid line
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        doubleQuoteStart = currentIdx++;
                    } else {
                        currentIdx++;
                        doubleQuoteStart = -1;
                    }
                    break;

                case '/':
                    if (-1 != singleQuoteStart || -1 != doubleQuoteStart || -1 != lineCommentStart || -1 != blockCommentStart) {
                        // ignore slash in char, string or comments
                        currentIdx++;
                        continue;
                    }
                    if (currentIdx < endPos - 1) {
                        ch = currentLine.charAt(currentIdx + 1);
                        // found line comment
                        if ('/' == ch) {
                            lineCommentStart = currentIdx;
                            validEndIdx = currentIdx;
                            currentIdx = endPos;
                            break processLineLoop;
                        } else if ('*' == ch) {
                            // found start of block comment
                            blockCommentStart = currentIdx;
                            currentIdx += 2;
                            // got something need to handle right now
                            if ( -1 != validStartIdx) {
                                validEndIdx = currentIdx - 2;
                                break processLineLoop;
                            } else {
                                // nothing before block comments
                                continue;
                            }
                        } else {
                            // division operator
                            currentIdx++;
                        }
                    } else {
                        // a /
                        // b
                        currentIdx++;
                    }
                    break;

                case '*':
                    if (-1 != singleQuoteStart || -1 != doubleQuoteStart || -1 != lineCommentStart || -1 == blockCommentStart) {
                        // ignore * in char, string, line comments, or multiply operator
                        if (-1 != doubleQuoteStart) {
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                        }
                        currentIdx++;
                        continue;
                    }
                    if (currentIdx < endPos - 1) {
                        if ('/' != currentLine.charAt(currentIdx + 1)) {
                            // ignore * in block comments
                            currentIdx++;
                            continue;
                        } else {
                            // reach end of block comments
                            blockCommentStart = -1;
                            currentIdx += 2;
                            if (currentIdx <= endPos - 1) {
                                validStartIdx = currentIdx;
                            } else {
                                validStartIdx = - 1;
                            }
                            validEndIdx = -1;
                        }
                    } else {
                        currentIdx++;
                    }
                    break;
                default:
                    // ordinary char
                    if (-1 != lineCommentStart || -1 != blockCommentStart) {
                        // in comments
                        currentIdx++;
                        continue;
                    }
                    if (-1 == validStartIdx) {
                        validStartIdx = currentIdx;
                    }
                    currentIdx++;
                    if ( currentIdx == endPos) {
                        validEndIdx = currentIdx;
                    }
                    break;
            } // end of switch
        } // end of processLineLoop

        // check which condition breaks the loop, from the highest priority
        if (-1 != lineCommentStart || -1 != blockCommentStart) {
            regMatchValidLineRange(filePath);
            if (-1 != lineCommentStart) {
                resetLineState();
                return true;
            } else {
                if (currentIdx < endPos) {
                    validStartIdx = validEndIdx = -1;
                    return false;
                } else {
                    return true;
                }
            }
        } else if (-1 != doubleQuoteStart) {
            // multiline string continues
            validEndIdx = endPos;
            regMatchValidLineRange(filePath);
            return true;
        } else {
            if (-1 == validEndIdx) {
                validEndIdx = endPos;
            }
            regMatchValidLineRange(filePath);
            resetLineState();
            return true;
        }
    }

    private void regMatchValidLineRange(String filePath) throws IOException, SearchCanceledException {
        if (-1 != validStartIdx && !currentLine.substring(validStartIdx, validEndIdx).trim().isEmpty()) {
            beforeJointLine = StringUtils.join(beforeLines, "\n");
            afterJointLine = StringUtils.join(afterLines, "\n");
            jointExtraLine = beforeJointLine + afterJointLine;
            regMatchKeywords(filePath, currentLine, currentLineNumber, validStartIdx, validEndIdx);
        }

    }

    /**
     * Search a line for a valid substring if any, or update the information of
     * where we are(in a string, met line comments, or in block comments)
     * @param filePath
     * @return whether a new line should be read to continue
     *
     */
    private boolean searchPythonLineIgnoreComments(String filePath) throws IOException, SearchCanceledException {
        int endPos;
        char ch;

        endPos = currentLine.length();
        processLineLoop:
        while (!searchTask.canceled && currentIdx < endPos) {
            ch = currentLine.charAt(currentIdx);
            if (-1 == validStartIdx) {
                validStartIdx = currentIdx;
            }
            switch (ch) {
                case '#':
                    if (-1 != singleQuoteStart || -1 != doubleQuoteStart
                            || -1 != tripleSingleQuoteStart || -1 != tripleDoubleQuoteStart) {
                        currentIdx++;
                        continue;
                    }
                    lineCommentStart = currentIdx;
                    validEndIdx = currentIdx;
                    currentIdx = endPos;
                    break processLineLoop;

                case '\'':
                    // triple single quotes
                    if (currentIdx + 2 < endPos && '\'' == currentLine.charAt(currentIdx + 1) && '\'' == currentLine.charAt(currentIdx + 2)) {
                        if ( -1 != tripleDoubleQuoteStart) {
                            // ignore triple single quotes in triple double quotes
                            currentIdx += 3;
                        }
                        else if (-1 != tripleSingleQuoteStart) {
                            currentIdx += 3;
                            tripleSingleQuoteStart= -1;
                        } else {
                            tripleSingleQuoteStart = currentIdx;
                            currentIdx += 3;
                        }
                    } else {
                        if (-1 != doubleQuoteStart ||
                            -1 != tripleSingleQuoteStart || -1 != tripleDoubleQuoteStart) {
                            // ignore single quote in double quote string or triple quoted string
                            currentIdx++;
                            continue;
                        } else if (-1 == singleQuoteStart) {
                            singleQuoteStart = currentIdx++;
                        } else {
                            singleQuoteStart = -1;
                            currentIdx++;
                        }
                    }
                    break;

                case '\\':
                    if (-1 != lineCommentStart) {
                        currentIdx++;
                        continue;
                    }
                    // if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                        // '\\' or "xxx\\yyy"
                        if (currentIdx < endPos - 1) {
                            char chTmp = currentLine.charAt(currentIdx + 1);
                            if ('\\' == chTmp || '"' == chTmp || '\'' == chTmp) {
                                currentIdx += 2;
                                continue;
                            }
                        }
                    // }
                    currentIdx++;
                    break;

                // TODO:
                // this is valid too for python
                // a = '\'SINGLE Quoted multiline strings: \n \
                // # OK abc multi lines\''
                //print a
                //a = "\"DOUBLE Quoted multiline strings: \n \
                // # OK abc multi lines\""
                //print a
                case '"':
                    // triple double quotes
                    if (currentIdx + 2 < endPos && '"' == currentLine.charAt(currentIdx + 1) && '"' == currentLine.charAt(currentIdx + 2)) {
                        if ( -1 != tripleSingleQuoteStart) {
                            // ignore triple double quotes in triple single quotes
                            currentIdx += 3;
                        }
                        else if (-1 != tripleDoubleQuoteStart) {
                            currentIdx += 3;
                            tripleDoubleQuoteStart = -1;
                        } else {
                            tripleDoubleQuoteStart = currentIdx;
                            currentIdx += 3;
                        }
                    } else {
                        if (-1 != singleQuoteStart ||
                                -1 != tripleSingleQuoteStart || -1 != tripleDoubleQuoteStart) {
                            // ignore double quote in single quote string or triple quoted string
                            currentIdx++;
                        } else if (-1 == doubleQuoteStart) {
                            doubleQuoteStart = currentIdx++;
                        } else {
                            doubleQuoteStart = -1;
                            currentIdx++;
                        }
                    }
                    break;

                default:
                    currentIdx++;
                    break;
            } // end of switch
        } // end of processLineLoop
        // check which condition breaks the loop, from the highest priority
        if (-1 == validEndIdx) {
            validEndIdx = endPos;
        }
        regMatchValidLineRange(filePath);
        if (-1 != lineCommentStart) {
            resetLineState();
        }
        return true;
    }

    /**
     * Search a line for a valid substring if any, or update the information of
     * where we are(in a string, met line comments, or in block comments)
     * @param filePath
     * @return whether a new line should be read to continue
     *
     */
    private boolean searchHtmlLineIgnoreComments(String filePath) throws IOException, SearchCanceledException {
        int endPos;
        char ch;

        endPos = currentLine.length();
        processLineLoop:
        while (!searchTask.canceled && currentIdx < endPos) {
            ch = currentLine.charAt(currentIdx);
            switch (ch) {
                case '<':
                    if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                        currentIdx++;
                    } else if (currentIdx + 4 <= endPos && "<!--".equals(currentLine.substring(currentIdx, currentIdx + 4))) {
                        // <!--
                        if (-1 != htmlStyleTagStart || -1 != htmlScriptTagStart) {
                            currentIdx += 4;
                        } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx += 4;
                        } else {
                            htmlCommentStart = currentIdx;
                            currentIdx += 4;
                            if (-1 != validStartIdx) {
                                validEndIdx = currentIdx - 4;
                                break processLineLoop;
                            }
                        }
                    } else if (currentIdx + 7 <= endPos && "<style>".equalsIgnoreCase(currentLine.substring(currentIdx, currentIdx + 7))) {
                        if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                            currentIdx += 7;
                        } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                            // <input value = "color: red; /* ' <!- abc red -> <style> red </style>- */
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx += 7;
                        } else {
                            // <style> ... </style>
                            htmlStyleTagStart = currentIdx;
                            currentIdx += 7;
                        }
                    } else if (currentIdx + 8 <= endPos && "</style>".equalsIgnoreCase(currentLine.substring(currentIdx, currentIdx + 8))) {
                        if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                            currentIdx += 8;
                        } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx += 8;
                        } else {
                            htmlStyleTagStart = -1;
                            currentIdx += 8;
                        }
                    } else if (currentIdx + 8 <= endPos && "<script>".equalsIgnoreCase(currentLine.substring(currentIdx, currentIdx + 8))) {
                        // <script> ... </script>
                        if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                            currentIdx += 8;

                        } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx += 8;
                        } else {
                            htmlScriptTagStart = currentIdx;
                            currentIdx += 8;
                        }
                    } else if (currentIdx + 9 <= endPos && "</script>".equalsIgnoreCase(currentLine.substring(currentIdx, currentIdx + 9))) {
                        if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                            currentIdx += 9;
                        } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx += 9;
                        } else {
                            htmlScriptTagStart = -1;
                            currentIdx += 9;
                        }
                    } else if (currentIdx + 2 <= endPos && "<!".equals(currentLine.substring(currentIdx, currentIdx + 2))) {
                        // <body style = "color: red; /* ' <!- ...
                        if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                            currentIdx += 2;
                        } else if ( -1 != singleQuoteStart || -1 != doubleQuoteStart) {
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx += 2;
                        } else {
                            // <!DOCTYPE html>
                            htmlTagStart = currentIdx;
                            currentIdx += 2;
                        }
                    } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        currentIdx++;
                    } else if (-1 == htmlTagStart) {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        if (currentIdx + 1 < endPos) {
                            if (StringUtils.isAlphanumeric(currentLine.substring(currentIdx + 1, currentIdx + 2))) {
                                htmlTagStart = currentIdx++;
                            } else {
                                currentIdx++;
                            }
                        } else {
                            currentIdx++;
                        }
                    } else {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        currentIdx++;
                    }
                    break;
                case '-':
                    // -->
                    if (currentIdx + 3 <= endPos && "-->".equals(currentLine.substring(currentIdx, currentIdx + 3))) {
                        if (-1 != htmlCommentStart) {
                            htmlCommentStart = -1;
                            currentIdx += 3;
                        } else {
                            if (-1 == blockCommentStart) {
                                if (-1 == validStartIdx) {
                                    validStartIdx = currentIdx;
                                }
                            }
                            currentIdx += 3;
                        }
                    } else {
                        if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                            currentIdx++;
                        } else {
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx++;
                        }
                    }
                    break;
                case '>':
                    if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                        currentIdx++;
                    } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        currentIdx++;
                    } else if (-1 != htmlTagStart) {
                        htmlTagStart = -1;
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        currentIdx++;
                    } else {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        currentIdx++;
                    }
                    break;

                case '\'':
                    // ignore single quotes in attribute value, css comment or html comments
                    if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                        currentIdx++;
                    } else if (-1 != doubleQuoteStart) {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        currentIdx++;
                    } else {
                        if (-1 != htmlTagStart) {
                            if (-1 == singleQuoteStart) {
                                if (-1 == validStartIdx) {
                                    validStartIdx = currentIdx;
                                }
                                singleQuoteStart = currentIdx++;
                            } else {
                                currentIdx++;
                                singleQuoteStart = -1;
                                htmlStyleAttrStart = -1;
                            }
                        } else {
                            currentIdx++;
                        }
                    }
                    break;

                case '"':
                    if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                        currentIdx++;
                    } else if (-1 != singleQuoteStart) {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        currentIdx++;
                    } else {
                        if (-1 != htmlTagStart) {
                            if (-1 == doubleQuoteStart) {
                                if (-1 == validStartIdx) {
                                    validStartIdx = currentIdx;
                                }
                                doubleQuoteStart = currentIdx++;
                            } else {
                                currentIdx++;
                                doubleQuoteStart = -1;
                                htmlStyleAttrStart = -1;
                            }
                        } else {
                            currentIdx++;
                        }
                    }
                    break;

                    /* /* */
                case '/':
                    // already in comments
                    if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                        currentIdx++;
                    } else if (-1 != htmlStyleTagStart || -1 != htmlScriptTagStart) {
                        // inline css and js support c-like block comments
                        if (currentIdx < endPos - 1) {
                            ch = currentLine.charAt(currentIdx + 1);
                            if ('/' == ch) {
                                lineCommentStart = currentIdx;
                                validEndIdx = currentIdx;
                                currentIdx = endPos;
                                break processLineLoop;
                            } else if ('*' == ch) {
                                // block comments in style attribute value
                                currentIdx += 2;
                                // block comment in style attribute
                                blockCommentStart = currentIdx - 2;
                                // got something need to handle right now
                                if (-1 != validStartIdx) {
                                    validEndIdx = currentIdx - 2;
                                    break processLineLoop;
                                } else {
                                    // nothing before block comments
                                }
                            } else {
                                if (-1 == validStartIdx) {
                                    validStartIdx = currentIdx;
                                }
                                currentIdx++;
                            }
                        } else {
                            // e.g. in text="/**/"
                            // attribute value may span multilines
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx++;
                        }
                    } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
                        if (-1 != htmlStyleAttrStart && currentIdx < endPos - 1) {
                            ch = currentLine.charAt(currentIdx + 1);
                            if ('*' == ch) {
                                // block comments in style attribute value
                                currentIdx += 2;
                                // block comment in style attribute
                                blockCommentStart = currentIdx - 2;
                                // got something need to handle right now
                                if (-1 != validStartIdx) {
                                    validEndIdx = currentIdx - 2;
                                    break processLineLoop;
                                } else {
                                    // nothing before block comments
                                }
                            } else {
                                if (-1 == validStartIdx) {
                                    validStartIdx = currentIdx;
                                }
                                currentIdx++;
                            }
                        } else {
                            // e.g. in text="/**/"
                            // attribute value may span multilines
                            if (-1 == validStartIdx) {
                                validStartIdx = currentIdx;
                            }
                            currentIdx++;
                        }
                    } else {
                        if (currentIdx < endPos - 1) {
                            ch = currentLine.charAt(currentIdx + 1);
                            if ('*' == ch) {
                                currentIdx += 2;
                                if (-1 != htmlTagStart) {
                                    // block comment in element style
                                    // found start of block comment
                                    blockCommentStart = currentIdx - 2;
                                    // got something need to handle right now
                                    if (-1 != validStartIdx) {
                                        validEndIdx = currentIdx - 2;
                                        break processLineLoop;
                                    } else {
                                        // nothing before block comments
                                    }
                                }
                            } else {
                                currentIdx++;
                            }
                        } else {
                            currentIdx++;
                        }
                    }
                    break;

                case '*':
                    if (-1 != htmlCommentStart) {
                        currentIdx++;
                    } else if (-1 != blockCommentStart) {
                        if (currentIdx < endPos - 1) {
                            // end of block comments
                            if ('/' != currentLine.charAt(currentIdx + 1)) {
                                currentIdx++;
                            } else {
                                // reach end of block comments
                                blockCommentStart = -1;
                                currentIdx += 2;
                                if (currentIdx <= endPos - 1) {
                                    validStartIdx = currentIdx;
                                } else {
                                    validStartIdx = -1;
                                }
                                validEndIdx = -1;
                            }
                        } else {
                            // /*
                            currentIdx++;
                        }
                    } else {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        currentIdx++;
                    }
                    break;
                default:
                    if (-1 != htmlCommentStart || -1 != blockCommentStart) {
                        // in comments
                        currentIdx++;
                    } else {
                        if (-1 == validStartIdx) {
                            validStartIdx = currentIdx;
                        }
                        if (currentIdx + 5 <= endPos && "style".equalsIgnoreCase(currentLine.substring(currentIdx, currentIdx + 5))) {
                            // <body style=""> ... </body>
                            if (-1 != htmlTagStart) {
                                htmlStyleAttrStart = currentIdx;
                            }
                            currentIdx += 5;
                        } else {
                            currentIdx++;
                            if (currentIdx == endPos) {
                                validEndIdx = currentIdx;
                            }
                        }
                    }
                    break;
            } // end of switch
        } // end of processLineLoop
        // check which condition breaks the loop, from the highest priority
        if (-1 != lineCommentStart) {
            // inline js support line comment
            lineCommentStart = -1;
            regMatchValidLineRange(filePath);
            return true;
        } else if (-1 != htmlCommentStart || -1 != blockCommentStart) {
            regMatchValidLineRange(filePath);
            if (currentIdx < endPos) {
                validStartIdx = validEndIdx = -1;
                return false;
            } else {
                return true;
            }
        } else if (-1 != singleQuoteStart || -1 != doubleQuoteStart) {
            // multiline string continues
            validEndIdx = endPos;
            regMatchValidLineRange(filePath);
            return true;
        } else {
            if (-1 == validEndIdx) {
                validEndIdx = endPos;
            }
            regMatchValidLineRange(filePath);
            return true;
        }
    }

    // process any comments start from the current line
    private boolean searchLinesIgnoreComments(String realFilePath, String fileExt) throws IOException, SearchCanceledException {
        checkCanceled();
        boolean readNewLine = true;
        String fileType = guessSourceFileType(fileExt);
        switch (fileType) {
            case "java":
            case "js":
            case "c":
            case "c#":
                readNewLine = searchCppLineIgnoreComments(realFilePath);
                break;
            case "python":
                readNewLine = searchPythonLineIgnoreComments(realFilePath);
                break;
            case "html":
                readNewLine = searchHtmlLineIgnoreComments(realFilePath);
                break;
            default:
                break;
        }
        return readNewLine;
    }

    private void regMatchKeywords(String realFilePath, String line, int lineNumber) throws IOException, SearchCanceledException {
        regMatchKeywords(realFilePath, line, lineNumber, 0, line.length());

    }
    private void regMatchKeywords(String realFilePath, String line, int lineNumber, int startPos, int endPos) throws IOException, SearchCanceledException {
        if(line.substring(startPos, endPos).trim().isEmpty()) {
            return;
        }
        int maxLineLen = Config.getMaxMatchStringLen();
        int extraLinesLen;
        if (line.length() >= maxLineLen) { // limit line length
            extraLinesLen = 0;
        } else {
            extraLinesLen = maxLineLen - line.length();
        }

        SearchKeywordsRequest request = searchTask.request;
        List<KeywordInfo> keywordInfos = request.keywordInfos;
        for (KeywordInfo keywordInfo : keywordInfos) {
            if (null == keywordInfo.keywordPatterns) {
                continue;
            }
            for (Pattern pattern : keywordInfo.keywordPatterns) {
                checkCanceled();
                Matcher m = pattern.matcher(line);
                for (boolean found = m.find(startPos); !searchTask.canceled && found; found = m.find()) {
                    boolean ignore = false;
                    String matched = m.group();
                    int idxStart1 = m.start();
                    int idxEnd1   = m.end();
                    if (idxEnd1 > endPos) {
                        logger.debug("Keyword {} found, file {}:{}", pattern.toString(), realFilePath, lineNumber);
                        logger.debug("[{}]", line);
                        logger.debug("Ignore matched idx in comments: [{}, {}], [{}, {}]", idxStart1, idxEnd1, startPos, endPos);
                        break;
                    }

                    ignoreKeywordsLoop:
                    for (Pattern pattern2 : request.ignoreKeywordPatterns) {
                        checkCanceled();
                        Matcher m2 = pattern2.matcher(line);
                        for (boolean found2 = m2.find(startPos); !searchTask.canceled && found2; found2 = m2.find()) {
                            String matched2 = m2.group();
                            int idxStart2 = m2.start();
                            int idxEnd2   = m2.end();
                            logger.debug("Ignore keyword found, file {}:{}", realFilePath, lineNumber);
                            logger.debug("[{}]", line);
                            logger.debug("\t(keyword: [{}], matched: [{}]), (ignore keyword: [{}], matched: [{}])",
                                    pattern.toString(), matched, pattern2.toString(), matched2);
                            logger.debug("\tmatched idx: [{}, {}], ignore matched idx: [{}, {}]", idxStart1, idxEnd1, idxStart2, idxEnd2);
                            // end index is the offset after the last character matched.
                            // if (idxEnd2 <= idxStart1 || idxStart2 >= idxEnd1) {
                            if (idxStart2 >= idxStart1 && idxEnd2 <= idxEnd1) {
                                logger.info("Line ignored: [{}]", line);
                                ignore = true;
                                break ignoreKeywordsLoop;
                            }
                        }
                    }
                    if (ignore) {
                        logger.info("Match ignored, continue: {}", matched);
                        continue;
                    }

                    // avoid super long lines to show in search result
                    String realLine = line.substring(0);
                    if (line.length() > maxLineLen) { // limit line length
                        int matchLen = idxEnd1 - idxStart1;
                        int halfTruncLen;
                        int idxTruncStart;
                        int idxTruncEnd;
                        // if matched string len is shorter then max len,
                        // pad to max len
                        if (maxLineLen > matchLen) {
                            halfTruncLen = (maxLineLen - matchLen) / 2;
                            idxTruncEnd = idxEnd1 + halfTruncLen;
                            if (idxTruncEnd > line.length()) {
                                idxTruncEnd = line.length();
                            }
                            idxTruncStart = idxTruncEnd - maxLineLen;
                            if (idxTruncStart < 0) {
                                idxTruncStart = 0;
                            }
                            idxStart1 -= idxTruncStart;
                            idxEnd1 -= idxTruncStart;
                        } else {
                            // if matched string len equals or exceeds max len,
                            // use the matched string as the line
                            idxTruncStart = idxStart1;
                            idxTruncEnd = idxEnd1;
                            idxStart1 = 0;
                            idxEnd1 = matchLen;
                        }
                        realLine = line.substring(idxTruncStart, idxTruncEnd);
                        // Matcher tmpM = pattern.matcher(realLine);
                        // if (tmpM.find()) {
                        //     idxStart1 = tmpM.start();
                        //     idxEnd1   = tmpM.end();
                        // }
                    }
                    // logger.info("before lines: \n{}", beforeJointLine);
                    // logger.info("after lines: \n{}", afterJointLine);
                    // logger.info("Extra lines len: {}", extraLinesLen);
                    // logger.info("Extra joint lines : \n{}", jointExtraLine);
                    if (extraLineCnt > 0 && extraLinesLen > 0) {
                        int truncExtraLenBefore = extraLinesLen / 2;
                        int idxTruncStart = beforeJointLine.length() - truncExtraLenBefore;
                        if (idxTruncStart < 0) {
                            idxTruncStart = 0;
                        }
                        // logger.info("idx trunc start: {}", idxTruncStart);
                        int remainLen = extraLinesLen - (beforeJointLine.length() - idxTruncStart);
                        if (remainLen > afterJointLine.length()) {
                            remainLen = afterJointLine.length();
                        }
                        int idxBeforeLineTruncEnd = beforeJointLine.length();
                        // logger.info("remain len: {}", remainLen);
                        beforeJointLine = jointExtraLine.substring(idxTruncStart, idxBeforeLineTruncEnd);
                        afterJointLine  = jointExtraLine.substring(idxBeforeLineTruncEnd, idxBeforeLineTruncEnd + remainLen);
                    } else {
                        beforeJointLine = "";
                        afterJointLine  = "";
                    }
                    KeywordMatchInfo keywordMatchInfo = new KeywordMatchInfo(realFilePath, pattern.toString(), "", realLine, lineNumber, idxStart1, idxEnd1);
                    keywordMatchInfo.keywordLevel = keywordInfo.level;
                    keywordMatchInfo.beforeLines.add(beforeJointLine);
                    keywordMatchInfo.afterLines.add(afterJointLine);
                    // result.keywordMatchInfos.add(keywordMatchInfo);
                    jsonGenerator.writeObject(keywordMatchInfo);
                    keywordMatchInfo = null;
                    ++result.resultCount;
                }
            }
        }
    }

    private static String guessSourceFileType(String fileExt) {
        KeywordsAnalysisConfig config = Config.getKeywordsAnalysisConfig();
        fileExt = fileExt.toLowerCase();
        if (null != config.jsExts && config.javaExts.contains(fileExt)) {
            return "java";
        }
        if (null != config.jsExts && config.jsExts.contains(fileExt)) {
            return "js";
        }
        if (null != config.cAndCppExts && config.cAndCppExts.contains(fileExt)) {
            return "c";
        }
        if (null != config.pyExts && config.pyExts.contains(fileExt)) {
            return "python";
        }
        if (null != config.htmlExts && config.htmlExts.contains(fileExt)) {
            return "html";
        }
        if (null != config.cSharpExts && config.cSharpExts.contains(fileExt)) {
            return "c#";
        }

        return "default";
    }


    private static boolean filterCommentLines(String line) {
        List<String> commentLinePatters = Config.getCommentLinePatterns();
        if (null != commentLinePatters) {
            line = StringUtils.trim(line);
            for (String pattern : commentLinePatters) {
                if (line.startsWith(pattern)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void prepareExtraLines(LineNumberReader br, List<String> beforeLines, List<String> afterLines) throws IOException {
        // String truncLine;
        int lineNumber = br.getLineNumber();

        // update before and after lines, used to show in search result
        if (extraLineCnt > 0) {
            br.mark(10 * 1024 * 1024); // 10m
            // before lines
            if (null != lastLine) {
                // if (lastLine.length() <= maxLineLen) {
                //     truncLine = lastLine;
                // } else {
                //     truncLine = lastLine.substring(0, maxLineLen);
                // }
                if (beforeLines.size() == extraLineCnt) {
                    beforeLines.remove(0);
                    beforeLines.add(lastLine);
                } else if (lineNumber > 1) {
                    beforeLines.add(lastLine);
                }
            }

            // after lines
            afterLines.clear();
            int cnt = 0;
            String tmpLine;
            while (cnt++ < extraLineCnt && null != (tmpLine = br.readLine())) {
                // if (tmpLine.length() <= maxLineLen) {
                //     truncLine = tmpLine;
                // } else {
                //     truncLine = tmpLine.substring(0, maxLineLen);
                // }
                afterLines.add(tmpLine);
            }
            br.reset();

            // beforeJointLine = StringUtils.join(beforeLines, "\n");
            // afterJointLine = StringUtils.join(afterLines, "\n");
            // jointExtraLine = beforeJointLine + afterJointLine;
        }
    }

    static class DownloadException extends IOException {
        DownloadException(String message) {
            super(message);
        }
    }

    // public static SearchKeywordsResult searchKeywordsInGitRepo(String srcRepoUrl, String userName, String password,
    //                                                            List<KeywordInfo> keywordInfos, ResultAttr resultAttr) throws IOException {
    //     String localDir = genTmpDir();
    //     long start = System.currentTimeMillis();
    //     SrcDownloadResult srcDownloadResult = SrcDownloader.downloadGitRepo(srcRepoUrl, userName, password, localDir);
    //     long end = System.currentTimeMillis();
    //     logger.info("Download time: {}s", (end - start) / 1000);

    //     SearchKeywordsResult result;
    //     try {
    //         if (srcDownloadResult.success) {
    //             result = doSearch(localDir, keywordInfos, null, resultAttr);
    //         } else {
    //             throw new DownloadException(srcDownloadResult.errMsg);
    //         }
    //     } finally {
    //         FileUtils.deleteQuietly(new File(localDir));
    //     }
    //     if (null != result) {
    //         result.measures.downloadTime = end - start;
    //         result.repoVersion = srcDownloadResult.version;
    //     }
    //     return result;
    // }

    public SearchKeywordsResult searchKeywordsInSvnRepo(SearchTask searchTask) throws IOException, SearchCanceledException {
        this.searchTask = searchTask;

        String localDir = getTaskDownloadSrcDir(searchTask.request.key);
        baseDir = new File(localDir);
        long start = System.currentTimeMillis();
        SearchKeywordsRequest request = searchTask.request;
        SrcDownloadResult srcDownloadResult = SrcDownloader.downloadSvnRepo(searchTask.request.repoInfo.repoAdrr, request.repoInfo.repoVersion,
                request.repoInfo.userName, request.repoInfo.password, localDir, searchTask);
        checkCanceled();
        long end = System.currentTimeMillis();
        logger.info("Download time: {}s", (end - start) / 1000);

        try {
            if (!searchTask.canceled && srcDownloadResult.success) {
                doSearch();
            } else {
                throw new DownloadException(srcDownloadResult.errMsg);
            }
        } finally {
            // FileUtils.deleteQuietly(new File(localDir));
        }
        if (null != result) {
            result.measures.downloadTime = end - start;
            if (!searchTask.canceled) {
                result.versionLogs = getSvnVersionLogs(localDir, request.repoInfo.lastRepoVersion, srcDownloadResult.version, request.repoInfo.userName, request.repoInfo.password);
                result.lastChangeTime = getSvnLastChangeTimestamp(localDir);
            }
            if (result.versionLogs != null && result.versionLogs.length() > 0) {
                final Pattern pattern = Pattern.compile("-+\\s*r(\\d+)\\s+\\|[\\S\\s]*");
                final Matcher matcher = pattern.matcher(result.versionLogs);
                if (matcher.matches()) {
                    result.repoVersion = matcher.group(1);
                }
            }
            if (StringUtils.isEmpty(result.repoVersion)) {
                result.repoVersion = srcDownloadResult.version;
            }
            logger.info("result repoVersion:", result.repoVersion);
            result.lastRepoVersion = request.repoInfo.lastRepoVersion;
        }
        return result;
    }

    /*
    svn log -r 3:0 -v
    With -v, also print all affected paths with each log message.
         Each changed path is preceded with a symbol describing the change:
           A: The path was added or copied.
           D: The path was deleted.
           R: The path was replaced (deleted and re-added in the same revision).
           M: The path's file and/or property content was modified.

    ------------------------------------------------------------------------
    r18 | tengjianping | 2018-03-28 18:20:53 +0800 (三, 28  3 2018) | 2 lines
    Changed paths:
       A /trunk/.gitmodules

    submodule test

    ------------------------------------------------------------------------
    r17 | tengjianping | 2018-02-12 16:41:23 +0800 (一, 12  2 2018) | 2 lines
    Changed paths:
       M /trunk/12306.py
       M /trunk/train_urls.py

    some upate
    */
    private static final int DEFAULT_VERSION_HISTORY_CNT = 50;
    private static String getSvnVersionLogs(String svnWorkDir, String startVersion, String endVersion, String userName, String password) {
        if (StringUtils.isEmpty(startVersion)) {
            int tmpVersion = 1;
            try {
                tmpVersion = Integer.valueOf(endVersion) - DEFAULT_VERSION_HISTORY_CNT;
            } catch (Exception e) {
                // ignore
            }
            if (tmpVersion < 1) {
                tmpVersion = 1;
            }
            startVersion = String.valueOf(tmpVersion);
        } else {
            try {
                int intEndVersion = Integer.parseInt(endVersion);
                int intStartVersion = Integer.parseInt(startVersion);
                if (intStartVersion < 1) {
                    intStartVersion = 1;
                }
                if (intStartVersion >= intEndVersion) {
                    return "";
                }
                startVersion = String.valueOf(intStartVersion + 1);
            } catch (Exception e) {
                // ignore
            }
        }
        String cmd = "svn log -r " + endVersion + ":" + startVersion;
        if (!StringUtils.isEmpty(userName) && !StringUtils.isEmpty(password)) {
            cmd += " --username " + userName;
            cmd += " --password " + password;
        }
        if (Config.isIgnoreSvnSSLCert()) {
            cmd += SrcDownloader.SVN_OPTIONS_IGNORE_SSL_CERT;
        }
        List<String> results = RuntimeEnv.runShell(cmd, svnWorkDir, -1);
        if (null == results || results.size() < 1) {
            return "";
        }
        for (String l : results) {
            logger.info("getSvnVersionLogs: {}", l);
        }
        return results.get(0);
    }

    /*
    svn info
    Path: .
    Working Copy Root Path: /Users/tengjp/git/my12306-test/my12306.git/test
    URL: https://github.com/tengjp/my12306.git/trunk
    Relative URL: ^/trunk
    Repository Root: https://github.com/tengjp/my12306.git
    Repository UUID: d9cd3027-8fbb-68b9-4f8b-ce57b7db7bff
    Revision: 18
    Node Kind: directory
    Schedule: normal
    Last Changed Author: tengjianping
    Last Changed Rev: 18
    Last Changed Date: 2018-03-28 18:20:53 +0800 (三, 28  3 2018)

    svn info --show-item last-changed-date
    2018-03-28T10:20:53.000000Z
     */
    private static long getSvnLastChangeTimestamp(String svnWordDir) {
        List<String> results = RuntimeEnv.runShell("svn info --show-item last-changed-date", svnWordDir, -1);
        if (null == results || results.size() < 1) {
            return 0;
        }
        String strTs = results.get(0).trim();
        if (strTs.charAt(strTs.length() - 1) == '\n') {
            strTs = strTs.substring(0, strTs.length() - 1);
        }
        logger.info("Last svn change date: {}", strTs);
        long timestamp = 0;
        try {
            timestamp = Instant.parse(strTs).toEpochMilli();
        } catch (Exception e) {
            logger.error("Failed to parse datetime: {}, {}", strTs, e.getMessage());
        }
        return timestamp;
    }

    private void doSearch() throws IOException, SearchCanceledException {
        long start = System.currentTimeMillis();
        searchKeywordsInDir();
        // result.resultCount = result.keywordMatchInfos.size();
        result.measures.searchTime = System.currentTimeMillis() - start;
        logger.info("Search time: {}ms", result.measures.searchTime);
    }

    public SearchKeywordsResult searchKeywordsInDir(String dir, SearchTask searchTask) throws IOException, SearchCanceledException {
        this.baseDir = new File(dir);
        this.searchTask = searchTask;
        return searchKeywordsInDir();
    }

    private SearchKeywordsResult searchKeywordsInDir() throws IOException, SearchCanceledException {
        SearchKeywordsRequest request = searchTask.request;
        ResultAttr resultAttr = request.resultAttr;
        if (null != resultAttr && resultAttr.extraLineCnt > 0) {
            extraLineCnt = resultAttr.extraLineCnt;
        }

        result = new SearchKeywordsResult();

        File resultFile = new File(Config.getTaskTmpResultFile(request.key));
        logger.info("result file: {}", resultFile.getAbsolutePath());
        jsonGenerator = jsonFactory.createGenerator(resultFile, JsonEncoding.UTF8);
        jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());
        jsonGenerator.writeStartObject();
        jsonGenerator.writeArrayFieldStart("result");

        try {
            searchKeywordsInFile(baseDir, null);
        } finally {
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject();
            jsonGenerator.close();
        }
        return result;
    }

    private static boolean filterBuildInIgnoreFile(File file) {
        String fileName = file.getName();
        if (file.isDirectory()) {
            if (isIgnoreDir(fileName)) {
                logger.info("Ignore dir {}", file.getAbsolutePath());
                return false;
            } else {
                return true;
            }
        } else {
            String ext = FilenameUtils.getExtension(fileName).toLowerCase();
            if (Config.getIgnoreFileTypes().contains(ext)) {
                return false;
            }
            // git pack file
            String packName = "pack-0d7e7d89bf0fcfaa04ab194d4edc90f12ca3e975.pack";
            int len = packName.length();
            if (fileName.length() == len && fileName.startsWith("pack") && fileName.endsWith(".pack")) {
                return false;
            }
            List<Pattern> ignoreFiles = Config.getIgnoreFilePatterns();
            if (null != ignoreFiles) {
                for (Pattern p : ignoreFiles) {
                    Matcher matcher = p.matcher(fileName);
                    if (matcher.find()) {
                        logger.info("Ignore file {}", file.getAbsolutePath());
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static boolean isIgnoreDir(String dirName) {
        if (StringUtils.equalsAnyIgnoreCase(dirName, ".svn", ".git")) {
            return true;
        }
        return false;
    }

    private boolean filterCustomIgnoreFiles(File file, List<String> ignoreFiles) {
        if (null != ignoreFiles) {
            String baseDirPath = baseDir.getAbsolutePath();
            String filePath    = file.getAbsolutePath();
            // the same dir
            if (baseDirPath.equals(filePath)) {
                return true;
            }
            if (!baseDirPath.endsWith("/")) {
                baseDirPath += "/";
            }
            filePath = filePath.substring(baseDirPath.length());
            for (String path : ignoreFiles) {
                if (StringUtils.equalsIgnoreCase(path, filePath)) {
                    logger.info("Ignore file {}", filePath);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean filterCustomIncludeFiles(File file, List<String> includeFileTypes) {
        if (null != includeFileTypes) {
            String ext = FilenameUtils.getExtension(file.getName());
            if (includeFileTypes.contains(ext.toLowerCase())) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    // private static boolean filterCustomIgnoreFileTypes(File file, List<String> ignoreFileTypes) {
    //     if (null != ignoreFileTypes) {
    //         String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
    //         if (ignoreFileTypes.contains(ext)) {
    //             return false;
    //         }
    //     }
    //     return true;
    // }

    private void searchKeywordsInFile(File file, String zipFilePath) throws SearchCanceledException {
        if (searchTask.canceled) {
            return;
        }
        SearchKeywordsRequest request = searchTask.request;
        if (file.isDirectory()) {
            checkCanceled();
            if (filterCustomIgnoreFiles(file, request.ignoreFiles)) {
                Arrays.stream(file.listFiles())
                        // .filter(SrcCodeAnalyser::filterBuildInIgnoreFile)
                        .forEach(f -> {
                            if (!searchTask.canceled) {
                                try {
                                    searchKeywordsInFile(f, zipFilePath);
                                } catch (Exception e) {
                                    logger.warn("Failed to handle file {}: {}, {}", f.getAbsolutePath(), e.getMessage(), e.getStackTrace());
                                }
                            }
                        });
            }
            checkCanceled();
        } else if (file.isFile()){
            if (!filterCustomIgnoreFiles(file, request.ignoreFiles) ||
                !filterBuildInIgnoreFile(file) ||
                !filterCustomIncludeFiles(file, request.includeFileTypes)) {
                return;
            }
            String filePath = file.getAbsolutePath();
            // treat it as text file whatever
            checkCanceled();
            searchKeywords(filePath, null);
                // String tmpDir = Config.genTmpDir();
                // File newBaseDir = new File(tmpDir);
                // // List<File> files = ZipUtils.unZipFileTo(new File(filePath), newBaseDir);
                // // files.stream().filter(SrcCodeAnalyser::filterBuildInIgnoreFile).forEach(f -> searchKeywordsInFile(newBaseDir, f, searchTask, result, jsonGenerator, newZipFilePath));
                // String baseAbsPath = baseDir.getAbsolutePath();
                // int baseDirLen = baseAbsPath.endsWith("/") ? baseAbsPath.length() : baseAbsPath.length() + 1;
                // String newZipFilePath;
                // // zip in zip
                // if (null != zipFilePath) {
                //     newZipFilePath = Paths.get(zipFilePath, filePath.substring(baseDirLen)).toString();
                // } else {
                //     newZipFilePath = filePath.substring(baseDirLen);
                // }

                // try {
                //     if (FileUtil.isCompressedFile(fileType)) {
                //         if (decompressFile(file, fileType, tmpDir)) {
                //             Arrays.stream(newBaseDir.listFiles())
                //                     .filter(SrcCodeAnalyser::filterBuildInIgnoreFile)
                //                     .forEach(f -> {
                //                         if (!searchTask.canceled) {
                //                             searchKeywordsInFile(newBaseDir, f, searchTask, result, jsonGenerator, newZipFilePath);
                //                         }
                //                     });

                //         }
                //     } else {
                //         logger.info("Ignore file of type {}: {}", fileType, filePath);
                //     }
                // } catch (Exception e) {
                //     logger.warn("Failed to handle file {}: {}, {}", filePath, e.getMessage(), e.getStackTrace());
                // } finally {
                //     FileUtils.deleteQuietly(newBaseDir);
                // }
                // return;
            // enable later
            // if ("class".equalsIgnoreCase(ext) && ByteCodeAnalyser.isClassFile(filePath)) {
            //     new ByteCodeAnalyser().searchKeywords(baseDir, filePath, keywords, keywordsPatterns, keywordInfos);
            //     return;
            // }
        }
    }

    private static String getFileExt(String filePath) {
        String ext = "";
        int dotIdx = filePath.lastIndexOf(".");
        if (dotIdx >= 0 & dotIdx < filePath.length()) {
            ext = filePath.substring(dotIdx + 1);
        }
        return ext;
    }
}

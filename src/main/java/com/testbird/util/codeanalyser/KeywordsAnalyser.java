package com.testbird.util.codeanalyser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public interface KeywordsAnalyser {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonFactory jsonFactory = objectMapper.getFactory();
    /**
     *  @param file Absolute path of the file to search for keywords
     *  @param zipFilePath zip file path if it's searching in the unzipped directory
     */
    void searchKeywords(String file, String zipFilePath) throws SearchCanceledException;
}
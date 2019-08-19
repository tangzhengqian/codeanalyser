package com.testbird.util.common.exception;

/**
 * Created by jiayinxi on 18/01/17.
 */
public class InitDeInitException extends Exception {
    private String title;

    public InitDeInitException(String title, String message) {
        super(message);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}

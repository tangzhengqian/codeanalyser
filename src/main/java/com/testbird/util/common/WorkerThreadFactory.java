package com.testbird.util.common;

import java.util.concurrent.ThreadFactory;

public class WorkerThreadFactory implements ThreadFactory {

    private final String mThreadName;

    public WorkerThreadFactory(String name) {
        mThreadName = name;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName(mThreadName + "-" + t.getId());
        return t;
    }

}

package com.testbird.util.common;

import com.testbird.util.common.exception.InitDeInitException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by jibo on 2017/6/15.
 */
public class GlobalScheduler {
    private final static Logger LOGGER = LoggerFactory.getLogger(GlobalScheduler.class);
    private static Scheduler scheduler = null;

    public static Scheduler getInstance() throws InitDeInitException {
        if (scheduler == null) {
            try {
                scheduler = StdSchedulerFactory.getDefaultScheduler();
            } catch (SchedulerException e) {
                LOGGER.error("GlobalScheduler getInstance error: {}", e.getMessage(), e);
                throw new InitDeInitException("GlobalScheduler getInstance fail", e.getMessage());
            }
        }
        return scheduler;
    }
}

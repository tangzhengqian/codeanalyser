package com.testbird.util.common;

import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.pipeline.MessageObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息监听并处理, 主要用于日志打印
 */
public class Slf4jLogMessageObserver extends MessageObserver {
    private static final Logger sLogger = LoggerFactory.getLogger(Slf4jLogMessageObserver.class);
    private Map<String, Timer> timers = new ConcurrentHashMap<String, Timer>();

    @Override
    protected void onReceived(Request request, Response response)
    {
        if(!request.getUrl().endsWith("devices.json"))
            sLogger.debug(" received  " + request.getUrl() + " " + request.getHttpMethod());
        timers.put(request.getCorrelationId(), new Timer());
    }

    @Override
    protected void onException(Throwable exception, Request request, Response response)
    {
        sLogger.debug(request.getEffectiveHttpMethod().toString()
                + " "
                + request.getUrl()
                + " threw exception: "
                + exception.getClass().getSimpleName(), exception);
    }

    @Override
    protected void onSuccess(Request request, Response response)
    {
    }

    @Override
    protected void onComplete(Request request, Response response)
    {
        Timer timer = timers.remove(request.getCorrelationId());
        if (timer != null) timer.stop();

        StringBuffer sb = new StringBuffer(request.getEffectiveHttpMethod().toString());
        sb.append(" ");
        sb.append(request.getUrl());

        if (timer != null)
        {
            sb.append(" responded with ");
            sb.append(response.getResponseStatus().toString());
            sb.append(" in ");
            sb.append(timer.toString());
        }
        else
        {
            sb.append(" responded with ");
            sb.append(response.getResponseStatus().toString());
            sb.append(" (no timer found)");
        }

        if(!request.getUrl().endsWith("devices.json")) sLogger.debug(sb.toString());
    }

    // SECTION: INNER CLASS

    private class Timer
    {
        private long startMillis = 0;
        private long stopMillis = 0;

        public Timer()
        {
            super();
            this.startMillis = System.currentTimeMillis();
        }

        public void stop()
        {
            this.stopMillis = System.currentTimeMillis();
        }

        public String toString()
        {
            long stopTime = (stopMillis == 0 ? System.currentTimeMillis() : stopMillis);

            return String.valueOf(stopTime - startMillis) + "ms";
        }
    }
}

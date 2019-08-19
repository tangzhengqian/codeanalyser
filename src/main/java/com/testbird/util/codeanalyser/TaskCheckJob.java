package com.testbird.util.codeanalyser;

import com.testbird.util.common.exception.InitDeInitException;
import com.testbird.util.common.GlobalScheduler;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Created by jibo on 2017/6/15.
 */
@DisallowConcurrentExecution
public class TaskCheckJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(TaskCheckJob.class);
    private static final int TASK_DIR_LENGTH = 32;
    private static String root = Config.getTaskDir();
    private static JobDetail job;

    @Override
    public void execute(JobExecutionContext context) {
        File[] results = new File(root).listFiles(
                task -> task.isDirectory()
                && task.getName().length() == TASK_DIR_LENGTH
                && new File(Config.getTaskFinishFile(task.getName())).isFile()
        );
        if (results != null) {
            for (File task : results) {
                String taskId = task.getName();
                logger.debug("{} task is ready", taskId);
                try {
                    SearchKeywordsRequest request = new SearchKeywordsRequest();
                    request.key = taskId;
                    SearchTask searchTask = new SearchTask(request);
                    searchTask.checkResponse();
                    searchTask.handleSearchResult();
                } catch (Throwable e) {
                    logger.error("Task check {} error {}", taskId, e.getMessage(), e);
                }
            }
        }
    }

    public static void start() throws InitDeInitException {
        if (job == null) {
            job = newJob(TaskCheckJob.class).build();
            Trigger trigger = newTrigger().startNow().withSchedule(simpleSchedule().withIntervalInMinutes(1).repeatForever()).build();
            try {
                GlobalScheduler.getInstance().scheduleJob(job, trigger);
                logger.info("Start task results checking scheduler");
            } catch (SchedulerException e) {
                logger.error("Start task results checking scheduler error: {}", e.getMessage(), e);
                throw new InitDeInitException("TaskCheckJob start fail", e.getMessage());
            }
        }
    }

    public static void stop() throws InitDeInitException {
        if (job != null) {
            Scheduler scheduler = GlobalScheduler.getInstance();
            try {
                scheduler.deleteJob(job.getKey());
            } catch (SchedulerException e) {
                logger.error("stop task results checking scheduler error: {}", e.getMessage(), e);
                throw new InitDeInitException("TaskCheck stop fail", e.getMessage());
            }
            logger.info("Stop task result checking scheduler");
        }
    }
}

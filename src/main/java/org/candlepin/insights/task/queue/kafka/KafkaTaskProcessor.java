/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.task.queue.kafka;


import org.candlepin.insights.task.TaskDescriptor;
import org.candlepin.insights.task.TaskExecutionException;
import org.candlepin.insights.task.TaskFactory;
import org.candlepin.insights.task.TaskType;
import org.candlepin.insights.task.TaskWorker;
import org.candlepin.insights.task.queue.kafka.message.TaskMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;


/**
 * Responsible for receiving task messages from Kafka when they become available.
 */
public class KafkaTaskProcessor {
    private static final Logger log = LoggerFactory.getLogger(KafkaTaskProcessor.class);

    private TaskWorker worker;

    public KafkaTaskProcessor(TaskFactory taskFactory) {
        worker = new TaskWorker(taskFactory);
    }

    @KafkaListener(id = "rhsm-conduit-task-processor", topics = "${rhsm-conduit.tasks.task-group}")
    public void onTaskAvailable(TaskMessage taskMessage) {
        try {
            log.info("Message received from kafka: {}", taskMessage);
            worker.executeTask(describe(taskMessage));
        }
        catch (TaskExecutionException e) {
            // If a task fails to execute for any reason, it is logged and will
            // not get retried.
            log.error("Failed to execute task: {}", taskMessage, e);
        }
    }

    private TaskDescriptor describe(TaskMessage message) throws TaskExecutionException {
        try {
            return TaskDescriptor.builder(TaskType.valueOf(message.getType()), message.getGroupId())
                       .setArgs(message.getArgs()).build();
        }
        catch (IllegalArgumentException | NullPointerException e) {
            throw new TaskExecutionException(
                String.format("Unknown TaskType received from message: %s", message.getType()));
        }
    }

}

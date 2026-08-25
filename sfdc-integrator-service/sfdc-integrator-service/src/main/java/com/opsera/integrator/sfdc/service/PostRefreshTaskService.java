package com.opsera.integrator.sfdc.service;

import com.opsera.integrator.sfdc.model.DomainQueueRequest;
import com.opsera.integrator.sfdc.model.PostRefreshRequest;

import java.util.List;

/**
 * Service interface for legacy post-refresh task operations.
 */
public interface PostRefreshTaskService {

    void execute(PostRefreshRequest request);

    void retrieveSchedulerClasses(PostRefreshRequest request);

    void removeDomainQueueTask(DomainQueueRequest request);

    List<String> listDomainQueueTasks(String pipelineId, String stepId);

    void clearDomainMap(DomainQueueRequest request);
}

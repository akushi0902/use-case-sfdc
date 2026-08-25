package com.opsera.integrator.sfdc.service;

import com.opsera.integrator.sfdc.model.PostRefreshRequest;

/**
 * Service interface for legacy post-refresh task operations.
 */
public interface PostRefreshTaskService {

    void execute(PostRefreshRequest request);
}

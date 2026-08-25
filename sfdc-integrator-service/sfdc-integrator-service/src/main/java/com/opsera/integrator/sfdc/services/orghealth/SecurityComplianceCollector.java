package com.opsera.integrator.sfdc.services.orghealth;

import com.opsera.integrator.sfdc.model.orghealth.MfaUserInfo;

import java.util.List;

/**
 * Service interface for security compliance metric collection, including MFA enrollment data.
 */
public interface SecurityComplianceCollector {

    List<MfaUserInfo> getUsersWithoutMfa(String customerId, String toolId);
}

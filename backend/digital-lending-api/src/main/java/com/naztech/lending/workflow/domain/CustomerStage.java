package com.naztech.lending.workflow.domain;

/**
 * What the applicant is told their application is doing.
 *
 * <p>Deliberately coarser than the internal state. A customer does not need to
 * know whether their file is with the branch manager or the operations manager,
 * and telling them invites a phone call to whichever one they think is slow.
 */
public enum CustomerStage {
    DRAFT,
    SUBMITTED,
    IN_PROGRESS,
    INFORMATION_REQUIRED,
    APPROVED,
    DISBURSED,
    DECLINED,
    WITHDRAWN
}

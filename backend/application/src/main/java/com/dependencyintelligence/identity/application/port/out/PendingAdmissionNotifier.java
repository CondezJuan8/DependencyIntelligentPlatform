package com.dependencyintelligence.identity.application.port.out;

import com.dependencyintelligence.identity.application.dto.AccountResult;

/** Best-effort notice to platform administrators when an owner requests access. */
public interface PendingAdmissionNotifier {
    void notify(AccountResult account);
}

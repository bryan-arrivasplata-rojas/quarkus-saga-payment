package com.example.payment.service;

import com.example.payment.domain.entity.TransactionAudit;
import com.example.payment.saga.TransferContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuditService {

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void log(TransferContext ctx, String status, String description) {
        TransactionAudit audit = new TransactionAudit();
        audit.sagaId = ctx.sagaId();
        audit.fromAccountId = ctx.fromAccountId();
        audit.toAccountId = ctx.toAccountId();
        audit.amount = ctx.amount();
        audit.status = status;
        audit.stepDescription = description;
        audit.persist();
    }
}
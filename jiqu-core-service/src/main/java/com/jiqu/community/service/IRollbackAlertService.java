package com.jiqu.community.service;

import com.jiqu.community.entity.RollbackFailureLog;


public interface IRollbackAlertService {

    void sendRollbackAlert(RollbackFailureLog log);
}
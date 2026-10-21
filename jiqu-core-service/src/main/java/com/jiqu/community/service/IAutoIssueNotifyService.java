package com.jiqu.community.service;



public interface IAutoIssueNotifyService {
    
    void sendAutoIssueNotify(Long dropId, Long userId, Long orderId);
}
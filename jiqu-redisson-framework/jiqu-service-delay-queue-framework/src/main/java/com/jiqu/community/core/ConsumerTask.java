package com.jiqu.community.core;


public interface ConsumerTask {
    
    void execute(String content);
  
    String topic();
}

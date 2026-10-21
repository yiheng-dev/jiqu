package com.jiqu.community.service;


public interface IDropAccessTokenService {
  
    boolean isEnabled();
 
    String issueAccessToken(Long dropId, Long userId);
    
    boolean validateAndConsume(Long dropId, Long userId, String token);
}
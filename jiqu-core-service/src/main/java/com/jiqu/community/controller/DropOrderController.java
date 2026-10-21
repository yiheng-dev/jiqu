package com.jiqu.community.controller;


import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.jiqu.community.dto.CancelDropOrderDto;
import com.jiqu.community.dto.GetDropOrderByDropIdDto;
import com.jiqu.community.dto.GetDropOrderDto;
import com.jiqu.community.dto.Result;
import com.jiqu.community.execute.RateLimitHandler;
import com.jiqu.community.ratelimit.extension.RateLimitScene;
import com.jiqu.community.service.IReconciliationTaskService;
import com.jiqu.community.service.IDropAccessTokenService;
import com.jiqu.community.service.IDropOrderService;
import com.jiqu.community.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/drop-order")
public class DropOrderController {

    @Resource
    private IDropOrderService dropOrderService;

    @Resource
    private IDropAccessTokenService accessTokenService;

    @Resource
    private RateLimitHandler rateLimitHandler;
    
    @Resource
    private IReconciliationTaskService reconciliationTaskService;

    @GetMapping("/drop/token/{id}")
    public Result<String> issueDropAccessToken(@PathVariable("id") Long dropId) {
        Long userId = UserHolder.getUser().getId();
        rateLimitHandler.execute(dropId, userId, RateLimitScene.ISSUE_TOKEN);
        String token = accessTokenService.issueAccessToken(dropId, userId);
        return Result.ok(token);
    }

    @PostMapping("/drop/{id}")
    public Result<Long> limitedDrop(@PathVariable("id") Long dropId,
                                       @RequestParam(name = "accessToken", required = false) String accessToken) {
        Long userId = UserHolder.getUser().getId();
        rateLimitHandler.execute(dropId, userId, RateLimitScene.DROP_ORDER);
        if (accessTokenService.isEnabled()) {
            if (accessToken == null || !accessTokenService.validateAndConsume(dropId, userId, accessToken)) {
                return Result.fail("令牌校验失败或令牌已失效");
            }
        }
        return dropOrderService.limitedDrop(dropId);
    }
    
    @PostMapping("/get/drop-order-id")
    public Result<Long> getLimitedDropOrder(@Valid @RequestBody GetDropOrderDto getDropOrderDto) {
        return Result.ok(dropOrderService.getLimitedDropOrder(getDropOrderDto));
    }

    @PostMapping("/get/drop-order-id/by/drop-id")
    public Result<Long> getLimitedDropOrderIdByDropId(@Valid @RequestBody GetDropOrderByDropIdDto getDropOrderByDropIdDto) {
        return Result.ok(dropOrderService.getLimitedDropOrderIdByDropId(getDropOrderByDropIdDto));
    }
    
    @PostMapping("/cancel")
    public Result<Boolean> cancel(@Valid @RequestBody CancelDropOrderDto cancelDropOrderDto) {
        return Result.ok(dropOrderService.cancel(cancelDropOrderDto));
    }
    
    @PostMapping(value = "/reconciliation/task/all")
    public Result<Void> reconciliationTaskAll() {
        reconciliationTaskService.reconciliationTaskExecute();
        return Result.ok();
    }
}

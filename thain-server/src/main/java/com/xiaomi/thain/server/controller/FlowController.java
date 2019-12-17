/*
 * Copyright (c) 2019, Xiaomi, Inc.  All rights reserved.
 * This source code is licensed under the Apache License Version 2.0, which
 * can be found in the LICENSE file in the root directory of this source tree.
 */

package com.xiaomi.thain.server.controller;

import com.xiaomi.thain.common.entity.ApiResult;
import com.xiaomi.thain.common.exception.ThainFlowRunningException;
import com.xiaomi.thain.common.exception.ThainRepeatExecutionException;
import com.xiaomi.thain.common.exception.ThainRuntimeException;
import com.xiaomi.thain.server.model.rp.FlowAllInfoRp;
import com.xiaomi.thain.server.model.rq.FlowListRq;
import com.xiaomi.thain.server.model.sp.FlowListSp;
import com.xiaomi.thain.server.service.FlowExecutionService;
import com.xiaomi.thain.server.service.FlowService;
import com.xiaomi.thain.server.service.PermissionService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.bind.annotation.*;

import static com.xiaomi.thain.server.handler.ThreadLocalUser.*;

/**
 * @author liangyongrui@xiaomi.com
 */
@Slf4j
@RestController
@RequestMapping("api/flow")
public class FlowController {

    private static final String NO_PERMISSION_MESSAGE = "You do not have permission to do this operation";

    @NonNull
    private final FlowService flowService;
    @NonNull
    private final PermissionService permissionService;
    @NonNull
    private final FlowExecutionService flowExecutionService;

    public FlowController(@NonNull FlowService flowService,
                          @NonNull PermissionService permissionService,
                          @NonNull FlowExecutionService flowExecutionService) {
        this.flowService = flowService;
        this.permissionService = permissionService;
        this.flowExecutionService = flowExecutionService;
    }

    @GetMapping("/getComponentDefineJson")
    public ApiResult getComponentDefineJson() {
        try {
            return ApiResult.success(flowService.getComponentDefineJsonString());
        } catch (Exception e) {
            log.error("getComponentDefineJson", e);
            return ApiResult.fail("Failed to obtain frontend component json definition : " + e.getMessage());
        }
    }

    @GetMapping("list")
    public ApiResult list(@NonNull FlowListRq flowListRq) {
        try {
            FlowListSp flowListSp;
            if (isAdmin()) {
                flowListSp = FlowListSp.getInstance(flowListRq);
            } else {
                flowListSp = FlowListSp.getInstance(flowListRq, getUsername(), getAuthorities());
            }
            return ApiResult.success(
                    flowService.getFlowList(flowListSp),
                    flowService.getFlowListCount(flowListSp),
                    flowListRq.page == null ? 1 : flowListRq.page,
                    flowListRq.pageSize == null ? 20 : flowListRq.pageSize);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @GetMapping("all-info/{flowId}")
    public ApiResult getAllInfo(@PathVariable("flowId") long flowId) {
        try {
            if (!isAdmin() && !permissionService.getFlowAccessible(flowId, getUsername(), getAuthorities())) {
                return ApiResult.fail(NO_PERMISSION_MESSAGE);
            }
            val flowModel = flowService.getFlow(flowId);
            if (flowModel == null) {
                throw new ThainRuntimeException();
            }
            val jobModelList = flowService.getJobModelList(flowId);
            return ApiResult.success(new FlowAllInfoRp(flowModel, jobModelList));
        } catch (Exception e) {
            return ApiResult.fail(ExceptionUtils.getRootCauseMessage(e));
        }
    }

    @DeleteMapping("{flowId}")
    public ApiResult delete(@PathVariable long flowId) {
        try {
            if (!isAdmin() && !permissionService.getFlowAccessible(flowId, getUsername(), getAuthorities())) {
                return ApiResult.fail(NO_PERMISSION_MESSAGE);
            }
            flowService.delete(flowId);
        } catch (Exception e) {
            log.error("delete:", e);
            return ApiResult.fail(e.getMessage());
        }
        return ApiResult.success();
    }

    @PatchMapping("/start/{flowId}")
    public ApiResult start(@PathVariable long flowId) {
        try {
            if (!isAdmin() && !permissionService.getFlowAccessible(flowId, getUsername(), getAuthorities())) {
                return ApiResult.fail(NO_PERMISSION_MESSAGE);
            }
            return ApiResult.success(flowService.start(flowId));
        } catch (ThainRepeatExecutionException | ThainFlowRunningException e) {
            log.warn(ExceptionUtils.getRootCauseMessage(e));
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("start:", e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @PatchMapping("/scheduling/{flowId}")
    public ApiResult scheduling(@PathVariable long flowId) {
        try {
            if (!isAdmin() && !permissionService.getFlowAccessible(flowId, getUsername(), getAuthorities())) {
                return ApiResult.fail(NO_PERMISSION_MESSAGE);
            }
            flowService.scheduling(flowId);
        } catch (Exception e) {
            log.error("scheduling:", e);
            return ApiResult.fail(e.getMessage());
        }
        return ApiResult.success();
    }

    @PatchMapping("/update/{flowId}/{cron}")
    public ApiResult updateCron(@PathVariable long flowId, @NonNull @PathVariable String cron) {
        try {
            if (!isAdmin() && !permissionService.getFlowAccessible(flowId, getUsername(), getAuthorities())) {
                return ApiResult.fail(NO_PERMISSION_MESSAGE);
            }
            flowService.updateCron(flowId, cron);
        } catch (Exception e) {
            log.error("scheduling:", e);
            return ApiResult.fail(e.getMessage());
        }
        return ApiResult.success();
    }

    @PatchMapping("/pause/{flowId}")
    public ApiResult pause(@PathVariable long flowId) {
        try {
            if (!isAdmin() && !permissionService.getFlowAccessible(flowId, getUsername(), getAuthorities())) {
                return ApiResult.fail(NO_PERMISSION_MESSAGE);
            }
            flowService.pause(flowId);
        } catch (Exception e) {
            log.error("pause:", e);
            return ApiResult.fail(e.getMessage());
        }
        return ApiResult.success();
    }

    @PatchMapping("/kill/{flowId}")
    public ApiResult kill(@PathVariable long flowId) {
        try {
            if (!isAdmin() && !permissionService.getFlowAccessible(flowId, getUsername(), getAuthorities())) {
                return ApiResult.fail(NO_PERMISSION_MESSAGE);
            }
            if (!flowExecutionService.killFlowExecutionsByFlowId(flowId)) {
                return ApiResult.fail("No execution need kill");
            }
        } catch (Exception e) {
            log.error("kill:", e);
            return ApiResult.fail(e.getMessage());
        }
        return ApiResult.success();
    }
}

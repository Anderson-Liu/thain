/*
 * Copyright (c) 2019, Xiaomi, Inc.  All rights reserved.
 * This source code is licensed under the Apache License Version 2.0, which
 * can be found in the LICENSE file in the root directory of this source tree.
 */
package com.xiaomi.thain.common.model.dp

import com.xiaomi.thain.common.model.rq.UpdateFlowRq

/**
 * add flow model
 *
 * @author liangyongrui@xiaomi.com
 */
data class UpdateFlowDp(
        val id: Long,
        val name: String?,
        val cron: String?,
        val modifyCallbackUrl: String?,
        val pauseContinuousFailure: Long?,
        val emailContinuousFailure: String?,
        val callbackUrl: String?,
        val callbackEmail: String?,
        /**
         * 秒时间戳
         */
        val slaDuration: Long?,
        val slaEmail: String?,
        val slaKill: Boolean = false,
//    /**
//     * 最后一次运行状态,com.xiaomi.thain.common.constant.FlowLastRunStatus
//     */
//    val lastRunStatus: Int?,
        /**
         * 调度状态，1 调度中、2 暂停调度、（3 未设置调度{只运行一次的任务}）
         */
        val schedulingStatus: Int
) {
    constructor(updateFlowRq: UpdateFlowRq, schedulingStatus: Int) : this(
            updateFlowRq.id,
            updateFlowRq.name,
            updateFlowRq.cron,
            updateFlowRq.modifyCallbackUrl,
            updateFlowRq.pauseContinuousFailure,
            updateFlowRq.emailContinuousFailure,
            updateFlowRq.callbackUrl,
            updateFlowRq.callbackEmail,
            updateFlowRq.slaDuration,
            updateFlowRq.slaEmail,
            updateFlowRq.slaKill,
            schedulingStatus
    )

}


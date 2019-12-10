package com.xiaomi.thain.common.model.dr

/**
 * Date 19-5-17 下午12:07
 * 数据库查出的flow
 *
 * @author liangyongrui@xiaomi.com
 */
data class FlowDr(
        val id: Long,
        val name: String,
        val cron: String,
        val modifyCallbackUrl: String,
        val pauseContinuousFailure: Long,
        val emailContinuousFailure: String,
        val createUser: String,
        val callbackUrl: String,
        val callbackEmail: String,
        /**
         * 创建的appId,"thain" 为网页创建
         */
        val createAppId: String,
        /**
         * 秒时间戳
         */
        val slaDuration: Long,
        val slaEmail: String,
        val slaKill: Boolean = false,
        /**
         * 最后一次运行状态,com.xiaomi.thain.common.constant.FlowLastRunStatus
         */
        val lastRunStatus: Int = 0,
        /**
         * 调度状态，1 调度中、2 暂停调度、（3 未设置调度{只运行一次的任务}）
         */
        val schedulingStatus: Int = 0,
        val retryNumber: Int = 0,
        val timeInterval: Int = 0,
        /**
         * 创建时间
         */
        val createTime: java.sql.Timestamp,
        /**
         * 更新时间
         */
        val updateTime: java.sql.Timestamp,
        /**
         * 状态更新时间
         */
        val statusUpdateTime: java.sql.Timestamp,
        val deleted: Boolean = false)
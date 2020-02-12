package com.xiaomi.thain.core

import com.alibaba.fastjson.JSON
import com.xiaomi.thain.common.constant.FlowExecutionStatus
import com.xiaomi.thain.common.constant.FlowSchedulingStatus
import com.xiaomi.thain.common.exception.ThainException
import com.xiaomi.thain.common.utils.ifNull
import com.xiaomi.thain.core.constant.FlowOperationType
import com.xiaomi.thain.core.model.dp.UpdateFlowDp
import com.xiaomi.thain.core.model.rq.AddFlowAndJobsRq
import com.xiaomi.thain.core.model.rq.AddJobRq
import com.xiaomi.thain.core.model.rq.UpdateFlowRq
import com.xiaomi.thain.core.process.ProcessEngine
import com.xiaomi.thain.core.process.ProcessEngineConfiguration
import com.xiaomi.thain.core.process.service.ComponentService
import com.xiaomi.thain.core.scheduler.SchedulerEngine
import com.xiaomi.thain.core.scheduler.SchedulerEngineConfiguration
import com.xiaomi.thain.core.utils.SendModifyUtils
import org.apache.commons.lang3.StringUtils.isNotBlank
import org.quartz.CronExpression
import org.quartz.SchedulerException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.text.MessageFormat
import java.text.ParseException

/**
 * @author liangyongrui@xiaomi.com
 * @date 19-5-16 下午8:38
 */
class ThainFacade(processEngineConfiguration: ProcessEngineConfiguration,
                  schedulerEngineConfiguration: SchedulerEngineConfiguration) {

    private val log = LoggerFactory.getLogger(this.javaClass)!!

    private val processEngine: ProcessEngine = ProcessEngine.newInstance(processEngineConfiguration, this)

    val schedulerEngine = SchedulerEngine(schedulerEngineConfiguration, processEngine)

    init {
        schedulerEngine.start()
        FlowOperationLogHandler.sqlSessionFactory = processEngine.sqlSessionFactory
        FlowOperationLogHandler.mailService = processEngine.processEngineStorage.mailService
    }

    /**
     * 新建任务, cron为空的则只部署，不调度, 这个flowJson是不含id的，如果含id也没用
     */
    @Throws(ThainException::class)
    fun addFlow(addRq: AddFlowAndJobsRq): Long {
        val flowId = processEngine.addFlow(addRq.flowModel, addRq.jobModelList)
                ?: throw ThainException("failed to insert flow")
        if (addRq.flowModel.cron.isNullOrBlank()) {
            return flowId
        }
        try {
            CronExpression.validateExpression(addRq.flowModel.cron)
            schedulerEngine.addFlow(flowId, addRq.flowModel.cron)
        } catch (e: Exception) {
            processEngine.deleteFlow(flowId)
            throw ThainException(e)
        }
        FlowOperationLogHandler(
                flowId = flowId,
                operationType = FlowOperationType.CREATE,
                appId = addRq.flowModel.createAppId ?: throw ThainException("app id cannot be empty"),
                username = addRq.flowModel.createUser ?: throw ThainException("create user cannot be empty"),
                extraInfo = JSON.toJSONString(mapOf(
                        "flow" to addRq.flowModel,
                        "jobList" to addRq.jobModelList
                ))).save()
        return flowId
    }

    /**
     * 更新flow
     */
    @Throws(SchedulerException::class, ThainException::class, ParseException::class)
    fun updateFlow(updateFlowRq: UpdateFlowRq, jobModelList: List<AddJobRq>) {
        val schedulingStatus = updateFlowRq.cron
                .takeIf { !it.isNullOrBlank() }
                ?.let { cron ->
                    CronExpression.validateExpression(cron)
                    FlowSchedulingStatus.getInstance(processEngine.getFlow(updateFlowRq.id).schedulingStatus)
                            .takeIf { it != FlowSchedulingStatus.NOT_SET }
                            .ifNull { FlowSchedulingStatus.SCHEDULING }
                }.ifNull { FlowSchedulingStatus.NOT_SET }

        if (schedulingStatus != FlowSchedulingStatus.SCHEDULING) {
            schedulerEngine.deleteFlow(updateFlowRq.id)
        } else {
            schedulerEngine.addFlow(updateFlowRq.id, updateFlowRq.cron)
        }
        val updateFlowDp = UpdateFlowDp(updateFlowRq, schedulingStatus)
        processEngine.processEngineStorage.flowDao.updateFlow(updateFlowDp, jobModelList)
        FlowOperationLogHandler(
                flowId = updateFlowRq.id,
                operationType = FlowOperationType.UPDATE,
                appId = updateFlowRq.appId,
                username = updateFlowRq.username,
                extraInfo = JSON.toJSONString(mapOf(
                        "flow" to updateFlowRq,
                        "jobList" to jobModelList
                ))).save()
    }

    /**
     * 删除Flow
     */
    @Throws(SchedulerException::class)
    fun deleteFlow(flowId: Long, appId: String, username: String) {
        schedulerEngine.deleteFlow(flowId)
        processEngine.deleteFlow(flowId)
        FlowOperationLogHandler(
                flowId = flowId,
                operationType = FlowOperationType.DELETE,
                appId = appId,
                username = username,
                extraInfo = "").save()
    }

    /**
     * 触发某个Flow
     *
     * 返回 flow execution id
     */
    fun startFlow(flowId: Long, variables: Map<String, String>, appId: String, username: String): Long {
        val id = processEngine.startProcess(flowId, variables)
        FlowOperationLogHandler(
                flowId = flowId,
                operationType = FlowOperationType.MANUAL_TRIGGER,
                appId = appId,
                username = username,
                extraInfo = JSON.toJSONString(mapOf("variables" to variables))).save()
        return id
    }

    val componentService: ComponentService
        get() = processEngine.processEngineStorage.componentService

    @Throws(ThainException::class)
    fun pauseFlow(flowId: Long, appId: String, username: String, auto: Boolean) {
        val flowDr = processEngine.processEngineStorage.flowDao.getFlow(flowId)
                ?: throw ThainException(MessageFormat.format(NON_EXIST_FLOW, flowId))
        try {
            processEngine.processEngineStorage.flowDao.pauseFlow(flowId)
            schedulerEngine.deleteFlow(flowId)
            if (isNotBlank(flowDr.modifyCallbackUrl)) {
                SendModifyUtils.sendPause(flowId, flowDr.modifyCallbackUrl)
            }
            FlowOperationLogHandler(
                    flowId = flowId,
                    operationType = if (auto) {
                        FlowOperationType.AUTO_PAUSE
                    } else {
                        FlowOperationType.MANUAL_PAUSE
                    },
                    appId = appId,
                    username = username,
                    extraInfo = "").save()
        } catch (e: Exception) {
            log.error("", e)
            try {
                val jobModelList = processEngine.processEngineStorage
                        .jobDao.getJobs(flowId)
                        .map { AddJobRq(it) }
                updateFlow(UpdateFlowRq(flowDr), jobModelList)
            } catch (ex: Exception) {
                log.error("", ex)
            }
            throw ThainException(e)
        }
    }

    fun schedulingFlow(flowId: Long, appId: String, username: String) {
        val flowModel = processEngine.processEngineStorage.flowDao.getFlow(flowId)
                ?: throw ThainException(MessageFormat.format(NON_EXIST_FLOW, flowId))
        schedulerEngine.addFlow(flowModel.id, flowModel.cron)
        processEngine.processEngineStorage.flowDao
                .updateSchedulingStatus(flowModel.id, FlowSchedulingStatus.SCHEDULING)
        if (isNotBlank(flowModel.modifyCallbackUrl)) {
            SendModifyUtils.sendScheduling(flowId, flowModel.modifyCallbackUrl)
        }
        FlowOperationLogHandler(
                flowId = flowId,
                operationType = FlowOperationType.SCHEDULE,
                appId = appId,
                username = username,
                extraInfo = "").save()
    }

    @Throws(ThainException::class)
    fun killFlowExecution(flowId: Long, flowExecutionId: Long, auto: Boolean, appId: String, username: String) {
        val flowExecutionModel = processEngine.processEngineStorage.flowExecutionDao
                .getFlowExecution(flowExecutionId)
                ?: throw ThainException("flowExecution id does not exist：$flowExecutionId")
        if (FlowExecutionStatus.getInstance(flowExecutionModel.status) != FlowExecutionStatus.RUNNING) {
            throw ThainException("flowExecution does not running: $flowExecutionId")
        }
        val operationType = if (auto) {
            processEngine.processEngineStorage.flowExecutionDao.updateFlowExecutionStatus(flowExecutionId, FlowExecutionStatus.AUTO_KILLED.code)
            FlowOperationType.AUTO_KILL
        } else {
            processEngine.processEngineStorage.flowExecutionDao.updateFlowExecutionStatus(flowExecutionId, FlowExecutionStatus.KILLED.code)
            FlowOperationType.MANUAL_KILL
        }
        processEngine.processEngineStorage.jobExecutionDao.killJobExecution(flowExecutionId)
        processEngine.processEngineStorage.flowDao.killFlow(flowExecutionModel.flowId)
        FlowOperationLogHandler(
                flowId = flowId,
                operationType = operationType,
                appId = appId,
                username = username,
                extraInfo = "").save()
    }

    @Throws(ThainException::class, ParseException::class, SchedulerException::class, IOException::class)
    fun updateCron(flowId: Long, cron: String?) {
        val flowDr = processEngine.processEngineStorage.flowDao.getFlow(flowId)
                ?: throw ThainException(MessageFormat.format(NON_EXIST_FLOW, flowId))
        val jobModelList = processEngine.processEngineStorage.jobDao.getJobs(flowId).map { AddJobRq(it) }
        if (cron == null) {
            updateFlow(UpdateFlowRq(flowDr), jobModelList)
        } else {
            updateFlow(UpdateFlowRq(flowDr.copy(cron = cron)), jobModelList)
        }
        if (isNotBlank(flowDr.modifyCallbackUrl)) {
            SendModifyUtils.sendScheduling(flowId, flowDr.modifyCallbackUrl)
        }
    }

    companion object {
        private const val NON_EXIST_FLOW = "flow does not exist:{0}"
    }

}

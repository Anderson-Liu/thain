package com.xiaomi.thain.core.process.runtime.executor.service

import com.xiaomi.thain.common.constant.FlowExecutionStatus
import com.xiaomi.thain.common.constant.FlowLastRunStatus
import com.xiaomi.thain.common.model.dr.FlowDr
import com.xiaomi.thain.core.process.ProcessEngine
import com.xiaomi.thain.core.process.ProcessEngineStorage
import com.xiaomi.thain.core.process.runtime.log.FlowExecutionLogHandler
import com.xiaomi.thain.core.process.runtime.notice.FlowHttpNotice
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.LoggerFactory

/**
 * Date 19-5-21 上午10:46
 * 任务服务类，对不影响任务执行的方法进行管理，如：日志,状态等
 *
 * @author liangyongrui@xiaomi.com
 */
class FlowExecutionService(val flowExecutionId: Long,
                           private val flowDr: FlowDr,
                           private val retryNumber: Int,
                           private val processEngineStorage: ProcessEngineStorage) {

    private val log = LoggerFactory.getLogger(this.javaClass)!!

    private val flowExecutionLogHandler = FlowExecutionLogHandler.getInstance(flowExecutionId, processEngineStorage)
    private val mailNotice = processEngineStorage.getMailNotice(flowDr.callbackEmail)
    private val flowHttpNotice = FlowHttpNotice.getInstance(flowDr.callbackUrl, flowDr.id, flowExecutionId)
    private val flowService = FlowService.getInstance(flowDr.id, processEngineStorage)
    private val flowExecutionDao = processEngineStorage.flowExecutionDao

    /**
     * 如果是异常结束,异常信息.
     * 正常结束时，errorMessage为""
     */
    var errorMessage = ""
        private set
    /**
     * 流程结束状态
     */
    var flowEndStatus = FlowLastRunStatus.SUCCESS
        private set

    private var flowExecutionEndStatus = FlowExecutionStatus.SUCCESS

    /**
     * 开始任务
     */
    fun startFlowExecution() {
        try {
            if (retryNumber == 0) {
                flowService.startFlow()
                flowHttpNotice.sendStart()
            }
            flowExecutionLogHandler.addInfo("begin to execute flow：$flowExecutionId")
        } catch (e: Exception) {
            log.error("", e)
        }
    }

    /**
     * 添加错误
     */
    fun addError(message: String) {
        errorMessage = message
        flowEndStatus = FlowLastRunStatus.ERROR
        flowExecutionEndStatus = FlowExecutionStatus.ERROR
    }

    /**
     * 添加错误
     */
    fun autoKilled() {
        errorMessage = "auto kill"
        flowEndStatus = FlowLastRunStatus.AUTO_KILLED
        flowExecutionEndStatus = FlowExecutionStatus.AUTO_KILLED
    }

    /**
     * 添加错误
     */
    fun killed() {
        errorMessage = "manual kill"
        flowEndStatus = FlowLastRunStatus.KILLED
        flowExecutionEndStatus = FlowExecutionStatus.KILLED
    }

    /**
     * 结束任务
     */
    fun endFlowExecution() {
        try {
            when (flowEndStatus) {
                FlowLastRunStatus.SUCCESS -> {
                    flowExecutionLogHandler.endSuccess()
                    flowHttpNotice.sendSuccess()
                }
                FlowLastRunStatus.KILLED -> {
                    flowExecutionLogHandler.endError(errorMessage)
                    flowHttpNotice.sendKilled()
                }
                FlowLastRunStatus.AUTO_KILLED -> {
                    flowExecutionLogHandler.endError(errorMessage)
                    flowHttpNotice.sendAutoKilled()
                }
                else -> {
                    if (flowDr.retryNumber <= retryNumber) {
                        try {
                            flowExecutionLogHandler.endError(errorMessage)
                            flowHttpNotice.sendError(errorMessage)
                            mailNotice.sendError(errorMessage)
                            checkContinuousFailure()
                        } finally {
                            flowService.endFlow(flowEndStatus)
                        }
                    } else {
                        flowExecutionEndStatus = FlowExecutionStatus.ERROR_WAITING_RETRY
                        ProcessEngine.getInstance(processEngineStorage.processEngineId).thainFacade
                                .schedulerEngine
                                .addRetry(flowDr, retryNumber + 1)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn(ExceptionUtils.getRootCauseMessage(e))
        } finally {
            processEngineStorage.flowExecutionDao.updateFlowExecutionStatus(flowExecutionId, flowExecutionEndStatus.code)
        }
    }

    fun addInfo(s: String) {
        flowExecutionLogHandler.addInfo(s)
    }

    /**
     * 连续失败暂停任务
     */
    private fun checkContinuousFailure() {
        flowDr.pauseContinuousFailure.takeIf { it > 0 }
                ?.let { flowExecutionDao.getLatest(flowDr.id, flowDr.pauseContinuousFailure) }
                ?.filter { FlowExecutionStatus.getInstance(it.status) == FlowExecutionStatus.ERROR }
                ?.takeIf { it.count() >= flowDr.pauseContinuousFailure - 1 }
                ?.apply { ProcessEngine.getInstance(processEngineStorage.processEngineId).thainFacade.pauseFlow(flowDr.id) }
                ?.takeIf { !flowDr.emailContinuousFailure.isBlank() }
                ?.apply {
                    processEngineStorage.mailService.send(
                            flowDr.emailContinuousFailure.trim().split(",".toRegex()).toTypedArray(),
                            "Thain 任务连续失败通知",
                            "您的任务：[thain-${flowDr.id}]${flowDr.name}, 连续失败了${flowDr.pauseContinuousFailure}次，任务已经暂停。最近一次失败原因：$errorMessage"
                    )
                }
    }

}

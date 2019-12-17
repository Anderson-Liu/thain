package com.xiaomi.thain.server.service.impl

import com.xiaomi.thain.common.exception.ThainException
import com.xiaomi.thain.common.exception.ThainRepeatExecutionException
import com.xiaomi.thain.core.model.dr.FlowDr
import com.xiaomi.thain.core.model.dr.JobDr
import com.xiaomi.thain.core.model.rq.AddFlowAndJobsRq
import com.xiaomi.thain.core.model.rq.AddFlowRq
import com.xiaomi.thain.core.model.rq.AddJobRq
import com.xiaomi.thain.core.model.rq.UpdateFlowRq
import com.xiaomi.thain.common.utils.ifNull
import com.xiaomi.thain.core.ThainFacade
import com.xiaomi.thain.server.dao.FlowDao
import com.xiaomi.thain.server.model.sp.FlowListSp
import com.xiaomi.thain.server.service.FlowService
import org.quartz.SchedulerException
import org.springframework.stereotype.Service
import java.io.IOException
import java.text.ParseException

/**
 * @author liangyongrui
 */
@Service
class FlowServiceImpl(
        private val flowDao: FlowDao,
        private val thainFacade: ThainFacade) : FlowService {

    override fun getFlowList(flowListSp: FlowListSp): List<FlowDr> {
        return flowDao.getFlowList(flowListSp)
    }

    override fun getFlowListCount(flowListSp: FlowListSp): Long {
        return flowDao.getFlowListCount(flowListSp)
    }

    override fun add(addFlowRq: AddFlowRq, addJobRqList: List<AddJobRq>, appId: String): Long {
        val flow = addFlowRq
                .takeIf { !it.slaKill || it.slaDuration == 0L }
                ?.copy(slaKill = true, slaDuration = 3L * 60 * 60)
                .ifNull { addFlowRq }.copy(createAppId = appId)
        val flowId = flow.id
        if (flowId != null && flowDao.flowExist(flowId)) {
            val updateFlowRq = UpdateFlowRq(flow, flowId)
            thainFacade.updateFlow(updateFlowRq, addJobRqList)
            return updateFlowRq.id
        }
        return thainFacade.addFlow(AddFlowAndJobsRq(flow, addJobRqList))
                .also { flowDao.updateAppId(it, appId) }
    }

    @Throws(SchedulerException::class)
    override fun delete(flowId: Long): Boolean {
        thainFacade.deleteFlow(flowId)
        return true
    }

    @Throws(ThainException::class, ThainRepeatExecutionException::class)
    override fun start(flowId: Long): Long {
        return thainFacade.startFlow(flowId)
    }

    override fun getFlow(flowId: Long): FlowDr? {
        return flowDao.getFlow(flowId)
    }

    override fun getJobModelList(flowId: Long): List<JobDr> {
        return flowDao.getJobModelList(flowId)
    }

    override fun getComponentDefineJsonString(): List<String> {
        return thainFacade.componentService.componentJsonList
    }

    @Throws(ThainException::class, SchedulerException::class, IOException::class)
    override fun scheduling(flowId: Long) {
        thainFacade.schedulingFlow(flowId)
    }

    @Throws(ThainException::class, ParseException::class, SchedulerException::class, IOException::class)
    override fun updateCron(flowId: Long, cron: String?) {
        thainFacade.updateCron(flowId, cron)
    }

    @Throws(ThainException::class)
    override fun pause(flowId: Long) {
        thainFacade.pauseFlow(flowId)
    }

}

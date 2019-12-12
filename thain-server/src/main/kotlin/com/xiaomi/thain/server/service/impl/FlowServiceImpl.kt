package com.xiaomi.thain.server.service.impl

import com.xiaomi.thain.common.exception.ThainException
import com.xiaomi.thain.common.exception.ThainRepeatExecutionException
import com.xiaomi.thain.common.model.dr.FlowDr
import com.xiaomi.thain.common.model.dr.JobDr
import com.xiaomi.thain.common.model.rq.AddFlowAndJobsRq
import com.xiaomi.thain.common.model.rq.AddFlowRq
import com.xiaomi.thain.common.model.rq.AddJobRq
import com.xiaomi.thain.common.model.rq.UpdateFlowRq
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

    @Throws(ThainException::class, ParseException::class, SchedulerException::class)
    override fun add(addFlowRq: AddFlowRq, addJobRqList: List<AddJobRq>, appId: String): Long {
        val localAddFlowRq = if (!addFlowRq.slaKill || addFlowRq.slaDuration == 0L) {
            addFlowRq.copy(slaKill = true, slaDuration = 3L * 60 * 60)
        } else {
            addFlowRq
        }.copy(createAppId = appId)
        val localAddFlowRqId = localAddFlowRq.id
        if (localAddFlowRqId != null && flowDao.flowExist(localAddFlowRqId)) {
            val updateFlowRq = UpdateFlowRq(localAddFlowRq, localAddFlowRqId)
            thainFacade.updateFlow(updateFlowRq, addJobRqList)
            return updateFlowRq.id
        }
        val flowId = thainFacade.addFlow(AddFlowAndJobsRq(localAddFlowRq, addJobRqList))
        flowDao.updateAppId(flowId, appId)
        return flowId
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

    override fun getComponentDefineStringMap(): Map<String, String> {
        return thainFacade.componentDefineJsonList
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

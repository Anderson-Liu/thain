package com.xiaomi.thain.server.model.rp

import com.xiaomi.thain.core.model.dr.FlowDr
import com.xiaomi.thain.core.model.dr.JobDr

/**
 * Date 19-7-1 上午10:33
 * flow model 和 jobModel list
 *
 * @author liangyongrui@xiaomi.com
 */
class FlowAllInfoRp(
        val flowModel: FlowDr,
        val jobModelList: List<JobDr>
)

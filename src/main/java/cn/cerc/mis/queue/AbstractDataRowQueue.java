package cn.cerc.mis.queue;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.cerc.db.core.DataCell;
import cn.cerc.db.core.DataRow;
import cn.cerc.db.core.IHandle;
import cn.cerc.db.core.ServiceException;
import cn.cerc.db.core.Utils;
import cn.cerc.db.queue.AbstractQueue;
import cn.cerc.db.queue.QueueServiceEnum;
import cn.cerc.mis.client.CorpConfigImpl;
import cn.cerc.mis.client.RemoteService;
import cn.cerc.mis.core.Application;

public abstract class AbstractDataRowQueue extends AbstractQueue {
    private static final Logger log = LoggerFactory.getLogger(AbstractDataRowQueue.class);

    /**
     * 生产者投放消息
     */
    @Deprecated
    protected String push(IHandle handle, DataRow dataRow) {
        return pushToLocal(handle, dataRow);
    }

    // Local 不需要传递 token，直接使用当前handle的令牌
    protected String pushToLocal(IHandle handle, DataRow dataRow) {
        if (dataRow.hasValue("token"))
            log.warn("{}.appendToLocal 代码编写不符合规范，请予改进", this.getClass().getName());
        else
            dataRow.setValue("token", handle.getSession().getToken());
        if (!dataRow.hasValue("corp_no_"))
            dataRow.setValue("corp_no_", handle.getSession().getCorpNo());
        if (!dataRow.hasValue("user_code_"))
            dataRow.setValue("user_code_", handle.getSession().getUserCode());
        return super.push(dataRow.json());
    }

    protected String pushToRemote(IHandle handle, CorpConfigImpl config, DataRow dataRow) throws ServiceException {
        Objects.requireNonNull(config);
        if (!Utils.isEmpty(config.getCorpNo())) {
            var serviceConfig = RemoteService.getServerConfig(Application.getContext());
            if (serviceConfig.isPresent()) {
                var remoteToken = serviceConfig.get().getToken(handle, config.getCorpNo());
                if (remoteToken.isPresent())
                    dataRow.setValue("token", remoteToken.get());
            }
        }

        if (!dataRow.hasValue("corp_no_"))
            dataRow.setValue("corp_no_", handle.getSession().getCorpNo());
        if (!dataRow.hasValue("user_code_"))
            dataRow.setValue("user_code_", handle.getSession().getUserCode());
        return super.push(dataRow.json());
    }

    @Override
    public final boolean consume(String message, boolean repushOnError) {
        var data = new DataRow().setJson(message);
        try (TaskHandle handle = new TaskHandle()) {
            if (data.hasValue("token")) {
                // 临时恢复token，由队列自己实现此方法，设置Redis缓存
                this.repairToken(data.getString("token"));
                boolean loadToken = handle.getSession().loadToken(data.getString("token"));
                if (!loadToken) {
                    String error = String.format("队列 token 已失效 %s，执行对象 %s，消息内容 %s", data.getString("token"), this.getClass(),
                            message);
                    RuntimeException e = new RuntimeException(error);
                    log.warn(e.getMessage(), e);
                    return true;
                }
                DataCell corpNo = data.bind("corp_no_");// 执行器的目标帐套
                DataCell userCode = data.bind("user_code_");
                if (corpNo.hasValue())
                    handle.buildSession(corpNo.getString(), userCode.getString());
            }
            boolean result = this.execute(handle, data);
            // 非Sqlmq队列执行失败后，将其插入到Sqlmq中继续执行
            if (repushOnError && !result && this.getDelayTime() > 0 && this.getService() != QueueServiceEnum.Sqlmq) {
                super.pushToSqlmq(message);
                return true;
            }
            return result;
        }
    }

    public abstract boolean execute(IHandle handle, DataRow data);

}

package cn.cerc.mis.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.cerc.db.core.DataRow;
import cn.cerc.db.core.ISession;
import cn.cerc.mis.core.Application;

public class SyncDatabase implements IPopProcesser {
    private static final Logger log = LoggerFactory.getLogger(SyncDatabase.class);
    private ISyncServer queue;

    public SyncDatabase(ISyncServer queue) {
        super();
        this.queue = queue;
    }

    public void push(ISession session, String tableCode, DataRow record, SyncOpera opera) {
        DataRow rs = new DataRow();
        rs.setValue("__table", tableCode);
        rs.setValue("__opera", opera.ordinal());
        rs.copyValues(record);
        queue.push(session, rs);
    }

    public int pop(ISession session, int maxRecords) {
        return queue.pop(session, this, maxRecords);
    }

    @Override
    public boolean popRecord(ISession session, DataRow record, boolean isQueue) {
        String tableCode = record.getString("__table");
        int opera = record.getInt("__opera");
        int error = record.getInt("__error");
        record.remove("__table");
        record.remove("__opera");
        record.remove("__error");

        IPushProcesser processer = Application.getBean("sync_" + tableCode, IPushProcesser.class);
        if (processer == null)
            processer = new PushTableDefault().setTableCode(tableCode);
        processer.setSession(session);

        boolean result = false;
        try {
            switch (SyncOpera.values()[opera]) {
            case Append:
                result = processer.appendRecord(record);
                break;
            case Delete:
                result = processer.deleteRecord(record);
                break;
            case Update:
                result = processer.updateRecord(record);
                break;
            case Reset:
                result = processer.resetRecord(record);
                break;
            default:
                throw new RuntimeException("not support opera.");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        if (isQueue) // 如果是以MQ为引擎，则不需要进行异常处理，直接上MQ控制台查看异常
            return result;

        if (!result) {
            record.setValue("__table", tableCode);
            record.setValue("__opera", opera);
            record.setValue("__error", error + 1);
            if (error < 5) {
                queue.repush(session, record);
                log.warn("sync {}.{} fail, times {}, record {}", tableCode, opera, error, record);
            } else {
                processer.abortRecord(record, SyncOpera.values()[opera]);
            }
        }
        return result;
    }

    public ISyncServer getQueue() {
        return queue;
    }

}

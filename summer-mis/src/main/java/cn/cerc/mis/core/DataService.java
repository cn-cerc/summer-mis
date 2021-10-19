package cn.cerc.mis.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.cerc.core.DataSet;
import cn.cerc.db.core.IHandle;
import cn.cerc.mis.other.TimeOut;

public abstract class DataService implements IService {
    private static final Logger log = LoggerFactory.getLogger(CustomService.class);

    @Override
    public final DataSet execute(IHandle handle, DataSet dataIn) throws ServiceException {
        String funcCode = dataIn.getHead().getString("_function_");
        DataSet dataOut = new DataSet();

        if (funcCode == null)
            return dataOut.setMessage("haed[_function_] is null");

        Class<?> self = this.getClass();
        Method method = null;
        for (Method item : self.getMethods()) {
            if (item.getName().equals(funcCode)) {
                method = item;
                break;
            }
        }

        if (method == null) {
            dataOut.setMessage(String.format("not find service: %s.%s ！", this.getClass().getName(), funcCode));
            dataOut.setState(ServiceState.NOT_FIND_SERVICE);
            return dataOut;
        }
        // 执行具体的服务函数
        if (method.getParameterCount() != 2) {
            String str = String.format("service format error, ParameterCount: %s", method.getParameterCount());
            return dataOut.setMessage(str);
        }

        try {
            long startTime = System.currentTimeMillis();
            dataOut = (DataSet) method.invoke(this, handle, dataIn);
            // 防止调用者修改并回写到数据库
            dataOut.disableStorage();
            dataOut.first();
            long totalTime = System.currentTimeMillis() - startTime;
            if (totalTime > 1000) {
                TimeOut timeOut = new TimeOut(handle, dataIn, funcCode, totalTime);
                log.warn("{}, {}, {}, {}", timeOut.getCorpNo(), timeOut.getUserCode(), timeOut.getService(),
                        timeOut.getTimer());
            }

            return dataOut;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            Throwable err = e.getCause() != null ? e.getCause() : e;
            String msg = err.getMessage() == null ? "error is null" : err.getMessage();
            if ((err instanceof ServiceException)) {
                return new DataSet().setState(ServiceState.ERROR).setMessage(msg);
            } else {
                log.error(msg, err);
                return new DataSet().setState(ServiceState.ERROR).setMessage(msg);
            }
        }
    }

}

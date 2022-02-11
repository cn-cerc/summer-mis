package cn.cerc.mis.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import cn.cerc.db.core.ClassData;
import cn.cerc.db.core.DataRow;
import cn.cerc.db.core.DataSet;
import cn.cerc.db.core.IHandle;
import cn.cerc.mis.security.SecurityPolice;
import cn.cerc.mis.security.SecurityStopException;

public final class ServiceMethod {
    private final Method method;
    private final ServiceMethodVersion version;

    public enum ServiceMethodVersion {
        ResultBoolean, ResultStatus, ResultDataSet, ResultDataSetByHeadIn, ResultBooleanByHeadIn
    }

    public ServiceMethod(Method method, ServiceMethodVersion version) {
        this.method = method;
        this.version = version;
    }

    public Method method() {
        return method;
    }

    public ServiceMethodVersion version() {
        return version;
    }

    public DataSet call(Object owner, IHandle handle, DataSet dataIn)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, DataValidateException {
        // 调用数据校验
        DataValidate validate = method.getDeclaredAnnotation(DataValidate.class);
        if (validate != null) {
            DataRow headIn = dataIn.head();
            String errorMsg = validate.message();
            for (String fieldCode : validate.value()) {
                if (!headIn.has(fieldCode)) {
                    if (errorMsg.contains("%s"))
                        throw new DataValidateException(String.format(errorMsg, fieldCode));
                    else
                        throw new DataValidateException(errorMsg);
                }
            }
        }
        // 执行权限检查
        if (!SecurityPolice.check(handle, method, owner)) {
            return new DataSet().setMessage(SecurityStopException.getAccessDisabled())
                    .setState(ServiceState.ACCESS_DISABLED);
        }

        // 执行具体的服务函数
        DataSet dataOut;
        switch (this.version) {
        case ResultBoolean: {
            if (owner instanceof CustomService) {
                boolean result = (Boolean) method.invoke(owner);
                dataOut = ((CustomService) owner).dataOut();
                dataOut.setState(result ? ServiceState.OK : ServiceState.ERROR);
            } else {
                dataOut = new DataSet().setMessage("It not is CustomService");
            }
            break;
        }
        case ResultStatus: {
            dataOut = new DataSet();
            IStatus result = (IStatus) method.invoke(owner, dataIn, dataOut);
            if (dataOut.state() == ServiceState.ERROR)
                dataOut.setState(result.getState());
            if (dataOut.message() == null)
                dataOut.setMessage(result.getMessage());
            break;
        }
        case ResultDataSet: {
            dataOut = (DataSet) method.invoke(owner, handle, dataIn);
            break;
        }
        case ResultDataSetByHeadIn: {
            dataOut = (DataSet) method.invoke(owner, handle, dataIn.head());
            break;
        }
        case ResultBooleanByHeadIn: {
            boolean result = (Boolean) method.invoke(owner);
            dataOut = new DataSet().setState(result ? ServiceState.OK : ServiceState.ERROR);
        }
        default: {
            dataOut = new DataSet().setMessage("can't support " + this.version.name());
            break;
        }
        }

        // 防止调用者修改并回写到数据库
        dataOut.disableStorage().first();
        return dataOut;
    }

    public static ServiceMethod build(Class<?> clazz, String funcCode) {
        // 第1代版本：不支持单例
        try {
            Method method = clazz.getMethod(funcCode);
            if (method.getModifiers() != ClassData.PUBLIC)
                return null;
            if (method.getReturnType() != boolean.class)
                return null;
            else
                return new ServiceMethod(method, ServiceMethodVersion.ResultBoolean);
        } catch (NoSuchMethodException | SecurityException e1) {
        }
        // 第2代版本：不支持单例
        try {
            Method method = clazz.getMethod(funcCode, DataSet.class, DataSet.class);
            if (method.getModifiers() != ClassData.PUBLIC)
                return null;
            if (method.getReturnType() != IStatus.class)
                return null;
            else
                return new ServiceMethod(method, ServiceMethodVersion.ResultStatus);
        } catch (NoSuchMethodException | SecurityException e1) {

        }
        // 第3代版本：支持单例
        try {
            Method method = clazz.getMethod(funcCode, IHandle.class, DataSet.class);
            if (method.getModifiers() != ClassData.PUBLIC)
                return null;
            if (method.getReturnType() != DataSet.class)
                return null;
            else
                return new ServiceMethod(method, ServiceMethodVersion.ResultDataSet);
        } catch (NoSuchMethodException | SecurityException e1) {

        }
        // 第4代版本：支持单例
        try {
            Method method = clazz.getMethod(funcCode, IHandle.class, DataRow.class);
            if (method.getModifiers() != ClassData.PUBLIC)
                return null;
            if (method.getReturnType() == DataSet.class)
                return new ServiceMethod(method, ServiceMethodVersion.ResultDataSetByHeadIn);
            else if (method.getReturnType() == boolean.class)
                return new ServiceMethod(method, ServiceMethodVersion.ResultBooleanByHeadIn);
            else
                return null;
        } catch (NoSuchMethodException | SecurityException e1) {
        }
        // 没有找到指定的函数
        return null;
    }

}

package cn.cerc.mis.security;

import java.lang.reflect.Method;

import cn.cerc.db.core.ClassResource;
import cn.cerc.mis.SummerMIS;
import cn.cerc.mis.core.SupportBeanName;

public class SecurityStopException extends SecurityException {
    private static final long serialVersionUID = -970178466412571534L;
    private static final ClassResource res = new ClassResource(SecurityStopException.class, SummerMIS.ID);
    private final String message;

    public SecurityStopException(String message) {
        super(message);
        this.message = message;
    }

    public SecurityStopException(Class<?> clazz) {
        super(getAccessDisabled());

        String[] path = clazz.getName().split("\\.");
        String beanId = path[path.length - 1];
        this.message = String.format("[%s]", beanId) + getAccessDisabled();
    }

    public SecurityStopException(Method method) {
        this(method, null, "");
    }

    public SecurityStopException(Method method, Object bean, String value) {
        super(getAccessDisabled());

        String[] path = method.getDeclaringClass().getName().split("\\.");
        String beanId = path[path.length - 1];
        if (bean instanceof SupportBeanName)
            beanId = ((SupportBeanName) bean).getBeanName();

        this.message = String.format("%s [%s.%s] 您未授权此权限代码：%s", getAccessDisabled(), beanId, method.getName(),
                value);
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    public static String getAccessDisabled() {
        return res.getString(1, "您没有权限执行此操作，请与系统管理员联系");
    }

    public static String getPleaseLogin() {
        return res.getString(2, "请您先登入系统");
    }

}

package cn.cerc.mis.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Description;

import cn.cerc.db.core.DataRow;
import cn.cerc.db.core.DataSet;
import cn.cerc.db.core.EntityHelper;
import cn.cerc.db.core.EntityImpl;
import cn.cerc.db.core.EntityKey;
import cn.cerc.db.core.IHandle;
import cn.cerc.mis.core.BookHandle;
import cn.cerc.mis.core.DataValidate;
import cn.cerc.mis.core.IService;
import cn.cerc.mis.core.LocalService;
import cn.cerc.mis.core.ServiceMethod;
import cn.cerc.mis.core.ServiceState;

public final class ServiceSign extends ServiceProxy implements ServiceSignImpl, InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(ServiceSign.class);
    private final String id;
    private Set<String> properties;
    private ServerOptionImpl server;

    public ServiceSign(String id) {
        super();
        this.id = id;
    }

    public ServiceSign(String id, ServerOptionImpl server) {
        super();
        this.id = id;
        this.server = server;
    }

    public String id() {
        return id;
    }

    public ServerOptionImpl server() {
        return this.server;
    }

    public Set<String> properties() {
        return properties;
    }

    public ServiceSign setProperties(Set<String> properties) {
        this.properties = properties;
        return this;
    }

    @Override
    public ServiceSign sign() {
        return this;
    }

    @Deprecated
    public ServiceSign call(IHandle handle) {
        return call(handle, new DataSet());
    }

    @Deprecated
    public ServiceSign call(IHandle handle, DataRow headIn) {
        DataSet dataIn = new DataSet();
        dataIn.head().copyValues(headIn);
        return call(handle, dataIn);
    }

    @Deprecated
    public ServiceSign call(IHandle handle, DataSet dataIn) {
        this.setSession(handle.getSession());
        ServiceSign sign = this.clone();
        sign.setDataIn(dataIn);
        var dataOut = LocalService.call(this.id, handle, dataIn);
        sign.setDataOut(dataOut);
        return sign;
    }

    public ServiceSign callLocal(IHandle handle) {
        return callLocal(handle, new DataSet());
    }

    public ServiceSign callLocal(IHandle handle, DataRow headIn) {
        DataSet dataIn = new DataSet();
        dataIn.head().copyValues(headIn);
        return callLocal(handle, dataIn);
    }

    @Override
    public ServiceSign callLocal(IHandle handle, DataSet dataIn) {
        if (handle instanceof BookHandle) {
            RuntimeException exception = new RuntimeException(
                    String.format("BookHandle 不可以用于 callLocal 调用, service %s, dataIn %s", this.id(), dataIn.json()));
            log.warn(exception.getMessage(), exception);
        }

        this.setSession(handle.getSession());
        ServiceSign sign = this.clone();
        sign.setDataIn(dataIn);
        var dataOut = LocalService.call(this.id, handle, dataIn);
        sign.setDataOut(dataOut);
        return sign;
    }

    @Override
    protected ServiceSign clone() {
        ServiceSign sign = new ServiceSign(this.id, this.server);
        sign.setSession(this.getSession());
        sign.properties = this.properties;
        return sign;
    }

    public ServiceSign callRemote(CorpConfigImpl config) {
        return callRemote(config, new DataSet());
    }

    public ServiceSign callRemote(CorpConfigImpl config, DataRow headIn) {
        DataSet dataIn = new DataSet();
        dataIn.head().copyValues(headIn);
        return callRemote(config, dataIn);
    }

    @Override
    public ServiceSign callRemote(CorpConfigImpl corpConfig, DataSet dataIn) {
        Objects.requireNonNull(corpConfig);
        Objects.requireNonNull(corpConfig.getSession());
        this.setSession(corpConfig.getSession());
        // 返回一个新的sign
        ServiceSign sign = this.clone();
        sign.setDataIn(dataIn);
        DataSet dataOut = null;
        try {
            dataOut = RemoteService.call(this, corpConfig, id(), dataIn, sign.server());
        } catch (Throwable e) {
            String message = String.format("代理类型 %s, 远程服务 %s, 目标帐套 %s, 服务入参 %s -> 异常消息 %s", corpConfig.getClass(), id(),
                    corpConfig.getCorpNo(), dataIn.json(), e.getMessage());
            log.error(message, e);
            dataOut = new DataSet().setError().setMessage(e.getMessage());
        }
        sign.setDataOut(dataOut);
        return sign;
    }

    public ServiceSign sign(IHandle handle) {
        return sign(handle, new DataSet());
    }

    public ServiceSign sign(IHandle handle, DataSet dataIn) {
        ServiceSign sign = this.clone();
        sign.setSession(handle.getSession());
        sign.setDataIn(dataIn);
        return sign;
    }

    public String getExportKey() {
        return ServiceExport.build(this, this.dataIn());
    }

    /**
     * 生成指定服务类的签名定义
     */
    public static void buildSourceCode(Class<?> clazz) {
        if (!IService.class.isAssignableFrom(clazz)) {
            System.out.println(String.format("// %s skip: it's not service", clazz.getSimpleName()));
            return;
        }
        Description description = clazz.getDeclaredAnnotation(Description.class);
        if (description != null)
            System.out.println(String.format("/** %s */", description.value()));
        System.out.println(String.format("public static class %s {", clazz.getSimpleName()));

        List<ServiceMethod> items = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            ServiceMethod sm = ServiceMethod.build(clazz, method.getName());
            if (sm != null)
                items.add(sm);
        }
        items.sort(Comparator.comparing(t -> t.method().getName().toLowerCase()));
        for (ServiceMethod svc : items) {
            description = svc.method().getDeclaredAnnotation(Description.class);
            if (description != null)
                System.out.println(String.format("/** %s */", description.value()));

            // 检查是否有重复校验的字段
            String function = svc.method().getName();
            DataValidate[] dataValidates = svc.method().getDeclaredAnnotationsByType(DataValidate.class);
            List<String> duplicates = Arrays.stream(dataValidates)
                    .collect(Collectors.groupingBy(e -> e.value(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            if (duplicates.size() > 0)
                throw new RuntimeException(String.format("服务对象 %s 重复定义元素 %s", function, String.join(", ", duplicates)));

            if (dataValidates.length > 0) {
                StringBuilder builder = new StringBuilder();
                for (DataValidate dataValidate : dataValidates)
                    builder.append("\"").append(dataValidate.value()).append("\",");
                builder.delete(builder.length() - 1, builder.length());

                if (svc.version().ordinal() > 0)
                    System.out.println(String.format(
                            "public static final ServiceSign %s = new ServiceSign(\"%s.%s\").setProperties(Set.of(%s));",
                            function, clazz.getSimpleName(), function, builder.toString()));
                else
                    System.out.println(String.format(
                            "public static final ServiceSign %s = new ServiceSign(\"%s.%s\").setProperties(Set.of(%s));",
                            function, clazz.getSimpleName(), function, builder.toString()));
            } else {
                if (svc.version().ordinal() > 0)
                    System.out.println(String.format("public static final ServiceSign %s = new ServiceSign(\"%s.%s\");",
                            function, clazz.getSimpleName(), function));
                else
                    System.out.println(String.format("public static final ServiceSign %s = new ServiceSign(\"%s.%s\");",
                            function, clazz.getSimpleName(), function));
            }
        }
        System.out.println("}");
    }

    /**
     * 业务对象建议使用 asRecord
     * <p>
     * 服务返回结果转换为指定的业务对象
     *
     * @param <T>    业务对象实体类
     * @param handle 句柄
     * @param clazz  业务对象实体类class
     * @param values 对应实体类的缓存key
     * @return 指定的实体对象
     */
    @Deprecated
    public <T extends EntityImpl> Optional<T> findOne(IHandle handle, Class<T> clazz, String... values) {
        EntityKey entityKey = EntityHelper.get(clazz).entityKey();
        DataSet dataIn = new DataSet();
        DataRow headIn = dataIn.head();
        int site = entityKey.corpNo() ? 1 : 0;
        String[] fields = entityKey.fields();
        for (int i = site; i < fields.length; i++)
            headIn.setValue(fields[i], values[i - site]);
        DataSet dataOut = this.callLocal(handle, dataIn).dataOut();
        if (dataOut.state() == ServiceState.OK)
            return Optional.of(dataOut.current().asEntity(clazz));
        return Optional.empty();
    }

    /**
     * 业务对象建议使用 asRecord
     */
    @Deprecated
    public <T extends EntityImpl> Set<T> findMany(IHandle handle, Class<T> clazz, String... values) {
        Set<T> set = new LinkedHashSet<>();
        EntityKey entityKey = EntityHelper.get(clazz).entityKey();
        DataSet dataIn = new DataSet();
        DataRow headIn = dataIn.head();
        int site = entityKey.corpNo() ? 1 : 0;
        String[] fields = entityKey.fields();
        if (values != null && values.length > 0) {
            for (int i = site; i < fields.length; i++)
                headIn.setValue(fields[i], values[i - site]);
        }
        DataSet dataOut = this.callLocal(handle, dataIn).dataOut();
        if (dataOut.state() != ServiceState.OK)
            return set;

        dataOut.records().stream().map(item -> item.asEntity(clazz)).forEach(set::add);
        return set;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("sign"))
            return this.sign();
        else if (method.getName().equals("call"))
            return method.invoke(this, args);
        else
            throw new RuntimeException("not support method: " + method.getName());
    }

}

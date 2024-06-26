package cn.cerc.mis.queue;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cn.cerc.db.core.ClassResource;
import cn.cerc.db.core.DataRow;
import cn.cerc.db.core.IHandle;
import cn.cerc.mis.SummerMIS;
import cn.cerc.mis.client.ServiceProxy;
import cn.cerc.mis.client.ServiceSign;
import cn.cerc.mis.message.MessageLevel;
import cn.cerc.mis.message.MessageProcess;
import cn.cerc.mis.message.MessageRecord;

public class AsyncService extends ServiceProxy {
    public static final String _message_ = "_message_";
    private static final Logger log = LoggerFactory.getLogger(AsyncService.class);
    private static final ClassResource res = new ClassResource(AsyncService.class, SummerMIS.ID);
    private ServiceSign sign;

    // 状态列表
    private static final List<String> processTiles = new ArrayList<>();

    static {
        processTiles.add(res.getString(1, "中止执行"));
        processTiles.add(res.getString(2, "排队中"));
        processTiles.add(res.getString(3, "正在执行中"));
        processTiles.add(res.getString(4, "执行成功"));
        processTiles.add(res.getString(5, "执行失败"));
        processTiles.add(res.getString(6, "下载完成"));
    }

    private String corpNo;
    private String userCode;
    private String token;

    // 预约时间，若为空则表示立即执行
    private String timer;
    // 执行进度
    private MessageProcess process = MessageProcess.wait;
    // 处理时间
    private String processTime;
    //
    private MessageLevel messageLevel = MessageLevel.Service;
    //
    private String msgId;

    public AsyncService(IHandle handle) {
        super();
        this.setSession(handle.getSession());
        if (handle != null) {
            this.setCorpNo(handle.getCorpNo());
            this.setUserCode(handle.getUserCode());
        }
    }

    public AsyncService(IHandle handle, ServiceSign service) {
        this(handle);
        this.setService(service);
    }

    public AsyncService(IHandle handle, String service) {
        this(handle);
        this.setService(service);
    }

    public static String getProcessTitle(int process) {
        return processTiles.get(process);
    }

    public AsyncService read(String jsonString) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(jsonString);
        this.setService(json.get("service").asText());
        if (json.has("dataOut"))
            this.dataOut().setJson(json.get("dataOut").asText());

        if (json.has("dataIn"))
            this.dataIn().setJson(json.get("dataIn").asText());

        if (json.has("process"))
            this.setProcess(MessageProcess.values()[json.get("process").asInt()]);

        if (json.has("timer"))
            this.setTimer(json.get("timer").asText());

        if (json.has("processTime"))
            this.setProcessTime(json.get("processTime").asText());

        if (json.has("token"))
            this.setToken(json.get("token").asText());
        return this;
    }

    public boolean exec(Object... args) {
        DataRow headIn = dataIn().head();
        if (args.length > 0) {
            if (args.length % 2 != 0) {
                throw new RuntimeException(res.getString(7, "传入的参数数量必须为偶数！"));
            }
            for (int i = 0; i < args.length; i = i + 2) {
                headIn.setValue(args[i].toString(), args[i + 1]);
            }
        }
        headIn.setValue("token", this.getSession().getToken());
        this.setToken(this.getSession().getToken());

        String subject = this.getSubject();
        if ("".equals(subject))
            throw new RuntimeException(res.getString(8, "后台任务标题不允许为空！"));

        if (subject == null || "".equals(subject))
            throw new RuntimeException("subject is null");

        MessageRecord msg = new MessageRecord();
        msg.setCorpNo(this.getCorpNo());
        msg.setUserCode(this.getUserCode());
        msg.setLevel(this.messageLevel);
        msg.setContent(this.toJson());
        msg.setSubject(subject);
        msg.setUiClass(MessageRecord.UIClass_Task);
        msg.setProcess(this.process);
        log.debug(this.getCorpNo() + ":" + this.getUserCode() + ":" + this);
        this.msgId = msg.send(this);

        dataOut().head().setValue("_msgId_", msgId);
        return !"".equals(msgId);
    }

    @Override
    public String toString() {
        return this.toJson();
    }

    private String toJson() {
        ObjectNode content = new ObjectMapper().createObjectNode();
        content.put("service", this.sign.id());
        if (this.dataIn() != null) {
            content.put("dataIn", dataIn().json());
        }
        if (this.dataOut() != null) {
            content.put("dataOut", dataOut().json());
        }
        content.put("timer", this.timer);
        content.put("token", this.token);
        content.put("process", this.process.ordinal());
        if (this.processTime != null) {
            content.put("processTime", this.processTime);
        }
        return content.toString();
    }

    @Deprecated
    public String getService() {
        return sign.id();
    }

    public AsyncService setService(ServiceSign service) {
        this.sign = service;
        return this;
    }

    @Deprecated
    public AsyncService setService(String service) {
        this.setSign(new ServiceSign(service));
        return this;
    }

    public MessageProcess getProcess() {
        return process;
    }

    public void setProcess(MessageProcess process) {
        this.process = process;
    }

    public String getTimer() {
        return timer;
    }

    public void setTimer(String timer) {
        this.timer = timer;
    }

    public String getProcessTime() {
        return processTime;
    }

    public void setProcessTime(String processTime) {
        this.processTime = processTime;
    }

    @Override
    public String getCorpNo() {
        return corpNo;
    }

    public void setCorpNo(String corpNo) {
        this.corpNo = corpNo;
    }

    @Override
    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    @Override
    public String message() {
        if (super.dataOut() == null)
            return null;
        if (!super.dataOut().head().exists(_message_))
            return null;
        return super.dataOut().head().getString(_message_);
    }

    public MessageLevel getMessageLevel() {
        return messageLevel;
    }

    public void setMessageLevel(MessageLevel messageLevel) {
        this.messageLevel = messageLevel;
    }

    public String getSubject() {
        return dataIn().head().getString("_subject_");
    }

    public void setSubject(String subject) {
        dataIn().head().setValue("_subject_", subject);
    }

    public void setSubject(String format, Object... args) {
        dataIn().head().setValue("_subject_", String.format(format, args));
    }

    public String getMsgId() {
        return msgId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Deprecated
    public String getMessage() {
        return this.message();
    }

    public ServiceSign getSign() {
        return this.sign;
    }

    public void setSign(ServiceSign sign) {
        this.sign = sign;
    }
}

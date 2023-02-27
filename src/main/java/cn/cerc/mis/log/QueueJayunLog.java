package cn.cerc.mis.log;

import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import cn.cerc.db.core.Curl;
import cn.cerc.db.core.ServerConfig;
import cn.cerc.db.core.Utils;
import cn.cerc.db.queue.AbstractQueue;
import cn.cerc.db.queue.QueueServiceEnum;
import cn.cerc.db.zk.ZkNode;

@Component
public class QueueJayunLog extends AbstractQueue {

    public static final String prefix = "/jayun-test";

    public QueueJayunLog() {
        super();
        this.setService(QueueServiceEnum.Redis);
    }

    public String push(JayunLogData logData) {
        return super.push(new Gson().toJson(logData));
    }

    @Override
    public boolean consume(String message, boolean repushOnError) {
        // 本地开发不发送日志到测试平台
        if (ServerConfig.isServerDevelop())
            return true;
        String site = ZkNode.get().getNodeValue(key("log"), () -> "");
        if (Utils.isEmpty(site))
            return true;
        JayunLogData data = new Gson().fromJson(message, JayunLogData.class);
        String token = ZkNode.get()
                .getNodeValue(key(String.format("%s/log/%s", data.getProject(), data.getLevel())), () -> "");
        if (Utils.isEmpty(token))
            return true;
        data.setToken(token);
        message = new Gson().toJson(data);
        try {
            Curl curl = new Curl();
            curl.doPost(site, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public static String key(String key) {
        return String.format("%s/%s", prefix, key);
    }

}

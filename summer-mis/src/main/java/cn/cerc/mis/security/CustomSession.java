package cn.cerc.mis.security;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.cerc.core.ISession;
import cn.cerc.core.LanguageResource;
import cn.cerc.db.jiguang.JiguangConnection;
import cn.cerc.db.mongo.MongoDB;
import cn.cerc.db.mssql.MssqlServer;
import cn.cerc.db.mysql.MysqlServerMaster;
import cn.cerc.db.mysql.MysqlServerSlave;
import cn.cerc.db.oss.OssConnection;
import cn.cerc.db.queue.QueueServer;
import cn.cerc.db.redis.JedisFactory;
import cn.cerc.mis.core.Application;
import cn.cerc.mis.core.SystemBuffer;
import cn.cerc.mis.other.MemoryBuffer;
import redis.clients.jedis.Jedis;

//@Scope(WebApplicationContext.SCOPE_REQUEST)
//@Scope(WebApplicationContext.SCOPE_SESSION)
//@Component
//@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
//@Scope(WebApplicationContext.SCOPE_REQUEST)
public class CustomSession implements ISession {
    private static final Logger log = LoggerFactory.getLogger(CustomSession.class);
    protected Map<String, Object> connections = new HashMap<>();
    private Map<String, Object> params = new HashMap<>();
    protected String permissions = null;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private static int currentSize = 0;
    private boolean active = true;

    public CustomSession() {
        super();
        params.put(ISession.CORP_NO, "");
        params.put(ISession.USER_CODE, "");
        params.put(ISession.USER_NAME, "");
        params.put(Application.ClientIP, "0.0.0.0");
        params.put(ISession.LANGUAGE_ID, LanguageResource.appLanguage);

        params.put(Application.SessionId, "");
        params.put(Application.ProxyUsers, "");

        if (log.isDebugEnabled()) {
            synchronized (this.getClass()) {
                ++currentSize;
                log.debug("current size: {}", currentSize);
            }
        }
    }

    @Override
    public final void setProperty(String key, Object value) {
        if (ISession.TOKEN.equals(key)) {
            if ("{}".equals(value)) {
                params.put(key, null);
            } else {
                if (value == null || "".equals(value))
                    params.clear();
                else {
                    params.put(key, value);
                }
            }
            return;
        }
        params.put(key, value);
    }

    @Override
    public final Object getProperty(String key) {
        if (key == null)
            return this;

        Object result = null;
        if (params.containsKey(key)) {
            result = params.get(key);
            if (result != null)
                return result;
        }

        if (connections.containsKey(key)) {
            result = connections.get(key);
            if (result != null)
                return result;
        }

        if (MysqlServerMaster.SessionId.equals(key)) {
            MysqlServerMaster obj = new MysqlServerMaster();
            connections.put(MysqlServerMaster.SessionId, obj);
            return connections.get(key);
        }

        if (MysqlServerSlave.SessionId.equals(key)) {
            MysqlServerSlave obj = new MysqlServerSlave();
            connections.put(MysqlServerSlave.SessionId, obj);
            return connections.get(key);
        }

        if (MssqlServer.SessionId.equals(key)) {
            MysqlServerMaster obj = new MysqlServerMaster();
            connections.put(MysqlServerMaster.SessionId, obj);
            return connections.get(key);
        }

        if (OssConnection.sessionId.equals(key)) {
            OssConnection obj = new OssConnection();
            connections.put(OssConnection.sessionId, obj);
            return connections.get(key);
        }

        if (QueueServer.SessionId.equals(key)) {
            QueueServer obj = new QueueServer();
            connections.put(QueueServer.SessionId, obj);
            return connections.get(key);
        }

        if (MongoDB.SessionId.equals(key)) {
            MongoDB obj = new MongoDB();
            connections.put(MongoDB.SessionId, obj);
            return connections.get(key);
        }

        if (JiguangConnection.sessionId.equals(key)) {
            JiguangConnection obj = new JiguangConnection();
            connections.put(JiguangConnection.sessionId, obj);
            return connections.get(key);
        }

        return null;
    }

    @Override
    public void close() {
        for (String key : this.connections.keySet()) {
            Object sess = this.connections.get(key);
            try {
                if (sess instanceof AutoCloseable) {
                    ((AutoCloseable) sess).close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        connections.clear();
        if (log.isDebugEnabled()) {
            if (this.active) {
                this.active = false;
                synchronized (this.getClass()) {
                    --currentSize;
                    log.debug("current size: {}", currentSize);
                }
            } else {
                log.warn("重复执行 session.close");
            }
        }
    }

    @Override
    public String getCorpNo() {
        return (String) this.getProperty(ISession.CORP_NO);
    }

    @Override
    public String getUserCode() {
        return (String) this.getProperty(ISession.USER_CODE);
    }

    @Override
    public final String getUserName() {
        return (String) this.getProperty(ISession.USER_NAME);
    }

    @Override
    public boolean logon() {
        return this.getProperty(ISession.TOKEN) != null;
    }

    @Override
    public void loadToken(String token) {
        SecurityService ws = Application.getBean(SecurityService.class);
        if (ws != null && ws.initSession(this, token)) {
            String key = MemoryBuffer.buildKey(SystemBuffer.UserObject.Permissions, token);
            try (Jedis jedis = JedisFactory.getJedis()) {
                String value = jedis.get(key);
                if (value == null) {
                    value = ws.getPermissions(this);
                    jedis.set(key, value);
                    jedis.expire(key, 3600);
                }
                this.permissions = value;
            }
        }
        log.debug("{}.{}[permissions]", this.getCorpNo(), this.getUserCode(), this.permissions);
    }

    @Override
    public final String getPermissions() {
        return this.permissions;
    }

    @Override
    public HttpServletRequest getRequest() {
        return this.request;
    }

    @Override
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public HttpServletResponse getResponse() {
        return this.response;
    }

    @Override
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

}

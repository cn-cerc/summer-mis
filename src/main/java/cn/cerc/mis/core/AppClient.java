package cn.cerc.mis.core;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;

import cn.cerc.db.core.ISession;
import cn.cerc.db.core.LanguageResource;
import cn.cerc.db.core.Utils;
import cn.cerc.db.redis.JedisFactory;
import cn.cerc.db.redis.RedisRecord;
import cn.cerc.mis.other.MemoryBuffer;
import redis.clients.jedis.Jedis;

@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
//@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AppClient implements Serializable {
//    private static final Logger log = LoggerFactory.getLogger(AppClient.class);

    private static final long serialVersionUID = -3593077761901636920L;

    // 缓存版本
    public static final int Version = 1;
    public static final String COOKIE_ROOT_PATH = "/";

    // 手机
    public static final String phone = "phone";
    public static final String android = "android";
    public static final String iphone = "iphone";
    public static final String wechat = "weixin";
    // 平板
    public static final String pad = "pad";
    // 电脑
    public static final String pc = "pc";
    // 看板
    public static final String kanban = "kanban";
    // 客户端专用浏览器
    public static final String ee = "ee";

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    private String cookieId = "";

    private final String key;

    private String token;

    private String device;

    private String deviceId;

    private String language;

    public AppClient(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;

        Cookie[] cookies = this.request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals(ISession.COOKIE_ID)) {
                    this.cookieId = cookie.getValue();
                    break;
                }
            }
        }

        if (Utils.isEmpty(this.cookieId)) {
            this.cookieId = Utils.getGuid();
            if (response != null) {
                Cookie cookie = new Cookie(ISession.COOKIE_ID, cookieId);
                cookie.setPath(COOKIE_ROOT_PATH);
                cookie.setHttpOnly(true);
                this.response.addCookie(cookie);
            }
        }

        this.key = MemoryBuffer.buildObjectKey(AppClient.class, this.cookieId, AppClient.Version);

        try (Jedis redis = JedisFactory.getJedis()) {
            this.device = request.getParameter(ISession.CLIENT_DEVICE);
            if (!Utils.isEmpty(device))
                redis.hset(key, ISession.CLIENT_DEVICE, device);
            else {
                this.device = redis.hget(key, ISession.CLIENT_DEVICE);
                if (Utils.isEmpty(device)) {
                    device = pc;
                    redis.hset(key, ISession.CLIENT_DEVICE, device);
                }
            }

            this.deviceId = request.getParameter(ISession.CLIENT_ID);
            if (!Utils.isEmpty(deviceId))
                redis.hset(key, ISession.CLIENT_ID, deviceId);
            else {
                this.deviceId = redis.hget(key, ISession.CLIENT_ID);

                if (Utils.isEmpty(deviceId)) {
                    if (cookies != null) {
                        for (Cookie cookie : request.getCookies()) {
                            if (cookie.getName().equals(ISession.CLIENT_ID)) {
                                this.deviceId = cookie.getValue();
                                break;
                            }
                        }
                    }
                }

//                if (Utils.isEmpty(deviceId)) {
//                    deviceId = Utils.getGuid();
//                    redis.hset(key, ISession.CLIENT_ID, deviceId);
//                    if (response != null) {
//                        Cookie cookie = new Cookie(ISession.CLIENT_ID, deviceId);
//                        cookie.setPath(COOKIE_ROOT_PATH);
//                        cookie.setHttpOnly(true);
//                        this.response.addCookie(cookie);
//                    }
//                }
            }

            this.language = request.getParameter(ISession.LANGUAGE_ID);
            if (!Utils.isEmpty(language))
                redis.hset(key, ISession.LANGUAGE_ID, language);
            else {
                this.language = redis.hget(key, ISession.LANGUAGE_ID);
                if (Utils.isEmpty(language)) {
                    language = LanguageResource.appLanguage;
                    redis.hset(key, ISession.LANGUAGE_ID, language);
                }
            }

            this.token = request.getParameter(ISession.TOKEN);
            if (!Utils.isEmpty(token))
                redis.hset(key, ISession.TOKEN, token);
            else
                this.token = redis.hget(key, ISession.TOKEN);

            redis.expire(key, RedisRecord.TIMEOUT);// 每次取值延长生命值
        }
    }

    /**
     * 读取 cookie 中的 id
     */
    public String getCookieId() {
        return this.cookieId;
    }

    public String getToken() {
        return token;
    }

    public void delete(String field) {
        try (Jedis redis = JedisFactory.getJedis()) {
            redis.hdel(key, field);
        }
    }

    public String getId() {
        return this.deviceId;
    }

    public void setId(String value) {
        this.deviceId = value == null ? "" : value;
        request.setAttribute(ISession.CLIENT_ID, deviceId);
        try (Jedis redis = JedisFactory.getJedis()) {
            redis.hset(key, ISession.CLIENT_ID, deviceId);
        }
        if (value != null && value.length() == 28)// 微信openid的长度
            setDevice(phone);
    }

    /**
     * 设备类型默认是 pc
     */
    public String getDevice() {
        return Utils.isEmpty(device) ? pc : device;
    }

    public void setDevice(String value) {
        this.device = Utils.isEmpty(value) ? pc : value;
        request.setAttribute(ISession.CLIENT_DEVICE, device);

        try (Jedis redis = JedisFactory.getJedis()) {
            redis.hset(key, ISession.CLIENT_DEVICE, device);
        }
    }

    public String getLanguage() {
        return this.language;
    }

    public boolean isPhone() {
        return phone.equals(getDevice()) || android.equals(getDevice()) || iphone.equals(getDevice())
                || wechat.equals(getDevice());
    }

    public boolean isKanban() {
        return kanban.equals(getDevice());
    }

    /**
     * 获取客户端真实IP地址，不直接使用request.getRemoteAddr() 的原因是有可能用户使用了代理软件方式避免真实IP地址
     * <p>
     * x-forwarded-for 是一串IP值，取第一个非unknown的有效IP字符串为客户端的真实IP
     * <p>
     * 如：x-forwarded-for：192.168.1.110, 192.168.1.120, 192.168.1.130, 192.168.1.100
     * <p>
     * 用户真实IP为： 192.168.1.110
     *
     * @param request HttpServletRequest
     * 
     * @return IP地址
     */
    public static String getClientIP(HttpServletRequest request) {
        if (request == null)
            return "";
        try {
            String ip = request.getHeader("x-forwarded-for");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip))
                ip = request.getHeader("Proxy-Client-IP");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip))
                ip = request.getHeader("WL-Proxy-Client-IP");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip))
                ip = request.getHeader("HTTP_CLIENT_IP");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip))
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip))
                ip = request.getRemoteAddr();
            if ("0:0:0:0:0:0:0:1".equals(ip))
                ip = "0.0.0.0";
            // 以第一个IP地址为用户的真实地址
            String[] arr = ip.split(",");
            ip = Arrays.stream(arr).findFirst().orElse("").trim();
            return ip;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String toString() {
        Map<String, String> items;
        try (Jedis redis = JedisFactory.getJedis()) {
            items = redis.hgetAll(key);
        }
        return new Gson().toJson(items);
    }

}

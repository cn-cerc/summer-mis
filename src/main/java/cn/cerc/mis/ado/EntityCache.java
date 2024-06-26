package cn.cerc.mis.ado;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.cerc.db.core.CacheLevelEnum;
import cn.cerc.db.core.DataRow;
import cn.cerc.db.core.DataSet;
import cn.cerc.db.core.EntityHelper;
import cn.cerc.db.core.EntityImpl;
import cn.cerc.db.core.EntityKey;
import cn.cerc.db.core.FieldDefs;
import cn.cerc.db.core.IHandle;
import cn.cerc.db.core.ISession;
import cn.cerc.db.core.SqlWhere;
import cn.cerc.db.redis.JedisFactory;
import cn.cerc.mis.core.SystemBuffer;
import redis.clients.jedis.Jedis;

public class EntityCache<T extends EntityImpl> implements IHandle {
    private static final Logger log = LoggerFactory.getLogger(EntityCache.class);
    private static Predicate<Object> IsEmptyArrayString = text -> (text instanceof String)
            && ((String) text).length() == 0;
    public static final int MaxRecord = 2000;
    private ISession session;
    private Class<T> clazz;
    private EntityKey entityKey;

    public EntityCache(IHandle handle, Class<T> clazz) {
        super();
        if (handle != null)
            this.session = handle.getSession();
        this.entityKey = EntityHelper.get(clazz).entityKey();
        if (this.entityKey == null)
            throw new RuntimeException("entityKey not define: " + clazz.getSimpleName());
        this.clazz = clazz;
    }

    // 请改使用EntityFactory.findOneBatch
    @Deprecated
    public Optional<T> locate(Map<String, Optional<T>> buffer, String... values) {
        StringBuffer sb = new StringBuffer();
        for (Object value : values)
            sb.append(value);
        String key = sb.toString();
        Optional<T> result = buffer.get(key);
        if (result == null) {
            result = get(values);
            buffer.put(key, result);
        }
        return result;
    }

    /**
     * @param values EntityCache.values 标识字段的值
     * @return 从Session缓存读取，若没有开通，则从Redis读取
     */
    public Optional<T> get(String... values) {
        if (Stream.of(values).allMatch(IsEmptyArrayString))
            return Optional.empty();

        log.debug("getSession: {}.{}", clazz.getSimpleName(), String.join(".", values));
        if (entityKey.cache() == CacheLevelEnum.Disabled)
            return getStorage(values);
        if (entityKey.cache() == CacheLevelEnum.RedisAndSession) {
            String[] keys = this.buildKeys(values);
            DataRow row = SessionCache.get(keys);
            if (row != null && row.size() > 0) {
                try {
                    return Optional.of(row.asEntity(clazz));
                } catch (Exception e) {
                    log.error("asEntity {}, json {}, error {}", clazz.getSimpleName(), row.json(), e.getMessage(), e);
                    SessionCache.del(keys);
                }
            }
        }
        return getRedis(values);
    }

    /**
     * @param values EntityCache.values 标识字段的值
     * @return 从Redis读取，若没有找到，则从数据库读取
     */
    public Optional<T> getRedis(String... values) {
        if (entityKey.cache() != CacheLevelEnum.Disabled) {
            log.debug("getRedis: {}.{}", clazz.getSimpleName(), String.join(".", values));
            String[] keys = this.buildKeys(values);
            try (Jedis jedis = JedisFactory.getJedis()) {
                String json = jedis.get(EntityCache.buildKey(keys));
                if ("".equals(json) || "{}".equals(json))
                    return Optional.empty();
                else if (json != null) {
                    try {
                        DataRow row = new DataRow().setJson(json);
                        if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
                            SessionCache.set(keys, row);
                        return Optional.of(row.asEntity(clazz));
                    } catch (Exception e) {
                        log.error("asEntity {}, json {}, error: {}", clazz.getSimpleName(), json, e.getMessage(), e);
                        jedis.del(EntityCache.buildKey(keys));
                        if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
                            SessionCache.del(keys);
                    }
                }
            }
        }
        return getStorage(values);
    }

    /**
     * @param values EntityCache.values 标识字段的值
     * @return 强制从database中读取，并刷新session缓存与redis缓存
     */
    public Optional<T> getStorage(String... values) {
        log.debug("getStorage: {}.{}", clazz.getSimpleName(), String.join(".", values));
        T entity = null;
        if (entityKey.virtual()) {
            entity = getVirtualEntity(values);
        } else {
            if (values.length == 0)
                throw new RuntimeException("The param values cat not be empty.");
            EntityMany<T> query = new EntityMany<T>(this, clazz, SqlWhere.create(this, clazz, values).build(), true,
                    true);
            if (query.size() > 1)
                throw new RuntimeException("There are too many records.");
            if (query.size() > 0)
                entity = query.get(0);
        }
        if (entity == null && entityKey.cache() != CacheLevelEnum.Disabled) {
            String[] keys = this.buildKeys(values);
            try (Jedis jedis = JedisFactory.getJedis()) {
                jedis.setex(buildKey(keys), entityKey.expire(), "");
            }
            if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
                SessionCache.set(keys, new DataRow());
        }
        return Optional.ofNullable(entity);
    }

    protected T getVirtualEntity(String... values) {
        int diff = entityKey.version() == 0 ? 1 : 2;
        String[] keys = this.buildKeys(values);
        // 尝试直接对entity进行填充
        DataRow headIn = new DataRow(new FieldDefs(clazz));
        for (int i = 0; i < keys.length - diff; i++)
            headIn.setValue(entityKey.fields()[i], keys[i + diff]);
        //
        T obj = headIn.asEntity(clazz);
        if (!(obj instanceof IVirtualEntity)) {
            log.error("{} 没有实现 IVirtualEntity", clazz.getSimpleName());
            return null;
        }
        @SuppressWarnings("unchecked")
        IVirtualEntity<T> impl = (IVirtualEntity<T>) obj;
        if (impl.fillItem(this, obj, headIn)) {
            DataRow row = new DataRow();
            row.loadFromEntity(obj);
            try (Jedis jedis = JedisFactory.getJedis()) {
                jedis.setex(buildKey(keys), entityKey.expire(), row.json());
            }
            if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
                SessionCache.set(keys, row);
            return obj;
        }

        // 载入全部的Entity
        DataSet query = impl.loadItems(this, headIn);
        if (query == null || query.size() == 0)
            return null;

        // 存入缓存
        if (entityKey.cache() != CacheLevelEnum.Disabled) {
            try (Jedis jedis = JedisFactory.getJedis()) {
                for (DataRow row : query) {
                    String[] rowKeys = buildKeys(row);
                    jedis.setex(buildKey(rowKeys), entityKey.expire(), row.json());
                    if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
                        SessionCache.set(rowKeys, row);
                }
            }
        }

        // 查找返回值中是否有符合的entity
        for (DataRow row : query) {
            boolean exists = true;
            for (int i = 0; i < keys.length - diff; i++) {
                Object value = keys[i + diff];
                if (!row.getValue(entityKey.fields()[i]).equals(value))
                    exists = false;
            }
            if (exists)
                return row.asEntity(clazz);
        }
        return null;
    }

    public void del(String... values) {
        if (entityKey.cache() == CacheLevelEnum.Disabled)
            return;
        String[] keys = this.buildKeys(values);
        try (Jedis jedis = JedisFactory.getJedis()) {
            jedis.del(buildKey(keys));
        }
        if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
            SessionCache.del(keys);
    }

    /**
     * @return 返回已缓存的key*列表，如果列表数量为0，则返回null
     */
    public Set<String> listKeys() {
        if (entityKey.cache() == CacheLevelEnum.Disabled)
            return null;

        int offset = 1;
        if (entityKey.version() > 0)
            offset++;
        if (entityKey.corpNo())
            offset++;

        String[] keys = new String[offset + 1];
        keys[0] = clazz.getSimpleName();
        if (entityKey.version() > 0)
            keys[1] = "" + entityKey.version();
        if (entityKey.corpNo())
            keys[offset - 1] = this.getCorpNo();
        keys[offset] = "*";

        try (Jedis jedis = JedisFactory.getJedis()) {
            Set<String> items = jedis.keys(EntityCache.buildKey(keys));
            return items.size() > 0 ? items : null;
        }
    }

    public static String buildKey(String... keys) {
        int flag = SystemBuffer.Entity.Cache.getStartingPoint() + SystemBuffer.Entity.Cache.ordinal();
        return flag + "." + String.join(".", keys);
    }

    public String[] buildKeys(String... values) {
        if ((values.length + (entityKey.corpNo() ? 1 : 0)) != entityKey.fields().length)
            throw new RuntimeException("params size is not match");

        int offset = 1;
        if (entityKey.version() > 0)
            offset++;
        if (entityKey.corpNo())
            offset++;

        String[] keys = new String[offset + values.length];
        keys[0] = clazz.getSimpleName();
        if (entityKey.version() > 0)
            keys[1] = "" + entityKey.version();
        if (entityKey.corpNo())
            keys[offset - 1] = this.getCorpNo();
        for (int i = 0; i < values.length; i++)
            keys[offset + i] = values[i];
        return keys;
    }

    public String[] buildKeys(DataRow row) {
        int offset = 1;
        if (entityKey.version() > 0)
            offset++;

        String[] keys = new String[offset + entityKey.fields().length];
        keys[0] = clazz.getSimpleName();
        if (entityKey.version() > 0)
            keys[1] = "" + entityKey.version();

        for (int i = 0; i < entityKey.fields().length; i++)
            keys[offset + i] = row.getString(entityKey.fields()[i]);
        return keys;
    }

    @Deprecated
    public static interface VirtualEntityImpl<T> extends IVirtualEntity<T> {

    }

    @Override
    public ISession getSession() {
        return session;
    }

    @Override
    public void setSession(ISession session) {
        this.session = session;
    }

}

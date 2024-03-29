package cn.cerc.mis.ado;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import cn.cerc.db.core.CacheLevelEnum;
import cn.cerc.db.core.DataRow;
import cn.cerc.db.core.EntityHelper;
import cn.cerc.db.core.EntityHomeImpl;
import cn.cerc.db.core.EntityImpl;
import cn.cerc.db.core.EntityKey;
import cn.cerc.db.core.Handle;
import cn.cerc.db.core.IHandle;
import cn.cerc.db.core.ISqlDatabase;
import cn.cerc.db.core.SqlQuery;
import cn.cerc.db.core.SqlServer;
import cn.cerc.db.core.SqlServerType;
import cn.cerc.db.core.SqlServerTypeException;
import cn.cerc.db.core.SqlText;
import cn.cerc.db.mssql.MssqlDatabase;
import cn.cerc.db.mysql.MysqlDatabase;
import cn.cerc.db.redis.JedisFactory;
import cn.cerc.db.sqlite.SqliteDatabase;
import redis.clients.jedis.Jedis;

public abstract class EntityHome<T extends EntityImpl> extends Handle implements EntityHomeImpl {
//    private static final Logger log = LoggerFactory.getLogger(EntityQuery.class);
    private static final ConcurrentHashMap<Class<?>, ISqlDatabase> buff = new ConcurrentHashMap<>();
    // 批量写入redis等缓存
    private static final String LUA_SCRIPT_MSETEX = "local keysLen = table.getn(KEYS);local argvLen = table.getn(ARGV);"
            + "local idx=1;local argVIdx=1;for idx=1,keysLen,1 do argVIdx=(idx-1)*2+1; "
            + "redis.call('Set',KEYS[idx],ARGV[argVIdx],'EX',ARGV[argVIdx+1]);end return keysLen;";
    protected final SqlQuery query;
    protected final Class<T> clazz;
    protected EntityHelper<T> helper;

    public static ISqlDatabase findDatabase(IHandle handle, Class<? extends EntityImpl> clazz) {
        ISqlDatabase database = buff.get(clazz);
        if (database == null) {
            SqlServer server = clazz.getAnnotation(SqlServer.class);
            SqlServerType sqlServerType = (server != null) ? server.type() : SqlServerType.Mysql;
            if (sqlServerType == SqlServerType.Mysql)
                database = new MysqlDatabase(handle, clazz);
            else if (sqlServerType == SqlServerType.Mssql)
                database = new MssqlDatabase(handle, clazz);
            else if (sqlServerType == SqlServerType.Sqlite)
                database = new SqliteDatabase(handle, clazz);
            else
                throw new SqlServerTypeException();
//            if (ServerConfig.isServerDevelop()) {
//                EntityKey ekey = clazz.getDeclaredAnnotation(EntityKey.class);
//                if (ekey == null || !ekey.virtual())
//                    database.createTable(false);
//            }
            buff.put(clazz, database);
        }
        return database;
    }

    // 注册与写入缓存相关的事件
    public static <T extends EntityImpl> void registerCacheListener(SqlQuery target, Class<T> clazz,
            boolean writeCacheAtOpen) {
        // 在open时，读入字段定义
        target.onAfterOpen(self -> self.fields().readDefine(clazz));
        EntityKey entityKey = clazz.getDeclaredAnnotation(EntityKey.class);
        if (entityKey == null || entityKey.cache() == CacheLevelEnum.Disabled)
            return;

        // 在open时，写入redis等缓存
        if (writeCacheAtOpen) {
            target.onAfterOpen(query -> {
                int count = 0;
                EntityCache<T> ec1 = new EntityCache<T>(query, clazz);
                List<String> batchKeys = new ArrayList<>();
                List<String> batchValues = new ArrayList<>();
                for (DataRow row : query.records()) {
                    if (++count > EntityCache.MaxRecord)
                        break;
                    String[] keys = ec1.buildKeys(row);
                    batchKeys.add(EntityCache.buildKey(keys));
                    batchValues.add(row.json());
                    batchValues.add("" + entityKey.expire());
                    if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
                        SessionCache.set(keys, row);
                }
                try (Jedis jedis = JedisFactory.getJedis()) {
                    jedis.evalsha(jedis.scriptLoad(LUA_SCRIPT_MSETEX), batchKeys, batchValues);
                }
            });
        }

        // 在post(insert、update)时，写入redis等缓存
        target.onAfterPost(row -> {
            EntityCache<T> ec2 = new EntityCache<T>(target, clazz);
            String[] keys = ec2.buildKeys(row);
            try (Jedis jedis = JedisFactory.getJedis()) {
                jedis.setex(EntityCache.buildKey(keys), entityKey.expire(), row.json());
                if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
                    SessionCache.set(keys, row);
            }
        });

        // 在delete时，清除redis等缓存
        target.onAfterDelete(row -> {
            EntityCache<T> ec3 = new EntityCache<T>(target, clazz);
            String[] keys = ec3.buildKeys(row);
            try (Jedis jedis = JedisFactory.getJedis()) {
                jedis.del(EntityCache.buildKey(keys));
                if (entityKey.cache() == CacheLevelEnum.RedisAndSession)
                    SessionCache.del(keys);
            }
        });
    }

    public EntityHome(IHandle handle, Class<T> clazz, SqlText sql, boolean useSlaveServer, boolean writeCacheAtOpen) {
        super(handle);
        this.clazz = clazz;
        this.helper = EntityHelper.create(clazz);
        query = new SqlQuery(this, helper.sqlServerType());
        query.operator().setTable(helper.table());
        query.operator().setOid(helper.idFieldCode());
        query.operator().setVersionField(helper.versionFieldCode());
        registerCacheListener(query, clazz, writeCacheAtOpen);
        if (sql != null) {
            query.setSql(sql);
            if (useSlaveServer)
                query.openReadonly();
            else
                query.open();
        }
        query.setReadonly(true);
    }

    public boolean isEmpty() {
        return query.size() == 0;
    }

    public boolean isPresent() {
        return query.size() > 0;
    }

    // load.isPresentThrow: 载入一条数据，若不为空就抛出异常
    // isPresentThrow.update: 更新entity，若为空无法更新就抛出异常
    protected <X extends Throwable> EntityHome<T> isPresentThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (query.size() > 0)
            throw exceptionSupplier.get();
        return this;
    }

    // load.isEmptyThrow: 载入一条数据，若为空就抛出异常
    protected <X extends Throwable> EntityHome<T> isEmptyThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (query.size() == 0)
            throw exceptionSupplier.get();
        return this;
    }

    protected T insert(Consumer<T> action) {
        T entity = helper.newEntity();
        action.accept(entity);
        this.insert(entity);
        return entity;
    }

    protected void insert(T entity) {
        query.setReadonly(false);
        try {
            helper.onInsertPostDefault(entity);
            entity.onInsertPost(query);
            query.append();
            query.current().loadFromEntity(entity);
            query.post();
            query.current().saveToEntity(entity);
            entity.setEntityHome(this);
        } finally {
            query.setReadonly(true);
        }
    }

    @Override
    public void post(EntityImpl entity) {
        @SuppressWarnings("unchecked")
        T obj = (T) entity;
        int recNo = this.findRecNo(entity);
        if (recNo == 0)
            this.insert(obj);
        else {
            save(recNo - 1, obj);
            query.current().saveToEntity(obj);
        }
    }

    protected EntityHome<T> update(Consumer<T> action) {
        Objects.requireNonNull(action);
        T entity = null;
        for (int i = 0; i < query.size(); i++) {
            DataRow row = query.records().get(i);
            entity = row.asEntity(this.clazz);
            entity.setEntityHome(this);
            action.accept(entity);
            save(i, entity);
        }
        return this;
    }

    public int deleteIf(Predicate<T> predicate) {
        Objects.requireNonNull(predicate);
        if (query.eof())
            return 0;
        query.setReadonly(false);
        try {
            int result = 0;
            query.first();
            while (!query.eof()) {
                T entity = this.query.current().asEntity(clazz);
                if (predicate.test(entity)) {
                    query.delete();
                    result++;
                } else
                    query.next();
            }
            query.first();
            return result;
        } finally {
            query.setReadonly(true);
        }
    }

    /**
     * 返回entity在query中的序号，从1开始，若有找到则变更并返回recNo，否则返回0
     */
    @Override
    public int findRecNo(EntityImpl entity) {
        if (helper.idField().isEmpty())
            throw new IllegalArgumentException("id define not exists");
        Object idValue = helper.readIdValue(entity);
        if (idValue == null)
            return 0;

        String value = String.valueOf(idValue);

        // 优先判断是否为当前行
        DataRow current = query.current();
        if (current != null) {
            if (current.getString(helper.idFieldCode()).equals(value))
                return query.recNo();
            // 如果只有一条记录，就不要再找了
            if (query.size() == 1)
                return 1;
        }

        // 再全部记录均查找一次
        for (int i = 0; i < query.size(); i++) {
            DataRow row = query.records().get(i);
            if (row.getString(helper.idFieldCode()).equals(value)) {
                query.setRecNo(i + 1);
                return i + 1;
            }
        }
        return 0;
    }

    protected EntityHome<T> save(int index, T entity) {
        query.setRecNo(index + 1);
        if (!isCurrentRow(entity))
            throw new RuntimeException("recNo error, refuse update");
        query.setReadonly(false);
        try {
            helper.onUpdatePostDefault(entity);
            entity.onUpdatePost(query);
            query.edit();
            query.current().loadFromEntity(entity);
            query.post();
        } finally {
            query.setReadonly(true);
        }
        return this;
    }

    /**
     * @param entity Entity实体对象
     * @return 判断传入的entity对象，是不是当前记录
     */
    protected boolean isCurrentRow(T entity) {
        DataRow row = query.current();
        if (row == null)
            return false;

        if (helper.idField().isEmpty())
            throw new IllegalArgumentException("id define not exists");

        Object idValue = helper.readIdValue(entity);
        if (idValue == null)
            return false;

        return row.getString(helper.idFieldCode()).equals(String.valueOf(idValue));
    }

    @Override
    public void refresh(EntityImpl entity) {
        int recNo = this.findRecNo(entity);
        if (recNo == 0)
            throw new RuntimeException("refresh error, not find in query");
        query.current().saveToEntity(entity);
    }

    public EntityHome<T> setJoinName(EntityHome<? extends EntityImpl> join, String codeField, String nameField) {
        if (query.size() == 0)
            return this;

        Map<String, String> items = new HashMap<>();
        join.query.forEach(row -> items.put(row.getString(codeField), row.getString(nameField)));

        T entity = null;
        query.first();
        while (query.fetch()) {
            if (entity == null)
                entity = query.current().asEntity(this.clazz);
            entity.onJoinName(query.current(), join.clazz, items);
        }
        return this;
    }

}

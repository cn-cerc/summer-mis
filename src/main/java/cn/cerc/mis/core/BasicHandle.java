package cn.cerc.mis.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import cn.cerc.db.core.IHandle;
import cn.cerc.db.core.ISession;

@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
public class BasicHandle implements IHandle, AutoCloseable {
    private ISession session;

    public BasicHandle() {
        super();
    }

    public BasicHandle(String token) {
        super();
        getSession().loadToken(token);
    }

    @Override
    public ISession getSession() {
        if (session == null)
            session = Application.getSession();
        return session;
    }

    @Autowired
    @Override
    public void setSession(ISession session) {
        this.session = session;
    }

    @Override
    public void close() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

}

package cn.cerc.mis.core;

import cn.cerc.db.core.ISession;

public interface IAppLanguage {
    
    String getLanguageId(ISession session, String defaultValue);
}
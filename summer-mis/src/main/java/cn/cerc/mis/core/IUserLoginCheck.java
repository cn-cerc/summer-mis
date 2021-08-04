package cn.cerc.mis.core;

import cn.cerc.db.core.IHandle;

public interface IUserLoginCheck extends IHandle {

    // 登录验证
    String getToken(String userCode, String password, String machineCode, String clientIP, String language);

    // 通过手机号获取帐号
    String getUserCode(String mobile);// FIXME 该方法不需要，所有关联的登录服务要调整

    // 错误消息
    String getMessage();
}

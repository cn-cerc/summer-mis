package cn.cerc.mis.book;

import cn.cerc.db.core.DataException;
import cn.cerc.db.core.ServiceException;

public interface IBookEnroll {
    // 将数据登记到帐本中, 若virtual为true则表示为处理分月专用
    boolean enroll(IBookData bookData, boolean virtual) throws ServiceException, DataException;

}

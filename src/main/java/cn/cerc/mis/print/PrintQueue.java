package cn.cerc.mis.print;

import cn.cerc.db.core.DataRow;
import cn.cerc.db.core.DataSet;
import cn.cerc.db.core.IHandle;
import cn.cerc.db.queue.QueueQuery;

public class PrintQueue {

    // 设置共享打印服务的设置记录之UID
    private String printerId = "";
    // 要打印的模版编号
    private String reportId = "";
    // 要打印的报表调用参数
    private String reportParams = "";
    // 要打印的份数
    private int reportNum = 1;
    // 打印机帐号
    private String userCode;
    // 打印行高
    private double reportLineHeight = 1;
    // 报表抬头
    private String reportRptHead = "";

    public PrintQueue() {
    }

    public PrintQueue(String userCode) {
        this.userCode = userCode;
    }

    public void sendAliMessage(IHandle handle) {
        if ("".equals(printerId)) {
            throw new RuntimeException("PrinterId is null");
        }
        if ("".equals(reportId)) {
            throw new RuntimeException("ReportId is null");
        }
        if ("".equals(reportParams)) {
            throw new RuntimeException("ReportParams is null");
        }
        if (userCode == null || "".equals(userCode)) {
            throw new RuntimeException("userCode is empty");
        }

        String queueCode = buildQueue();

        // 设置参数
        DataSet dataSet = new DataSet();
        DataRow headIn = dataSet.head();
        headIn.setJson(reportParams);
        headIn.setValue("_printerId_", printerId);
        headIn.setValue("_reportId_", reportId);
        headIn.setValue("_reportNum_", reportNum);
        headIn.setValue("_reportLineHeight_", reportLineHeight);
        headIn.setValue("_reportRptHead_", reportRptHead);
        QueueQuery query = new QueueQuery(queueCode);
        query.save(dataSet.json());
    }

    private String buildQueue() {
        return "print-" + userCode;
    }

    public String getPrinterId() {
        return printerId;
    }

    public void setPrinterId(String printerId) {
        this.printerId = printerId;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getReportParams() {
        return reportParams;
    }

    public void setReportParams(String reportParams) {
        this.reportParams = reportParams;
    }

    public int getReportNum() {
        return reportNum;
    }

    public void setReportNum(int reportNum) {
        this.reportNum = reportNum;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public double getReportLineHeight() {
        return reportLineHeight;
    }

    public void setReportLineHeight(double reportLineHeight) {
        this.reportLineHeight = reportLineHeight;
    }

    public String getReportRptHead() {
        return reportRptHead;
    }

    public void setReportRptHead(String reportRptHead) {
        this.reportRptHead = reportRptHead;
    }
}

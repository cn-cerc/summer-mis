package cn.cerc.mis.excel.output;

import cn.cerc.core.DataSet;
import cn.cerc.core.Datetime;
import cn.cerc.core.FieldMeta;
import com.google.gson.Gson;
import jxl.write.WriteException;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class DataSetExportToExcelTest {

    @Test
    public void test_export() {
        DataSet dataSet = buildData();
        try {
            File file = new File("test.xls");
            FileOutputStream outputStream = new FileOutputStream(file);
            DataSetExportToExcel.output(dataSet, outputStream);
            outputStream.close();
        } catch (IOException | WriteException e) {
            e.printStackTrace();
        }
    }

    private DataSet buildData() {
        DataSet dataSet = new DataSet();
        dataSet.append();
        dataSet.setValue("code_", "131001");
        dataSet.setValue("name_", "狼王");
        dataSet.setValue("number_", 100);
        dataSet.setValue("date_", new Datetime().toString());
        dataSet.setValue("super_", false);

        dataSet.append();
        dataSet.setValue("code_", "173015");
        dataSet.setValue("name_", "德岛");
        dataSet.setValue("number_", 150);
        dataSet.setValue("date_", new Datetime().getDate());
        dataSet.setValue("super_", true);

        dataSet.fields().get("code_").setName("帐套");
        dataSet.fields().get("name_").setName("公司");
        dataSet.fields().get("number_").setName("员工");
        dataSet.fields().get("date_").setName("时间");
        dataSet.fields().get("super_").setName("管理员");

        dataSet.buildMeta();
        dataSet.setMetaInfo(true);
        System.out.println(dataSet.toJson());
        for (FieldMeta meta : dataSet.fields()) {
            System.out.println(new Gson().toJson(meta));
        }
        return dataSet;
    }

}
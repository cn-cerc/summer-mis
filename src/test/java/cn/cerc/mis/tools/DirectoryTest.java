package cn.cerc.mis.tools;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import cn.cerc.db.core.Datetime;
import cn.cerc.db.core.Handle;
import cn.cerc.db.core.ISession;
import cn.cerc.db.core.LanguageResource;
import cn.cerc.db.core.Utils;
import cn.cerc.db.mysql.MysqlQuery;
import cn.cerc.mis.core.Application;
import cn.cerc.mis.core.ISystemTable;

public class DirectoryTest extends Handle {

    @Before
    public void setUp() throws Exception {
        ISession session = Application.getBean(ISession.class);
        setSession(session);
    }

    @Test
    @Ignore
    public void test() {
        Directory dir = new Directory();
        dir.setOnFilter(file -> {
            // 列出所有的java文件
            return file.getName().endsWith(".java");
        });

        int count = 0;
        if (dir.list("C:\\Users\\10914\\Documents\\iWork\\ufamily\\src\\main") > 0) {
            for (String fileName : dir.getFiles()) {
                StringList src = new StringList();
                src.loadFromFile(fileName);
                for (String line : src.getItems()) {
                    String text = processString(line);
                    if (text != null) {
                        count += WriteLine(text);
                    }
                }
            }
        } else {
            System.out.println("没有找到任何目录与文件");
        }
        System.out.println(count);
    }

    private int WriteLine(String text) {
        if (text.length() > 150) {
            System.err.println(text);
            return 0;
        }
        ISystemTable systemTable = Application.getSystemTable();
        MysqlQuery dsLang = new MysqlQuery(this);
        dsLang.add("select * from %s", systemTable.getLanguage());
        dsLang.add("where key_='%s' and lang_='en'", text);
        dsLang.open();
        if (dsLang.eof()) {
            System.out.println(text);
            dsLang.append();
            dsLang.setValue("key_", Utils.safeString(text));
            dsLang.setValue("lang_", LanguageResource.LANGUAGE_EN);
            dsLang.setValue("value_", "");
            dsLang.setValue("supportAndroid_", false);
            dsLang.setValue("supportIphone_", false);
            dsLang.setValue("enable_", true);
            dsLang.setValue("updateUser_", this.getUserCode());
            dsLang.setValue("updateDate_", new Datetime());
            dsLang.setValue("createUser_", this.getUserCode());
            dsLang.setValue("createDate_", new Datetime());
            dsLang.post();
            return 1;
        }
        return 0;
    }

    private String processString(String text) {
        String flag = "R.asString(this,";
        int start = text.indexOf(flag);
        if (start == -1) {
            return null;
        }

        String s1 = text.substring(flag.length() + start);
        if (s1.indexOf("\"") == -1) {
            return null;
        }
        s1 = s1.substring(s1.indexOf("\"") + 1);
        if (s1.indexOf("\"") == -1) {
            return null;
        }
        s1 = s1.substring(0, s1.indexOf("\""));
        return s1;
    }

}

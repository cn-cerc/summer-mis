package cn.cerc.mis.custom;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import cn.cerc.db.core.ISession;
import cn.cerc.mis.core.Application;
import cn.cerc.mis.core.SummerSpringConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SummerSpringConfiguration.class)
//@ContextConfiguration(locations = { "classpath:summer-mis-spring.xml" })
@WebAppConfiguration
public class SessionDefaultTest {
    @Autowired
    ISession handle;

    @Test
    @Ignore
    public void test() {
        System.out.println(handle);
        System.out.println(Application.getBean(ISession.class));
        System.out.println(Application.getBean(ISession.class));
        System.out.println(Application.getBean(ISession.class));
    }

    public static void main(String[] args) {
        Application.initOnlyFramework();
        System.out.println(Application.getBean(ISession.class));
        System.out.println(Application.getBean(ISession.class));
    }
}

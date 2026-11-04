package edu.campus.lostfound;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 校园失物招领系统启动类。
 *
 * <p>范围内的两个功能：F1 统一身份认证与账号绑定（auth 包）、F2 失物认领全流程（claim 包）。
 * <p>分包与依赖方向见各包的 package-info.java，对应架构表 3-2 与 3.3.3 的三条硬约束。
 */
@SpringBootApplication
@MapperScan("edu.campus.lostfound.data.mapper")
@EnableScheduling
@EnableTransactionManagement
public class LostfoundApplication {

    public static void main(String[] args) {
        SpringApplication.run(LostfoundApplication.class, args);
    }
}

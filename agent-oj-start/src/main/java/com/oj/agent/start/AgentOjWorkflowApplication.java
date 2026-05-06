package com.oj.agent.start;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = "com.oj.agent",
        exclude = {MilvusVectorStoreAutoConfiguration.class}
)
public class AgentOjWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOjWorkflowApplication.class, args);
    }
}

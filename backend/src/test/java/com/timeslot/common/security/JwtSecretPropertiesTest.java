package com.timeslot.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定配置契约：真实配置（application.yml）不得再内置公开 JWT 默认密钥，
 * 未显式提供 JWT_SECRET 时应用必须启动失败；测试环境使用独立的 test property。
 */
class JwtSecretPropertiesTest {

    private Properties load(String location) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(location));
        yaml.afterPropertiesSet();
        return yaml.getObject();
    }

    @Test
    void realConfigMustNotShipPublicDefaultSecret() {
        Properties properties = load("application.yml");
        String secret = properties.getProperty("timeslot.jwt.secret");

        assertEquals("${JWT_SECRET:}", secret, "真实配置必须仅依赖环境变量 JWT_SECRET，不得带默认值");
        assertNotEquals("time-slot-team-ready-baseline-v0-2-secret-key-please-change", secret);
    }

    @Test
    void testProfileProvidesIndependentSecret() {
        Properties properties = load("application-test.yml");
        String secret = properties.getProperty("timeslot.jwt.secret");

        assertTrue(secret != null && secret.length() >= 32, "测试配置必须提供独立的足够强度密钥");
        assertNotEquals("time-slot-team-ready-baseline-v0-2-secret-key-please-change", secret,
                "测试密钥不得复用历史公开默认值");
    }
}

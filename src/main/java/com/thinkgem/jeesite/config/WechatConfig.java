package com.thinkgem.jeesite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@Configuration
@PropertySource({"classpath:wechat.properties"})
public class WechatConfig {
    @Value("${app_key}")
    public String app_key;
    @Value("${app_id}")
    public String app_id;
    @Value("${mch_id}")
    public String mch_id;
    @Value("${fee_type}")
    public String fee_type;
    @Value("${trade_type}")
    public String trade_type;
    @Value("${notify_url}")
    public String notify_url;
    @Value("${charset}")
    public String charset;
    @Value("${signType}")
    public String signType;
    @Value("${api_url}")
    public String api_url;



}
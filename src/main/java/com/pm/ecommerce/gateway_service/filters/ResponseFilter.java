package com.pm.ecommerce.gateway_service.filters;

import com.netflix.zuul.ZuulFilter;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResponseFilter extends ZuulFilter {
    @Override
    public String filterType() {
        return "post";
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        return null;
    }
}

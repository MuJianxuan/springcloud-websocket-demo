
package com.example.demo.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * ********************
 * 跨域过滤器
 *
 * @author yk
 * @version 1.0
 * @created 2020/5/27 下午14:26
 * **********************
 */
@Component
public class CorsFilter implements WebFilter {
    public static final String TOKEN_HEADER = "Authorization";

    @Override
    public Mono<Void> filter(ServerWebExchange ctx, WebFilterChain chain) {
        ServerHttpRequest request = ctx.getRequest();
        if (CorsUtils.isCorsRequest(request)) {
            ServerHttpResponse response = ctx.getResponse();
            HttpHeaders headers = response.getHeaders();
            String origin = Objects.requireNonNull(request.getHeaders().get(HttpHeaders.ORIGIN)).get(0);
            headers.setAccessControlAllowOrigin(origin);
            headers.setAccessControlAllowCredentials(true);
            headers.setAccessControlMaxAge(Integer.MAX_VALUE);
            headers.setAccessControlAllowHeaders(
                    Arrays.asList("content-type", "x-requested-with", TOKEN_HEADER, "content-disposition"));
            headers.setAccessControlAllowMethods(Arrays.asList(HttpMethod.OPTIONS, HttpMethod.GET, HttpMethod.HEAD,
                    HttpMethod.POST, HttpMethod.DELETE, HttpMethod.PUT, HttpMethod.PATCH));
            headers.setAccessControlExposeHeaders(Collections.singletonList(TOKEN_HEADER));
            if (request.getMethod() == HttpMethod.OPTIONS) {
                response.setStatusCode(HttpStatus.OK);
                return Mono.empty();
            }
        }
        return chain.filter(ctx);
    }
}

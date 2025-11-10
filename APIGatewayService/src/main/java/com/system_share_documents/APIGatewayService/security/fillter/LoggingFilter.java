package com.system_share_documents.APIGatewayService.security.fillter;

import com.system_share_documents.APIGatewayService.entity.RequestLog;
import com.system_share_documents.APIGatewayService.repository.RequestLogRepository;
import com.system_share_documents.APIGatewayService.service.BufferingServerHttpResponseDecorator;
import com.system_share_documents.APIGatewayService.service.DuplicateLogCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class LoggingFilter implements WebFilter {

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private DuplicateLogCache duplicateLogCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicOrWebsocketPath(path)) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().toString();

        // Lấy traceId từ header hoặc sinh mới
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        String finalTraceId = traceId;

        // Gắn traceId vào request trước khi forward
        ServerHttpRequest requestWithTraceId = exchange.getRequest()
                .mutate()
                .header("X-Trace-Id", finalTraceId)
                .build();

        ServerWebExchange exchangeWithTraceId = exchange.mutate()
                .request(requestWithTraceId)
                .build();

        return DataBufferUtils.join(exchangeWithTraceId.getRequest().getBody())
                .defaultIfEmpty(exchangeWithTraceId.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);
                    String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);

                    Flux<DataBuffer> cachedFlux = Flux.defer(() ->
                            Mono.just(exchangeWithTraceId.getResponse().bufferFactory().wrap(bodyBytes)));

                    ServerHttpRequestDecorator mutatedRequest = new ServerHttpRequestDecorator(exchangeWithTraceId.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedFlux;
                        }
                    };

                    BufferingServerHttpResponseDecorator decoratedResponse =
                            new BufferingServerHttpResponseDecorator(exchangeWithTraceId.getResponse());

                    ServerWebExchange finalExchange = exchangeWithTraceId.mutate()
                            .request(mutatedRequest)
                            .response(decoratedResponse)
                            .build();

                    return ReactiveSecurityContextHolder.getContext()
                            .map(ctx -> ctx.getAuthentication() != null ? ctx.getAuthentication().getName() : "anonymous")
                            .defaultIfEmpty("anonymous")
                            .flatMap(userId -> chain.filter(finalExchange)
                                    .then(Mono.defer(() -> {
                                        long endTime = System.currentTimeMillis();
                                        Long responseTime = endTime - startTime;
                                        int status = decoratedResponse.getStatusCode() != null
                                                ? decoratedResponse.getStatusCode().value()
                                                : 500;
                                        String responseBody = decoratedResponse.getFullBody();

                                        // Thông tin thêm
                                        ServerHttpRequest req = finalExchange.getRequest();
                                        String ip = req.getRemoteAddress() != null
                                                ? req.getRemoteAddress().getAddress().getHostAddress()
                                                : "unknown";
                                        String userAgent = req.getHeaders().getFirst("User-Agent");

                                        // Chống duplicate
                                        String duplicateKey = method + "|" + path + "|" + userId;
                                        if (duplicateLogCache.isDuplicate(duplicateKey)) {
                                            return Mono.empty();
                                        }

                                        // Lấy service name từ Gateway Route
                                        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                                        String serviceName = (route != null) ? route.getId() : "UNKNOWN";

                                        RequestLog log = RequestLog.builder()
                                                .method(method)
                                                .path(path)
                                                .status(status)
                                                .userId(userId)
                                                .traceId(finalTraceId)
                                                .ipAddress(ip)
                                                .userAgent(userAgent)
                                                .serviceName(serviceName)
                                                .responseTimeMs(responseTime)
                                                .createdAt(new Timestamp(System.currentTimeMillis()))
                                                .requestBody(requestBody)
                                                .responseBody(responseBody)
                                                .build();

                                        return Mono.fromCallable(() -> requestLogRepository.save(log))
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .then();
                                    })));
                });
    }

    private boolean isPublicOrWebsocketPath(String path) {
        return path.startsWith("/identity/") ||
                path.startsWith("/oauth2/") ||
                path.startsWith("/api/login/") ||
                path.startsWith("/ws-") ||
                path.startsWith("/address/");
    }
}

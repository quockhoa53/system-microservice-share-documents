package com.system_share_documents.APIGatewayService.service;

import com.system_share_documents.APIGatewayService.entity.RequestLog;
import com.system_share_documents.APIGatewayService.repository.RequestLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Bỏ qua log cho public paths và websocket
        if (isPublicOrWebsocketPath(path)) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().toString();

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);
                    String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);

                    Flux<DataBuffer> cachedFlux = Flux.defer(() ->
                            Mono.just(exchange.getResponse().bufferFactory().wrap(bodyBytes)));

                    ServerHttpRequestDecorator mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedFlux;
                        }
                    };

                    BufferingServerHttpResponseDecorator decoratedResponse =
                            new BufferingServerHttpResponseDecorator(exchange.getResponse());

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(mutatedRequest)
                            .response(decoratedResponse)
                            .build();

                    return ReactiveSecurityContextHolder.getContext()
                            .map(ctx -> ctx.getAuthentication() != null ? ctx.getAuthentication().getName() : "anonymous")
                            .defaultIfEmpty("anonymous")
                            .flatMap(userId -> chain.filter(mutatedExchange)
                                    .then(Mono.defer(() -> {
                                        long endTime = System.currentTimeMillis();
                                        Long responseTime = endTime - startTime;
                                        int status = decoratedResponse.getStatusCode() != null
                                                ? decoratedResponse.getStatusCode().value()
                                                : 500;
                                        String responseBody = decoratedResponse.getFullBody();

                                        // Lấy IP, User-Agent, TraceId
                                        ServerHttpRequest req = exchange.getRequest();
                                        String ip = req.getRemoteAddress() != null
                                                ? req.getRemoteAddress().getAddress().getHostAddress()
                                                : "unknown";
                                        String userAgent = req.getHeaders().getFirst("User-Agent");
                                        String traceId = req.getHeaders().getFirst("X-Trace-Id");
                                        if (traceId == null) {
                                            traceId = UUID.randomUUID().toString();
                                        }

                                        // Service name có thể hardcode hoặc đọc từ config
                                        String serviceName = "APIGatewayService";

                                        RequestLog log = RequestLog.builder()
                                                .method(method)
                                                .path(path)
                                                .status(status)
                                                .userId(userId)
                                                .traceId(traceId)
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

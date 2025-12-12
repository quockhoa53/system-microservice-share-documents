package com.system_share_documents.AppCommonService.filter;

import com.system_share_documents.AppCommonService.utils.ClientUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP Request/Response Logging Filter
 * Tự động log tất cả HTTP requests/responses và đẩy lên Graylog qua MDC
 */
@Slf4j
@Component
@Order(1)
public class HttpLoggingFilter implements Filter {

    private static final String[] EXCLUDED_PATHS = {
            "/actuator",
            "/public",
            "/favicon.ico"
    };

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Bỏ qua các path không cần log
        if (shouldSkipLogging(path)) {
            chain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();

        // Lấy hoặc tạo traceId
        String traceId = httpRequest.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }

        // Lấy userId từ SecurityContext
        String userId = getUserId();

        // Wrap request và response để có thể đọc body nhiều lần
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        try {
            // Đặt thông tin vào MDC để logback có thể gửi lên Graylog
            MDC.put("traceId", traceId);
            MDC.put("userId", userId != null ? userId : "anonymous");
            MDC.put("method", httpRequest.getMethod());
            MDC.put("path", path);
            MDC.put("ipAddress", ClientUtils.getClientIp(httpRequest));
            MDC.put("userAgent", ClientUtils.getUserAgent(httpRequest));

            // Thêm traceId vào response header
            wrappedResponse.setHeader("X-Trace-Id", traceId);

            // Tiếp tục filter chain
            chain.doFilter(wrappedRequest, wrappedResponse);

        } finally {
            // Tính response time
            long responseTime = System.currentTimeMillis() - startTime;
            int status = wrappedResponse.getStatus();

            // Đọc request body (full body, không giới hạn)
            String requestBody = getRequestBody(wrappedRequest);

            // Đọc response body (full body, không giới hạn)
            String responseBody = getResponseBody(wrappedResponse);

            // Cập nhật MDC với thông tin response và body
            MDC.put("status", String.valueOf(status));
            MDC.put("responseTime", String.valueOf(responseTime));
            MDC.put("requestBody", requestBody != null ? requestBody : "");
            MDC.put("responseBody", responseBody != null ? responseBody : "");

            // Log request/response
            logApiRequest(httpRequest, status, responseTime, requestBody, responseBody);

            // Copy response body về response gốc
            wrappedResponse.copyBodyToResponse();

            // Clear MDC
            MDC.clear();
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    private boolean shouldSkipLogging(String path) {
        for (String excludedPath : EXCLUDED_PATHS) {
            if (path.startsWith(excludedPath)) {
                return true;
            }
        }
        return false;
    }

    private String getUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        try {
            byte[] content = request.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("Failed to read request body", e);
        }
        return "";
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        try {
            byte[] content = response.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("Failed to read response body", e);
        }
        return "";
    }

    private void logApiRequest(HttpServletRequest request, int status, long responseTime,
                               String requestBody, String responseBody) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullPath = queryString != null ? path + "?" + queryString : path;

        // Format request body (truncate nếu quá dài)
        String formattedRequestBody = formatBody(requestBody, 1000);
        String formattedResponseBody = formatBody(responseBody, 1000);

        // Log với format [API] bao gồm cả request và response
        String logMessage = String.format("[API] %s %s | Status: %d | Time: %dms | Request: %s | Response: %s",
                method, fullPath, status, responseTime, formattedRequestBody, formattedResponseBody);

        if (status >= 400) {
            log.error(logMessage);
        } else if (responseTime > 1000) {
            log.warn(logMessage);
        } else {
            log.info(logMessage);
        }
    }

    /**
     * Format body để log, truncate nếu quá dài
     */
    private String formatBody(String body, int maxLength) {
        if (body == null || body.isEmpty()) {
            return "<empty>";
        }

        // Loại bỏ whitespace thừa
        String trimmed = body.trim();

        // Truncate nếu quá dài
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength) + "... (truncated)";
        }

        return trimmed;
    }
}


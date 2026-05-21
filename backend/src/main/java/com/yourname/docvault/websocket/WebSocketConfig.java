package com.yourname.docvault.websocket;

import com.yourname.docvault.auth.JwtService;
import com.yourname.docvault.auth.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwtService;
    private final String allowedOrigins;

    public WebSocketConfig(JwtService jwtService, @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.jwtService = jwtService;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = firstNativeHeader(accessor, "Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new AccessDeniedException("Missing websocket JWT");
                    }
                    UserPrincipal principal = jwtService.parseToken(authHeader.substring(7));
                    accessor.setUser(new SocketPrincipal(principal.id(), principal.username()));
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    Principal principal = accessor.getUser();
                    String destination = accessor.getDestination();
                    if (principal == null || destination == null || !ownsTopic(principal.getName(), destination)) {
                        throw new AccessDeniedException("Cannot subscribe to another user's topic");
                    }
                }
                return message;
            }
        });
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private boolean ownsTopic(String userId, String destination) {
        return destination.equals("/topic/upload-progress/" + userId)
                || destination.equals("/topic/search-results/" + userId);
    }

    private record SocketPrincipal(Long id, String username) implements Principal {
        @Override
        public String getName() {
            return id.toString();
        }
    }
}

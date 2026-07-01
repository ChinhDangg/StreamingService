package dev.chinh.taskresponder.config;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final Duration maxConnectionTimeMillis = Duration.ofMinutes(30);
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    private TaskScheduler messageBrokerTaskScheduler;

    @Value("${allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp")
                .setAllowedOrigins(allowedOrigins);
    }

    @Autowired
    public void setMessageBrokerTaskScheduler(@Lazy TaskScheduler taskScheduler) {
        this.messageBrokerTaskScheduler = taskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Outboxes (where server pushes messages)
        registry.enableSimpleBroker("/queue")
                .setTaskScheduler(messageBrokerTaskScheduler)
                .setHeartbeatValue(new long[] { 20000, 20000 });
        // Inboxes (where client sends messages)
        //config.setApplicationDestinationPrefixes("/app");
        // private prefix where spring generates a unique id for each user
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith("/queue/")) {
                        throw new IllegalArgumentException("Direct queue subscriptions are not allowed");
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
                activeSessions.put(session.getId(), session);

                messageBrokerTaskScheduler.schedule(() -> {
                    WebSocketSession targetSession = activeSessions.get(session.getId());
                    if (targetSession != null && targetSession.isOpen()) {
                        try {
                            System.out.println("Max connection time reached, closing session: " + session.getId());
                            targetSession.close();
                        } catch (IOException e) {
                            System.err.println("Failed to close session: " + e.getMessage());
                        }
                    }
                }, Instant.now().plus(maxConnectionTimeMillis));

                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus closeStatus) throws Exception {
                try (var _ = activeSessions.remove(session.getId())) {}
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }

}

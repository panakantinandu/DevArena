package com.devarena.config;

import com.devarena.realtime.RealtimeHub;
import com.devarena.realtime.WsAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final RealtimeHub hub;
    private final WsAuthInterceptor auth;
    private final AppProperties props;

    public WebSocketConfig(RealtimeHub hub, WsAuthInterceptor auth, AppProperties props) {
        this.hub = hub;
        this.auth = auth;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(hub, "/ws")
                .addInterceptors(auth)
                // Origin check: only the configured frontend origins may open sockets.
                .setAllowedOrigins(props.corsAllowedOrigins().toArray(String[]::new));
    }
}

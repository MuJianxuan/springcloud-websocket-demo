
package com.example.websocketserver.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

//@Configuration
//@EnableWebSocketMessageBroker
public class WebSocketAutoConfig implements WebSocketMessageBrokerConfigurer {

	// webSocket相关配置
	// 链接地址
	public static String WEBSOCKET_PATH = "/ws";
	// 消息代理路径
	public static String WEBSOCKET_BROADCAST_PATH = "/toAll";
	// 前端发送给服务端请求地址
	public static final String FORE_TO_SERVER_PATH = "/chat";
	// 服务端生产地址,客户端订阅此地址以接收服务端生产的消息
	public static final String PRODUCER_PATH = "/toAll/screen";
	// 点对点消息推送地址前缀
	public static final String P2P_PUSH_BASEPATH = "/user";
	// 点对点消息推送地址后缀,最后的地址为/user/用户识别码/msg
	public static final String P2P_PUSH_PATH = "/msg";

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(WEBSOCKET_PATH)
				.setAllowedOrigins("*")
				.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// 服务端发送消息给客户端的域,多个用逗号隔开
		registry.enableSimpleBroker(WEBSOCKET_BROADCAST_PATH, P2P_PUSH_BASEPATH);
		// 定义一对一推送的时候前缀
		registry.setUserDestinationPrefix(P2P_PUSH_BASEPATH);
	}

}

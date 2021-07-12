# Spring cloud Gateway + Websocket 实现消息转发

## Websocket 通信模型

![这里写图片描述](https://i.loli.net/2021/07/12/vCEhn6Hm4qJ7FN5.png)

## 架构模型

![img](https://i.loli.net/2021/07/12/jZGceIVn4xFfKiX.png)

> 简要说明：
>
> 1、为什么要集成Mq做消息广播？
>
> 我们的服务可能是多台的，是一个集群；并且websocket是长连接的，不会每一次发消息都换一台服务器；所以，如果A注册在服务A上，B注册在服务B上，这个时候，如何让前台所有的用户都能接收到消息，便需要一个通信机制。消息只能由我们存储的websocket的sesion来发送消息，因此需要在服务A/B接收到消息队列的消息后，调用服务器上存储的session来进行推送消息给前端。

## 添加相关依赖（基于2.2.x）

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.2.12.RELEASE</version>
    <relativePath/> <!-- lookup parent from repository -->
</parent>


<properties>
  <java.version>1.8</java.version>
  <spring-cloud.version>Hoxton.SR4</spring-cloud.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>fastjson</artifactId>
        <version>1.2.75</version>
    </dependency>
 </dependencies>
    
```

## Spring Cloud Gateway  转发ws协议

>前置了解：
>
>ws连接之前，会尝试http请求先访问服务器的信息，因此需要路由两个地方。此外本demo是基于 2.2.x 或以上，2.0.x或2.1.x或有不同配置。

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
</dependencies>
```

### 需要配置一下返回的 header  origin信息

```java
/**
 * ********************
 * 数据返回前重新设置header origin,解决origin重复多次的跨域问题
 *
 * @author yk
 * @version 1.0
 * @created 2020/5/27 下午14:30
 * **********************
 */
@Component
public class CorsResponseHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER + 1;
    }

    @SuppressWarnings("serial")//抑制编译错误
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.defer(() -> {
            exchange.getResponse().getHeaders().entrySet().stream()
                    .filter(kv -> (kv.getValue() != null && kv.getValue().size() > 1))
                    .filter(kv -> (kv.getKey().equals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                            || kv.getKey().equals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)))
                    .forEach(kv -> {
                        kv.setValue(new ArrayList<String>() {
                            {
                                add(kv.getValue().get(0));
                            }
                        });
                    });
            return chain.filter(exchange);
        }));
    }
}
```

### 跨域过滤器

```java
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
```

### 集成注册中心 (nacos/eureka)

> websocket-server : 注册中心上的服务名
>
> ---- 此处应该还做 路径剪切等等
>
> predicates:
>
> ​		- Path=/websocket-server/info/**

```yaml
spring:
  application:
    name: gateway
  cloud:
    gateway:
      discovery:
        locator:
          lower-case-service-id: true
          # 启用eureka网关集成
          enabled: true
      routes:
        # 负责websocket的前置连接
        - id: websocket-server-http
          # 重点！/info必须使用http进行转发，lb代表从注册中心获取服务
          uri: lb://websocket-server
          predicates:
            # 重点！转发该路径！
            - Path=/websocket-server/info/**
        - id: websocket-server-ws
          # 重点！lb:ws://代表从注册中心获取服务，并且转发协议为websocket，这种格式怀疑人生！
          uri: lb:ws://websocket-server
          predicates:
            # 转发/bullet端点下的所有路径
            - Path=/websocket-server/**
```

## 基于 @EnableWebSocketMessageBroker 注解式 websocket服务

```java


import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
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
```

### 静态资源文件放行

```java
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }
}
```

### 发送消息接口

```java
@Controller
public class WebcocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping(WebSocketAutoConfig.FORE_TO_SERVER_PATH)
    @SendTo(WebSocketAutoConfig.PRODUCER_PATH) // SendTo 发送至 Broker 下的指定订阅路径
    public void say(RequestMessage clientMessage) throws InterruptedException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        ResponseMessage responseMessage = new ResponseMessage("string", "收到的消息为:" + clientMessage.getContent());

        messagingTemplate.convertAndSend(WebSocketAutoConfig.PRODUCER_PATH, objectMapper.writeValueAsString(responseMessage));
    }
}
```

### 前端文件（用于本地测试）

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Insert title here</title>
    <script src="https://cdn.bootcdn.net/ajax/libs/sockjs-client/1.4.0/sockjs.min.js"></script>
    <script src="https://cdn.bootcdn.net/ajax/libs/stomp.js/2.3.3/stomp.js"></script>
    <script>
        var stompClient = null;

        function connection() {
            // 建立连接对象
            var url = "http://127.0.0.1:80/websocket-server/ws";
            var socket = new SockJS(url);
            // 获取STOMP子协议的客户端对象
            stompClient = Stomp.over(socket);
            // 定义客户端的认证信息,按需求配置
            var headers = {
                Authorization: sessionStorage.getItem("token")
            };
            // 向服务器发起websocket连接
            stompClient.connect(
                headers,
                () => {
                    //subscribe 这个和后台配置有关
                    stompClient.subscribe(
                        "/toAll/screen",
                        () => {
                            // 订阅服务端提供的某个topic
                        },
                        headers
                    );
                    //uniqueId 由自己生成，用于一对一发送消息
                    stompClient.subscribe(
                        "/user/uniqueId/msg",
                        msg => {
                            //有多个subscribe时，接收到消息会走最后一个回调函数
                            // 订阅服务端提供的某个topic
                            let body = JSON.parse(msg.body);
                            console.log(body)
                            var $p = document.createElement('p');
                            $p.innerText = body.content + '----' + dateFtt(new Date())
                            document.getElementById('content').appendChild($p);
                        },
                        headers
                    );
                },
                () => {
                    // 连接发生错误时的处理函数
                    console.log("webSocket建立连接失败，请检查服务器状态!");
                }
            );
        }

        function dateFtt(date, fmt) {
            if (!fmt) {
                fmt = 'yyyy-MM-dd hh:mm:ss'
            }
            var o = {
                "M+": date.getMonth() + 1,                 //月份
                "d+": date.getDate(),                    //日
                "h+": date.getHours(),                   //小时
                "m+": date.getMinutes(),                 //分
                "s+": date.getSeconds(),                 //秒
                "q+": Math.floor((date.getMonth() + 3) / 3), //季度
                "S": date.getMilliseconds()             //毫秒
            };
            if (/(y+)/.test(fmt))
                fmt = fmt.replace(RegExp.$1, (date.getFullYear() + "").substr(4 - RegExp.$1.length));
            for (var k in o)
                if (new RegExp("(" + k + ")").test(fmt))
                    fmt = fmt.replace(RegExp.$1, (RegExp.$1.length == 1) ? (o[k]) : (("00" + o[k]).substr(("" + o[k]).length)));
            return fmt;
        }

        window.onload = function () {
            connection();
        }

        function handleSend() {
            var msg = document.getElementById('sendMsg').value;
            stompClient.send('/chat', {}, JSON.stringify({content: msg}));
        }

    </script>
</head>
<body>
<div><input type="text" id="sendMsg">
    <button onclick="handleSend()">发送</button>
</div>
<div id="content"></div>
</body>
</html>
```

## 基于 @ServerEndpoint(...) 注解 式服务

> 依赖不用变，唯一不同的是，需要添加一个 bean。

### 配置一个Websocket服务暴露Bean

```java
@Configuration
public class WebsocketServerConfig {
    @Bean
    public ServerEndpointExporter serverEndpointExporter(){
        return new ServerEndpointExporter();
    }
}
```

### 添加websocket服务（应该可以添加多个，可以看上面new的那个bean的初始化逻辑）

```java


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Rao
 * @Date 2021/7/12
 **/
@Slf4j
@Component
@ServerEndpoint("/imserver/{userId}")
public class WebsocketServer {

    /**concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。*/
    private static final ConcurrentHashMap<String,WebsocketServer> WEBSOCKET_SERVER_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();
    /**与某个客户端的连接会话，需要通过它来给客户端发送数据*/
    private Session session;
    /**接收userId*/
    private String userId;

    /**
     * 连接建立成功调用的方法*/
    @OnOpen
    public void onOpen(Session session,@PathParam("userId") String userId) {
        this.session = session;
        this.userId=userId;

        // 先尝试移除
        WEBSOCKET_SERVER_CONCURRENT_HASH_MAP.remove(userId);

        WEBSOCKET_SERVER_CONCURRENT_HASH_MAP.put(userId,this);
        try {
            sendMessage("连接成功");
        } catch (IOException e) {
            log.error("用户:"+userId+",网络异常!!!!!!");
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        WEBSOCKET_SERVER_CONCURRENT_HASH_MAP.remove( userId);

        log.info("用户退出:"+userId+",当前在线人数为:");
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息*/
    @OnMessage
    public void onMessage(String message, Session session) {
        
        log.info("用户消息:"+userId+",报文:"+message);
        //可以群发消息
        //消息保存到数据库、redis
        if(StringUtils.isNotBlank(message)){
            try {
                //解析发送的报文
                JSONObject jsonObject = JSON.parseObject(message);
                //追加发送人(防止串改)
                jsonObject.put("fromUserId",this.userId);
                String toUserId=jsonObject.getString("toUserId");
                //传送给对应toUserId用户的websocket
                if(StringUtils.isNotBlank(toUserId)&&WEBSOCKET_SERVER_CONCURRENT_HASH_MAP.containsKey(toUserId)){
                    WEBSOCKET_SERVER_CONCURRENT_HASH_MAP.get(toUserId).sendMessage(jsonObject.toJSONString());
                }else{
                    log.error("请求的userId:"+toUserId+"不在该服务器上");
                    //否则不在这个服务器上，发送到mysql或者redis
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("用户错误:"+this.userId+",原因:"+error.getMessage());
    }
    /**
     * 实现服务器主动推送
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }


    /**
     * 发送自定义消息
     * */
    public static void sendInfo(String message,@PathParam("userId") String userId) throws IOException {
        log.info("发送消息到:"+userId+"，报文:"+message);
        if(StringUtils.isNotBlank(userId)&&WEBSOCKET_SERVER_CONCURRENT_HASH_MAP.containsKey(userId)){
            WEBSOCKET_SERVER_CONCURRENT_HASH_MAP.get(userId).sendMessage(message);
        }else{
            log.error("用户"+userId+",不在线！");
        }
    }

}
```

```java
@Controller
public class WebcocketController {

    @RequestMapping("/push/{toUserId}")
    public ResponseEntity<String> pushToWeb(String message, @PathVariable String toUserId) throws IOException {
        WebsocketServer.sendInfo(message,toUserId);
        return ResponseEntity.ok("MSG SEND SUCCESS");
    }

}
```

### 前端文件（用于本地测试）

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>websocket通讯</title>
</head>
<script src="https://cdn.bootcss.com/jquery/3.3.1/jquery.js"></script>
<script>
    var socket;
    function openSocket() {
        if(typeof(WebSocket) == "undefined") {
            console.log("您的浏览器不支持WebSocket");
        }else{
            console.log("您的浏览器支持WebSocket");
            //实现化WebSocket对象，指定要连接的服务器地址与端口  建立连接
            //等同于socket = new WebSocket("ws://localhost:8888/xxxx/im/25");
            //var socketUrl="${request.contextPath}/im/"+$("#userId").val();
            var socketUrl="http://127.0.0.1/websocket-server/imserver/"+$("#userId").val();
            socketUrl=socketUrl.replace("https","ws").replace("http","ws");
            console.log(socketUrl);
            if(socket!=null){
                socket.close();
                socket=null;
            }
            socket = new WebSocket(socketUrl);
            //打开事件
            socket.onopen = function() {
                console.log("websocket已打开");
                //socket.send("这是来自客户端的消息" + location.href + new Date());
            };
            //获得消息事件
            socket.onmessage = function(msg) {
                console.log(msg.data);
                //发现消息进入    开始处理前端触发逻辑
            };
            //关闭事件
            socket.onclose = function() {
                console.log("websocket已关闭");
            };
            //发生了错误事件
            socket.onerror = function() {
                console.log("websocket发生了错误");
            }
        }
    }
    function sendMessage() {
        if(typeof(WebSocket) == "undefined") {
            console.log("您的浏览器不支持WebSocket");
        }else {
            console.log("您的浏览器支持WebSocket");
            console.log('{"toUserId":"'+$("#toUserId").val()+'","contentText":"'+$("#contentText").val()+'"}');
            socket.send('{"toUserId":"'+$("#toUserId").val()+'","contentText":"'+$("#contentText").val()+'"}');
        }
    }
</script>
<body>
<p>【userId】：<div><input id="userId" name="userId" type="text" value="10"></div>
<p>【toUserId】：<div><input id="toUserId" name="toUserId" type="text" value="20"></div>
<p>【toUserId】：<div><input id="contentText" name="contentText" type="text" value="hello websocket"></div>
<p>【操作】：<div><a onclick="openSocket()">开启socket</a></div>
<p>【操作】：<div><a onclick="sendMessage()">发送消息</a></div>
</body>

</html>
```

## Github Demo

>[MuJianxuan/springcloud-websocket-demo: Spring Cloud Websocket Demo (github.com)](https://github.com/MuJianxuan/springcloud-websocket-demo)

参考博客：

1、[SpringBoot2.0集成WebSocket，实现后台向前端推送信息_★【World Of Moshow 郑锴】★-CSDN博客](https://blog.csdn.net/moshowgame/article/details/80275084)

2、[通过Spring Cloud Gateway转发WebSocket实现消息推送_渔溪大王的博客-CSDN博客_springcloud websocket](https://blog.csdn.net/weixin_41047704/article/details/93483160)




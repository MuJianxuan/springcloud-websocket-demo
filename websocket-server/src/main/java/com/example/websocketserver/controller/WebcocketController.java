
package com.example.websocketserver.controller;

import com.example.websocketserver.configuration.WebSocketAutoConfig;
import com.example.websocketserver.pojo.RequestMessage;
import com.example.websocketserver.pojo.ResponseMessage;
import com.example.websocketserver.websocket.WebsocketServer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;


/**
 * ********************
 *
 * @author yangke
 * @version 1.0
 * @created 2019年6月6日 下午3:26:39
 * **********************
 */
@Controller
public class WebcocketController {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping(WebSocketAutoConfig.FORE_TO_SERVER_PATH)
    @SendTo(WebSocketAutoConfig.PRODUCER_PATH) // SendTo 发送至 Broker 下的指定订阅路径
    public void say(RequestMessage clientMessage) throws InterruptedException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        ResponseMessage responseMessage = new ResponseMessage("string", "收到的消息为:" + clientMessage.getContent());

        messagingTemplate.convertAndSend(WebSocketAutoConfig.PRODUCER_PATH, objectMapper.writeValueAsString(responseMessage));
    }

    @RequestMapping("/push/{toUserId}")
    public ResponseEntity<String> pushToWeb(String message, @PathVariable String toUserId) throws IOException {
        WebsocketServer.sendInfo(message,toUserId);
        return ResponseEntity.ok("MSG SEND SUCCESS");
    }


}
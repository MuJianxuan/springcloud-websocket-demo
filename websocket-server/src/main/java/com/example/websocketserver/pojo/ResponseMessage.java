package com.example.websocketserver.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseMessage {

	/**
	 * 消息标识
	 */
	private String msgType;
	/**
	 * 发送的消息
	 */
	private String content;

}

package com.example.websocketserver.pojo;

import lombok.Data;

@Data
public class RequestMessage {

	/**
	 * 消息内容
	 */
	private String content;

	/**
	 * 消息接收者
	 */
	private String receiver;
}

package com.jiqu.community.exception;


import lombok.Data;
import lombok.EqualsAndHashCode;
import com.jiqu.community.enums.BaseCode;


@EqualsAndHashCode(callSuper = true)
@Data
public class JiquFrameException extends BaseException {
	
	private Integer code;
	
	private String message;

	public JiquFrameException() {
		super();
	}

	public JiquFrameException(String message) {
		super(message);
	}
	
	public JiquFrameException(Integer code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}
	
	public JiquFrameException(BaseCode baseCode) {
		super(baseCode.getMsg());
		this.code = baseCode.getCode();
		this.message = baseCode.getMsg();
	}

	public JiquFrameException(Throwable cause) {
		super(cause);
	}

	public JiquFrameException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}
}

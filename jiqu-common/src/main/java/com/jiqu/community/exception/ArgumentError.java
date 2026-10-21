package com.jiqu.community.exception;

import lombok.Data;


@Data
public class ArgumentError {
	
	private String argumentName;
	
	private String message;
}

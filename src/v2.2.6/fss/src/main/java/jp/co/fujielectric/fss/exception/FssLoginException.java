/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.exception;

import lombok.Getter;
import lombok.Setter;

/**
 * ログイン例外
 */
@SuppressWarnings("serial")
public class FssLoginException extends Exception {
    public enum Code {
        ALREADY_LOGGED_IN,
        NOTHING_USER,
        NOTHING_PASSWORD,
        UNMATCH_PASWORD,
        EARLY_ACCESS,
        EXPIRED_ACCESS,
        PW_LOGIN_LOCKED,
        LOGIN_LOCK,        
    }

    @Getter
    private final Code code;
    
    /**
     * エラーメッセージ
     */
    @Getter
    @Setter
    private String loginErrMsg;

    public FssLoginException(Code _code) {
        super();
        this.code = _code;
        this.loginErrMsg = "";
    }

    public FssLoginException(Code _code, String _errMsg) {
        super();
        this.code = _code;
        this.loginErrMsg = _errMsg;
    }
}
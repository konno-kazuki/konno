/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.exception;

import lombok.Getter;
import lombok.Setter;

/**
 * FSS固有例外
 */
@SuppressWarnings("serial")
public class FssException extends Exception {   
    /**
     * コード
     */
    @Getter
    @Setter
    private int code = 0;

    /**
     * フラグ
     */
    @Getter
    @Setter
    private boolean flg = false;
    
    public FssException() {
        super();
    }

    public FssException(int code) {
        super();
        this.code = code;
    }
    
    public FssException(String message) {
        super(message);
    }

    public FssException(int code, String message) {
        super(message);
        this.code = code;
    }
    
    public FssException(String message, Throwable cause) {
        super(message, cause);
    }

    //[v2.2.3]
    public FssException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    //[v2.2.3]
    public FssException(int code, boolean flg, String message) {
        super(message);
        this.code = code;
        this.flg = flg;
    }

    //[v2.2.3]
    public FssException(int code, boolean flg, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.flg = flg;
    }    
}
